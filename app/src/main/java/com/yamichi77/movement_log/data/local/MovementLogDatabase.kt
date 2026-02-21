package com.yamichi77.movement_log.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MoveLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MovementLogDatabase : RoomDatabase() {
    abstract fun moveLogDao(): MoveLogDao

    companion object {
        @Volatile
        private var instance: MovementLogDatabase? = null

        fun getInstance(context: Context): MovementLogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MovementLogDatabase::class.java,
                    "movement_log.db",
                ).build().also { instance = it }
            }
    }
}
