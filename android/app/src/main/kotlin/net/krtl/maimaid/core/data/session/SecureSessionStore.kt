package net.krtl.maimaid.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.krtl.maimaid.core.domain.AuthUser

@Serializable
data class TokenBundle(
    val userId: String,
    val email: String,
    val isAdmin: Boolean,
    val accessToken: String,
    val refreshToken: String
) {
    fun toAuthUser(): AuthUser = AuthUser(
        id = userId,
        email = email,
        isAdmin = isAdmin
    )
}

class SecureSessionStore(
    context: Context,
    private val json: Json
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("maimaid.secure.session.preferences_pb") }
    )
    @Volatile
    private var cachedBundle: TokenBundle? = null

    fun loadCachedBundle(): TokenBundle? = cachedBundle

    suspend fun loadBundle(): TokenBundle? {
        cachedBundle?.let { return it }
        val raw = dataStore.data.first()[KEY_BUNDLE] ?: return null
        val resolved = runCatching { json.decodeFromString<TokenBundle>(raw) }.getOrNull()
        cachedBundle = resolved
        return resolved
    }

    suspend fun saveBundle(bundle: TokenBundle) {
        val raw = json.encodeToString(TokenBundle.serializer(), bundle)
        cachedBundle = bundle
        dataStore.edit { prefs ->
            prefs[KEY_BUNDLE] = raw
        }
    }

    suspend fun clear() {
        cachedBundle = null
        dataStore.edit { prefs ->
            prefs.remove(KEY_BUNDLE)
        }
    }

    companion object {
        private val KEY_BUNDLE = stringPreferencesKey("token_bundle")
    }
}
