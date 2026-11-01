package com.team11.labdip.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.team11.labdip.data.model.Reading
import kotlin.math.roundToInt

@Entity(
    tableName = "readings",
    indices = [Index("sample_id", "received_at"), Index("received_at")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "device_time") val deviceTime: String,
    // Phone clock; the ESP32 reports 1970 until NTP syncs, so ordering uses this
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "delta_e") val deltaE: Double,
    val r: Int,
    val g: Int,
    val b: Int,
    @ColumnInfo(name = "lab_l") val labL: Double,
    @ColumnInfo(name = "lab_a") val labA: Double,
    @ColumnInfo(name = "lab_b") val labB: Double,
    @ColumnInfo(name = "ambient_temp") val ambientTemp: Double?,
    val humidity: Double?,
    @ColumnInfo(name = "light_lux") val lightLux: Int?,
    @ColumnInfo(name = "bath_temp") val bathTemp: Double?,
    @ColumnInfo(name = "bath_alert") val bathAlert: Boolean,
    @ColumnInfo(name = "setpoint_c") val setpointC: Double?,
    @ColumnInfo(name = "target_l") val targetL: Double?,
    @ColumnInfo(name = "target_a") val targetA: Double?,
    @ColumnInfo(name = "target_b") val targetB: Double?,
    @ColumnInfo(name = "target_set") val targetSet: Boolean,
    val fabric: String?,
    val dye: String?,
    @ColumnInfo(name = "dye_conc") val dyeConc: Double?,
)

fun Reading.toEntity(receivedAt: Long) = ReadingEntity(
    sampleId = sampleId,
    deviceTime = timestamp,
    receivedAt = receivedAt,
    deltaE = deltaE,
    r = red,
    g = green,
    b = blue,
    labL = labL,
    labA = labA,
    labB = labB,
    ambientTemp = ambientTemp,
    humidity = humidity,
    lightLux = lightLux?.roundToInt(),
    bathTemp = bathTemp,
    bathAlert = bathAlert,
    setpointC = setpointC,
    targetL = targetL,
    targetA = targetA,
    targetB = targetB,
    targetSet = targetSet,
    fabric = fabric,
    dye = dye,
    dyeConc = dyeConc,
)
