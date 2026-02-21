package com.yamichi77.movement_log.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "move_log")
data class MoveLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val recordedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val activityStatus: String,
    val accuracy: Float,
    val isUploaded: Boolean,
)
