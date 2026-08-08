package net.krtl.maimaid.core.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackendErrorDto(
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class BackendAuthUserDto(
    val id: String,
    val email: String,
    val isAdmin: Boolean
)

@Serializable
data class BackendAuthPayloadDto(
    val user: BackendAuthUserDto,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class BackendSessionExchangeRequestDto(
    val sessionCode: String
)

@Serializable
data class BackendRefreshRequestDto(
    val refreshToken: String
)

@Serializable
data class BackendMeDto(
    val id: String,
    val email: String,
    val isAdmin: Boolean
)

@Serializable
data class BackendLogoutResponseDto(
    val success: Boolean
)

@Serializable
data class BackendImportDivingFishRequestDto(
    val profileId: String,
    val username: String? = null,
    val qq: String? = null
)

@Serializable
data class BackendImportLxnsRequestDto(
    val profileId: String,
    val accessToken: String
)

@Serializable
data class BackendLxnsOauthTokenRequestDto(
    val code: String,
    val codeVerifier: String
)

@Serializable
data class BackendLxnsOauthTokenResponseDto(
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class BackendImportRunResponseDto(
    val importRunId: String,
    val fetchedCount: Int,
    val upsertedCount: Int,
    val skippedCount: Int
)

@Serializable
data class BackendSyncPushRequestDto(
    val idempotencyKey: String,
    val profileUpserts: List<JsonObject> = emptyList(),
    val scoreUpserts: List<JsonObject> = emptyList(),
    val playRecordUpserts: List<JsonObject> = emptyList()
)

@Serializable
data class BackendSyncPushResponseDto(
    val latestRevision: String
)

@Serializable
data class BackendSyncPullResponseDto(
    val latestRevision: String,
    @SerialName("snapshot") val snapshot: BackendSyncSnapshotDto
)

@Serializable
data class BackendSyncSnapshotDto(
    val profiles: List<JsonObject> = emptyList(),
    val scores: List<JsonObject> = emptyList(),
    val records: List<JsonObject> = emptyList()
)
