package net.yumicoradio.android.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Uploads a file to the chat server's `/chat/upload` endpoint.
 *
 * The server rejects anything without a current CSRF token (handed out over the socket as
 * `upload-token`), verifies the real file type by magic number, and enforces a daily byte quota per
 * IP — so several of the failures here are expected states, not bugs, and each gets its own message.
 */
class UploadClient(private val http: OkHttpClient) {

    sealed interface Result {
        data class Success(val url: String, val filename: String, val size: Long) : Result
        data class Failure(val message: String) : Result
    }

    /** Bytes sent so far, the total, and the current rate — what the site's overlay shows. */
    data class Progress(val sent: Long, val total: Long, val bytesPerSecond: Double) {
        val fraction: Float get() = if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
    }

    suspend fun upload(
        context: Context,
        uri: Uri,
        token: String?,
        endpoint: String = DEFAULT_ENDPOINT,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (token.isNullOrEmpty()) {
            return@withContext Result.Failure("Not connected to the chat, so there is no upload token yet.")
        }

        val name = displayName(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        // Read into memory: the server caps uploads at 200 MB, and streaming from a content Uri
        // through OkHttp would mean holding the descriptor open across retries anyway.
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext Result.Failure("Could not read that file.")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                name,
                ProgressBody(bytes, mime.toMediaTypeOrNull(), onProgress),
            )
            .addFormDataPart("token", token)
            .build()

        val request = Request.Builder().url(endpoint).post(body).build()

        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(text)
                    Result.Success(
                        url = json.optString("url"),
                        filename = json.optString("filename", name),
                        size = json.optLong("size", bytes.size.toLong()),
                    )
                } else {
                    Result.Failure(serverMessage(text, response.code))
                }
            }
        }.getOrElse { Result.Failure("Upload failed: ${it.message}") }
    }

    /** The server explains its own refusals; pass that through rather than inventing wording. */
    private fun serverMessage(body: String, code: Int): String =
        runCatching { JSONObject(body).optString("message").takeIf { it.isNotEmpty() } }
            .getOrNull() ?: "Upload rejected by the server (HTTP $code)."

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "upload"
    }

    /**
     * Reports upload progress as the bytes go out.
     *
     * OkHttp offers no upload callback of its own, so the body writes in chunks and measures as it
     * goes; without this the UI can only say "uploading" and hope.
     */
    private class ProgressBody(
        private val bytes: ByteArray,
        private val type: okhttp3.MediaType?,
        private val onProgress: (Progress) -> Unit,
    ) : okhttp3.RequestBody() {

        override fun contentType() = type

        override fun contentLength(): Long = bytes.size.toLong()

        override fun writeTo(sink: okio.BufferedSink) {
            val total = bytes.size.toLong()
            var sent = 0L
            val started = System.nanoTime()
            var lastReport = 0L

            while (sent < total) {
                val chunk = minOf(CHUNK, total - sent).toInt()
                sink.write(bytes, sent.toInt(), chunk)
                sent += chunk

                // Throttled: a callback per 16 KB chunk would recompose the UI far faster than a
                // screen can show, for no extra information.
                val now = System.nanoTime()
                if (now - lastReport > REPORT_NS || sent == total) {
                    lastReport = now
                    val seconds = (now - started) / 1_000_000_000.0
                    val rate = if (seconds > 0) sent / seconds else 0.0
                    onProgress(Progress(sent, total, rate))
                }
            }
            sink.flush()
        }

        private companion object {
            const val CHUNK = 16L * 1024
            const val REPORT_NS = 150_000_000L
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://s1.yumicoradio.net/chat/upload"

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

        fun formatSpeed(bytesPerSecond: Double): String = when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format("%.0f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.0f B/s", bytesPerSecond)
        }
    }
}
