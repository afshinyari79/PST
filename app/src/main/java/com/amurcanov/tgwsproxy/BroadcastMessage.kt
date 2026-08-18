package com.amurcanov.tgwsproxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object BroadcastMessage {
    private const val MESSAGE_URL =
        "https://raw.githubusercontent.com/afshinyari79/ProxyYab/main/message.txt"

    suspend fun fetch(): String = withContext(Dispatchers.IO) {
        try {
            val bustedUrl = MESSAGE_URL + "?_=" + System.currentTimeMillis()
            val conn = URL(bustedUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Pragma", "no-cache")
            try {
                val code = conn.responseCode
                if (code != 200) return@withContext ""
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                    .use { it.readText() }.trim()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
