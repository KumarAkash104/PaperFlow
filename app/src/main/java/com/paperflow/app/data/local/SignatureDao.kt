package com.paperflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paperflow.app.data.model.SignatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignatureDao {

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    suspend fun getAllSignaturesSync(): List<SignatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: String)
}
