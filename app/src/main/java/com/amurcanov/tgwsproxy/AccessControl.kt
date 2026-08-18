package com.amurcanov.tgwsproxy

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AccessControl {

    private const val WORKER_CHECK_URL =
        "https://proxysabet-bot.afshinyari79.workers.dev/check"

    enum class AccessResult { OK, NOT_REGISTERED, SERVICE_PAUSED, NETWORK_ERROR }

    data class AccessOutcome(val result: AccessResult, val detail: String? = null)

    fun getOrCreateDeviceCode(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return id?.lowercase() ?: "unknown-device"
    }

    suspend fun checkAccess(context: Context): AccessOutcome = withContext(Dispatchers.IO) {
        val deviceCode = getOrCreateDeviceCode(context)
        try {
            val body = httpGet("$WORKER_CHECK_URL?device=$deviceCode")
            val json = JSONObject(body)
            val allowed = json.optBoolean("allowed", false)

            if (allowed) {
                return@withContext AccessOutcome(AccessResult.OK)
            }

            val reason = json.optString("reason", "")
            return@withContext if (reason == "expired") {
                AccessOutcome(AccessResult.SERVICE_PAUSED)
            } else {
                AccessOutcome(AccessResult.NOT_REGISTERED)
            }
        } catch (e: Exception) {
            AccessOutcome(AccessResult.NETWORK_ERROR, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
                .use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
