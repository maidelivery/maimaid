package org.rhythmeta.maimaid.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.catalogSyncDataStore by preferencesDataStore(name = "catalog_sync")

class CatalogSyncStateStore(
    private val context: Context,
) {
    suspend fun currentMd5(): String? = context.catalogSyncDataStore.data.first()[CatalogMd5Key]

    suspend fun save(version: String, md5: String) {
        context.catalogSyncDataStore.edit { preferences ->
            preferences[CatalogVersionKey] = version
            preferences[CatalogMd5Key] = md5
        }
    }

    private companion object {
        val CatalogVersionKey = stringPreferencesKey("catalog_version")
        val CatalogMd5Key = stringPreferencesKey("catalog_md5")
    }
}
