package com.team11.labdip.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.team11.labdip.BuildConfig
import com.team11.labdip.data.settings.BrokerSettings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val host: String) : ConnectionState
    data class Connected(val host: String, val topic: String) : ConnectionState
    /** Was connected, lost it, retrying. */
    data class Reconnecting(val host: String) : ConnectionState
    /** Never reached the broker with these settings; still retrying unless [retrying] is false. */
    data class Offline(val host: String, val reason: String, val retrying: Boolean = true) : ConnectionState
}

/** Owns the single MQTT 3.1.1 connection (the firmware uses PubSubClient, MQTT 3.1.1). */
class MqttRepository {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    private val _messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<ByteArray> = _messages

    private var client: Mqtt3AsyncClient? = null
    // Listeners of a replaced client must not touch state; each connect() bumps this
    private var generation = 0
    private val clientId = "labdip-android-" + UUID.randomUUID().toString().take(8)

    @Synchronized
    fun connect(b: BrokerSettings) {
        disconnect()
        val gen = generation
        if (!BuildConfig.DEBUG && !b.tls) {
            _state.value = ConnectionState.Offline(b.host, "Release builds need TLS. Turn on TLS in settings.", retrying = false)
            return
        }
        _state.value = ConnectionState.Connecting(b.host)
        var everConnected = false
        val c = builder(b)
            .automaticReconnect().initialDelay(1, TimeUnit.SECONDS).maxDelay(30, TimeUnit.SECONDS).applyAutomaticReconnect()
            .addConnectedListener {
                synchronized(this) {
                    if (gen != generation) return@addConnectedListener
                    everConnected = true
                    _state.value = ConnectionState.Connected(b.host, b.topic)
                    client?.subscribeWith()?.topicFilter(b.topic)?.qos(MqttQos.AT_MOST_ONCE)?.send()
                }
            }
            .addDisconnectedListener { ctx ->
                synchronized(this) {
                    if (gen != generation || ctx.source == MqttDisconnectSource.USER) return@addDisconnectedListener
                    _state.value = if (everConnected) ConnectionState.Reconnecting(b.host)
                    else ConnectionState.Offline(b.host, ctx.cause.message ?: "unreachable")
                }
            }
            .buildAsync()
        client = c
        // Registered once per client so resubscribes after reconnect don't duplicate deliveries
        c.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { _messages.tryEmit(it.payloadAsBytes) }
        c.connectWith().cleanSession(true).keepAlive(30)
            .let { if (b.username.isEmpty()) it else it.simpleAuth().username(b.username).password(b.password.encodeToByteArray()).applySimpleAuth() }
            .send()
    }

    @Synchronized
    fun disconnect() {
        generation++
        client?.disconnect()
        client = null
        _state.value = ConnectionState.Idle
    }

    /** One-off connect with a throwaway client; does not touch the live connection. */
    suspend fun test(b: BrokerSettings): Result<Unit> = runCatching {
        if (!BuildConfig.DEBUG && !b.tls) error("Release builds need TLS.")
        val c = builder(b, "$clientId-test").buildAsync()  // same id would kick the live session
        try {
            withTimeout(8_000) {
                c.connectWith().cleanSession(true)
                    .let { if (b.username.isEmpty()) it else it.simpleAuth().username(b.username).password(b.password.encodeToByteArray()).applySimpleAuth() }
                    .send().await()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun builder(b: BrokerSettings, id: String = clientId) = MqttClient.builder()
        .useMqttVersion3()
        .identifier(id)
        .serverHost(b.host)
        .serverPort(b.port)
        .let { if (b.tls) it.sslWithDefaultConfig() else it }
}
