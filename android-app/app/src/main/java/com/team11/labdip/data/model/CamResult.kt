package com.team11.labdip.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One JSON message from `camera/cam_capture.py` on `.../cam`. Contract in `app-change.md`. */
@Serializable
data class CamResult(
    @SerialName("sample_id") val sampleId: String,
    val timestamp: String,
    @SerialName("L") val labL: Double,
    @SerialName("a") val labA: Double,
    @SerialName("b") val labB: Double,
    @SerialName("R") val red: Int,
    @SerialName("G") val green: Int,
    @SerialName("B") val blue: Int,
    val grid: Int,
    /** Row-major from top-left; null = cell mostly off the patch, else [L, a, b, ΔE2000 vs patch]. */
    val cells: List<List<Double>?>,
    val roi: String,
    val white: String,
    @SerialName("levelness_max") val levelnessMax: Double? = null,
    @SerialName("levelness_mean") val levelnessMean: Double? = null,
    @SerialName("level_tol") val levelTol: Double? = null,
    val uniform: Boolean? = null,
    @SerialName("target_L") val targetL: Double? = null,
    @SerialName("target_a") val targetA: Double? = null,
    @SerialName("target_b") val targetB: Double? = null,
    val deltaE2000: Double? = null,
    @SerialName("sensor_L") val sensorL: Double? = null,
    @SerialName("sensor_a") val sensorA: Double? = null,
    @SerialName("sensor_b") val sensorB: Double? = null,
    @SerialName("cam_vs_sensor_dE2000") val camVsSensor: Double? = null,
) {
    val hasTarget: Boolean get() = targetL != null && targetA != null && targetB != null
}

/** Received camera result plus the phone time it arrived (the laptop clock may differ). */
data class CamShot(val result: CamResult, val receivedAt: Long)
