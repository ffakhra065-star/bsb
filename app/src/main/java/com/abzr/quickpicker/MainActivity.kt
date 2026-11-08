package com.abzr.quickpicker

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun tv(t: String, size: Float) = TextView(this).apply { text = t; textSize = size; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(tv("Quick Picker", 26f))
        root.addView(tv(
            "Kaise use karein:\n\n" +
            "1. Neeche Photos permission do.\n" +
            "2. Chrome me kisi website par \"Browse file\" dabao.\n" +
            "3. List me \"Quick Picker\" chuno, photo/file tap karo. Bas.\n\n" +
            "Koi bhi file/video/PDF: kisi app me Share dabao -> Quick Picker. " +
            "Wo yahan save ho jayegi aur picker ke \"Saved\" tab me milegi.", 16f))
        root.addView(Button(this).apply {
            text = "Photos permission do"
            setOnClickListener {
                val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                        else Manifest.permission.READ_EXTERNAL_STORAGE
                requestPermissions(arrayOf(p), 1)
            }
        })
        info = tv("", 15f)
        root.addView(info)
        root.addView(Button(this).apply {
            text = "Saved files saaf karo"
            setOnClickListener { Shelf.dir(this@MainActivity).listFiles()?.forEach { it.delete() }; refresh() }
        })
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { refresh() }

    private fun refresh() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
        val ok = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        info.text = "Photos permission: ${if (ok) "mil gayi ✔" else "abhi nahi"}\nSaved files: ${Shelf.list(this).size}"
    }
}
