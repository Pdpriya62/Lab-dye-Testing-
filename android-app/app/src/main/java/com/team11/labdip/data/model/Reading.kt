package com.team11.labdip.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One JSON message from `lab_dip_esp32.ino` `publishReading()`. Fields without a default are required. */
@Serializable
data class Reading(
    @SerialName("sample_id") val sampleId: String,
    val timestamp: String,
    val deltaE: Double,
    @SerialName("R") val red: Int,
    @SerialName("G") val green: Int,
    @SerialName("B") val blue: Int,
    @SerialName("L") val labL: Double,
    @SerialName("a") val labA: Double,
    @SerialName("b") val labB: Double,
    @SerialName("ambient_temp") val ambientTemp: Double? = null,
    val humidity: Double? = null,
    // Firmware sends round(float), which may serialize as 412 or 412.0
    @SerialName("light_lux") val lightLux: Double? = null,
    @SerialName("bath_temp") val bathTemp: Double? = null,
    @SerialName("bath_alert") val bathAlert: Boolean,
    @SerialName("setpoint_c") val setpointC: Double? = null,
    @SerialName("target_L") val targetL: Double? = null,
    @SerialName("target_a") val targetA: Double? = null,
    @SerialName("target_b") val targetB: Double? = null,
    @SerialName("target_set") val targetSet: Boolean = false,
    val fabric: String? = null,
    val dye: String? = null,
    @SerialName("dye_conc") val dyeConc: Double? = null,
)
