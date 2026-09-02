package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import org.rhythmeta.maimaid.core.network.BackendApiClient

sealed interface LetterGameEvent {
    data class Room(val room: LetterGameRoom) : LetterGameEvent
    data class Match(val match: LetterGameMatchSnapshot) : LetterGameEvent
    data class ActionAccepted(val action: JsonElement) : LetterGameEvent
    data class MemberRemoved(val roomId: String, val reason: String?) : LetterGameEvent
    data class RoomDissolved(val roomId: String) : LetterGameEvent
    data class Error(val code: String, val message: String?) : LetterGameEvent
}

class LetterGameRepository(
    private val apiClient: BackendApiClient,
    private val sessionManager: BackendSessionManager,
    private val json: Json,
) {
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val mutableEvents = MutableSharedFlow<LetterGameEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<LetterGameEvent> = mutableEvents.asSharedFlow()

    suspend fun listPublicRooms(): List<LetterGameRoom> {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms")
        return json.decodeFromJsonElement(LetterGameRoomsResponse.serializer(), payload).rooms
    }

    suspend fun createRoom(request: LetterGameCreateRequest): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms", "POST", json.encodeToJsonElement(request))
        val value = payload.jsonObject["room"] ?: payload
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), value)
    }

    suspend fun joinRoom(code: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest(
            "v1/letter-game/rooms:join",
            "POST",
            buildJsonObject { put("code", code.trim().uppercase()) },
        )
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun rejectMember(roomId: String, memberId: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/members/$memberId/reject", "POST")
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun getRoom(roomIdOrCode: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/${roomIdOrCode.trim().uppercase()}")
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun startMatch(roomId: String): LetterGameMatchSnapshot {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/start", "POST")
        return json.decodeFromJsonElement(LetterGameMatchSnapshot.serializer(), payload.jsonObject["match"] ?: payload)
    }

    suspend fun updateRoom(roomId: String, request: LetterGameCreateRequest): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId", "PATCH", json.encodeToJsonElement(request))
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun reopenRoom(roomId: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/reopen", "POST")
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun approveMember(roomId: String, memberId: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/members/$memberId/approve", "POST")
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun kickMember(roomId: String, memberId: String): LetterGameRoom {
        val payload = sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/members/$memberId/kick", "POST")
        return json.decodeFromJsonElement(LetterGameRoom.serializer(), payload.jsonObject["room"] ?: payload)
    }

    suspend fun leaveRoom(roomId: String) {
        sessionManager.authorizedRequest("v1/letter-game/rooms/$roomId/leave", "POST")
    }

    suspend fun getMatch(matchId: String): LetterGameMatchSnapshot {
        val payload = sessionManager.authorizedRequest("v1/letter-game/matches/$matchId")
        return json.decodeFromJsonElement(LetterGameMatchSnapshot.serializer(), payload.jsonObject["match"] ?: payload)
    }

    fun connect(roomCode: String): WebSocket? {
        val token = sessionManager.accessTokenOrNull() ?: return null
        val httpUrl = apiClient.endpoint("v1/letter-game/rooms/${roomCode.trim().uppercase()}/ws")
        val wsUrl = httpUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build()
        return httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mutableEvents.tryEmit(LetterGameEvent.Error("connection_failed", t.message))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                when (reason) {
                    "room_dissolved" -> mutableEvents.tryEmit(LetterGameEvent.RoomDissolved(roomCode))
                    "kicked", "left", "rejected" ->
                        mutableEvents.tryEmit(LetterGameEvent.MemberRemoved(roomCode, reason))
                    "room_access_denied", "member_removed" ->
                        mutableEvents.tryEmit(LetterGameEvent.MemberRemoved(roomCode, null))
                }
            }
        })
    }

    fun resume(webSocket: WebSocket, matchId: String, revision: Int) {
        webSocket.send(json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "resume")
            put("matchId", matchId)
            put("lastRevision", revision)
        }))
    }

    fun sendAction(webSocket: WebSocket, matchId: String, revision: Int, actionId: String, payload: JsonObject) {
        webSocket.send(json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "action")
            put("matchId", matchId)
            put("actionId", actionId)
            put("expectedRevision", revision)
            put("payload", payload)
        }))
    }

	private fun handleMessage(text: String) {
        runCatching { json.parseToJsonElement(text).jsonObject }.onSuccess { message ->
            when (message["type"]?.toString()?.trim('"')) {
                "room_snapshot" -> message["room"]?.let { mutableEvents.tryEmit(LetterGameEvent.Room(json.decodeFromJsonElement(LetterGameRoom.serializer(), it))) }
                "match_snapshot" -> message["match"]?.let { mutableEvents.tryEmit(LetterGameEvent.Match(json.decodeFromJsonElement(LetterGameMatchSnapshot.serializer(), it))) }
                "action_accepted" -> mutableEvents.tryEmit(LetterGameEvent.ActionAccepted(message["action"] ?: JsonObject(emptyMap())))
                "member_removed" -> mutableEvents.tryEmit(
                    LetterGameEvent.MemberRemoved(
                        roomId = message["roomId"]?.toString()?.trim('"').orEmpty(),
                        reason = message["reason"]?.toString()?.trim('"'),
                    ),
                )
                "room_dissolved" -> mutableEvents.tryEmit(
                    LetterGameEvent.RoomDissolved(message["roomId"]?.toString()?.trim('"').orEmpty()),
                )
                "action_rejected" -> mutableEvents.tryEmit(LetterGameEvent.Error(message["code"]?.toString()?.trim('"') ?: "action_failed", message["message"]?.toString()?.trim('"')))
            }
        }.onFailure { mutableEvents.tryEmit(LetterGameEvent.Error("invalid_message", it.message)) }
    }
}
