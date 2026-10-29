package com.team11.labdip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.team11.labdip.data.db.AppDatabase
import com.team11.labdip.data.db.ReadingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private val dao = db.readings()

    private fun row(id: String, at: Long, de: Double) = ReadingEntity(
        sampleId = id, deviceTime = "1970-01-01T00:00:00", receivedAt = at, deltaE = de, r = 1, g = 2, b = 3,
        labL = 50.0, labA = 0.0, labB = 0.0, ambientTemp = null, humidity = null, lightLux = null, bathTemp = null,
        bathAlert = false, setpointC = null, targetL = null, targetA = null, targetB = null, targetSet = false,
        fabric = "cotton", dye = null, dyeConc = null,
    )

    @After fun close() = db.close()

    @Test fun latestOrdersByReceivedAtNotDeviceTime() = runTest {
        dao.latest().test {
            assertNull(awaitItem())
            dao.insert(row("S1", 200, 1.0))
            assertEquals(200L, awaitItem()!!.receivedAt)
            dao.insert(row("S2", 100, 2.0))
            assertEquals(200L, awaitItem()!!.receivedAt)
        }
    }

    @Test fun samplesSummaryAndTrendLimit() = runTest {
        dao.insertAll((1..5).map { row("S1", it * 10L, it.toDouble()) } + row("S2", 1_000, 0.4))
        dao.samples().test {
            val list = awaitItem()
            assertEquals(listOf("S2", "S1"), list.map { it.sampleId })
            assertEquals(5, list[1].n)
            assertEquals(5.0, list[1].lastDeltaE, 1e-9)
        }
        dao.trend("S1", 3).test { assertEquals(listOf(50L, 40L, 30L), awaitItem().map { it.receivedAt }) }
        dao.trend("S1", -1).test { assertEquals(5, awaitItem().size) }
    }

    @Test fun deleteSampleOnlyThatSample() = runTest {
        dao.insertAll(listOf(row("S1", 1, 1.0), row("S2", 2, 1.0)))
        dao.deleteSample("S1")
        assertEquals(0, dao.allFor("S1").size)
        assertEquals(1, dao.allFor("S2").size)
    }
}
