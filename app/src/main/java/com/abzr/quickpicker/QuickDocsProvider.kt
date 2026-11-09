package com.abzr.quickpicker

import android.content.ContentUris
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException

/** Files app ke side menu me "Quick Picker" dikhane ke liye (backup raasta). */
class QuickDocsProvider : DocumentsProvider() {
    private val rootCols = arrayOf(
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
        Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_MIME_TYPES
    )
    private val docCols = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE
    )
    private val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(rootCols)
        c.newRow()
            .add(Root.COLUMN_ROOT_ID, "quick")
            .add(Root.COLUMN_DOCUMENT_ID, "root")
            .add(Root.COLUMN_TITLE, "Quick Picker")
            .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
            .add(Root.COLUMN_ICON, R.drawable.ic_launcher)
            .add(Root.COLUMN_MIME_TYPES, "*/*")
        return c
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(docCols)
        addDoc(c, documentId)
        return c
    }

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?
    ): Cursor {
        val c = MatrixCursor(docCols)
        val ctx = context!!
        if (parentDocumentId == "root") {
            addDoc(c, "photos")
            Shelf.dir(ctx).listFiles()?.sortedByDescending { it.lastModified() }
                ?.forEach { addDoc(c, "s:" + it.name) }
        } else if (parentDocumentId == "photos") {
            try {
                ctx.contentResolver.query(
                    images, arrayOf(MediaStore.Images.Media._ID), null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { q ->
                    var n = 0
                    while (q.moveToNext() && n < 80) { addDoc(c, "p:" + q.getLong(0)); n++ }
                }
            } catch (e: Exception) { }
        }
        return c
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val ctx = context!!
        return when {
            documentId.startsWith("s:") ->
                ParcelFileDescriptor.open(
                    File(Shelf.dir(ctx), File(documentId.substring(2)).name),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            documentId.startsWith("p:") ->
                ctx.contentResolver.openFileDescriptor(
                    ContentUris.withAppendedId(images, documentId.substring(2).toLong()), "r"
                ) ?: throw FileNotFoundException(documentId)
            else -> throw FileNotFoundException(documentId)
        }
    }

    private fun addDoc(c: MatrixCursor, id: String) {
        val ctx = context!!
        when {
            id == "root" -> dirRow(c, id, "Quick Picker")
            id == "photos" -> dirRow(c, id, "Recent photos")
            id.startsWith("s:") -> {
                val f = File(Shelf.dir(ctx), File(id.substring(2)).name)
                if (!f.exists()) throw FileNotFoundException(id)
                fileRow(c, id, f.name, Shelf.mimeOf(f.name), f.lastModified(), f.length())
            }
            id.startsWith("p:") -> {
                val uri = ContentUris.withAppendedId(images, id.substring(2).toLong())
                val q = ctx.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE,
                        MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED
                    ), null, null, null
                ) ?: throw FileNotFoundException(id)
                q.use {
                    if (!it.moveToFirst()) throw FileNotFoundException(id)
                    fileRow(c, id, it.getString(0) ?: "photo", it.getString(1) ?: "image/*",
                        it.getLong(3) * 1000, it.getLong(2))
                }
            }
            else -> throw FileNotFoundException(id)
        }
    }

    private fun dirRow(c: MatrixCursor, id: String, name: String) {
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            .add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_LAST_MODIFIED, null)
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_SIZE, null)
    }

    private fun fileRow(c: MatrixCursor, id: String, name: String, mime: String, modified: Long, size: Long) {
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_MIME_TYPE, mime)
            .add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_LAST_MODIFIED, modified)
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_SIZE, size)
    }
}
