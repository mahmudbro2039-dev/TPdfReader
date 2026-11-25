package com.example.data.search.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        IndexedDocEntity::class,
        PageTextFts::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SearchIndexDatabase : RoomDatabase() {
    abstract fun searchIndexDao(): SearchIndexDao

    companion object {
        const val SEARCH_INDEX_DB_NAME = "search_index.db"

        @Volatile
        private var instance: SearchIndexDatabase? = null

        fun getInstance(context: Context): SearchIndexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SearchIndexDatabase::class.java,
                    SEARCH_INDEX_DB_NAME
                )
                    // Derived data. Safe to drop and rebuild. NEVER do this in the main DB.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
