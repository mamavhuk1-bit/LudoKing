package com.ludo.remote

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ActiveRoom(
    val roomId: String,
    val deviceName: String,
    val battery: Int,
    val gameState: String,
    val rankOrder: String
)

class RemoteActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY

    private lateinit var roomsContainer: LinearLayout
    private lateinit var selectedRoomTitle: TextView
    private lateinit var liveProgressText: TextView
    private lateinit var statusText: TextView

    private var selectedRoomId: String = ""
    private val chosenRanks = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private val autoRefresh = object : Runnable {
        override fun run() {
            fetchActiveRooms()
            handler.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B1120")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
        }
        scroll.addView(root)

        val header = TextView(this).apply {
            text = "🎮 LudoKing Live Controller"
            textSize = 22f
            setTextColor(Color.parseColor("#FACC15"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(header)

        val sub = TextView(this).apply {
            text = "শুধুমাত্র বর্তমানে অনলাইন থাকা ডিভাইসগুলো এখানে দেখাবে:"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }
        root.addView(sub)

        roomsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(roomsContainer)

        val controlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 28f
                setStroke(3, Color.parseColor("#334155"))
            }
        }
        root.addView(controlCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })

        selectedRoomTitle = TextView(this).apply {
            text = "কোনো অনলাইন ডিভাইস পাওয়া যায়নি"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        controlCard.addView(selectedRoomTitle)

        liveProgressText = TextView(this).apply {
            text = "📊 লাইভ প্রোগ্রেস: --"
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 10, 0, 20)
        }
        controlCard.addView(liveProgressText)

        val hint = TextView(this).apply {
            text = "ক্রমানুসারে কালার চাপুন (প্রথমটি ১ম হবে, পরেরটি ২য়... অথবা শুধু ১ম সিলেক্ট করে Apply দিন):"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, 16)
        }
        controlCard.addView(hint)

        val colors = listOf(
            Triple("RED", "#E53935", Color.WHITE),
            Triple("GREEN", "#43A047", Color.WHITE),
            Triple("YELLOW", "#FBC02D", Color.BLACK),
            Triple("BLUE", "#1E88E5", Color.WHITE)
        )

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
        }
        for ((cName, hex, txtCol) in colors) {
            val btn = Button(this).apply {
                text = cName
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(txtCol)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex))
                    cornerRadius = 18f
                }
                setOnClickListener { toggleRankColor(cName) }
            }
            btnRow.addView(btn, LinearLayout.LayoutParams(0, 115, 1f).apply {
                leftMargin = 6; rightMargin = 6
            })
        }
        controlCard.addView(btnRow)

        statusText = TextView(this).apply {
            text = "সিলেক্টেড র‍্যাঙ্ক: স্বাভাবিক খেলা (Normal)"
            textSize = 15f
            setTextColor(Color.parseColor("#34D399"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 20)
        }
        controlCard.addView(statusText)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val saveBtn = Button(this).apply {
            text = "✅ Apply Rank"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 20f
            }
            setOnClickListener { pushRankOrder() }
        }
        val clearBtn = Button(this).apply {
            text = "🔄 Reset Normal"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#475569"))
                cornerRadius = 20f
            }
            setOnClickListener {
                chosenRanks.clear()
                updateRankLabel()
                pushRankOrder()
            }
        }
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, 115, 1f).apply { rightMargin = 10 })
        actionRow.addView(clearBtn, LinearLayout.LayoutParams(0, 115, 1f).apply { leftMargin = 10 })
        controlCard.addView(actionRow)

        setContentView(scroll)
        handler.post(autoRefresh)
    }

    private fun toggleRankColor(color: String) {
        if (color in chosenRanks) chosenRanks.remove(color) else chosenRanks.add(color)
        updateRankLabel()
    }

    private fun updateRankLabel() {
        statusText.text = if (chosenRanks.isEmpty()) {
            "সিলেক্টেড র‍্যাঙ্ক: স্বাভাবিক খেলা (Normal)"
        } else {
            "নির্ধারিত ক্রম: " + chosenRanks.mapIndexed { i, c -> "#${i + 1} $c" }.joinToString("  ➔  ")
        }
    }

    private fun fetchActiveRooms() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // শুধুমাত্র গত ১৫ সেকেন্ডের মধ্যে হার্টবিট পাঠানো সক্রিয় রুমগুলো আনা হবে
                val cutoff = Instant.now().minusSeconds(15).toString()
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/game_rooms?select=room_id,device_name,battery_level,game_state,rank_order&updated_at=gte.$cutoff&order=updated_at.desc")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(req).execute().use { res ->
                    val text = res.body?.string() ?: return@use
                    val arr = JSONArray(text)
                    val list = mutableListOf<ActiveRoom>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            ActiveRoom(
                                roomId = o.optString("room_id", ""),
                                deviceName = o.optString("device_name", "Android Device"),
                                battery = o.optInt("battery_level", 100),
                                gameState = o.optString("game_state", ""),
                                rankOrder = o.optString("rank_order", "")
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        renderDeviceCards(list)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderDeviceCards(rooms: List<ActiveRoom>) {
        roomsContainer.removeAllViews()
        if (rooms.isEmpty()) {
            selectedRoomId = ""
            selectedRoomTitle.text = "বর্তমানে কোনো লুডু অ্যাপ চালু নেই"
            liveProgressText.text = "📊 লাইভ প্রোগ্রেস: অপেক্ষা করা হচ্ছে..."
            return
        }

        if (selectedRoomId.isEmpty() || rooms.none { it.roomId == selectedRoomId }) {
            selectedRoomId = rooms.first().roomId
        }

        for (r in rooms) {
            val isSel = (r.roomId == selectedRoomId)
            if (isSel) {
                selectedRoomTitle.text = "🟢 ${r.deviceName} (Room #${r.roomId}) | 🔋 ${r.battery}%"
                liveProgressText.text = "📊 লাইভ অবস্থা: ${r.gameState}"
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                background = GradientDrawable().apply {
                    setColor(if (isSel) Color.parseColor("#1D4ED8") else Color.parseColor("#1E293B"))
                    cornerRadius = 22f
                    if (isSel) setStroke(4, Color.parseColor("#FACC15"))
                }
                setOnClickListener {
                    selectedRoomId = r.roomId
                    chosenRanks.clear()
                    if (r.rankOrder.isNotEmpty() && r.rankOrder != "null") {
                        chosenRanks.addAll(r.rankOrder.split(",").filter { it.isNotEmpty() })
                    }
                    updateRankLabel()
                    renderDeviceCards(rooms)
                }
            }

            val topLine = TextView(this).apply {
                text = "🟢 ${r.deviceName}   •   Room #${r.roomId}   •   🔋 ${r.battery}%"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val botLine = TextView(this).apply {
                text = "📊 ${r.gameState}"
                textSize = 13f
                setTextColor(Color.parseColor("#BAE6FD"))
                setPadding(0, 6, 0, 0)
            }
            card.addView(topLine)
            card.addView(botLine)
            roomsContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })
        }
    }

    private fun pushRankOrder() {
        if (selectedRoomId.isEmpty()) {
            Toast.makeText(this, "আগে একটি অনলাইন ডিভাইস সিলেক্ট করুন!", Toast.LENGTH_SHORT).show()
            return
        }
        val orderStr = chosenRanks.joinToString(",")
        val firstWinner = chosenRanks.firstOrNull() ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("rank_order", orderStr)
                    put("winner_color", if (firstWinner.isEmpty()) JSONObject.NULL else firstWinner)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/game_rooms?room_id=eq.$selectedRoomId")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(body)
                    .build()
                client.newCall(req).execute().use { res ->
                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful) {
                            Toast.makeText(this@RemoteActivity, "✅ কমান্ড সেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoRefresh)
    }
}