package com.team11.labdip.data.model

import kotlinx.serialization.json.Json

/** Validates untrusted `.../cam` payloads from cam_capture.py (same rules as readings, security.md). Never throws. */
object CamParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val rois = setOf("auto", "centre")
    private val whites = setOf("none", "manual", "auto", "flat")

    fun parse(bytes: ByteArray): Result<CamResult> = runCatching {
        require(bytes.size <= ReadingParser.MAX_BYTES) { "payload too large: ${bytes.size} B" }
        val c = json.decodeFromString<CamResult>(bytes.decodeToString())
        lab(c.labL, c.labA, c.labB)
        listOf(c.red, c.green, c.blue).forEach { require(it in 0..255) { "RGB out of range: $it" } }
        require(c.grid in 2..4) { "grid ${c.grid}" }
        require(c.cells.size == c.grid * c.grid) { "cells ${c.cells.size} for grid ${c.grid}" }
        c.cells.forEach { cell ->
            if (cell != null) {
                require(cell.size == 4) { "cell needs 4 numbers" }
                lab(cell[0], cell[1], cell[2])
                de(cell[3])
            }
        }
        require(c.roi in rois) { "roi ${c.roi}" }
        require(c.white in whites) { "white ${c.white}" }
        listOf(c.levelnessMax, c.levelnessMean, c.levelTol).forEach { v ->
            if (v != null) require(v.isFinite() && v in 0.0..100.0) { "levelness out of range: $v" }
        }
        c.deltaE2000?.let(::de)
        c.camVsSensor?.let(::de)
        // All three or none: a partial target or sensor colour is a malformed message
        triple(c.targetL, c.targetA, c.targetB)
        triple(c.sensorL, c.sensorA, c.sensorB)
        require(c.timestamp.length <= 32) { "timestamp too long" }
        c.copy(sampleId = ReadingParser.cleanText(c.sampleId) ?: "?")
    }

    private fun triple(l: Double?, a: Double?, b: Double?) {
        if (l == null && a == null && b == null) return
        require(l != null && a != null && b != null) { "partial Lab" }
        lab(l, a, b)
    }

    private fun lab(l: Double, a: Double, b: Double) {
        require(l.isFinite() && l in 0.0..100.0) { "L out of range: $l" }
        require(a.isFinite() && a in -128.0..128.0) { "a out of range: $a" }
        require(b.isFinite() && b in -128.0..128.0) { "b out of range: $b" }
    }

    private fun de(v: Double) = require(v.isFinite() && v in 0.0..300.0) { "deltaE out of range: $v" }
}
