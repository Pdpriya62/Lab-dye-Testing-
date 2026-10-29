package com.team11.labdip.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
}
