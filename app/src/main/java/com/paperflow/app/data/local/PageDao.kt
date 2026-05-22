package com.paperflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.paperflow.app.data.model.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun getPagesByDocument(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesByDocumentSync(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: String)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deletePagesByDocument(documentId: String)
}
