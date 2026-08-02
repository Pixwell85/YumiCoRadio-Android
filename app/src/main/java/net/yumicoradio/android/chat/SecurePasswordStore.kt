// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.yumicoradio.android.data.PrefsStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores one reserved nickname's password encrypted at rest. The AES/GCM key lives in the Android
 * Keystore (hardware-backed where available) and never leaves it; only the IV+ciphertext and the
 * nickname (not secret) are persisted, via [PrefsStore]. Crypto failures degrade to "no password".
 */
class SecurePasswordStore(private val prefs: PrefsStore) {

    suspend fun save(nick: String, password: String) {
        val (iv, ct) = encrypt(password)
        prefs.setReservedPassword(nick, pack(iv, ct))
    }

    /** The stored password iff one is stored for exactly [nick]; null otherwise. */
    suspend fun load(nick: String): String? {
        val (storedNick, blob) = prefs.reservedPassword() ?: return null
        if (storedNick != nick) return null
        val (iv, ct) = unpack(blob) ?: return null
        return decrypt(iv, ct)
    }

    suspend fun clear() = prefs.clearReservedPassword()

    private fun encrypt(password: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv to cipher.doFinal(password.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(iv: ByteArray, ct: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return kg.generateKey()
    }

    companion object {
        private const val ALIAS = "yumi_reserved_pw"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        /** iv and ciphertext as `hex(iv):hex(ct)` — pure, unit-tested, API-independent. */
        fun pack(iv: ByteArray, ct: ByteArray): String = iv.toHex() + ":" + ct.toHex()

        fun unpack(blob: String): Pair<ByteArray, ByteArray>? {
            val parts = blob.split(":")
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
            return runCatching { parts[0].fromHex() to parts[1].fromHex() }.getOrNull()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray {
            require(length % 2 == 0)
            return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
