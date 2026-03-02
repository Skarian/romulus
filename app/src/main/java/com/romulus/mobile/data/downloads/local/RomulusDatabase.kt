package com.romulus.mobile.data.downloads.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadTaskEntity::class, QueueRunEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DownloadStateConverter::class)
abstract class RomulusDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun queueRunDao(): QueueRunDao
}
