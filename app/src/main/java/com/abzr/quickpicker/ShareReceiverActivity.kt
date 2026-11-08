package com.abzr.quickpicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND ->
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }
        Thread {
            var n = 0
            uris.forEach { if (Shelf.save(this, it)) n++ }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (n > 0) "$n file Quick Picker me save ho gayi" else "File save nahi hui",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }.start()
    }
}
