package com.team11.labdip.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.team11.labdip.BuildConfig
import com.team11.labdip.data.settings.BrokerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
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

    // Replies from the rig to app commands, already checked by RigCommands.cleanReply
    private val _replies = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val replies: SharedFlow<String> = _replies

    // Camera-station results (cam_capture.py); parsed by CameraRepository, never by ReadingParser
    private val _camera = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val camera: SharedFlow<ByteArray> = _camera

    // Rig presence from its retained status / last will; null = nothing heard (older firmware)
    private val _rigOnline = MutableStateFlow<Boolean?>(null)
    val rigOnline: StateFlow<Boolean?> = _rigOnline

    private var client: Mqtt3AsyncClient? = null
    private var current: BrokerSettings? = null
    // Listeners of a replaced client must not touch state; each connect() bumps this
    private var generation = 0
    private val clientId = "labdip-android-" + UUID.randomUUID().toString().take(8)

    suspend fun connect(b: BrokerSettings) = connectTo(b, resolve(b.host))

    @Synchronized
    private fun connectTo(b: BrokerSettings, addr: InetAddress?) {
        disconnect()
        val gen = generation
        if (!BuildConfig.DEBUG && !b.tls) {
            _state.value = ConnectionState.Offline(b.host, "Release builds need TLS. Turn on TLS in settings.", retrying = false)
            return
        }
        _state.value = ConnectionState.Connecting(b.host)
        current = b
        val replyTopic = ackTopic(b.topic)
        val rigStatusTopic = statusTopic(b.topic)
        val cameraTopic = camTopic(b.topic)
        var everConnected = false
        val c = builder(b, addr = addr)
            .automaticReconnect().initialDelay(1, TimeUnit.SECONDS).maxDelay(30, TimeUnit.SECONDS).applyAutomaticReconnect()
            .addConnectedListener {
                synchronized(this) {
                    if (gen != generation) return@addConnectedListener
                    everConnected = true
                    _state.value = ConnectionState.Connected(b.host, b.topic)
                    client?.subscribeWith()?.topicFilter(b.topic)?.qos(MqttQos.AT_MOST_ONCE)?.send()
                    client?.subscribeWith()?.topicFilter(replyTopic)?.qos(MqttQos.AT_MOST_ONCE)?.send()
                    client?.subscribeWith()?.topicFilter(rigStatusTopic)?.qos(MqttQos.AT_MOST_ONCE)?.send()
                    client?.subscribeWith()?.topicFilter(cameraTopic)?.qos(MqttQos.AT_LEAST_ONCE)?.send()
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
        c.publishes(MqttGlobalPublishFilter.SUBSCRIBED) {
            when (it.topic.toString()) {
                replyTopic -> RigCommands.cleanReply(it.payloadAsBytes)?.let(_replies::tryEmit)
                // Exact words only; anything else on the status topic is ignored
                rigStatusTopic -> when (it.payloadAsBytes.decodeToString()) {
                    "online" -> _rigOnline.value = true
                    "offline" -> _rigOnline.value = false
                }
                cameraTopic -> _camera.tryEmit(it.payloadAsBytes)
                else -> _messages.tryEmit(it.payloadAsBytes)
            }
        }
        c.connectWith().cleanSession(true).keepAlive(30)
            .let { if (b.username.isEmpty()) it else it.simpleAuth().username(b.username).password(b.password.encodeToByteArray()).applySimpleAuth() }
            .send()
    }

    @Synchronized
    fun disconnect() {
        generation++
        client?.disconnect()
        client = null
        current = null
        _rigOnline.value = null
        _state.value = ConnectionState.Idle
    }

    /** Publishes one command (already built by [RigCommands]) to the rig's command topic. */
    suspend fun send(command: String) {
        val (c, b) = synchronized(this) { client to current }
        check(c != null && b != null && _state.value is ConnectionState.Connected) { "Not connected to the broker" }
        c.publishWith().topic(commandTopic(b.topic)).payload(command.encodeToByteArray()).qos(MqttQos.AT_LEAST_ONCE).send().await()
    }

    /** One-off connect with a throwaway client; does not touch the live connection. */
    suspend fun test(b: BrokerSettings): Result<Unit> = runCatching {
        if (!BuildConfig.DEBUG && !b.tls) error("Release builds need TLS.")
        val c = builder(b, "$clientId-test", resolve(b.host)).buildAsync()  // same id would kick the live session
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

    // shortcut: IPv4 first, because broker.hivemq.com's IPv6 address refuses 1883 on the lab Wi-Fi;
    // upgrade to trying every address in turn if a broker is ever reachable only over IPv6
    private suspend fun resolve(host: String): InetAddress? = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getAllByName(host).let { all -> all.firstOrNull { it is Inet4Address } ?: all.first() } }.getOrNull()
    }

    private fun builder(b: BrokerSettings, id: String = clientId, addr: InetAddress? = null) = MqttClient.builder()
        .useMqttVersion3()
        .identifier(id)
        .let {
            // getByAddress keeps the host name, so TLS still verifies the certificate against it.
            // DNS failed: hand the client the name so it reports the error and keeps retrying.
            if (addr == null) it.serverHost(b.host).serverPort(b.port)
            else it.serverAddress(InetSocketAddress(InetAddress.getByAddress(b.host, addr.address), b.port))
        }
        .let { if (b.tls) it.sslWithDefaultConfig() else it }
}
