package com.anasdev.pdfreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Transaction
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollectionsWithFiles(): Flow<List<CollectionWithFiles>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollectionById(collectionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFileToCollection(entity: CollectionFileEntity)

    @Query("DELETE FROM collection_files WHERE collectionId = :collectionId AND fileUri = :fileUri")
    suspend fun removeFileFromCollection(collectionId: Long, fileUri: String)

    @Query("SELECT DISTINCT fileUri FROM collection_files")
    suspend fun getAllCollectionFileUris(): List<String>

    @Query("DELETE FROM collection_files WHERE fileUri = :fileUri")
    suspend fun deleteFileFromAllCollections(fileUri: String)
}
