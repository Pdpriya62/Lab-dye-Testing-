package com.team11.labdip.data.model

import kotlinx.serialization.json.Json

/** Validates untrusted MQTT payloads (rules in security.md, section 1). Never throws. */
object ReadingParser {
    const val MAX_BYTES = 2048
    private const val MAX_TEXT = 32

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val allowedText = Regex("[A-Za-z0-9 _.-]*")

    fun parse(bytes: ByteArray): Result<Reading> = runCatching {
        require(bytes.size <= MAX_BYTES) { "payload too large: ${bytes.size} B" }
        val r = json.decodeFromString<Reading>(bytes.decodeToString())
        r.checkRanges()
        r.copy(sampleId = cleanText(r.sampleId) ?: "?", fabric = cleanText(r.fabric), dye = cleanText(r.dye))
    }

    /** Trimmed text, null when blank, "?" when too long or outside the whitelist. */
    fun cleanText(s: String?): String? {
        val t = s?.trim()
        if (t.isNullOrEmpty()) return null
        return if (t.length <= MAX_TEXT && allowedText.matches(t)) t else "?"
    }

    private fun Reading.checkRanges() {
        check("deltaE", deltaE, 0.0, 300.0)
        check("R", red.toDouble(), 0.0, 255.0)
        check("G", green.toDouble(), 0.0, 255.0)
        check("B", blue.toDouble(), 0.0, 255.0)
        check("L", labL, 0.0, 100.0)
        check("a", labA, -128.0, 128.0)
        check("b", labB, -128.0, 128.0)
        check("ambient_temp", ambientTemp, -20.0, 80.0)
        check("humidity", humidity, 0.0, 100.0)
        check("light_lux", lightLux, 0.0, 100_000.0)
        check("bath_temp", bathTemp, -10.0, 130.0)
        check("setpoint_c", setpointC, 0.0, 130.0)
        check("dye_conc", dyeConc, 0.0, 100.0)
        check("target_L", targetL, 0.0, 100.0)
        check("target_a", targetA, -128.0, 128.0)
        check("target_b", targetB, -128.0, 128.0)
    }

    private fun check(name: String, v: Double?, min: Double, max: Double) {
        if (v == null) return
        require(v.isFinite() && v in min..max) { "$name out of range: $v" }
    }
}
