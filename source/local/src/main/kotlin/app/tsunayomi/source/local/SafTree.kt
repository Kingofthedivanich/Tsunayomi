package app.tsunayomi.source.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

internal data class DocumentEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val lastModified: Long,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val nameWithoutExtension: String get() = if (isDirectory) name else name.substringBeforeLast('.')
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

/**
 * Thin wrapper over DocumentsContract. DocumentFile issues one query per property,
 * which is painfully slow on large libraries; here one query lists a whole folder.
 */
internal class SafTree(private val resolver: ContentResolver) {
    fun children(parent: Uri): List<DocumentEntry> {
        val tree = treeOf(parent)
        // A picked tree root (…/tree/<id>) has no document part; anything below it does (…/tree/<id>/document/<id>).
        val parentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    add(
                        DocumentEntry(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            documentId = id,
                            name = cursor.getString(1).orEmpty(),
                            mimeType = cursor.getString(2).orEmpty(),
                            lastModified = cursor.getLong(3),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    fun document(uri: Uri): DocumentEntry? =
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            DocumentEntry(
                uri = uri,
                documentId = cursor.getString(0),
                name = cursor.getString(1).orEmpty(),
                mimeType = cursor.getString(2).orEmpty(),
                lastModified = cursor.getLong(3),
            )
        }

    fun readText(uri: Uri): String? = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }

    private fun treeOf(uri: Uri): Uri =
        DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
