package com.amurcanov.tgwsproxy

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AccessControl {

    private const val WHITELIST_URL =
        "https://raw.githubusercontent.com/afshinyari79/ProxyYab/main/whitelist_ids.txt"
    private const val CURRENT_PASSWORD_URL =
        "https://raw.githubusercontent.com/afshinyari79/ProxyYab/main/current_password.txt"

    enum class AccessResult { OK, NOT_REGISTERED, SERVICE_PAUSED, NETWORK_ERROR }

    data class AccessOutcome(val result: AccessResult, val detail: String? = null)

    fun getOrCreateDeviceCode(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return id?.lowercase() ?: "unknown-device"
    }

    suspend fun checkAccess(context: Context): AccessOutcome = withContext(Dispatchers.IO) {
        val deviceCode = getOrCreateDeviceCode(context)
        try {
            val whitelistBody = httpGet(WHITELIST_URL)
            val whitelisted = whitelistBody
                .split(Regex("\\r?\\n"))
                .any { it.trim().equals(deviceCode, ignoreCase = true) }

            if (!whitelisted) {
                return@withContext AccessOutcome(AccessResult.NOT_REGISTERED)
            }

            val remotePassword = httpGet(CURRENT_PASSWORD_URL).trim()
            return@withContext if (remotePassword.isNotEmpty()) {
                AccessOutcome(AccessResult.OK)
            } else {
                AccessOutcome(AccessResult.SERVICE_PAUSED)
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
            if (code == 404) return ""
            if (code != 200) throw Exception("HTTP $code")
            return BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                .use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
