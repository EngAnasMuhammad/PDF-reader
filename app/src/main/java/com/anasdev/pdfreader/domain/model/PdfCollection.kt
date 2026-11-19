package com.anasdev.pdfreader.domain.model

data class CollectionFile(
    val uri: String,
    val title: String,
    val addedAt: Long
)

data class PdfCollection(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val files: List<CollectionFile>
)
