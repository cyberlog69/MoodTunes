package com.moodtunes.app.data.local.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small at-rest encryption helper for user secrets (API tokens, scrobbler
 * session keys, server passwords) stored in SharedPreferences.
 *
 * Values are encrypted with AES-256/GCM using a non-exportable key held in the
 * Android Keystore. Legacy plaintext values are accepted by [decrypt] so they
 * can be re-encrypted transparently on first read.
 */
class SecretCipher(private val keyAlias: String = DEFAULT_KEY_ALIAS) {

    fun isEncrypted(value: String?): Boolean = value?.startsWith(MARKER) == true

    fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return plaintext
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val payload = Base64.encodeToString(
                cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP
            )
            "$MARKER$iv:$payload"
        }.onFailure {
            Timber.e(it, "SecretCipher: failed to encrypt preference value")
        }.getOrNull()
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return stored
        if (!isEncrypted(stored)) return stored

        return runCatching {
            val payload = stored.removePrefix(MARKER)
            val separator = payload.indexOf(':')
            require(separator > 0) { "Malformed encrypted value" }

            val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.onFailure {
            Timber.e(it, "SecretCipher: failed to decrypt preference value")
        }.getOrNull()
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MARKER = "enc:v1:"
        const val DEFAULT_KEY_ALIAS = "moodtunes_user_secrets_v1"
    }
}
