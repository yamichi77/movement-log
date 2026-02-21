package com.yamichi77.movement_log.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoveLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MoveLogEntity)

    @Query("SELECT * FROM move_log ORDER BY recordedAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<MoveLogEntity?>

    @Query("SELECT COUNT(*) FROM move_log")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM move_log ORDER BY recordedAtEpochMillis DESC LIMIT 50")
    fun observeLatestFifty(): Flow<List<MoveLogEntity>>

    @Query("SELECT * FROM move_log ORDER BY recordedAtEpochMillis ASC")
    fun observeAllByRecordedAtAsc(): Flow<List<MoveLogEntity>>

    @Query("SELECT * FROM move_log WHERE isUploaded = 0 ORDER BY recordedAtEpochMillis ASC LIMIT :limit")
    suspend fun getPendingUploads(limit: Int): List<MoveLogEntity>

    @Query("UPDATE move_log SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)
}
