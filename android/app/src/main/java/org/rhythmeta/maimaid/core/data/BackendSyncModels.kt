package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BackendRemoteProfile(
    val id: String,
    val name: String,
    val server: String,
    val avatarUrl: String? = null,
    val isActive: Boolean = false,
    val playerRating: Int = 0,
    val plate: String? = null,
    val dfUsername: String = "",
    val b35Count: Int = 35,
    val b15Count: Int = 15,
    val b35RecLimit: Int = 10,
    val b15RecLimit: Int = 10,
    val createdAt: String,
    val lastImportDateDf: String? = null,
    val lastImportDateLxns: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class BackendRemoteSheet(
    val songIdentifier: String,
    val songId: Int = 0,
    val chartType: String,
    val difficulty: String,
)

@Serializable
data class BackendRemoteScore(
    val profileId: String,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val achievements: Double,
    val rank: String,
    val dxScore: Int = 0,
    val fc: String? = null,
    val fs: String? = null,
    val achievedAt: String,
    val sheet: BackendRemoteSheet? = null,
)

@Serializable
data class BackendRemotePlayRecord(
    val profileId: String,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val achievements: Double,
    val rank: String,
    val dxScore: Int = 0,
    val fc: String? = null,
    val fs: String? = null,
    val playTime: String,
    val sheet: BackendRemoteSheet? = null,
)

@Serializable
data class BackendSyncEvent(
    val revision: String,
    val profileId: String? = null,
    val entityType: String,
    val entityId: String,
    val op: String,
)

@Serializable
data class BackendSyncSnapshot(
    val profiles: List<BackendRemoteProfile> = emptyList(),
    val scores: List<BackendRemoteScore> = emptyList(),
    val records: List<BackendRemotePlayRecord> = emptyList(),
)

@Serializable
data class BackendSyncPullResponse(
    val events: List<BackendSyncEvent> = emptyList(),
    val latestRevision: String = "0",
    val hasMore: Boolean = false,
    val snapshot: BackendSyncSnapshot = BackendSyncSnapshot(),
)

@Serializable
data class BackendProfileUpsert(
    val profileId: String,
    val name: String,
    val server: String,
    val isActive: Boolean,
    val playerRating: Int,
    val plate: String? = null,
    val avatarUrl: String? = null,
    val dfUsername: String,
    val b35Count: Int,
    val b15Count: Int,
    val b35RecLimit: Int,
    val b15RecLimit: Int,
    val createdAt: String,
    val clientUpdatedAt: String? = null,
)

@Serializable
data class BackendScoreEntry(
    val songIdentifier: String,
    val songId: Int? = null,
    val type: String,
    val difficulty: String,
    val achievements: Double,
    val rank: String,
    val dxScore: Int,
    val fc: String? = null,
    val fs: String? = null,
    val achievedAt: String,
)

@Serializable
data class BackendPlayRecordEntry(
    val songIdentifier: String,
    val songId: Int? = null,
    val type: String,
    val difficulty: String,
    val achievements: Double,
    val rank: String,
    val dxScore: Int,
    val fc: String? = null,
    val fs: String? = null,
    val playTime: String,
)

@Serializable
data class BackendScoreSet(val profileId: String, val scores: List<BackendScoreEntry>)

@Serializable
data class BackendRecordSet(val profileId: String, val records: List<BackendPlayRecordEntry>)

@Serializable
data class BackendSyncPushPayload(
    val idempotencyKey: String,
    val forceProfileOverwrite: Boolean = false,
    val profileUpserts: List<BackendProfileUpsert>,
    val scoreUpserts: List<BackendScoreSet>,
    val playRecordUpserts: List<BackendRecordSet>,
    val replaceScoreProfileIds: List<String> = emptyList(),
    val replacePlayRecordProfileIds: List<String> = emptyList(),
)

@Serializable
data class BackendPendingSyncMutation(
    val payload: BackendSyncPushPayload,
    val profileFingerprintById: Map<String, String> = emptyMap(),
)

@Serializable
data class BackendSyncConflict(
    val profileId: String,
    val reason: String,
    val serverProfile: BackendRemoteProfile? = null,
)

@Serializable
data class BackendSyncPushResponse(
    val latestRevision: String = "0",
    val conflicts: List<BackendSyncConflict> = emptyList(),
    val profileVersions: Map<String, String> = emptyMap(),
)

object FlexibleDoubleSerializer : kotlinx.serialization.KSerializer<Double> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "FlexibleDouble",
        kotlinx.serialization.descriptors.PrimitiveKind.DOUBLE,
    )

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Double {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: return decoder.decodeDouble()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.doubleOrNull ?: primitive.content.toDouble()
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}
