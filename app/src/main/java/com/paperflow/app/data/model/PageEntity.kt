package com.paperflow.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class PageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val imagePath: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
