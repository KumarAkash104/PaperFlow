package com.paperflow.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.paperflow.app.data.local.AppDatabase
import com.paperflow.app.data.model.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val photoDao = db.photoDao()
    private val appContext = context.applicationContext

    fun getAllPhotos(): Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    suspend fun getAllPhotosSync(): List<PhotoEntity> = photoDao.getAllPhotosSync()

    fun searchPhotos(query: String): Flow<List<PhotoEntity>> = photoDao.searchPhotos(query)

    suspend fun getPhoto(id: String): PhotoEntity? = photoDao.getPhotoById(id)

    suspend fun addPhoto(title: String, imagePath: String, width: Int, height: Int, fileSize: Long, originalPath: String? = null): PhotoEntity {
        val photo = PhotoEntity(
            title = title,
            imagePath = imagePath,
            originalPath = originalPath,
            fileSize = fileSize,
            width = width,
            height = height
        )
        photoDao.insertPhoto(photo)
        return photo
    }

    suspend fun updatePhoto(photo: PhotoEntity) {
        photoDao.updatePhoto(photo.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePhoto(id: String) {
        val photo = photoDao.getPhotoById(id) ?: return
        // Delete photo files
        File(photo.imagePath).delete()
        photo.originalPath?.let { File(it).delete() }
        photoDao.deletePhotoById(id)
    }

    suspend fun renamePhoto(id: String, newTitle: String) {
        val photo = photoDao.getPhotoById(id) ?: return
        photoDao.updatePhoto(
            photo.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        )
    }

    fun createPhotoImageFile(): File {
        val folder = File(appContext.filesDir, "photos")
        if (!folder.exists()) folder.mkdirs()
        val name = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(folder, name)
    }

    suspend fun importPhotoFromUri(uri: Uri, title: String): PhotoEntity? = withContext(Dispatchers.IO) {
        try {
            val destinationFile = createPhotoImageFile()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (destinationFile.exists() && destinationFile.length() > 0) {
                // Decode bounds to get dimensions
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destinationFile.absolutePath, options)
                
                // Add to DB
                return@withContext addPhoto(
                    title = title,
                    imagePath = destinationFile.absolutePath,
                    width = options.outWidth,
                    height = options.outHeight,
                    fileSize = destinationFile.length(),
                    originalPath = destinationFile.absolutePath // original backup
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
