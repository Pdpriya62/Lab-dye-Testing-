package com.team11.labdip

import app.cash.turbine.test
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.db.ReadingDao
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.db.SampleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDao : ReadingDao {
    val rows = mutableListOf<ReadingEntity>()
    override suspend fun insert(r: ReadingEntity) { rows += r }
    override suspend fun insertAll(rows: List<ReadingEntity>) { this.rows += rows }
    override fun latest(): Flow<ReadingEntity?> = emptyFlow()
    override fun trend(id: String, n: Int): Flow<List<ReadingEntity>> = emptyFlow()
    override suspend fun allFor(id: String) = rows.filter { it.sampleId == id }
    override fun samples(): Flow<List<SampleSummary>> = emptyFlow()
    override suspend fun deleteSample(id: String) { rows.removeAll { it.sampleId == id } }
}

class ReadingRepositoryTest {
    private val good = """{"sample_id":"S1","timestamp":"t","deltaE":0.5,"R":1,"G":2,"B":3,"L":50,"a":0,"b":0,"bath_alert":false}""".encodeToByteArray()

    @Test fun validInsertedInvalidCounted() = runTest {
        val dao = FakeDao()
        var now = 0L
        val repo = ReadingRepository(dao) { now }
        repo.dropped.test {
            assertEquals(0, awaitItem())
            repo.ingest(good)
            now += 3_000
            repo.ingest("{bad".encodeToByteArray())
            assertEquals(1, awaitItem())
        }
        assertEquals(1, dao.rows.size)
        assertEquals(0L, dao.rows[0].receivedAt)
    }

    @Test fun floodIsRateLimited() = runTest {
        val dao = FakeDao()
        var now = 5_000L
        val repo = ReadingRepository(dao) { now }
        repeat(25) { repo.ingest(good) }
        assertEquals(ReadingRepository.MAX_PER_SECOND, dao.rows.size)
        assertEquals(15, repo.dropped.value)
        now += 1_000
        repo.ingest(good)
        assertEquals(ReadingRepository.MAX_PER_SECOND + 1, dao.rows.size)
    }

    @Test fun deleteReturnsRowsForUndo() = runTest {
        val dao = FakeDao()
        val repo = ReadingRepository(dao) { 0L }
        repo.ingest(good)
        val removed = repo.deleteSample("S1")
        assertEquals(0, dao.rows.size)
        repo.restore(removed)
        assertEquals(1, dao.rows.size)
    }
}
