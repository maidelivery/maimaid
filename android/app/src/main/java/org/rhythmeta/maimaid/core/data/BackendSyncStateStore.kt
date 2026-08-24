package org.rhythmeta.maimaid.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.backendSyncDataStore by preferencesDataStore(name = "backend_sync")

@Serializable
data class BackendSyncPersistentState(
    val ownerUserId: String? = null,
    val lastRevision: String = "0",
    val remoteUpdatedAtByProfile: Map<String, String> = emptyMap(),
    val syncedFingerprintByProfile: Map<String, String> = emptyMap(),
    val syncedDataFingerprintByProfile: Map<String, String> = emptyMap(),
    val pendingDataReplaceProfileIds: Set<String> = emptySet(),
    val pendingMutation: BackendPendingSyncMutation? = null,
)

class BackendSyncStateStore(
    context: Context,
    private val json: Json,
) {
    private val applicationContext = context.applicationContext

    suspend fun load(): BackendSyncPersistentState {
        val payload = applicationContext.backendSyncDataStore.data.first()[StateKey]
        return payload?.let {
            runCatching { json.decodeFromString<BackendSyncPersistentState>(it) }.getOrNull()
        } ?: BackendSyncPersistentState()
    }

    suspend fun update(transform: (BackendSyncPersistentState) -> BackendSyncPersistentState) {
        applicationContext.backendSyncDataStore.edit { preferences ->
            val current = preferences[StateKey]?.let {
                runCatching { json.decodeFromString<BackendSyncPersistentState>(it) }.getOrNull()
            } ?: BackendSyncPersistentState()
            preferences[StateKey] = json.encodeToString(
                BackendSyncPersistentState.serializer(),
                transform(current),
            )
        }
    }

    suspend fun clearSessionState(keepOwner: Boolean) {
        update { current ->
            BackendSyncPersistentState(ownerUserId = current.ownerUserId.takeIf { keepOwner })
        }
    }

    suspend fun markDataPending(profileId: String, replace: Boolean = false) {
        update { current ->
            current.copy(
                pendingDataReplaceProfileIds = if (replace) {
                    current.pendingDataReplaceProfileIds + profileId
                } else current.pendingDataReplaceProfileIds,
            )
        }
    }

    companion object {
        fun profileFingerprint(profile: org.rhythmeta.maimaid.core.database.UserProfileEntity): String {
            val value = listOf(
                profile.name.trim(),
                profile.server.lowercase(),
                profile.isActive.toString(),
                profile.playerRating.toString(),
                profile.plate.orEmpty(),
                profile.avatarUrl.orEmpty(),
                profile.dfUsername.trim(),
                profile.b35Count.toString(),
                profile.b15Count.toString(),
                profile.b35RecLimit.toString(),
                profile.b15RecLimit.toString(),
                profile.createdAt.toString(),
            ).joinToString("\u001f")
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        fun dataFingerprint(
            scores: List<org.rhythmeta.maimaid.core.database.ScoreEntity>,
            records: List<org.rhythmeta.maimaid.core.database.PlayRecordEntity>,
        ): String {
            val value = buildString {
                scores.sortedWith(compareBy({ it.sheetKey }, { it.achievedAt })).forEach { score ->
                    append("s|").append(score.profileId).append('|').append(score.sheetKey).append('|')
                        .append(score.achievement).append('|').append(score.rank).append('|')
                        .append(score.dxScore).append('|').append(score.fc.orEmpty()).append('|')
                        .append(score.fs.orEmpty()).append('|').append(score.achievedAt).append('\n')
                }
                records.sortedWith(compareBy({ it.playedAt }, { it.id })).forEach { record ->
                    append("r|").append(record.id).append('|').append(record.profileId).append('|')
                        .append(record.sheetKey).append('|').append(record.achievement).append('|')
                        .append(record.rank).append('|').append(record.dxScore).append('|')
                        .append(record.fc.orEmpty()).append('|').append(record.fs.orEmpty()).append('|')
                        .append(record.playedAt).append('\n')
                }
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        private val StateKey = stringPreferencesKey("state")
    }
}
