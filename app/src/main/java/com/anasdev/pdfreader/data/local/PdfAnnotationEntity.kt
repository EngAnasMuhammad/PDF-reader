package com.anasdev.pdfreader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_annotations",
    indices = [Index(value = ["fileUri"])]
)
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val fileUri: String,
    val pageIndex: Int,
    val type: String,
    val payloadJson: String,
    val createdAt: Long
)
