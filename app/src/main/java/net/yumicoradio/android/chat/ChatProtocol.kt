// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage
import net.yumicoradio.android.chat.model.ChatUser
import org.json.JSONArray
import org.json.JSONObject

/**
 * The chat server's wire format, in one place.
 *
 * Every parse returns null or skips rather than throwing: one malformed frame must not take down
 * the whole stream, and the server is free to add fields this client has never heard of.
 */
object ChatProtocol {

    fun parseMessage(json: JSONObject): ChatMessage? {
        val text = json.optString("text").takeIf { it.isNotEmpty() } ?: return null
        return ChatMessage(
            user = json.optString("user").ifEmpty { "System" },
            text = text,
            type = json.optString("type").ifEmpty { "message" },
            channel = ChatChannel.fromSlug(json.optString("channel").takeIf { it.isNotEmpty() }),
            allChannels = json.optBoolean("allChannels", false),
        )
    }

    fun parsePresence(json: JSONObject): ChatMessage? {
        val nickname = json.optString("nickname").takeIf { it.isNotEmpty() } ?: return null
        val text = when (json.optString("action")) {
            "join" -> "$nickname joined the chat."
            "leave" -> "$nickname left the chat."
            else -> return null
        }
        return ChatMessage(
            user = "System",
            text = text,
            type = "system",
            channel = ChatChannel.ACTIVITY,
            timestamp = json.optLong("timestamp").takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    fun parseUserList(array: JSONArray): List<ChatUser> =
        (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val nickname = entry.optString("nickname").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ChatUser(
                nickname,
                entry.optString("color").takeIf { it.isNotEmpty() },
                entry.optString("status").takeIf { it.isNotEmpty() },
                entry.optString("role").takeIf { it.isNotEmpty() },
                entry.optBoolean("bot", false),
            )
        }

    /**
     * The `motd-all` payload: channel slug → the lines to print for it.
     *
     * Each channel's value is an array of `{text, type}` objects, where the type drives the colour
     * (`system`, `info`, …) exactly as it does for ordinary messages. A plain string is accepted
     * too, since older servers sent one.
     */
    fun parseMotd(json: JSONObject): Map<ChatChannel, List<ChatMessage>> =
        ChatChannel.entries.filter { it.serverBacked }.mapNotNull { channel ->
            val lines = when (val raw = json.opt(channel.slug)) {
                is JSONArray -> (0 until raw.length()).mapNotNull { i ->
                    val entry = raw.optJSONObject(i) ?: return@mapNotNull null
                    val text = entry.optString("text").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    ChatMessage(
                        user = MOTD_USER,
                        text = text,
                        type = entry.optString("type").ifEmpty { "system" },
                        channel = channel,
                    )
                }

                is String -> raw.lines().filter { it.isNotBlank() }.map { line ->
                    ChatMessage(MOTD_USER, line, "system", channel)
                }

                else -> emptyList()
            }
            lines.takeIf { it.isNotEmpty() }?.let { channel to it }
        }.toMap()

    fun joinPayload(
        nickname: String,
        password: String?,
        color: String? = null,
        reconnectToken: String? = null,
    ): JSONObject =
        JSONObject().put("nickname", nickname)
            // Tells the server this client can be reserved: it only prompts clients that say so.
            .put("caps", JSONArray().put("reserve-v1").put("presence-v1"))
            .apply {
                if (!password.isNullOrEmpty()) put("password", password)
                // Only a well-formed hex reaches the wire; anything else means "Auto", which the
                // server represents by the field's absence. Same guard the server applies itself.
                if (color != null && isValidNickColor(color)) put("color", color)
                if (!reconnectToken.isNullOrEmpty()) put("reconnectToken", reconnectToken)
            }

    /** A nickname-colour override the server will accept: `#rrggbb`, case-insensitive. */
    fun isValidNickColor(color: String): Boolean = color.matches(NICK_COLOR)

    private val NICK_COLOR = Regex("^#[0-9a-fA-F]{6}$")

    fun messagePayload(text: String): JSONObject = JSONObject().put("text", text)

    fun channelPayload(channel: ChatChannel): JSONObject = JSONObject().put("channel", channel.slug)

    /** MOTD lines have no author; this stands in so they render without a `<nick>` prefix. */
    const val MOTD_USER = "MOTD"
}
