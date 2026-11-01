package com.team11.labdip.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team11.labdip.LabDipApp
import com.team11.labdip.data.mqtt.MqttRepository
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.data.settings.BrokerSettings
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Text fields as typed; parsed and checked only on save or test. */
data class SettingsForm(
    val host: String = "",
    val port: String = "",
    val tls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val topic: String = "",
    val passMax: String = "",
    val marginalMax: String = "",
    val bathTolerance: String = "",
    val luxMin: String = "",
    val luxMax: String = "",
    val targetL: String = "",
    val targetA: String = "",
    val targetB: String = "",
)

enum class Field { Host, Port, Topic, PassMax, MarginalMax, BathTol, LuxMin, LuxMax, Target }

data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val errors: Map<Field, String> = emptyMap(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val saved: Boolean = false,
)

fun AppSettings.toForm() = SettingsForm(
    host = broker.host, port = broker.port.toString(), tls = broker.tls, username = broker.username,
    password = broker.password, topic = broker.topic,
    passMax = passMax.toString(), marginalMax = marginalMax.toString(), bathTolerance = bathTolerance.toString(),
    luxMin = luxMin.toInt().toString(), luxMax = luxMax.toInt().toString(),
    targetL = targetL?.toString() ?: "", targetA = targetA?.toString() ?: "", targetB = targetB?.toString() ?: "",
)

/** Returns settings or field errors. Pure, so it is unit-tested. */
fun SettingsForm.validate(): Pair<AppSettings?, Map<Field, String>> {
    val e = mutableMapOf<Field, String>()
    val h = host.trim()
    if (h.isEmpty() || h.length > 253 || h.any { it.isWhitespace() || it == '/' }) e[Field.Host] = "Enter a host name like broker.hivemq.com"
    val p = port.trim().toIntOrNull()
    if (p == null || p !in 1..65535) e[Field.Port] = "Port must be 1 to 65535"
    val t = topic.trim()
    if (t.isEmpty() || t.length > 200) e[Field.Topic] = "Enter the topic the rig publishes on"
    // Commands go to a topic derived from this one, and MQTT can't publish to a wildcard
    else if (t.any { it == '+' || it == '#' }) e[Field.Topic] = "Use the exact topic, without + or #"
    fun num(f: Field, s: String, min: Double, max: Double): Double? {
        val v = s.trim().toDoubleOrNull()
        if (v == null || !v.isFinite() || v < min || v > max) { e[f] = "Enter a number from ${min.clean()} to ${max.clean()}"; return null }
        return v
    }
    val pass = num(Field.PassMax, passMax, 0.01, 300.0)
    val marg = num(Field.MarginalMax, marginalMax, 0.01, 300.0)
    if (pass != null && marg != null && marg < pass) e[Field.MarginalMax] = "Marginal limit must be at least the pass limit"
    val tol = num(Field.BathTol, bathTolerance, 0.1, 50.0)
    val lMin = num(Field.LuxMin, luxMin, 0.0, 100_000.0)
    val lMax = num(Field.LuxMax, luxMax, 1.0, 100_000.0)
    if (lMin != null && lMax != null && lMax <= lMin) e[Field.LuxMax] = "Upper limit must be above the lower limit"

    val parts = listOf(targetL, targetA, targetB).map { it.trim() }
    var target: Triple<Double, Double, Double>? = null
    if (parts.any { it.isNotEmpty() }) {
        val l = parts[0].toDoubleOrNull()
        val a = parts[1].toDoubleOrNull()
        val b = parts[2].toDoubleOrNull()
        if (l == null || a == null || b == null || l !in 0.0..100.0 || a !in -128.0..128.0 || b !in -128.0..128.0) {
            e[Field.Target] = "Fill all three: L 0 to 100, a and b −128 to 128. Or leave all empty."
        } else target = Triple(l, a, b)
    }
    if (e.isNotEmpty()) return null to e
    return AppSettings(
        onboarded = true,
        broker = BrokerSettings(h, p!!, tls, username.trim(), password, t),
        passMax = pass!!, marginalMax = marg!!, bathTolerance = tol!!, luxMin = lMin!!, luxMax = lMax!!,
        targetL = target?.first, targetA = target?.second, targetB = target?.third,
    ) to emptyMap()
}

private fun Double.clean() = if (this % 1.0 == 0.0) toLong().toString() else toString()

class SettingsViewModel(private val repo: SettingsRepository, private val mqtt: MqttRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val s = repo.settings.first()
            _state.update { it.copy(form = s.toForm(), loaded = true) }
        }
    }

    fun edit(f: (SettingsForm) -> SettingsForm) = _state.update { it.copy(form = f(it.form), message = null, saved = false) }

    fun resetDefaults() = _state.update { it.copy(form = AppSettings().toForm(), errors = emptyMap(), message = null) }

    fun save() {
        val (s, errors) = _state.value.form.validate()
        if (s == null) {
            _state.update { it.copy(errors = errors, message = "Fix the highlighted fields.", messageIsError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errors = emptyMap()) }
            repo.save(s)
            mqtt.connect(s.broker)
            _state.update { it.copy(busy = false, saved = true, message = "Saved. Connecting to ${s.broker.host}.", messageIsError = false) }
        }
    }

    fun test() {
        val (s, errors) = _state.value.form.validate()
        if (s == null) {
            _state.update { it.copy(errors = errors, message = "Fix the highlighted fields.", messageIsError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errors = emptyMap(), message = "Testing ${s.broker.host}:${s.broker.port}…", messageIsError = false) }
            val r = mqtt.test(s.broker)
            _state.update {
                it.copy(
                    busy = false,
                    message = if (r.isSuccess) "Connected to ${s.broker.host}. Tap Save and reconnect to use it."
                    else "Can't reach ${s.broker.host}: ${r.exceptionOrNull()?.message ?: "unknown error"}. Check Wi-Fi, host and port.",
                    messageIsError = r.isFailure,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as LabDipApp).container
                SettingsViewModel(c.settings, c.mqtt)
            }
        }
    }
}
