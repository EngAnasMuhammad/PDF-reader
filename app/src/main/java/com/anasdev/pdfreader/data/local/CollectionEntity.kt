package com.anasdev.pdfreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_files",
    primaryKeys = ["collectionId", "fileUri"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("collectionId"),
        Index("fileUri")
    ]
)
data class CollectionFileEntity(
    val collectionId: Long,
    val fileUri: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis()
)
