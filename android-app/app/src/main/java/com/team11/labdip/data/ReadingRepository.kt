package com.team11.labdip.data

import com.team11.labdip.data.db.ReadingDao
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.db.toEntity
import com.team11.labdip.data.model.ReadingParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** MQTT bytes in, validated rows into Room. Room is the single source of truth for the UI. */
class ReadingRepository(
    private val dao: ReadingDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _dropped = MutableStateFlow(0)
    /** Messages rejected by the parser or the rate limit since app start. */
    val dropped: StateFlow<Int> = _dropped

    private var windowStart = 0L
    private var windowCount = 0

    /** Call from a single collector; the rate-limit window is not thread-safe. */
    suspend fun ingest(bytes: ByteArray) {
        val now = clock()
        if (now - windowStart >= 1_000) {
            windowStart = now
            windowCount = 0
        }
        if (++windowCount > MAX_PER_SECOND) {
            _dropped.update { it + 1 }
            return
        }
        ReadingParser.parse(bytes).fold(
            onSuccess = { dao.insert(it.toEntity(now)) },
            onFailure = { _dropped.update { it + 1 } },
        )
    }

    fun latest() = dao.latest()
    fun trend(sampleId: String, n: Int) = dao.trend(sampleId, n)
    fun samples() = dao.samples()
    suspend fun allFor(sampleId: String) = dao.allFor(sampleId)

    /** Returns the deleted rows so the caller can offer undo. */
    suspend fun deleteSample(sampleId: String): List<ReadingEntity> {
        val rows = dao.allFor(sampleId)
        dao.deleteSample(sampleId)
        return rows
    }

    suspend fun restore(rows: List<ReadingEntity>) = dao.insertAll(rows)

    companion object {
        const val MAX_PER_SECOND = 10
    }
}
