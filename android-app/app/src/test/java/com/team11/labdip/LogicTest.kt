package com.team11.labdip

import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.db.csvCell
import com.team11.labdip.data.db.toCsv
import com.team11.labdip.data.model.Status
import com.team11.labdip.data.model.labToRgb
import com.team11.labdip.data.model.rgbToLab
import com.team11.labdip.data.model.statusFor
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.ui.settings.Field
import com.team11.labdip.ui.settings.toForm
import com.team11.labdip.ui.settings.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StatusTest {
    @Test fun boundariesAreInclusive() {
        assertEquals(Status.Pass, statusFor(1.0, 1.0, 2.0))
        assertEquals(Status.Marginal, statusFor(1.01, 1.0, 2.0))
        assertEquals(Status.Marginal, statusFor(2.0, 1.0, 2.0))
        assertEquals(Status.Fail, statusFor(2.01, 1.0, 2.0))
        assertEquals(Status.Pass, statusFor(0.0, 1.0, 2.0))
    }
}

class ColorMathTest {
    @Test fun labRoundTripWithinOneStep() {
        for ((r, g, b) in listOf(Triple(182, 40, 52), Triple(10, 120, 200), Triple(255, 255, 255), Triple(0, 0, 0), Triple(128, 128, 128))) {
            val (l, a, bb) = rgbToLab(r, g, b)
            val (r2, g2, b2) = labToRgb(l, a, bb)
            assertTrue("$r,$g,$b -> $r2,$g2,$b2", abs(r - r2) <= 1 && abs(g - g2) <= 1 && abs(b - b2) <= 1)
        }
    }

    @Test fun whiteIsL100() {
        assertEquals(100.0, rgbToLab(255, 255, 255).first, 0.01)
    }
}

class CsvTest {
    private fun row(dye: String?) = ReadingEntity(
        sampleId = "S001", deviceTime = "2026-10-29T10:15:02", receivedAt = 0, deltaE = 1.42, r = 1, g = 2, b = 3,
        labL = 41.2, labA = -5.5, labB = 30.1, ambientTemp = null, humidity = 58.0, lightLux = 1180, bathTemp = null,
        bathAlert = true, setpointC = 60.0, targetL = null, targetA = null, targetB = null, targetSet = false,
        fabric = "cotton", dye = dye, dyeConc = null,
    )

    @Test fun headerMatchesLoggerPy() {
        val first = toCsv(emptyList()).lineSequence().first()
        assertEquals("sample_id,timestamp,fabric,dye,dye_conc,deltaE,R,G,B,L,a,b,ambient_temp,humidity,light_lux,bath_temp,setpoint_c,bath_alert", first)
    }

    @Test fun rowFormat() {
        val line = toCsv(listOf(row("reactive_red"))).lines()[1]
        assertEquals("S001,2026-10-29T10:15:02,cotton,reactive_red,,1.42,1,2,3,41.2,-5.5,30.1,,58.0,1180,,60.0,True", line)
    }

    @Test fun formulaInjectionBlocked() {
        assertEquals("'=SUM(A1)", csvCell("=SUM(A1)"))
        assertEquals("'@cmd", csvCell("@cmd"))
        assertEquals("'+x", csvCell("+x"))
        assertEquals("-5.5", csvCell("-5.5"))
        assertEquals("\"a,b\"", csvCell("a,b"))
    }
}

class SettingsValidationTest {
    @Test fun defaultsAreValid() {
        val (s, e) = AppSettings().toForm().validate()
        assertNotNull(s)
        assertTrue(e.isEmpty())
    }

    @Test fun badPortAndHost() {
        val (s, e) = AppSettings().toForm().copy(port = "70000", host = "bad host").validate()
        assertNull(s)
        assertTrue(Field.Port in e && Field.Host in e)
    }

    @Test fun marginalBelowPassRejected() {
        val (_, e) = AppSettings().toForm().copy(passMax = "2", marginalMax = "1").validate()
        assertTrue(Field.MarginalMax in e)
    }

    @Test fun partialTargetRejected() {
        val (_, e) = AppSettings().toForm().copy(targetL = "50").validate()
        assertTrue(Field.Target in e)
    }

    @Test fun fullTargetAccepted() {
        val (s, _) = AppSettings().toForm().copy(targetL = "52.3", targetA = "10.1", targetB = "-4.2").validate()
        assertEquals(-4.2, s!!.targetB!!, 1e-9)
    }
}
