package com.anasdev.pdfreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecentFile::class,
        PdfAnnotationEntity::class,
        CollectionEntity::class,
        CollectionFileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getRecentFileDao(): RecentFileDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. PDF Annotations table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pdf_annotations` (
                        `id` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pdf_annotations_fileUri` ON `pdf_annotations` (`fileUri`)"
                )

                // 2. Collections table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 3. Collection Files table (with cascade delete on collection removal)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collection_files` (
                        `collectionId` INTEGER NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionId`, `fileUri`),
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_files_collectionId` ON `collection_files` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_files_fileUri` ON `collection_files` (`fileUri`)"
                )
            }
        }
    }
}
