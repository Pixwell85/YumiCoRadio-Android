// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.yumicoradio.android.data.PrefsStore
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureVoterTokenStore(private val prefs: PrefsStore) {
    suspend fun loadOrCreate(): String = load() ?: rotate()

    suspend fun rotate(): String {
        val token = ByteArray(16).also(SecureRandom()::nextBytes).toHex()
        save(token)
        return token
    }

    suspend fun load(): String? {
        val blob = prefs.ratingsVoterTokenBlob() ?: return null
        val parts = blob.split(':')
        if (parts.size != 3 || parts[0] != "v1") return rotate()
        val token = runCatching {
            val iv = parts[1].fromHex()
            val encrypted = parts[2].fromHex()
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
        return token?.takeIf { it.matches(Regex("^[0-9a-f]{32}$")) } ?: rotate()
    }

    private suspend fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.setRatingsVoterTokenBlob("v1:${cipher.iv.toHex()}:${cipher.doFinal(token.toByteArray()).toHex()}")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0 && matches(Regex("^[0-9a-f]+$")))
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    companion object {
        private const val ALIAS = "yumi_ratings_voter_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
