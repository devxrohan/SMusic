package com.devxrohan.smusic.discordrpc.gateway

import com.devxrohan.smusic.discordrpc.gateway.entities.Heartbeat
import com.devxrohan.smusic.discordrpc.gateway.entities.Identify.Companion.toIdentifyPayload
import com.devxrohan.smusic.discordrpc.gateway.entities.Payload
import com.devxrohan.smusic.discordrpc.gateway.entities.Ready
import com.devxrohan.smusic.discordrpc.gateway.entities.Resume
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.DISPATCH
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.HEARTBEAT
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.HELLO
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.IDENTIFY
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.INVALID_SESSION
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.PRESENCE_UPDATE
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.RECONNECT
import com.devxrohan.smusic.discordrpc.gateway.entities.op.OpCode.RESUME
import com.devxrohan.smusic.discordrpc.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.logging.Level
import java.util.logging.Level.INFO
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Modified by Zion Huang
 */
open class DiscordWebSocket(
    private val token: String,
) : CoroutineScope {
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null
    private val connectionReady = MutableStateFlow(false)
    private var connected: Boolean
        get() = connectionReady.value
        set(value) { connectionReady.value = value }
    private var client: HttpClient = HttpClient {
        install(WebSockets)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default

    @Synchronized
    fun connect() {
        if (connectionJob?.isActive == true || !coroutineContext.isActive) return
        connectionJob = launch {
            try {
                Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Connect called")
                val url = resumeGatewayUrl ?: gatewayUrl
                websocket = client.webSocketSession(url)

                // start receiving messages
                websocket!!.incoming.receiveAsFlow()
                    .collect {
                        when (it) {
                            is Frame.Text -> {
                                val jsonString = it.readText()
                                onMessage(json.decodeFromString(jsonString))
                            }

                            else -> {}
                        }
                    }
                handleClose()
            } catch (cancelled: CancellationException) { resetConnection(); throw cancelled
            } catch (e: Exception) {
                Logger.getLogger("DiscordGateway").log(INFO, "Gateway: connection interrupted")
                resetConnection()
            }
        }
    }

    private suspend fun handleClose() {
        heartbeatJob?.cancel()
        connected = false
        val close = websocket?.closeReason?.await()
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Closed with code: ${close?.code}, reason: ${close?.message},  can_reconnect: ${close?.code?.toInt() == 4000}")
        if (close?.code?.toInt() == 4000) {
            val previous = connectionJob
            launch {
                delay(200.milliseconds)
                previous?.join()
                connect()
            }
        } else
            resetConnection()
    }

    private suspend fun onMessage(payload: Payload) {
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")

        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            DISPATCH -> payload.handleDispatch()
            HEARTBEAT -> sendHeartBeat()
            RECONNECT -> reconnectWebSocket()
            INVALID_SESSION -> handleInvalidSession()
            HELLO -> payload.handleHello()
            else -> {}
        }
    }

    open fun Payload.handleDispatch() {
        when (this.t.toString()) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                Logger.getLogger("DiscordGateway").log(INFO, "Gateway: resume_gateway_url updated to $resumeGatewayUrl")
                Logger.getLogger("DiscordGateway").log(INFO, "Gateway: authenticated session ready")
                connected = true
                return
            }

            "RESUMED" -> {
                connected = true
                Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Session Resumed")
            }

            else -> {}
        }
    }

    private suspend inline fun handleInvalidSession() {
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Handling Invalid Session")
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Sending Identify after 150ms")
        delay(150)
        sendIdentify()
    }

    private suspend inline fun Payload.handleHello() {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    private suspend fun sendHeartBeat() {
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect"
            )
        )
    }

    private suspend fun sendIdentify() {
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d = token.toIdentifyPayload()
        )
    }

    private suspend fun sendResume() {
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Sending $RESUME")
        send(
            op = RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token
            )
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            while (isActive) {
                sendHeartBeat()
                delay(interval)
            }
        }
    }

    private fun isSocketConnectedToAccount(): Boolean {
        return connected && websocket?.isActive == true
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun isWebSocketConnected(): Boolean {
        return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        if (websocket?.isActive == true) {
            val payload = json.encodeToString(
                Payload(
                    op = op,
                    d = json.encodeToJsonElement(d),
                )
            )
            websocket?.send(Frame.Text(payload))
        }
    }

    private fun resetConnection() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        websocket?.cancel()
        websocket = null
        connected = false
    }

    fun close() {
        resetConnection()
        this.cancel()
        client.close()
        resumeGatewayUrl = null
        sessionId = null
    }

    suspend fun sendActivity(presence: Presence) {
        if (withTimeoutOrNull(15_000L) { connectionReady.first { it } } != true) return
        if (!isSocketConnectedToAccount()) return
        Logger.getLogger("DiscordGateway").log(INFO, "Gateway: Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence
        )
    }

}
