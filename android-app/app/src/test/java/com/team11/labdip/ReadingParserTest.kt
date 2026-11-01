package com.team11.labdip

import com.team11.labdip.data.model.ReadingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingParserTest {
    // Exactly what publishReading() in lab_dip_esp32.ino emits, with all optional fields
    private val valid = """{"sample_id":"S001","timestamp":"2026-10-29T10:15:02","deltaE":1.42,"R":182,"G":40,"B":52,
        "L":41.2,"a":58.3,"b":30.1,"ambient_temp":27.4,"humidity":58,"light_lux":1180,"bath_temp":61.8,"bath_alert":false,
        "setpoint_c":60,"target_L":42,"target_a":57,"target_b":31,"target_set":true,"fabric":"cotton","dye":"reactive_red","dye_conc":2}"""

    private fun parse(s: String) = ReadingParser.parse(s.encodeToByteArray())

    @Test fun validPayload() {
        val r = parse(valid).getOrThrow()
        assertEquals("S001", r.sampleId)
        assertEquals(1.42, r.deltaE, 1e-9)
        assertEquals(182, r.red)
        assertEquals(30.1, r.labB, 1e-9)
        assertEquals(61.8, r.bathTemp!!, 1e-9)
        assertTrue(r.targetSet)
        assertEquals("reactive_red", r.dye)
    }

    @Test fun minimalPayloadWithoutOptionalFields() {
        val r = parse("""{"sample_id":"S1","timestamp":"1970-01-01T00:00:00","deltaE":0.5,"R":1,"G":2,"B":3,"L":50,"a":0,"b":0,"bath_alert":false}""")
        assertTrue(r.isSuccess)
        assertNull(r.getOrThrow().bathTemp)
        assertFalse(r.getOrThrow().targetSet)
    }

    @Test fun luxAsFloatAccepted() {
        assertEquals(412.0, parse(valid.replace("\"light_lux\":1180", "\"light_lux\":412.0")).getOrThrow().lightLux!!, 1e-9)
    }

    @Test fun unknownKeysIgnored() {
        assertTrue(parse(valid.replace("{", "{\"extra\":123,")).isSuccess)
    }

    @Test fun missingRequiredFieldRejected() {
        assertTrue(parse(valid.replace("\"deltaE\":1.42,", "")).isFailure)
        assertTrue(parse(valid.replace("\"bath_alert\":false,", "")).isFailure)
    }

    @Test fun outOfRangeRejected() {
        assertTrue(parse(valid.replace("\"R\":182", "\"R\":256")).isFailure)
        assertTrue(parse(valid.replace("\"L\":41.2", "\"L\":-1")).isFailure)
        assertTrue(parse(valid.replace("\"deltaE\":1.42", "\"deltaE\":301")).isFailure)
        assertTrue(parse(valid.replace("\"bath_temp\":61.8", "\"bath_temp\":131")).isFailure)
        assertTrue(parse(valid.replace("\"humidity\":58", "\"humidity\":101")).isFailure)
    }

    @Test fun nanAndInfinityRejected() {
        assertTrue(parse(valid.replace("\"deltaE\":1.42", "\"deltaE\":NaN")).isFailure)
        assertTrue(parse(valid.replace("\"deltaE\":1.42", "\"deltaE\":\"NaN\"")).isFailure)
        assertTrue(parse(valid.replace("\"L\":41.2", "\"L\":Infinity")).isFailure)
    }

    @Test fun wrongTypeRejected() {
        assertTrue(parse(valid.replace("\"R\":182", "\"R\":182.5")).isFailure)
        // kotlinx.serialization reads quoted numbers even when not lenient; range checks still apply
        assertTrue(parse(valid.replace("\"R\":182", "\"R\":\"999\"")).isFailure)
        assertTrue(parse(valid.replace("\"R\":182", "\"R\":\"abc\"")).isFailure)
    }

    @Test fun oversizeRejected() {
        val big = valid.replace("\"fabric\":\"cotton\"", "\"fabric\":\"" + "x".repeat(ReadingParser.MAX_BYTES) + "\"")
        assertTrue(parse(big).isFailure)
    }

    @Test fun garbageRejected() {
        assertTrue(parse("not json").isFailure)
        assertTrue(parse("").isFailure)
        assertTrue(ReadingParser.parse(byteArrayOf(0xFF.toByte(), 0x00, 0x7B)).isFailure)
    }

    @Test fun hostileStringsReplaced() {
        val r = parse(valid.replace("\"dye\":\"reactive_red\"", "\"dye\":\"=HYPERLINK(evil)\"")
            .replace("\"sample_id\":\"S001\"", "\"sample_id\":\"" + "A".repeat(40) + "\"")).getOrThrow()
        assertEquals("?", r.dye)
        assertEquals("?", r.sampleId)
    }

    @Test fun cleanTextTrimsAndBlanksToNull() {
        assertEquals("cotton", ReadingParser.cleanText("  cotton "))
        assertNull(ReadingParser.cleanText("   "))
        assertNull(ReadingParser.cleanText(null))
        assertEquals("?", ReadingParser.cleanText("<script>"))
    }
}
