package com.lockdown.strictblocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockItem::class], version = 1, exportSchema = false)
abstract class LockdownDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao

    companion object {
        @Volatile
        private var instance: LockdownDatabase? = null

        fun get(context: Context): LockdownDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LockdownDatabase::class.java,
                    "lockdown.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
