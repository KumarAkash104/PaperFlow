package com.paperflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.data.model.PageEntity
import com.paperflow.app.data.model.SignatureEntity
import com.paperflow.app.data.model.PhotoEntity

@Database(
    entities = [DocumentEntity::class, PageEntity::class, SignatureEntity::class, PhotoEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun signatureDao(): SignatureDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paperflow_database",
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
