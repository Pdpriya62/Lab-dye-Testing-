package com.team11.labdip.ui.live

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team11.labdip.LabDipApp
import com.team11.labdip.data.CameraRepository
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.model.CamShot
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.model.RIG_STALE_MS
import com.team11.labdip.data.model.RigLink
import com.team11.labdip.data.model.Status
import com.team11.labdip.data.model.rigLink
import com.team11.labdip.data.model.labToRgb
import com.team11.labdip.data.model.statusFor
import com.team11.labdip.data.mqtt.ConnectionState
import com.team11.labdip.data.mqtt.MqttRepository
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Metric { DeltaE, Bath, Ambient, Humidity, Light }

data class Rgb(val r: Int, val g: Int, val b: Int)

data class LiveUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val reading: ReadingEntity? = null,
    val settings: AppSettings = AppSettings(),
    val metric: Metric = Metric.DeltaE,
    val dropped: Int = 0,
    val now: Long = System.currentTimeMillis(),
    val rigOnline: Boolean? = null,
    val cam: CamShot? = null,
) {
    val status: Status? get() = reading?.let { statusFor(it.deltaE, settings.passMax, settings.marginalMax) }

    val camAgeMs: Long get() = cam?.let { now - it.receivedAt } ?: 0
    /** Same Pass/Marginal limits as the sensor, applied to the camera's ΔE2000. */
    val camStatus: Status? get() = cam?.result?.deltaE2000?.let { statusFor(it, settings.passMax, settings.marginalMax) }
    val camOtherSample: Boolean get() = cam != null && reading != null && cam.result.sampleId != reading.sampleId
    val camOld: Boolean get() = cam != null && camAgeMs > CAM_OLD_MS

    /** Camera's own target when it sent one, else the live target. */
    val camTarget: Rgb? get() {
        val c = cam?.result ?: return null
        if (c.targetL != null && c.targetA != null && c.targetB != null) {
            return labToRgb(c.targetL, c.targetA, c.targetB).let { Rgb(it.first, it.second, it.third) }
        }
        return target
    }

    /** Firmware target when it says one is set, else the manual target from Settings. */
    val target: Rgb? get() {
        val r = reading
        if (r != null && r.targetSet && r.targetL != null && r.targetA != null && r.targetB != null) {
            return labToRgb(r.targetL, r.targetA, r.targetB).let { Rgb(it.first, it.second, it.third) }
        }
        val s = settings
        if (s.targetL != null && s.targetA != null && s.targetB != null) {
            return labToRgb(s.targetL, s.targetA, s.targetB).let { Rgb(it.first, it.second, it.third) }
        }
        return null
    }

    val ageMs: Long get() = reading?.let { now - it.receivedAt } ?: 0
    val rig: RigLink get() = rigLink(rigOnline, reading?.let { now - it.receivedAt })
    val stale: Boolean get() = connection !is ConnectionState.Connected || rig != RigLink.Online
    val bathAlert: Boolean get() = reading?.bathAlert == true && ageMs < STALE_MS

    companion object {
        const val STALE_MS = RIG_STALE_MS
        const val CAM_OLD_MS = 10 * 60_000L
    }
}

class LiveViewModel(
    mqtt: MqttRepository,
    readings: ReadingRepository,
    settings: SettingsRepository,
    private val vibrator: Vibrator?,
    camera: CameraRepository,
) : ViewModel() {
    private val metric = MutableStateFlow(Metric.DeltaE)
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(1_000) } }

    val state: StateFlow<LiveUiState> = combine(
        combine(mqtt.state, mqtt.rigOnline) { c, r -> c to r }, readings.latest(), settings.settings, metric,
        combine(readings.dropped, camera.dropped, ticker, camera.latest) { d, cd, t, cam -> Triple(d + cd, t, cam) },
    ) { (conn, rigOnline), reading, s, m, (dropped, now, cam) ->
        LiveUiState(conn, reading, s, m, dropped, now, rigOnline, cam)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    init {
        // One buzz when the bath alert starts on a fresh reading, not on every recomposition or rotation
        viewModelScope.launch {
            var previous = false
            readings.latest()
                .map { it != null && it.bathAlert && System.currentTimeMillis() - it.receivedAt < LiveUiState.STALE_MS }
                .distinctUntilChanged()
                .collect { alert ->
                    if (alert && !previous) vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                    previous = alert
                }
        }
    }

    fun select(m: Metric) { metric.value = m }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LabDipApp
                val c = app.container
                LiveViewModel(c.mqtt, c.readings, c.settings, app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator, c.camera)
            }
        }
    }
}
