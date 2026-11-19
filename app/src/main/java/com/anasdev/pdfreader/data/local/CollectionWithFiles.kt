package com.anasdev.pdfreader.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class CollectionWithFiles(
    @Embedded
    val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "collectionId"
    )
    val files: List<CollectionFileEntity>
)
