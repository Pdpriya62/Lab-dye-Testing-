package com.team11.labdip.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team11.labdip.LabDipApp
import com.team11.labdip.data.ReadingRepository
import com.team11.labdip.data.mqtt.ConnectionState
import com.team11.labdip.data.mqtt.MqttRepository
import com.team11.labdip.data.mqtt.RigCommands
import com.team11.labdip.data.mqtt.commandTopic
import com.team11.labdip.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Text as typed; checked by [RigCommands] only when sent. */
data class ControlsForm(
    val sampleId: String = "",
    val fabric: String = "",
    val dye: String = "",
    val conc: String = "",
    val bath: String = "",
    val targetL: String = "",
    val targetA: String = "",
    val targetB: String = "",
)

enum class LogKind { Sent, Reply, Problem }

data class LogLine(val at: Long, val kind: LogKind, val text: String)

data class ControlsUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val commandTopic: String = "",
    val form: ControlsForm = ControlsForm(),
    val error: String? = null,
    val sending: Boolean = false,
    val log: List<LogLine> = emptyList(),
    val rigOffline: Boolean = false,
) {
    val canSend: Boolean get() = connection is ConnectionState.Connected && !sending
}

private data class Local(
    val form: ControlsForm = ControlsForm(),
    val error: String? = null,
    val sending: Boolean = false,
    val log: List<LogLine> = emptyList(),
)

class ControlsViewModel(
    private val mqtt: MqttRepository,
    readings: ReadingRepository,
    settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private var lastReplyAt = 0L

    val state: StateFlow<ControlsUiState> = combine(mqtt.state, settings.settings, local, mqtt.rigOnline) { conn, s, l, rig ->
        ControlsUiState(conn, commandTopic(s.broker.topic), l.form, l.error, l.sending, l.log, rigOffline = rig == false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlsUiState())

    init {
        viewModelScope.launch {
            mqtt.replies.collect {
                lastReplyAt = clock()
                log(LogKind.Reply, it)
            }
        }
        // Start from what the rig last reported, so small edits are quick
        viewModelScope.launch {
            val r = readings.latest().first() ?: return@launch
            local.update { l ->
                l.copy(
                    form = l.form.copy(
                        sampleId = r.sampleId,
                        fabric = r.fabric.orEmpty(),
                        dye = r.dye.orEmpty(),
                        conc = r.dyeConc?.toString().orEmpty(),
                        bath = r.setpointC?.toString().orEmpty(),
                        targetL = if (r.targetSet) r.targetL?.toString().orEmpty() else "",
                        targetA = if (r.targetSet) r.targetA?.toString().orEmpty() else "",
                        targetB = if (r.targetSet) r.targetB?.toString().orEmpty() else "",
                    ),
                )
            }
        }
    }

    fun edit(change: (ControlsForm) -> ControlsForm) = local.update { it.copy(form = change(it.form), error = null) }

    fun capture() = send(listOf(RigCommands.capture()))
    fun pause() = send(listOf(RigCommands.pause()))
    fun resume() = send(listOf(RigCommands.resume()))
    fun setSampleId() = send(listOf(RigCommands.id(form().sampleId)))
    fun setTarget() = form().let { send(listOf(RigCommands.target(it.targetL, it.targetA, it.targetB))) }
    fun setBath() = send(listOf(RigCommands.bath(form().bath)))

    fun setDetails() {
        val f = form()
        val cmds = listOfNotNull(
            // ID first: users fill in the whole Sample card and press this one button
            f.sampleId.takeIf { it.isNotBlank() }?.let(RigCommands::id),
            f.fabric.takeIf { it.isNotBlank() }?.let(RigCommands::fabric),
            f.dye.takeIf { it.isNotBlank() }?.let(RigCommands::dye),
            f.conc.takeIf { it.isNotBlank() }?.let(RigCommands::conc),
        )
        if (cmds.isEmpty()) local.update { it.copy(error = "Enter a sample ID, fabric, dye or concentration") } else send(cmds)
    }

    private fun form() = local.value.form

    private fun send(cmds: List<Result<String>>) {
        cmds.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { e ->
            local.update { it.copy(error = e.message) }
            return
        }
        local.update { it.copy(error = null, sending = true) }
        viewModelScope.launch {
            val start = clock()
            for (c in cmds.map { it.getOrThrow() }) {
                log(LogKind.Sent, c)
                val sent = runCatching { mqtt.send(c) }
                if (sent.isFailure) {
                    log(LogKind.Problem, "Not sent: ${sent.exceptionOrNull()?.message ?: "broker error"}")
                    local.update { it.copy(sending = false) }
                    return@launch
                }
            }
            local.update { it.copy(sending = false) }
            delay(REPLY_TIMEOUT_MS)
            if (lastReplyAt < start) log(LogKind.Problem, "No reply from the rig. Is it powered and on Wi-Fi?")
        }
    }

    private fun log(kind: LogKind, text: String) =
        local.update { it.copy(log = (listOf(LogLine(clock(), kind, text)) + it.log).take(MAX_LOG)) }

    companion object {
        const val REPLY_TIMEOUT_MS = 5_000L
        private const val MAX_LOG = 30

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as LabDipApp).container
                ControlsViewModel(c.mqtt, c.readings, c.settings)
            }
        }
    }
}
