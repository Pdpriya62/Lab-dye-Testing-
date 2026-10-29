package com.team11.labdip.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SampleSummary(
    @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "last_at") val lastAt: Long,
    val n: Int,
    val fabric: String?,
    val dye: String?,
    @ColumnInfo(name = "last_delta_e") val lastDeltaE: Double,
)

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(r: ReadingEntity)

    @Insert
    suspend fun insertAll(rows: List<ReadingEntity>)

    @Query("SELECT * FROM readings ORDER BY received_at DESC LIMIT 1")
    fun latest(): Flow<ReadingEntity?>

    /** Newest first. n = -1 means no limit (SQLite LIMIT -1). */
    @Query("SELECT * FROM readings WHERE sample_id = :id ORDER BY received_at DESC LIMIT :n")
    fun trend(id: String, n: Int): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE sample_id = :id ORDER BY received_at")
    suspend fun allFor(id: String): List<ReadingEntity>

    @Query(
        """
        SELECT r.sample_id,
               MAX(r.received_at) AS last_at,
               COUNT(*)           AS n,
               MAX(r.fabric)      AS fabric,
               MAX(r.dye)         AS dye,
               (SELECT delta_e FROM readings r2
                 WHERE r2.sample_id = r.sample_id
                 ORDER BY received_at DESC LIMIT 1) AS last_delta_e
        FROM readings r
        GROUP BY r.sample_id
        ORDER BY last_at DESC
        """
    )
    fun samples(): Flow<List<SampleSummary>>

    @Query("DELETE FROM readings WHERE sample_id = :id")
    suspend fun deleteSample(id: String)
}
