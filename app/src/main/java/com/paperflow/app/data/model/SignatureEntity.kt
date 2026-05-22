package com.paperflow.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
)
