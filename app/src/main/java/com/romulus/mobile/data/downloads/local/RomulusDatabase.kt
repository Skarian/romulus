package com.romulus.mobile.data.downloads.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadTaskEntity::class, QueueRunEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DownloadStateConverter::class)
abstract class RomulusDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun queueRunDao(): QueueRunDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE download_tasks ADD COLUMN sourceDisplayName TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
