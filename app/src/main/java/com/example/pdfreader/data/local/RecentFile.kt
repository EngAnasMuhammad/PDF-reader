package com.example.pdfreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val uri: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val lastPage: Int = 0
)