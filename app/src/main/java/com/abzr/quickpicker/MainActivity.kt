package com.abzr.quickpicker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object { var redirected = false }

    private lateinit var permStatus: TextView
    private lateinit var defStatus: TextView
    private lateinit var defBtn: Button
    private lateinit var testStatus: TextView
    private lateinit var savedStatus: TextView

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun perm() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                         else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun card(parent: LinearLayout, title: String, desc: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(Ui.CARD, dp(16))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        c.addView(text(title, 17f, Color.WHITE, true))
        if (desc.isNotEmpty()) c.addView(text(desc, 14f, Ui.SUB).apply { setPadding(0, dp(6), 0, 0) })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return c
    }

    private fun status(card: LinearLayout) = text("", 14f, Color.WHITE).apply {
        setPadding(0, dp(10), 0, dp(10)); card.addView(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Ui.BG)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(24))
        }
        col.addView(text("Quick Picker", 30f, Color.WHITE, true))
        col.addView(text("Chrome me upload ke liye halka file picker", 14f, Ui.SUB).apply { setPadding(0, dp(4), 0, dp(20)) })

        val c1 = card(col, "1. Photos permission", "Picker me recent photos dikhane ke liye.")
        permStatus = status(c1)
        c1.addView(Ui.btn(this, "Permission do").apply {
            setOnClickListener { requestPermissions(arrayOf(perm()), 1) }
        })

        val c2 = card(col, "2. Files app ka default hatao",
            "Agar Chrome seedha Files app khol deta hai, to wahan \"Open by default\" → \"Clear defaults\" dabao.")
        defStatus = status(c2)
        defBtn = Ui.btn(this, "Settings kholo").apply { setOnClickListener { openFilesSettings() } }
        c2.addView(defBtn)

        val c3 = card(col, "3. Test karo", "Chrome jaisi hi request bhejta hai. Agar Quick Picker list me aaye to sab theek.")
        testStatus = status(c3)
        c3.addView(Ui.btn(this, "Test picker").apply {
            setOnClickListener {
                val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                startActivityForResult(i, 5)
            }
        })

        val c4 = card(col, "Saved files", "Kisi bhi app se Share → Quick Picker karo, file yahan rahegi.")
        savedStatus = status(c4)
        c4.addView(Ui.btn(this, "Saved files saaf karo", false).apply {
            setOnClickListener { Shelf.dir(this@MainActivity).listFiles()?.forEach { it.delete() }; refresh() }
        })

        setContentView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(-1, -2)) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // Default laga hua mila to ek baar khud Settings khol do
        val d = otherDefault()
        if (d != null && !redirected) {
            redirected = true
            Toast.makeText(this, "Yahan \"Open by default\" → \"Clear defaults\" dabao", Toast.LENGTH_LONG).show()
            openSettingsFor(d)
        }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { refresh() }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req != 5) return
        val u = data?.data ?: data?.clipData?.getItemAt(0)?.uri
        testStatus.text = if (res == RESULT_OK && u != null) {
            val n = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            "✔ File mili: ${n ?: u}"
        } else "Cancel kiya"
    }

    private fun probe() = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")

    /** GET_CONTENT par jo dusra app seedha khulta hai (None => Android list dikhata hai). */
    private fun otherDefault(): String? {
        val p = packageManager.resolveActivity(probe(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return if (p == null || p == "android" || p == packageName) null else p
    }

    private fun label(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun openSettingsFor(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun openFilesSettings() {
        val d = otherDefault()
            ?: packageManager.queryIntentActivities(probe(), PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }.firstOrNull { it != packageName && it != "android" }
        if (d != null) openSettingsFor(d)
        else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun refresh() {
        val ok = checkSelfPermission(perm()) == PackageManager.PERMISSION_GRANTED
        permStatus.text = if (ok) "✔ Permission mil gayi" else "✗ Abhi nahi mili"
        val d = otherDefault()
        defStatus.text = if (d == null) "✔ Koi dusra default nahi, Android list dikhayega"
                         else "✗ ${label(d)} seedha khul raha hai"
        savedStatus.text = "${Shelf.list(this).size} file saved"
    }
}
