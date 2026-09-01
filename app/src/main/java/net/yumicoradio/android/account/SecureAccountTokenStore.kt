// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.yumicoradio.android.data.PrefsStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AccountTokenStore {
    suspend fun load(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

class SecureAccountTokenStore(private val prefs: PrefsStore) : AccountTokenStore {
    override suspend fun save(token: String) {
        require(TOKEN.matches(token))
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        prefs.setAccountTokenBlob(pack(cipher.iv, cipher.doFinal(token.toByteArray(Charsets.UTF_8))))
    }

    override suspend fun load(): String? {
        val blob = prefs.accountTokenBlob() ?: return null
        val (iv, encrypted) = unpack(blob) ?: return clearAndNull()
        val token = runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
        return token?.takeIf(TOKEN::matches) ?: clearAndNull()
    }

    override suspend fun clear() = prefs.clearAccountTokenBlob()

    private suspend fun clearAndNull(): String? {
        clear()
        return null
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ALIAS = "yumi_account_session_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private val TOKEN = Regex("^[A-Za-z0-9_-]{32,256}$")

        fun pack(iv: ByteArray, encrypted: ByteArray): String =
            "v1:${iv.toHex()}:${encrypted.toHex()}"

        fun unpack(blob: String): Pair<ByteArray, ByteArray>? {
            val parts = blob.split(':')
            if (parts.size != 3 || parts[0] != "v1" || parts[1].length !in 24..32 || parts[2].isEmpty()) {
                return null
            }
            return runCatching { parts[1].fromHex() to parts[2].fromHex() }.getOrNull()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
        private fun String.fromHex(): ByteArray {
            require(length % 2 == 0 && matches(Regex("^[0-9a-f]+$")))
            return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
