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
import androidx.core.content.edit

data class ProfileCredentials(
    val lxnsToken: String = "",
)

class ProfileCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "profile_credentials",
        Context.MODE_PRIVATE,
    )

    fun credentials(profileId: String): ProfileCredentials = ProfileCredentials(
        lxnsToken = decrypt(preferences.getString("$profileId.lxns", null)),
    )

    fun save(profileId: String, credentials: ProfileCredentials) {
        preferences.edit {
					putString("$profileId.lxns", encrypt(credentials.lxnsToken))
						.remove("$profileId.df")
				}
    }

    fun delete(profileId: String) {
        preferences.edit {
					remove("$profileId.df")
						.remove("$profileId.lxns")
				}
    }

    private fun encrypt(value: String): String? {
        if (value.isEmpty()) return null
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decrypt(payload: String?): String {
        if (payload.isNullOrEmpty()) return ""
        return runCatching {
            val (encodedIv, encodedValue) = payload.split(':', limit = 2)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encodedValue, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrDefault("")
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
        const val KeyAlias = "maimaid.profile.credentials"
        const val Transformation = "AES/GCM/NoPadding"
    }
}
