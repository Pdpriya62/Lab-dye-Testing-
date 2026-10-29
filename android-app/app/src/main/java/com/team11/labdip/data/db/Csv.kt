package com.team11.labdip.data.db

/** Same columns as `FIELDS` in logger.py, so exports read like trials_real.csv. */
val CSV_HEADER = listOf(
    "sample_id", "timestamp", "fabric", "dye", "dye_conc", "deltaE", "R", "G", "B", "L", "a", "b",
    "ambient_temp", "humidity", "light_lux", "bath_temp", "setpoint_c", "bath_alert",
)

fun toCsv(rows: List<ReadingEntity>): String = buildString {
    appendLine(CSV_HEADER.joinToString(","))
    for (r in rows) {
        val cells = listOf(
            r.sampleId, r.deviceTime, r.fabric, r.dye, r.dyeConc, r.deltaE, r.r, r.g, r.b, r.labL, r.labA, r.labB,
            r.ambientTemp, r.humidity, r.lightLux, r.bathTemp, r.setpointC, if (r.bathAlert) "True" else "False",
        )
        appendLine(cells.joinToString(",") { csvCell(it?.toString() ?: "") })
    }
}

/** Blocks spreadsheet formula injection and quotes cells that need it. */
fun csvCell(raw: String): String {
    // Negative numbers are data, not formulas
    val isNumber = raw.toDoubleOrNull() != null
    val safe = if (!isNumber && raw.isNotEmpty() && raw[0] in "=+-@") "'$raw" else raw
    return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}
