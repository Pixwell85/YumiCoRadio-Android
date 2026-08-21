// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

object FdroidPackageParser {
    fun suggestedVersionCode(json: String): Int? = runCatching {
        Json.parseToJsonElement(json)
            .jsonObject["suggestedVersionCode"]
            ?.jsonPrimitive
            ?.intOrNull
            ?.takeIf { it > 0 }
    }.getOrNull()
}

sealed interface FdroidUpdateResult {
    data object UpToDate : FdroidUpdateResult
    data class Available(val versionCode: Int) : FdroidUpdateResult
    data class Failure(val message: String) : FdroidUpdateResult
}

class FdroidUpdateChecker(private val http: OkHttpClient) {
    suspend fun check(currentVersionCode: Int): FdroidUpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(PACKAGE_API)
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("F-Droid returned HTTP ${response.code}.")
                val code = FdroidPackageParser.suggestedVersionCode(response.body?.string().orEmpty())
                    ?: error("F-Droid returned an invalid package response.")
                if (code > currentVersionCode) {
                    FdroidUpdateResult.Available(code)
                } else {
                    FdroidUpdateResult.UpToDate
                }
            }
        }.getOrElse { error ->
            FdroidUpdateResult.Failure(error.message ?: "Could not contact F-Droid.")
        }
    }

    companion object {
        const val PACKAGE_API = "https://f-droid.org/api/v1/packages/net.yumicoradio.android"
        const val PACKAGE_PAGE = "https://f-droid.org/packages/net.yumicoradio.android/"
    }
}
