package com.team11.labdip.data.mqtt

import java.util.Locale

/** Command and reply topics sit next to the data topic: `rvu/team11/labdip/data` gives `.../cmd` and `.../ack`, as in the firmware. */
fun commandTopic(dataTopic: String) = sibling(dataTopic, "cmd")
fun ackTopic(dataTopic: String) = sibling(dataTopic, "ack")
fun statusTopic(dataTopic: String) = sibling(dataTopic, "status")
/** Camera station results from `camera/cam_capture.py --mqtt`. */
fun camTopic(dataTopic: String) = sibling(dataTopic, "cam")

private fun sibling(dataTopic: String, leaf: String) =
    dataTopic.substringBeforeLast('/', "").let { if (it.isEmpty()) leaf else "$it/$leaf" }

/**
 * Builds the same text commands the firmware accepts on serial. The limits match `runCommand()` in
 * `lab_dip_esp32.ino`, so bad input is caught here instead of costing a round trip.
 */
object RigCommands {
    const val MAX_REPLY_BYTES = 128
    private val text = Regex("[A-Za-z0-9 _.-]{1,24}")

    fun id(v: String) = textCommand("ID", v, "Sample ID")
    fun fabric(v: String) = textCommand("FABRIC", v, "Fabric")
    fun dye(v: String) = textCommand("DYE", v, "Dye")
    fun capture(): Result<String> = Result.success("CAPTURE")
    fun pause(): Result<String> = Result.success("PAUSE")
    fun resume(): Result<String> = Result.success("RESUME")

    fun target(l: String, a: String, b: String): Result<String> = runCatching {
        "TARGET ${fmt(num(l, 0.0, 100.0, "L"))} ${fmt(num(a, -128.0, 128.0, "a"))} ${fmt(num(b, -128.0, 128.0, "b"))}"
    }

    fun bath(v: String): Result<String> = runCatching { "BATH ${fmt(num(v, 0.0, 130.0, "Bath setpoint"))}" }
    fun conc(v: String): Result<String> = runCatching { "CONC ${fmt(num(v, 0.0, 100.0, "Concentration"))}" }

    /** Rig replies are untrusted too: short printable ASCII only. Null means drop. */
    fun cleanReply(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_REPLY_BYTES) return null
        if (bytes.any { it < 32 || it > 126 }) return null
        return bytes.decodeToString()
    }

    private fun textCommand(cmd: String, v: String, label: String) = runCatching {
        val t = v.trim()
        require(text.matches(t)) { "$label: 1 to 24 letters, digits, spaces, _ . or -" }
        "$cmd $t"
    }

    private fun num(s: String, min: Double, max: Double, label: String): Double {
        val v = s.trim().toDoubleOrNull()
        require(v != null && v.isFinite() && v in min..max) { "$label: enter a number from ${whole(min)} to ${whole(max)}" }
        return v
    }

    private fun whole(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}
