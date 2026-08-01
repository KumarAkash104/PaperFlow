package com.paperflow.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.paperflow.app.data.local.AppDatabase
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.data.model.PageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val documentDao = db.documentDao()
    private val pageDao = db.pageDao()
    private val appContext = context.applicationContext

    // ── Document operations ──

    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    suspend fun getAllDocumentsSync(): List<DocumentEntity> = documentDao.getAllDocumentsSync()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(query)

    suspend fun getDocument(id: String): DocumentEntity? = documentDao.getDocumentById(id)

    suspend fun createDocument(title: String? = null): DocumentEntity {
        val timestamp = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
        val doc = DocumentEntity(title = title ?: "Scan $timestamp")
        documentDao.insertDocument(doc)
        return doc
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(id: String) {
        val pages = pageDao.getPagesByDocumentSync(id)
        pages.forEach { page -> File(page.imagePath).delete() }
        documentDao.deleteDocumentById(id)
    }

    suspend fun renameDocument(id: String, newTitle: String) {
        val doc = documentDao.getDocumentById(id) ?: return
        documentDao.updateDocument(
            doc.copy(title = newTitle, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun copyDocumentsInto(sourceDocumentIds: List<String>, targetDocumentId: String): Int {
        val sources = sourceDocumentIds.filter { it != targetDocumentId }.distinct()
        if (sources.isEmpty()) return 0

        var nextIndex = pageDao.getPagesByDocumentSync(targetDocumentId).size
        var copiedCount = 0
        sources.forEach { sourceId ->
            val pages = pageDao.getPagesByDocumentSync(sourceId)
            pages.forEach { page ->
                val sourceFile = File(page.imagePath)
                if (sourceFile.exists()) {
                    val copyFile = createScanImageFile()
                    sourceFile.copyTo(copyFile, overwrite = true)
                    addPage(targetDocumentId, copyFile.absolutePath, nextIndex)
                    nextIndex += 1
                    copiedCount += 1
                }
            }
        }
        refreshDocumentMeta(targetDocumentId)
        return copiedCount
    }

    suspend fun moveDocumentsInto(sourceDocumentIds: List<String>, targetDocumentId: String): Int {
        val sources = sourceDocumentIds.filter { it != targetDocumentId }.distinct()
        if (sources.isEmpty()) return 0

        var nextIndex = pageDao.getPagesByDocumentSync(targetDocumentId).size
        var movedCount = 0
        sources.forEach { sourceId ->
            val pages = pageDao.getPagesByDocumentSync(sourceId)
            pages.forEach { page ->
                pageDao.updatePage(
                    page.copy(
                        documentId = targetDocumentId,
                        pageIndex = nextIndex,
                    ),
                )
                nextIndex += 1
                movedCount += 1
            }
            documentDao.deleteDocumentById(sourceId)
        }
        refreshDocumentMeta(targetDocumentId)
        return movedCount
    }

    suspend fun copyPagesInto(pageIds: List<String>, targetDocumentId: String): Int {
        var nextIndex = pageDao.getPagesByDocumentSync(targetDocumentId).size
        var copiedCount = 0
        pageIds.distinct().forEach { pageId ->
            val page = pageDao.getPageById(pageId) ?: return@forEach
            if (page.documentId == targetDocumentId) return@forEach // Already in target
            
            val sourceFile = File(page.imagePath)
            if (sourceFile.exists()) {
                val copyFile = createScanImageFile()
                sourceFile.copyTo(copyFile, overwrite = true)
                addPage(targetDocumentId, copyFile.absolutePath, nextIndex)
                nextIndex += 1
                copiedCount += 1
            }
        }
        refreshDocumentMeta(targetDocumentId)
        return copiedCount
    }

    suspend fun movePagesInto(pageIds: List<String>, targetDocumentId: String): Int {
        var nextIndex = pageDao.getPagesByDocumentSync(targetDocumentId).size
        var movedCount = 0
        val affectedSourceDocs = mutableSetOf<String>()
        
        pageIds.distinct().forEach { pageId ->
            val page = pageDao.getPageById(pageId) ?: return@forEach
            if (page.documentId == targetDocumentId) return@forEach // Already in target
            
            affectedSourceDocs.add(page.documentId)
            pageDao.updatePage(
                page.copy(
                    documentId = targetDocumentId,
                    pageIndex = nextIndex
                )
            )
            nextIndex += 1
            movedCount += 1
        }
        
        // Clean up affected source documents (re-index or delete if empty)
        affectedSourceDocs.forEach { sourceDocId ->
            val remainingPages = pageDao.getPagesByDocumentSync(sourceDocId)
            if (remainingPages.isEmpty()) {
                documentDao.deleteDocumentById(sourceDocId)
            } else {
                remainingPages.forEachIndexed { index, p ->
                    pageDao.updatePage(p.copy(pageIndex = index))
                }
                refreshDocumentMeta(sourceDocId)
            }
        }
        
        refreshDocumentMeta(targetDocumentId)
        return movedCount
    }

    // ── Page operations ──

    fun getPages(documentId: String): Flow<List<PageEntity>> =
        pageDao.getPagesByDocument(documentId)

    suspend fun getPagesSync(documentId: String): List<PageEntity> =
        pageDao.getPagesByDocumentSync(documentId)

    suspend fun getPage(pageId: String): PageEntity? =
        pageDao.getPageById(pageId)

    suspend fun addPage(documentId: String, imagePath: String, index: Int): PageEntity {
        val page = PageEntity(
            documentId = documentId,
            imagePath = imagePath,
            pageIndex = index,
        )
        pageDao.insertPage(page)
        refreshDocumentMeta(documentId)
        return page
    }

    suspend fun deletePage(pageId: String) {
        val page = pageDao.getPageById(pageId) ?: return
        File(page.imagePath).delete()
        pageDao.deletePageById(pageId)
        // Re-index remaining pages
        val remaining = pageDao.getPagesByDocumentSync(page.documentId)
        remaining.forEachIndexed { index, p ->
            pageDao.updatePage(p.copy(pageIndex = index))
        }
        refreshDocumentMeta(page.documentId)
    }

    suspend fun reorderPages(documentId: String, pageIds: List<String>) {
        db.withTransaction {
            pageIds.forEachIndexed { index, pageId ->
                val page = pageDao.getPageById(pageId) ?: return@forEachIndexed
                pageDao.updatePage(page.copy(pageIndex = index))
            }
            refreshDocumentMeta(documentId)
        }
    }

    private suspend fun refreshDocumentMeta(documentId: String) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        val pages = pageDao.getPagesByDocumentSync(documentId)
        documentDao.updateDocument(
            doc.copy(
                pageCount = pages.size,
                thumbnailPath = pages.firstOrNull()?.imagePath,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun getDocumentDir(documentId: String): File {
        val folder = File(appContext.filesDir, "doc_$documentId")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun createScanImageFile(extension: String = ".png"): File {
        val folder = File(appContext.filesDir, "scans")
        if (!folder.exists()) folder.mkdirs()
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        val name = "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext"
        return File(folder, name)
    }
}
