package com.ludo.remote

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class RemoteActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY

    private lateinit var roomInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🎮 LudoKing Controller"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subTitle = TextView(this).apply {
            text = "গেম শুরু হলে নিচে Room ID অটো চলে আসবে"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 36)
        }
        root.addView(subTitle)

        roomInput = EditText(this).apply {
            hint = "Room ID (যেমন: 4521)"
            textSize = 22f
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }
        root.addView(roomInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })

        val refreshBtn = Button(this).apply {
            text = "🔄 সর্বশেষ Room ID খুঁজুন"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener { fetchLatestRoom() }
        }
        root.addView(refreshBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })

        val colors = listOf(
            Triple("RED জিতবে", "RED", "#E53935"),
            Triple("GREEN জিতবে", "GREEN", "#43A047"),
            Triple("YELLOW জিতবে", "YELLOW", "#FBC02D"),
            Triple("BLUE জিতবে", "BLUE", "#1E88E5")
        )

        for ((label, code, hex) in colors) {
            val btn = Button(this).apply {
                text = label
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.parseColor(hex))
                setTextColor(if (code == "YELLOW") Color.BLACK else Color.WHITE)
                setOnClickListener { setWinner(code) }
            }
            root.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                150
            ).apply { bottomMargin = 20 })
        }

        statusText = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(Color.parseColor("#34D399"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        root.addView(statusText)

        setContentView(root)
        fetchLatestRoom()
    }

    private fun fetchLatestRoom() {
        statusText.text = "সর্বশেষ গেম খোঁজা হচ্ছে..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/game_rooms?select=room_id&order=created_at.desc&limit=1")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(req).execute().use { res ->
                    val text = res.body?.string() ?: return@use
                    val arr = JSONArray(text)
                    withContext(Dispatchers.Main) {
                        if (arr.length() > 0) {
                            val latestId = arr.getJSONObject(0).getString("room_id")
                            roomInput.setText(latestId)
                            statusText.text = "✅ Room $latestId পাওয়া গেছে! কালার সিলেক্ট করুন।"
                        } else {
                            statusText.text = "কোনো চলমান গেম পাওয়া যায়নি।"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ নেটওয়ার্ক সমস্যা, আবার চেষ্টা করুন।"
                }
            }
        }
    }

    private fun setWinner(color: String) {
        val roomId = roomInput.text.toString().trim()
        if (roomId.isEmpty()) {
            Toast.makeText(this, "আগে Room ID দিন!", Toast.LENGTH_SHORT).show()
            return
        }
        statusText.text = "আপডেট হচ্ছে..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply { put("winner_color", color) }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/game_rooms?room_id=eq.$roomId")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(body)
                    .build()
                client.newCall(req).execute().use { res ->
                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful) {
                            statusText.text = "✅ সেট হয়েছে! এখন $color ১ম হবে।\nআপনি অ্যাপটি বন্ধ করতে পারেন।"
                        } else {
                            statusText.text = "❌ আপডেট ব্যর্থ হয়েছে (${res.code})"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ নেটওয়ার্ক এরর!"
                }
            }
        }
    }
}