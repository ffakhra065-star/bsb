package com.abzr.quickpicker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

data class Item(val uri: Uri, val name: String, val mime: String)

/** Share ki hui files yahan save hoti hain, aur picker me dikhti hain. */
object Shelf {
    fun dir(c: Context) = File(c.filesDir, "shelf").apply { mkdirs() }

    fun list(c: Context): List<Item> =
        dir(c).listFiles()?.sortedByDescending { it.lastModified() }?.map {
            Item(FileProvider.getUriForFile(c, "${c.packageName}.files", it), it.name, mimeOf(it.name))
        } ?: emptyList()

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(name).extension.lowercase())
            ?: "application/octet-stream"

    fun save(c: Context, src: Uri): Boolean = try {
        var name = c.contentResolver.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "file_${System.currentTimeMillis()}"
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var f = File(dir(c), name)
        if (f.exists()) f = File(dir(c), "${System.currentTimeMillis()}_$name")
        c.contentResolver.openInputStream(src)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        true
    } catch (e: Exception) {
        false
    }
}
