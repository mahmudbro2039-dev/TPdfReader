package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PdfDocumentEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MjPdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var instance: MjPdfDatabase? = null

        fun getInstance(context: Context): MjPdfDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MjPdfDatabase::class.java,
                    "mj_pdf_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
