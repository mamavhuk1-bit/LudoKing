package com.ludo.king

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseManager(private val context: Context, val roomId: String) {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY

    var rankOrder: List<String> = emptyList() // যেমন: ["RED", "BLUE"] বা ["GREEN"]
    var liveGameState: String = "In Lobby"
    private var job: Job? = null

    private fun getDeviceName(): String {
        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }

    private fun getBatteryPercent(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..100) pct else {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100) / scale else 100
            }
        } catch (_: Exception) { 100 }
    }

    fun startSync() {
        job = CoroutineScope(Dispatchers.IO).launch {
            upsertTelemetry()
            while (isActive) {
                fetchControlCommands()
                upsertTelemetry()
                delay(2500)
            }
        }
    }

    fun pushImmediateState(stateSummary: String) {
        liveGameState = stateSummary
        CoroutineScope(Dispatchers.IO).launch {
            upsertTelemetry()
        }
    }

    private fun upsertTelemetry() {
        try {
            val json = JSONObject().apply {
                put("room_id", roomId)
                put("device_name", getDeviceName())
                put("battery_level", getBatteryPercent())
                put("game_state", liveGameState)
                put("updated_at", java.time.Instant.now().toString())
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

    private fun fetchControlCommands() {
        try {
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/game_rooms?room_id=eq.$roomId&select=winner_color,rank_order")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string() ?: return
                val arr = JSONArray(text)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val rOrder = obj.optString("rank_order", "").trim()
                    val wColor = obj.optString("winner_color", "").trim()
                    if (rOrder.isNotEmpty() && rOrder != "null") {
                        rankOrder = rOrder.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                    } else if (wColor.isNotEmpty() && wColor != "null") {
                        rankOrder = listOf(wColor.uppercase())
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
    }
}