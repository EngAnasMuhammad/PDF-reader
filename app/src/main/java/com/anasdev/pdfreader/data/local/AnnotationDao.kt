package com.anasdev.pdfreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM pdf_annotations WHERE fileUri = :fileUri ORDER BY createdAt ASC")
    fun getAnnotationsForFile(fileUri: String): Flow<List<PdfAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(entity: PdfAnnotationEntity)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: String)

    @Query("DELETE FROM pdf_annotations WHERE fileUri = :fileUri")
    suspend fun clearAnnotationsForFile(fileUri: String)
}
