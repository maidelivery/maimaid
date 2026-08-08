package net.krtl.maimaid.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class CommunityAliasSyncStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("community_alias_sync.preferences_pb") }
    )

    suspend fun getLastPollAt(): Long? = dataStore.data.map { it[LAST_POLL_AT] }.first()

    suspend fun setLastPollAt(timestampMillis: Long) {
        dataStore.edit { it[LAST_POLL_AT] = timestampMillis }
    }

    suspend fun getLastApprovedSyncAt(): Long? = dataStore.data.map { it[LAST_APPROVED_SYNC_AT] }.first()

    suspend fun setLastApprovedSyncAt(timestampMillis: Long) {
        dataStore.edit { it[LAST_APPROVED_SYNC_AT] = timestampMillis }
    }

    suspend fun clearLastApprovedSyncAt() {
        dataStore.edit { it.remove(LAST_APPROVED_SYNC_AT) }
    }

    companion object {
        private val LAST_POLL_AT = longPreferencesKey("lastPollAt")
        private val LAST_APPROVED_SYNC_AT = longPreferencesKey("lastApprovedSyncAt")
    }
}
