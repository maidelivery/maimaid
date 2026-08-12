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

        private val StateKey = stringPreferencesKey("state")
    }
}
