package com.example.pdfreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY timestamp DESC LIMIT 20")
    fun getRecentFiles(): Flow<List<RecentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFile)

    @Delete
    suspend fun deleteRecentFile(file: RecentFile)

    @Query("DELETE FROM recent_files")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getFileByUri(uri: String): RecentFile?

    @Query("UPDATE recent_files SET lastPage = :lastPage WHERE uri = :uri")
    suspend fun updatePage(uri: String,lastPage: Int)
}