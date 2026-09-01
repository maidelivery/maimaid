package org.rhythmeta.maimaid.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.core.content.edit

@Serializable
data class BackendAuthUser(
    val id: String,
    val email: String,
    val username: String = "",
    val usernameDiscriminator: String = "",
    val handle: String = "",
    val isAdmin: Boolean = false,
) {
    val displayHandle: String
        get() = handle.ifBlank {
            val resolvedUsername = username.ifBlank { email }
            if (usernameDiscriminator.isBlank()) resolvedUsername else "$resolvedUsername#$usernameDiscriminator"
        }
}

@Serializable
data class BackendTokenBundle(
    val user: BackendAuthUser,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 0,
)

class BackendTokenStore(
    context: Context,
    private val json: Json,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "backend_session",
        Context.MODE_PRIVATE,
    )

    fun load(): BackendTokenBundle? = decrypt(preferences.getString(TokenKey, null))?.let { payload ->
        runCatching { json.decodeFromString<BackendTokenBundle>(payload) }.getOrNull()
    }

    fun save(bundle: BackendTokenBundle) {
        preferences.edit {
					putString(TokenKey, encrypt(json.encodeToString(BackendTokenBundle.serializer(), bundle)))
				}
    }

    fun clear() {
        preferences.edit { remove(TokenKey) }
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(payload: String?): String? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val (encodedIv, encodedValue) = payload.split(':', limit = 2)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encodedValue, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TokenKey = "tokens"
        const val KeyAlias = "maimaid.backend.tokens"
        const val Transformation = "AES/GCM/NoPadding"
    }
}
