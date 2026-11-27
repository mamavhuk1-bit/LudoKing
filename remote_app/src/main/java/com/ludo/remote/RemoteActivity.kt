package com.ludo.remote

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RoomData(
    val roomId: String,
    val deviceName: String,
    val batteryLevel: Int,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val activeColors: List<String>,
    val playersInfo: Map<String, String>,
    val gameState: String,
    val rankOrder: List<String>,
    val lastPing: Long
)

class RemoteActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY
    private val bucketName = "Ludo"

    private var liveRooms = listOf<RoomData>()
    private var selectedRoomId: String? = null
    private val selectedRankList = mutableListOf<String>()
    private var lastRenderedSignature = ""

    private lateinit var liveSectionHeader: TextView
    private lateinit var deviceBarLayout: LinearLayout
    private lateinit var deviceTitleText: TextView
    private lateinit var locationText: TextView
    private lateinit var progressContainer: LinearLayout
    private lateinit var mapBtn: Button
    private lateinit var buttonsContainer: LinearLayout
    private lateinit var rankPreviewText: TextView
    private lateinit var lastMatchResultText: TextView
    private var syncJob: Job? = null

    private val colorMap = mapOf(
        "RED" to "#E53935",
        "GREEN" to "#43A047",
        "YELLOW" to "#FBC02D",
        "BLUE" to "#1E88E5"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#080E21")

        val prefs = getSharedPreferences("ludo_remote_prefs", Context.MODE_PRIVATE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#080E21"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 40, 34, 40)
        }
        scroll.addView(root)
        setContentView(scroll)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        }
        val liveDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
        }
        ValueAnimator.ofFloat(0.35f, 1.0f).apply {
            duration = 750
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { liveDot.alpha = it.animatedValue as Float }
            start()
        }
        headerRow.addView(liveDot, LinearLayout.LayoutParams(22, 22).apply { rightMargin = 14 })

        headerRow.addView(TextView(this).apply {
            text = "LUDO ROYAL CONTROLLER"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD700"))
        })
        root.addView(headerRow)

        liveSectionHeader = TextView(this).apply {
            text = "LIVE SECTION (0 ACTIVE)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 14)
        }
        root.addView(liveSectionHeader)

        val deviceScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        deviceBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 4, 4, 14)
        }
        deviceScroll.addView(deviceBarLayout)
        root.addView(deviceScroll)

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#131E3A"), Color.parseColor("#0D152B"))
            ).apply {
                cornerRadius = 26f
                setStroke(3, Color.parseColor("#2563EB"))
            }
        }

        deviceTitleText = TextView(this).apply {
            text = "No Live Match Running"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        infoCard.addView(deviceTitleText)

        val locRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 12)
        }

        locationText = TextView(this).apply {
            text = "Location: Waiting for live game..."
            textSize = 13.5f
            setTextColor(Color.parseColor("#FDE047"))
        }
        locRow.addView(locationText, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 10 })

        mapBtn = Button(this).apply {
            text = "OPEN MAP"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                bounceView(this)
                openCurrentRoomMap()
            }
        }
        locRow.addView(mapBtn, LinearLayout.LayoutParams(-2, 84))
        infoCard.addView(locRow)

        progressContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        infoCard.addView(progressContainer)
        root.addView(infoCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 })

        val ctrlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 26)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111A33"))
                cornerRadius = 26f
                setStroke(3, Color.parseColor("#FFD700"))
            }
        }

        ctrlCard.addView(TextView(this).apply {
            text = "Tap Color to Lock #1 Winner:"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, 14)
        })

        buttonsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ctrlCard.addView(buttonsContainer)

        rankPreviewText = TextView(this).apply {
            text = "Mode: Normal Fair Play"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 16)
        }
        ctrlCard.addView(rankPreviewText)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val applyBtn = Button(this).apply {
            text = "APPLY RANK"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#15803D"))
            ).apply {
                cornerRadius = 22f
                setStroke(3, Color.parseColor("#86EFAC"))
            }
            setOnClickListener {
                bounceView(this)
                vibrateLight()
                if (selectedRankList.isEmpty()) {
                    Toast.makeText(this@RemoteActivity, "Select a player color first!", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("last_target", selectedRankList.first()).putString("last_outcome", "In Progress...").apply()
                    updateLastMatchBanner()
                    pushRankOrderToSelectedRoom(selectedRankList)
                }
            }
        }

        val resetBtn = Button(this).apply {
            text = "RESET NORMAL"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#475569"), Color.parseColor("#334155"))
            ).apply {
                cornerRadius = 22f
                setStroke(2, Color.parseColor("#94A3B8"))
            }
            setOnClickListener {
                bounceView(this)
                vibrateLight()
                selectedRankList.clear()
                updateRankPreview()
                lastRenderedSignature = ""
                refreshActiveRoomUI()
                pushRankOrderToSelectedRoom(emptyList())
            }
        }

        actionRow.addView(applyBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { rightMargin = 10 })
        actionRow.addView(resetBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { leftMargin = 10 })
        ctrlCard.addView(actionRow)
        root.addView(ctrlCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 })

        val historyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 20, 26, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 22f
                setStroke(2, Color.parseColor("#334155"))
            }
        }
        historyCard.addView(TextView(this).apply {
            text = "PREVIOUS MATCH TARGET RESULT"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 6)
        })
        lastMatchResultText = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        historyCard.addView(lastMatchResultText)
        root.addView(historyCard)

        updateLastMatchBanner()
        startLiveSync()
    }

    private fun updateLastMatchBanner() {
        val prefs = getSharedPreferences("ludo_remote_prefs", Context.MODE_PRIVATE)
        val target = prefs.getString("last_target", "") ?: ""
        val outcome = prefs.getString("last_outcome", "") ?: ""
        if (target.isEmpty()) {
            lastMatchResultText.text = "No previous target recorded."
            lastMatchResultText.setTextColor(Color.parseColor("#64748B"))
        } else {
            lastMatchResultText.text = "Target: #1 $target  ->  $outcome"
            lastMatchResultText.setTextColor(if (outcome.contains("WON")) Color.parseColor("#4ADE80") else Color.parseColor("#FACC15"))
        }
    }

    private fun bounceView(v: View) {
        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1.04f, 1.0f)
        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1.04f, 1.0f)
        ObjectAnimator.ofPropertyValuesHolder(v, sx, sy).apply {
            duration = 250
            interpolator = OvershootInterpolator()
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                else v.vibrate(35)
            }
        } catch (_: Exception) {}
    }

    private fun openCurrentRoomMap() {
        val room = liveRooms.find { it.roomId == selectedRoomId } ?: return
        if (room.latitude == 0.0 && room.longitude == 0.0) {
            Toast.makeText(this, "GPS coordinates not locked yet!", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("geo:${room.latitude},${room.longitude}?q=${room.latitude},${room.longitude}(${Uri.encode(room.deviceName)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        try { startActivity(mapIntent) } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${room.latitude},${room.longitude}")))
        }
    }

    private fun updateRankPreview() {
        if (selectedRankList.isEmpty()) {
            rankPreviewText.text = "Mode: Normal Fair Play"
            rankPreviewText.setTextColor(Color.parseColor("#38BDF8"))
        } else {
            val formatted = selectedRankList.mapIndexed { idx, col -> "#${idx + 1} $col" }.joinToString("  >  ")
            rankPreviewText.text = "LOCKED: $formatted"
            rankPreviewText.setTextColor(Color.parseColor("#4ADE80"))
        }
    }

    private fun startLiveSync() {
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val fetched = fetchOnlyLiveRooms()
                withContext(Dispatchers.Main) {
                    liveRooms = fetched
                    liveSectionHeader.text = "LIVE SECTION (${liveRooms.size} ACTIVE)"
                    if (selectedRoomId == null && liveRooms.isNotEmpty()) {
                        selectedRoomId = liveRooms.first().roomId
                        selectedRankList.clear()
                        selectedRankList.addAll(liveRooms.first().rankOrder)
                        updateRankPreview()
                    } else if (liveRooms.none { it.roomId == selectedRoomId }) {
                        selectedRoomId = liveRooms.firstOrNull()?.roomId
                    }
                    renderDeviceTabs()
                    refreshActiveRoomUI()
                }
                delay(1800)
            }
        }
    }

    private fun renderDeviceTabs() {
        deviceBarLayout.removeAllViews()
        for (r in liveRooms) {
            val isSelected = (r.roomId == selectedRoomId)
            val tab = TextView(this).apply {
                text = "  LIVE: ${r.deviceName} (${r.batteryLevel}%)  "
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(24, 15, 24, 15)
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#1D4ED8") else Color.parseColor("#1E293B"))
                    cornerRadius = 24f
                    setStroke(if (isSelected) 4 else 2, if (isSelected) Color.parseColor("#FFD700") else Color.parseColor("#475569"))
                }
                setOnClickListener {
                    bounceView(this)
                    vibrateLight()
                    selectedRoomId = r.roomId
                    selectedRankList.clear()
                    selectedRankList.addAll(r.rankOrder)
                    lastRenderedSignature = ""
                    updateRankPreview()
                    renderDeviceTabs()
                    refreshActiveRoomUI()
                }
            }
            deviceBarLayout.addView(tab, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 14 })
        }
    }

    private fun refreshActiveRoomUI() {
        val room = liveRooms.find { it.roomId == selectedRoomId }
        if (room == null) {
            deviceTitleText.text = "No Live Match Right Now"
            locationText.text = "Waiting for an active game..."
            progressContainer.removeAllViews()
            buttonsContainer.removeAllViews()
            lastRenderedSignature = ""
            return
        }

        deviceTitleText.text = "${room.deviceName} (Room #${room.roomId})  |  ${room.batteryLevel}%"
        locationText.text = "Location: ${room.locationName}"

        val prefs = getSharedPreferences("ludo_remote_prefs", Context.MODE_PRIVATE)
        val savedTarget = prefs.getString("last_target", "") ?: ""
        if (savedTarget.isNotEmpty() && room.gameState.contains("$savedTarget:100%")) {
            prefs.edit().putString("last_outcome", "WON (1st Place!)").apply()
            updateLastMatchBanner()
        }

        progressContainer.removeAllViews()
        val parts = room.gameState.split("|").map { it.trim() }.filter { it.contains(":") }
        for (part in parts) {
            val colName = part.substringBefore(":").trim()
            val rest = part.substringAfter(":").trim()
            val pctVal = rest.substringBefore("%").toIntOrNull() ?: 0
            val pName = room.playersInfo[colName] ?: colName
            val hex = colorMap[colName] ?: "#38BDF8"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6, 0, 6)
            }
            row.addView(TextView(this).apply {
                text = "$pName ($colName) — $rest"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(hex))
            })

            val track = FrameLayout(this).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")); cornerRadius = 10f }
            }
            val fill = View(this).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = 10f }
            }
            val trackW = (resources.displayMetrics.widthPixels - 140).coerceAtLeast(200)
            val fillW = ((trackW * pctVal) / 100).coerceAtLeast(16)
            track.addView(fill, FrameLayout.LayoutParams(fillW, 13))
            row.addView(track, LinearLayout.LayoutParams(-1, 13).apply { topMargin = 4 })
            progressContainer.addView(row)
        }

        val currentSig = "${room.roomId}|${room.activeColors.joinToString(",")}|${room.playersInfo}|${selectedRankList.joinToString(",")}"
        if (currentSig == lastRenderedSignature) return
        lastRenderedSignature = currentSig

        buttonsContainer.removeAllViews()
        val colorsToShow = room.activeColors
        if (colorsToShow.isEmpty()) {
            buttonsContainer.addView(TextView(this).apply {
                text = "Match in lobby — waiting for board start..."
                textSize = 13.5f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            })
            return
        }

        val gridRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = colorsToShow.size.toFloat()
        }

        colorsToShow.forEach { col ->
            val hex = colorMap[col] ?: "#3B82F6"
            val pName = room.playersInfo[col] ?: ""
            val rankPos = selectedRankList.indexOf(col)
            val label = buildString {
                if (rankPos != -1) append("#${rankPos + 1} ")
                append(col)
                if (pName.isNotEmpty() && !pName.equals(col, ignoreCase = true)) append("\n($pName)")
            }

            val btn = Button(this).apply {
                text = label
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (col == "YELLOW") Color.BLACK else Color.WHITE)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex))
                    cornerRadius = 20f
                    setStroke(if (rankPos != -1) 7 else 2, if (rankPos != -1) Color.WHITE else Color.parseColor("#475569"))
                }
                setOnClickListener {
                    bounceView(this)
                    vibrateLight()
                    if (selectedRankList.contains(col)) selectedRankList.remove(col)
                    else selectedRankList.add(col)
                    updateRankPreview()
                    lastRenderedSignature = ""
                    refreshActiveRoomUI()
                }
            }
            gridRow.addView(btn, LinearLayout.LayoutParams(0, 124, 1f).apply { leftMargin = 6; rightMargin = 6 })
        }
        buttonsContainer.addView(gridRow)
    }

    private fun fetchOnlyLiveRooms(): List<RoomData> {
        val result = mutableListOf<RoomData>()
        val now = System.currentTimeMillis()
        try {
            val listBody = """{"prefix":"","limit":50,"offset":0}""".toRequestBody("application/json".toMediaType())
            val listReq = Request.Builder()
                .url("$baseUrl/storage/v1/object/list/$bucketName")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(listBody)
                .build()

            val fileNames = mutableListOf<String>()
            client.newCall(listReq).execute().use { res ->
                val raw = res.body?.string() ?: ""
                if (res.isSuccessful && raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val name = arr.getJSONObject(i).optString("name", "")
                        if (name.startsWith("room_") && name.endsWith(".json")) fileNames.add(name)
                    }
                }
            }

            for (fName in fileNames) {
                val getReq = Request.Builder()
                    .url("$baseUrl/storage/v1/object/authenticated/$bucketName/$fName?t=$now")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(getReq).execute().use { res ->
                    val text = res.body?.string() ?: ""
                    if (res.isSuccessful && text.startsWith("{")) {
                        val obj = JSONObject(text)
                        val ping = obj.optLong("last_ping", 0L)
                        if (ping == 0L || (now - ping) > 15000L) {
                            deleteStaleRoom(fName)
                            return@use
                        }
                        val rId = obj.optString("room_id", fName.removePrefix("room_").removeSuffix(".json"))
                        val dev = obj.optString("device_name", "Android Device")
                        val bat = obj.optInt("battery_level", 100)
                        val lat = obj.optDouble("latitude", 0.0)
                        val lng = obj.optDouble("longitude", 0.0)
                        val loc = obj.optString("location_name", "Locating...")
                        val state = obj.optString("game_state", "In Lobby")

                        val colsRaw = obj.optString("active_colors", "").trim()
                        val activeCols = if (colsRaw.isNotEmpty() && colsRaw != "null") {
                            colsRaw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                        } else emptyList()

                        val pInfoMap = mutableMapOf<String, String>()
                        val pRaw = obj.optString("players_info", "").trim()
                        if (pRaw.isNotEmpty() && pRaw != "null") {
                            pRaw.split("|").forEach { entry ->
                                val parts = entry.trim().split(":")
                                if (parts.size == 2) pInfoMap[parts[0].trim().uppercase()] = parts[1].trim()
                            }
                        }

                        val rRaw = obj.optString("rank_order", "").trim()
                        val rList = if (rRaw.isNotEmpty() && rRaw != "null") {
                            rRaw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                        } else emptyList()

                        result.add(RoomData(rId, dev, bat, lat, lng, loc, activeCols, pInfoMap, state, rList, ping))
                    }
                }
            }
        } catch (_: Exception) {}
        return result.sortedByDescending { it.lastPing }
    }

    private fun deleteStaleRoom(fileName: String) {
        try {
            val delReq = Request.Builder()
                .url("$baseUrl/storage/v1/object/$bucketName/$fileName")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            client.newCall(delReq).execute().close()
        } catch (_: Exception) {}
    }

    private fun pushRankOrderToSelectedRoom(order: List<String>) {
        val rId = selectedRoomId ?: return
        val existing = liveRooms.find { it.roomId == rId } ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("room_id", rId)
                    put("device_name", existing.deviceName)
                    put("battery_level", existing.batteryLevel)
                    put("latitude", existing.latitude)
                    put("longitude", existing.longitude)
                    put("location_name", existing.locationName)
                    put("active_colors", existing.activeColors.joinToString(","))
                    put("players_info", existing.playersInfo.entries.joinToString(" | ") { "${it.key}:${it.value}" })
                    put("game_state", existing.gameState)
                    put("rank_order", order.joinToString(","))
                    put("last_ping", System.currentTimeMillis())
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/storage/v1/object/$bucketName/room_$rId.json")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("x-upsert", "true")
                    .post(body)
                    .build()
                client.newCall(req).execute().close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RemoteActivity,
                        if (order.isEmpty()) "Reset to Normal Play!" else "Locked: ${order.mapIndexed { i, c -> "#${i + 1} $c" }.joinToString(", ")}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }
}
