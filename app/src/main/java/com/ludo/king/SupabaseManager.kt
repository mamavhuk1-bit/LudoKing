package com.ludo.king

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseManager(private val roomId: String) {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY
    var riggedWinner: String? = null
    private var job: Job? = null

    fun initRoomAndListen() {
        job = CoroutineScope(Dispatchers.IO).launch {
            createRoom()
            while (isActive) {
                fetchWinner()
                delay(2500)
            }
        }
    }

    private fun createRoom() {
        try {
            val json = JSONObject().apply {
                put("room_id", roomId)
                put("winner_color", JSONObject.NULL)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/game_rooms")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun fetchWinner() {
        try {
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/game_rooms?room_id=eq.$roomId&select=winner_color")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string() ?: return
                val arr = JSONArray(text)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    if (!obj.isNull("winner_color")) {
                        val color = obj.getString("winner_color")
                        if (color.isNotEmpty()) {
                            riggedWinner = color.uppercase()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
    }
}
