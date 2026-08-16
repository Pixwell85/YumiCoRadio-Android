// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

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
import okhttp3.RequestBody
import org.json.JSONObject

/** Builds the wire request without ever placing the capability after the file in multipart data. */
internal fun buildUploadRequest(
    endpoint: String,
    token: String,
    filename: String,
    fileBody: RequestBody,
): Request {
    val multipart = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", filename, fileBody)
        .build()
    return Request.Builder()
        .url(endpoint)
        .header("X-Upload-Token", token)
        .post(multipart)
        .build()
}

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

        // Stream straight from the content Uri rather than reading the whole file into a ByteArray:
        // the server accepts up to 200 MB, and a single array that big is an OutOfMemoryError on
        // most devices. ProgressBody re-opens the stream in writeTo, so OkHttp retries stay correct.
        val size = fileSize(context, uri)
        val canRead = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true }
        }.getOrNull() == true
        if (!canRead) return@withContext Result.Failure("Could not read that file.")

        val request = buildUploadRequest(
            endpoint = endpoint,
            token = token,
            filename = name,
            fileBody = ProgressBody(context.contentResolver, uri, mime.toMediaTypeOrNull(), size, onProgress),
        )

        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(text)
                    Result.Success(
                        url = json.optString("url"),
                        filename = json.optString("filename", name),
                        size = json.optLong("size", size.coerceAtLeast(0)),
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
     * File length for the Content-Length header and the progress total, or -1 when unknown.
     *
     * Only a length we trust is returned: a SAF provider that reports a wrong or zero size for a
     * real file would make OkHttp promise a Content-Length the stream never matches, failing the
     * upload. When in doubt we return -1, which sends chunked — slower to frame, but it cannot lie.
     */
    private fun fileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index).takeIf { it > 0 }?.let { return it }
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 } ?: -1L
    }

    /**
     * Reports upload progress as the bytes go out.
     *
     * OkHttp offers no upload callback of its own, so the body writes in chunks and measures as it
     * goes; without this the UI can only say "uploading" and hope.
     */
    private class ProgressBody(
        private val resolver: android.content.ContentResolver,
        private val uri: Uri,
        private val type: okhttp3.MediaType?,
        private val length: Long,
        private val onProgress: (Progress) -> Unit,
    ) : okhttp3.RequestBody() {

        override fun contentType() = type

        override fun contentLength(): Long = length

        override fun writeTo(sink: okio.BufferedSink) {
            // Re-open per call: OkHttp may invoke writeTo more than once (a retry), and a content
            // stream is single-pass.
            val stream = resolver.openInputStream(uri)
                ?: throw java.io.IOException("Could not open the file for upload.")
            stream.use { input ->
                val buffer = ByteArray(CHUNK)
                var sent = 0L
                val started = System.nanoTime()
                var lastReport = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    sent += read

                    // Throttled: a callback per 16 KB chunk would recompose the UI far faster than a
                    // screen can show, for no extra information.
                    val now = System.nanoTime()
                    if (now - lastReport > REPORT_NS) {
                        lastReport = now
                        val seconds = (now - started) / 1_000_000_000.0
                        val rate = if (seconds > 0) sent / seconds else 0.0
                        onProgress(Progress(sent, if (length >= 0) length else sent, rate))
                    }
                }
                val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                val rate = if (seconds > 0) sent / seconds else 0.0
                onProgress(Progress(sent, if (length >= 0) length else sent, rate))
                sink.flush()
            }
        }

        private companion object {
            const val CHUNK = 16 * 1024
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
