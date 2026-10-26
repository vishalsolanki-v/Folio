package com.folio.app.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Lists folders the user added with ACTION_OPEN_DOCUMENT_TREE (plan §4.7). One query per folder, no DocumentFile. */
object DocumentTree {
    data class Entry(
        val docId: String,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
        val lastModified: Long,
    ) {
        val isFolder: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    fun rootDocId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(treeUri: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    fun displayName(context: Context, treeUri: Uri, docId: String = rootDocId(treeUri)): String =
        runCatching {
            context.contentResolver.query(documentUri(treeUri, docId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }

    /** Children of [docId], hidden (dot) files skipped. Throws if the folder's permission is gone. */
    fun list(context: Context, treeUri: Uri, docId: String): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = context.contentResolver.query(children, columns, null, null, null)
            ?: throw SecurityException("Folder is not accessible")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    add(
                        Entry(
                            docId = c.getString(0),
                            name = name,
                            mime = c.getString(2) ?: "application/octet-stream",
                            sizeBytes = if (c.isNull(3)) -1 else c.getLong(3),
                            lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                        ),
                    )
                }
            }
        }
    }
}
