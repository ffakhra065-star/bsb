package com.abzr.quickpicker

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Chrome ke upar bottom-sheet jaisa halka picker. */
class PickerActivity : Activity() {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<Uri, Bitmap>(60)
    private val selected = LinkedHashSet<Uri>()
    private var items = listOf<Item>()
    private var multi = false
    private var showGallery = true
    private var asked = false

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var tabGallery: Button
    private lateinit var tabSaved: Button
    private lateinit var doneBtn: Button
    private val adapter = Adapter()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun perm() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                         else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun hasPerm() = checkSelfPermission(perm()) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        multi = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        val t = intent.type
        showGallery = t == null || t == "*/*" || t.startsWith("image")

        val root = FrameLayout(this)
        root.setOnClickListener { setResult(RESULT_CANCELED); finish() }

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(sheet, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.68).toInt(), Gravity.BOTTOM))

        sheet.addView(TextView(this).apply {
            text = "Upload ke liye file chuno"; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), 0, 0, dp(8))
        })

        val tabs = LinearLayout(this)
        tabGallery = Button(this).apply { text = "Photos"; setOnClickListener { showGallery = true; load() } }
        tabSaved = Button(this).apply { text = "Saved"; setOnClickListener { showGallery = false; load() } }
        tabs.addView(tabGallery, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(tabSaved, LinearLayout.LayoutParams(0, -2, 1f))
        sheet.addView(tabs)

        val body = FrameLayout(this)
        grid = GridView(this).apply {
            numColumns = 3; horizontalSpacing = dp(4); verticalSpacing = dp(4)
            this.adapter = this@PickerActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> onPick(items[pos]) }
        }
        empty = TextView(this).apply { gravity = Gravity.CENTER; textSize = 15f; setPadding(dp(24), 0, dp(24), 0) }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        sheet.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this)
        bottom.addView(Button(this).apply {
            text = "Files app"
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(intent.type ?: "*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi)
                startActivityForResult(i, 1)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        doneBtn = Button(this).apply {
            text = "Done"; visibility = if (multi) View.VISIBLE else View.GONE
            setOnClickListener { if (selected.isNotEmpty()) finishWith(selected.toList()) }
        }
        bottom.addView(doneBtn, LinearLayout.LayoutParams(0, -2, 1f))
        sheet.addView(bottom)

        setContentView(root)
        load()
    }

    private fun load() {
        tabGallery.setTypeface(null, if (showGallery) Typeface.BOLD else Typeface.NORMAL)
        tabSaved.setTypeface(null, if (!showGallery) Typeface.BOLD else Typeface.NORMAL)
        if (!showGallery) {
            setItems(Shelf.list(this), "Abhi koi saved file nahi.\nKisi bhi app se Share -> Quick Picker karo.")
            return
        }
        if (!hasPerm()) {
            setItems(emptyList(), "Photos dikhane ke liye permission chahiye.\n(Ya \"Saved\" / \"Files app\" use karo)")
            if (!asked) { asked = true; requestPermissions(arrayOf(perm()), 7) }
            return
        }
        pool.execute {
            val l = queryImages()
            main.post { if (showGallery) setItems(l, "Koi photo nahi mili.") }
        }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { load() }

    private fun setItems(l: List<Item>, emptyMsg: String) {
        items = l
        adapter.notifyDataSetChanged()
        empty.text = emptyMsg
        empty.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun queryImages(): List<Item> {
        val out = mutableListOf<Item>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try {
            contentResolver.query(
                base,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 90) {
                    out.add(Item(ContentUris.withAppendedId(base, c.getLong(0)), c.getString(1) ?: "", c.getString(2) ?: "image/*"))
                }
            }
        } catch (e: Exception) { }
        return out
    }

    private fun onPick(item: Item) {
        if (!multi) { finishWith(listOf(item.uri)); return }
        if (!selected.add(item.uri)) selected.remove(item.uri)
        doneBtn.text = if (selected.isEmpty()) "Done" else "Done (${selected.size})"
        adapter.notifyDataSetChanged()
    }

    private fun finishWith(uris: List<Uri>) {
        val clip = ClipData.newRawUri(null, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        val r = Intent().setData(uris[0]); r.clipData = clip; r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, r)
        finish()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req != 1 || res != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        if (uris.isNotEmpty()) finishWith(uris)
    }

    override fun onDestroy() { super.onDestroy(); pool.shutdownNow() }

    private fun decode(uri: Uri): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= 256) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    } catch (e: Throwable) { null }

    private inner class Cell(c: Context) : FrameLayout(c) {
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val label = TextView(c).apply { gravity = Gravity.CENTER; textSize = 11f; setPadding(dp(4), 0, dp(4), 0) }
        val check = TextView(c).apply {
            text = "✔"; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setBackgroundColor(0x990066FF.toInt())
        }
        init {
            setBackgroundColor(0xFFEEEEEE.toInt())
            addView(img, LayoutParams(-1, -1)); addView(label, LayoutParams(-1, -1)); addView(check, LayoutParams(-1, -1))
        }
        override fun onMeasure(w: Int, h: Int) { super.onMeasure(w, w) }

        fun bind(item: Item) {
            tag = item.uri
            check.visibility = if (selected.contains(item.uri)) View.VISIBLE else View.GONE
            img.setImageDrawable(null)
            if (!item.mime.startsWith("image")) { label.text = item.name; return }
            label.text = ""
            val cached = cache.get(item.uri)
            if (cached != null) { img.setImageBitmap(cached); return }
            pool.execute {
                val b = decode(item.uri) ?: return@execute
                cache.put(item.uri, b)
                main.post { if (tag == item.uri) img.setImageBitmap(b) }
            }
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val cell = (cv as? Cell) ?: Cell(this@PickerActivity)
            cell.bind(items[p])
            return cell
        }
    }
}
