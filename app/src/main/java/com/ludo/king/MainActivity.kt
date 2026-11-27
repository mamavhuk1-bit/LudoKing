package com.ludo.king

import android.Manifest
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONArray
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class PlayerColor(val colorHex: Int, val lightHex: Int, val darkHex: Int, val startOffset: Int) {
    RED(Color.parseColor("#E32626"), Color.parseColor("#FF8A80"), Color.parseColor("#8E0000"), 0),
    GREEN(Color.parseColor("#1E9E3E"), Color.parseColor("#B9F6CA"), Color.parseColor("#0A521C"), 13),
    YELLOW(Color.parseColor("#F5C71A"), Color.parseColor("#FFFF8D"), Color.parseColor("#B58900"), 26),
    BLUE(Color.parseColor("#1882E8"), Color.parseColor("#82B1FF"), Color.parseColor("#074382"), 39)
}

data class Token(
    val owner: PlayerColor,
    val id: Int,
    var steps: Int = -1,
    var hopScale: Float = 1.0f
)

class MainActivity : AppCompatActivity() {
    private lateinit var supabase: SupabaseManager
    private lateinit var rootContainer: FrameLayout
    private val roomId = Random.nextInt(1000, 9999).toString()

    private var currentScreen = "SPLASH"
    private var vsComputer = false
    private var isSnakesMode = false
    private var bgMusicPlayer: MediaPlayer? = null
    private var startSfxPlayer: MediaPlayer? = null
    private var isMusicMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, t -> t.printStackTrace() }
        super.onCreate(savedInstanceState)

        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#0D1B4C")

        supabase = SupabaseManager(this, roomId)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
        }
        supabase.startSync()

        rootContainer = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.parseColor("#0D1B4C"))
        }
        setContentView(rootContainer)

        setupBackNavigation()
        startLobbyMusic()
        showSplashScreen()
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) { true }
    }

    private var netDialog: Dialog? = null
    private val netHandler = Handler(Looper.getMainLooper())

    private fun enforceNetAndLoc(onReady: () -> Unit) {
        if (!isInternetAvailable()) {
            if (netDialog?.isShowing == true) return
            val d = Dialog(this).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(44, 40, 44, 40)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#133878"), Color.parseColor("#09193D"))).apply {
                    cornerRadius = 36f; setStroke(6, Color.parseColor("#FFD700"))
                }
            }
            val icon = TextView(this).apply {
                text = "(( WI-FI / DATA ))"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FACC15")); gravity = Gravity.CENTER; setPadding(0, 0, 0, 12)
            }
            ValueAnimator.ofFloat(0.88f, 1.15f).apply {
                duration = 650; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { val s = it.animatedValue as Float; icon.scaleX = s; icon.scaleY = s }; start()
            }
            card.addView(icon)
            card.addView(TextView(this).apply {
                text = "NO INTERNET CONNECTION"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FFD700")); gravity = Gravity.CENTER
            })
            card.addView(TextView(this).apply {
                text = "\u0987\u09A8\u09CD\u099F\u09BE\u09B0\u09A8\u09C7\u099F \u09B8\u0982\u09AF\u09CB\u0997 \u09A8\u09C7\u0987!\n\u0997\u09C7\u09AE\u099F\u09BF \u0996\u09C7\u09B2\u09A4\u09C7 \u09AE\u09CB\u09AC\u09BE\u0987\u09B2 \u09A1\u09BE\u099F\u09BE \u09AC\u09BE Wi-Fi \u099A\u09BE\u09B2\u09C1 \u0995\u09B0\u09C1\u09A8\u0964\n\nPlease turn on Mobile Data or Wi-Fi."
                textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 14, 0, 24)
            })
            card.addView(makeRoyalButton("Retry / \u09AA\u09C1\u09A8\u09B0\u09BE\u09DF \u099A\u09C7\u09B7\u09CD\u099F\u09BE \u0995\u09B0\u09C1\u09A8", "#16A34A").apply {
                setOnClickListener {
                    if (isInternetAvailable()) { d.dismiss(); netDialog = null; enforceNetAndLoc(onReady) }
                }
            }, LinearLayout.LayoutParams(-1, 110))
            d.setContentView(card)
            d.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            netDialog = d; d.show()
            val watcher = object : Runnable {
                override fun run() {
                    if (d.isShowing && !isFinishing) {
                        if (isInternetAvailable()) { d.dismiss(); netDialog = null; enforceNetAndLoc(onReady) }
                        else netHandler.postDelayed(this, 1500)
                    }
                }
            }
            netHandler.postDelayed(watcher, 1500)
            return
        }
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101)
            } else { supabase.startLocationTracking() }
        } catch (_: Exception) {}
        onReady()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) supabase.startLocationTracking()
    }

    private fun startLobbyMusic() {
        try {
            if (bgMusicPlayer == null) {
                bgMusicPlayer = MediaPlayer.create(this, R.raw.gamesound)?.apply {
                    isLooping = true
                    setVolume(0.75f, 0.75f)
                }
            }
            if (!isMusicMuted && bgMusicPlayer?.isPlaying == false) bgMusicPlayer?.start()
        } catch (_: Exception) {}
    }

    private fun stopLobbyMusic() {
        try { if (bgMusicPlayer?.isPlaying == true) bgMusicPlayer?.pause() } catch (_: Exception) {}
    }

    private fun playGameStartSound() {
        if (isMusicMuted) return
        try {
            startSfxPlayer?.release()
            startSfxPlayer = MediaPlayer.create(this, R.raw.start)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {}
    }

    private fun toggleLobbyMute(): Boolean {
        isMusicMuted = !isMusicMuted
        SoundEngine.isMuted = isMusicMuted
        if (isMusicMuted) stopLobbyMusic() else if (currentScreen != "GAME") startLobbyMusic()
        return isMusicMuted
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                SoundEngine.playStep()
                when (currentScreen) {
                    "GAME" -> showRoyalConfirmDialog("Leave Match?", "Do you want to quit the current match and return to Home?") {
                        startLobbyMusic()
                        showHomeScreen()
                    }
                    "AFTER_NEXT" -> showAfterPassScreen()
                    "AFTER_PASS" -> showHomeScreen()
                    "HOME" -> showRoyalConfirmDialog("Exit Ludo King", "Are you sure you want to exit the game?") { finish() }
                }
            }
        })
    }

    private fun createRoyalDialog(titleText: String): Pair<Dialog, LinearLayout> {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#133878"), Color.parseColor("#0A1D45"))
            ).apply {
                cornerRadius = 36f
                setStroke(6, Color.parseColor("#FFD700"))
            }
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        card.addView(title)

        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog to card
    }

    private fun makeRoyalButton(label: String, bgHex: String, textHex: Int = Color.WHITE): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textHex)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#FFD700"))
            }
        }
    }

    private fun showRoyalConfirmDialog(title: String, msg: String, onYes: () -> Unit) {
        val (dialog, card) = createRoyalDialog(title)
        val body = TextView(this).apply {
            text = msg
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        card.addView(body)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val noBtn = makeRoyalButton("Cancel", "#334155").apply { setOnClickListener { dialog.dismiss() } }
        val yesBtn = makeRoyalButton("Confirm", "#16A34A").apply { setOnClickListener { dialog.dismiss(); onYes() } }
        row.addView(noBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { rightMargin = 12 })
        row.addView(yesBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { leftMargin = 12 })
        card.addView(row)
        dialog.show()
    }

    private fun showServerError() {
        val (dialog, card) = createRoyalDialog("⚠ SERVER CONNECTION ERROR")
        val body = TextView(this).apply {
            text = "Unable to connect to Ludo King Online Servers (Error 503).\n\nPlease check your internet connection or play Pass N Play / Computer mode."
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        card.addView(body)
        val okBtn = makeRoyalButton("OK", "#E53935").apply { setOnClickListener { dialog.dismiss() } }
        card.addView(okBtn, LinearLayout.LayoutParams(-1, 110))
        dialog.show()
    }

    private fun showSplashScreen() {
        currentScreen = "SPLASH"
        rootContainer.removeAllViews()
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.screen_open)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        rootContainer.addView(iv, FrameLayout.LayoutParams(-1, -1))
        Handler(Looper.getMainLooper()).postDelayed({ enforceNetAndLoc { showHomeScreen() } }, 2200)
    }

    private fun showHomeScreen() {
        currentScreen = "HOME"
        startLobbyMusic()
        supabase.pushMatchTelemetry("On Home Screen", "", "")
        rootContainer.removeAllViews()
        val frame = FrameLayout(this)

        val bg = ImageView(this).apply {
            setImageResource(R.drawable.screen_home)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val animOverlay = object : View(this) {
            var phase = 0f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            init {
                ValueAnimator.ofFloat(0f, 6.28f).apply {
                    duration = 1600
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            override fun onDraw(canvas: Canvas) {
                val alpha = (110 + sin(phase) * 120).toInt().coerceIn(20, 255)
                p.color = Color.argb(alpha, 255, 235, 59)
                canvas.drawRoundRect(width * 0.20f, height * 0.542f, width * 0.478f, height * 0.638f, 24f, 24f, p)
                canvas.drawRoundRect(width * 0.522f, height * 0.542f, width * 0.80f, height * 0.638f, 24f, 24f, p)
            }
        }
        frame.addView(animOverlay, FrameLayout.LayoutParams(-1, -1))

        frame.addView(makeMuteButton(), FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 110; rightMargin = 24
        })

        frame.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val nx = ev.x / v.width
                val ny = ev.y / v.height
                when {
                    nx in 0.19f..0.48f && ny in 0.53f..0.65f -> {
                        SoundEngine.playStep(); isSnakesMode = false; vsComputer = true; showAfterPassScreen()
                    }
                    nx in 0.52f..0.81f && ny in 0.53f..0.65f -> {
                        SoundEngine.playStep(); isSnakesMode = false; vsComputer = false; showAfterPassScreen()
                    }
                    nx in 0.72f..0.95f && ny in 0.65f..0.78f -> {
                        SoundEngine.playStep(); isSnakesMode = true; vsComputer = false; showAfterPassScreen()
                    }
                    else -> showServerError()
                }
            }
            true
        }
        rootContainer.addView(frame, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showSnakesWizard() {
        currentScreen = "SNAKES_WIZARD"
        rootContainer.removeAllViews()
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.parseColor("#450A0A"))
        }
        val bridge = object {
            @JavascriptInterface
            fun onTick() { SoundEngine.playStep() }

            @JavascriptInterface
            fun onServerError() { runOnUiThread { showServerError() } }

            @JavascriptInterface
            fun onBackLudo() { runOnUiThread { showHomeScreen() } }

            @JavascriptInterface
            fun onStartSnakeGame(boardIdx: Int, pCount: Int, namesJson: String, isComp: Boolean) {
                runOnUiThread {
                    val arr = JSONArray(namesJson)
                    val pNames = (0 until arr.length()).map { arr.getString(it) }
                    val palette = listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED)
                    val pCols = palette.take(pCount)
                    currentScreen = "GAME"
                    stopLobbyMusic()
                    playGameStartSound()
                    rootContainer.removeAllViews()
                    val sView = SnakesAndLaddersView(this@MainActivity, boardIdx, pCols, pNames, isComp, supabase) {
                        startLobbyMusic()
                        showHomeScreen()
                    }
                    rootContainer.addView(sView, FrameLayout.LayoutParams(-1, -1))
                }
            }
        }
        webView.addJavascriptInterface(bridge, "SnakeBridge")
        webView.loadUrl("file:///android_asset/snakes_wizard.html")
        rootContainer.addView(webView, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showAfterPassScreen() {
        currentScreen = "AFTER_PASS"
        rootContainer.removeAllViews()

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#081636")) }
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.screen_home)
            scaleType = ImageView.ScaleType.FIT_XY
            alpha = 0.25f
        }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val bridge = object {
            @JavascriptInterface
            fun onTick() { SoundEngine.playStep() }

            @JavascriptInterface
            fun onNext(mode: Int) {
                runOnUiThread {
                    SoundEngine.playStep()
                    showAfterNextScreen()
                }
            }

            @JavascriptInterface
            fun onBack() {
                runOnUiThread {
                    SoundEngine.playStep()
                    showHomeScreen()
                }
            }

            @JavascriptInterface
            fun onHelp() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, if (isSnakesMode) "Snakes & Ladders Mode" else "Select Classic, Team Up or Quick", Toast.LENGTH_SHORT).show()
                }
            }
        }
        webView.addJavascriptInterface(bridge, "SelectBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("setGameType($isSnakesMode)", null)
            }
        }
        webView.loadUrl("file:///android_asset/select_game.html")
        frame.addView(webView, FrameLayout.LayoutParams(-1, -1))

        frame.addView(makeMuteButton(), FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 42; rightMargin = 24
        })

        rootContainer.addView(frame, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showAfterNextScreen() {
        currentScreen = "AFTER_NEXT"
        rootContainer.removeAllViews()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#081636")) }
        val dimBg = ImageView(this).apply {
            setImageResource(R.drawable.screen_home)
            scaleType = ImageView.ScaleType.FIT_XY
            alpha = 0.22f
        }
        root.addView(dimBg, FrameLayout.LayoutParams(-1, -1))

        var playerCount = 2
        val playerNames = mutableListOf("Player 1", "Player 2", "Player 3", "Player 4")
        val badgeHexes = listOf("#1882E8", "#1E9E3E", "#E32626", "#F5C71A")

        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 36, 36, 36)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0E3A85"), Color.parseColor("#072154"))
            ).apply {
                cornerRadius = 32f
                setStroke(6, Color.parseColor("#FFD700"))
            }
        }

        val headerTitle = TextView(this).apply {
            text = "CHOOSE COLOR AND NAME"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        mainCard.addView(headerTitle)

        val playersBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1554B8"))
                cornerRadius = 22f
                setStroke(3, Color.parseColor("#38BDF8"))
            }
        }
        mainCard.addView(playersBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 28 })

        fun rebuildPlayerRows() {
            playersBox.removeAllViews()
            val outerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val greenCheck = TextView(this).apply {
                text = "✔"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#22C55E"))
                    setStroke(6, Color.WHITE)
                }
            }
            outerRow.addView(greenCheck, LinearLayout.LayoutParams(95, 95).apply { rightMargin = 24 })

            val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (i in 0 until playerCount) {
                val pRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 8, 0, 8)
                }
                val pinDot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(badgeHexes[i]))
                        setStroke(5, Color.WHITE)
                    }
                }
                pRow.addView(pinDot, LinearLayout.LayoutParams(48, 48).apply { rightMargin = 16 })

                val namePill = TextView(this).apply {
                    text = playerNames[i]
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#0F172A"))
                    setPadding(24, 14, 24, 14)
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = 14f
                        setStroke(3, Color.parseColor("#94A3B8"))
                    }
                    setOnClickListener { promptRoyalEditNames(playerCount, playerNames) { rebuildPlayerRows() } }
                }
                pRow.addView(namePill, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 14 })

                val diceIcon = TextView(this).apply { text = "🎲"; textSize = 22f }
                pRow.addView(diceIcon)
                listCol.addView(pRow)
            }
            outerRow.addView(listCol, LinearLayout.LayoutParams(-1, -2))
            playersBox.addView(outerRow)
        }
        rebuildPlayerRows()

        val countRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val countButtons = mutableMapOf<Int, TextView>()

        fun updateCountRings() {
            countButtons.forEach { (cnt, tv) ->
                val isSelected = (cnt == playerCount)
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isSelected) Color.parseColor("#166534") else Color.parseColor("#0B2A66"))
                    setStroke(if (isSelected) 10 else 6, if (isSelected) Color.parseColor("#00E676") else Color.parseColor("#F59E0B"))
                }
            }
        }

        for (cnt in listOf(2, 3, 4)) {
            val btn = TextView(this).apply {
                text = "${cnt}P"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener {
                    playerCount = cnt
                    SoundEngine.playStep()
                    updateCountRings()
                    rebuildPlayerRows()
                }
            }
            countButtons[cnt] = btn
            countRow.addView(btn, LinearLayout.LayoutParams(130, 130).apply { leftMargin = 20; rightMargin = 20 })
        }
        updateCountRings()
        mainCard.addView(countRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 32 })

        val playBtn = Button(this).apply {
            text = "Play"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))
            ).apply {
                cornerRadius = 40f
                setStroke(6, Color.parseColor("#FBBF24"))
            }
            setOnClickListener { startGame(playerCount, playerNames.take(playerCount), oneTokenOut = false) }
        }
        mainCard.addView(playBtn, LinearLayout.LayoutParams(460, 115).apply { bottomMargin = 20 })

        val playOneOutBtn = Button(this).apply {
            text = "Play\n- One Token Out -"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1E40AF"))
            ).apply {
                cornerRadius = 36f
                setStroke(6, Color.parseColor("#FBBF24"))
            }
            setOnClickListener { startGame(playerCount, playerNames.take(playerCount), oneTokenOut = true) }
        }
        mainCard.addView(playOneOutBtn, LinearLayout.LayoutParams(460, 135))

        root.addView(mainCard, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.CENTER
            leftMargin = 32; rightMargin = 32
        })

        val backBtn = TextView(this).apply {
            text = "↩"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E3A8A"))
                setStroke(6, Color.parseColor("#FBBF24"))
            }
            setOnClickListener { SoundEngine.playStep(); showAfterPassScreen() }
        }
        root.addView(backBtn, FrameLayout.LayoutParams(115, 115).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            bottomMargin = 36; leftMargin = 36
        })

        root.addView(makeMuteButton(), FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 36; rightMargin = 28
        })

        rootContainer.addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    private fun promptRoyalEditNames(count: Int, names: MutableList<String>, onSaved: () -> Unit) {
        val (dialog, card) = createRoyalDialog("✏ EDIT PLAYER NAMES")
        val badgeColors = listOf("#1882E8", "#1E9E3E", "#E32626", "#F5C71A")

        val inputs = (0 until count).map { idx ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(badgeColors[idx]))
                    setStroke(3, Color.WHITE)
                }
            }
            row.addView(dot, LinearLayout.LayoutParams(44, 44).apply { rightMargin = 16 })

            val et = EditText(this).apply {
                setText(names[idx])
                hint = "Player ${idx + 1}"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                textSize = 16f
                setPadding(24, 18, 24, 18)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E3A8A"))
                    cornerRadius = 18f
                    setStroke(2, Color.parseColor("#FFD700"))
                }
            }
            row.addView(et, LinearLayout.LayoutParams(-1, -2))
            card.addView(row)
            et
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 28, 0, 0)
        }
        val cancelBtn = makeRoyalButton("Cancel", "#475569").apply { setOnClickListener { dialog.dismiss() } }
        val saveBtn = makeRoyalButton("Save Names", "#16A34A").apply {
            setOnClickListener {
                inputs.forEachIndexed { idx, et ->
                    val txt = et.text.toString().trim()
                    if (txt.isNotEmpty()) names[idx] = txt
                }
                dialog.dismiss()
                onSaved()
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { rightMargin = 12 })
        btnRow.addView(saveBtn, LinearLayout.LayoutParams(0, 110, 1f).apply { leftMargin = 12 })
        card.addView(btnRow)
        dialog.show()
    }

    private fun makeMuteButton(): TextView {
        return TextView(this).apply {
            text = if (isMusicMuted) "🔇" else "🔊"
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC0D1B4C"))
                setStroke(4, Color.parseColor("#FFD700"))
            }
            setOnClickListener {
                val muted = toggleLobbyMute()
                text = if (muted) "🔇" else "🔊"
            }
        }
    }

    private fun startGame(playerCount: Int, names: List<String>, oneTokenOut: Boolean) {
        currentScreen = "GAME"
        stopLobbyMusic()
        playGameStartSound()

        rootContainer.removeAllViews()
        val activeColors = when (playerCount) {
            2 -> listOf(PlayerColor.BLUE, PlayerColor.GREEN)
            3 -> listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN)
            else -> listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW)
        }
        val gameView: View = if (isSnakesMode) {
            SnakesAndLaddersView(this, 0, activeColors, names, vsComputer, supabase) {
                startLobbyMusic()
                showHomeScreen()
            }
        } else {
            LudoGameView(this, activeColors, names, oneTokenOut, vsComputer, supabase) {
                startLobbyMusic()
                showHomeScreen()
            }
        }
        rootContainer.addView(gameView, FrameLayout.LayoutParams(-1, -1))

        val banner = TextView(this).apply {
            text = "GAME START"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(64, 24, 64, 24)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F0133878"), Color.parseColor("#F50A1D45"))
            ).apply {
                cornerRadius = 16f
                setStroke(5, Color.parseColor("#FFD700"))
            }
            scaleX = 0.2f; scaleY = 0.2f; alpha = 0f
        }
        rootContainer.addView(banner, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })

        banner.animate()
            .scaleX(1.05f).scaleY(1.05f).alpha(1f)
            .setDuration(380)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                Handler(Looper.getMainLooper()).postDelayed({
                    banner.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f).setDuration(350)
                        .withEndAction { rootContainer.removeView(banner) }.start()
                }, 1300)
            }.start()
    }

    override fun onPause() { super.onPause(); stopLobbyMusic() }
    override fun onResume() { super.onResume(); if (currentScreen != "GAME" && !isMusicMuted) startLobbyMusic() }
    override fun onDestroy() { super.onDestroy(); bgMusicPlayer?.release(); startSfxPlayer?.release(); supabase.stop() }
}

class LudoGameView(
    context: Context,
    private val activePlayers: List<PlayerColor>,
    private val playerNames: List<String>,
    oneTokenOut: Boolean,
    private val vsComputer: Boolean,
    private val supabase: SupabaseManager,
    private val onExitHome: () -> Unit
) : View(context) {

    private var currentPlayerIdx = 0
    private var diceValue = 1
    private var consecutiveSixes = 0
    private var isDiceRolled = false
    private var isAnimating = false
    private val finishedRankings = mutableListOf<PlayerColor>()

    private var diceRotation = 0f
    private var diceScale = 1f
    private var pulsePhase = 0f

    private val tokens = activePlayers.flatMap { p ->
        (0..3).map { id -> Token(p, id, steps = if (oneTokenOut && id == 0) 0 else -1) }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardTop = 0f
    private var boardLeft = 0f
    private var activeDiceRect = RectF()
    private var muteRect = RectF()
    private var backRect = RectF()
    private val handler = Handler(Looper.getMainLooper())

    private val pulseRunnable = object : Runnable {
        override fun run() {
            pulsePhase += 0.24f
            invalidate()
            handler.postDelayed(this, 40)
        }
    }

    init {
        handler.post(pulseRunnable)
        syncProgressToRemote()
    }

    private val mainTrack = listOf(
        1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6,
        6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
        7 to 0, 8 to 0,
        8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
        9 to 6, 10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
        14 to 7, 14 to 8,
        13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8,
        8 to 9, 8 to 10, 8 to 11, 8 to 12, 8 to 13, 8 to 14,
        7 to 14, 6 to 14,
        6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
        5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
        0 to 7, 0 to 6
    )

    private val safeGlobalIndices = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    private val homeStretches = mapOf(
        PlayerColor.RED to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7, 6 to 7),
        PlayerColor.GREEN to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6),
        PlayerColor.YELLOW to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7, 8 to 7),
        PlayerColor.BLUE to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9, 7 to 8)
    )

    private val yardCoords = mapOf(
        PlayerColor.RED to listOf(2.0f to 2.0f, 4.0f to 2.0f, 2.0f to 4.0f, 4.0f to 4.0f),
        PlayerColor.GREEN to listOf(11.0f to 2.0f, 13.0f to 2.0f, 11.0f to 4.0f, 13.0f to 4.0f),
        PlayerColor.YELLOW to listOf(11.0f to 11.0f, 13.0f to 11.0f, 11.0f to 13.0f, 13.0f to 13.0f),
        PlayerColor.BLUE to listOf(2.0f to 11.0f, 4.0f to 11.0f, 2.0f to 13.0f, 4.0f to 13.0f)
    )

    private fun syncProgressToRemote() {
        val summary = activePlayers.joinToString(" | ") { p ->
            val pTokens = tokens.filter { it.owner == p }
            val totalSteps = pTokens.sumOf { if (it.steps < 0) 0 else it.steps }
            val pct = ((totalSteps / 228.0) * 100).toInt().coerceIn(0, 100)
            val homeCount = pTokens.count { it.steps == 57 }
            "${p.name}:$pct%(H:$homeCount)"
        }
        val colorsCsv = activePlayers.joinToString(",") { it.name }
        val pInfo = activePlayers.mapIndexed { idx, p -> "${p.name}:${playerNames.getOrElse(idx) { p.name }}" }.joinToString(" | ")
        supabase.pushMatchTelemetry(summary, colorsCsv, pInfo)
    }

    private fun rollRankedDice(player: PlayerColor): Int {
        val order = supabase.rankOrder
        if (order.isEmpty()) return Random.nextInt(1, 7)

        val activeOrdered = order.filter { colName ->
            activePlayers.any { it.name == colName } && finishedRankings.none { it.name == colName }
        }
        if (activeOrdered.isEmpty()) return Random.nextInt(1, 7)

        val currentTopTarget = activeOrdered.first()
        val myRankIdx = activeOrdered.indexOf(player.name)
        val myTokens = tokens.filter { it.owner == player }
        val finishedCount = myTokens.count { it.steps == 57 }
        val mustWaitForHigherRank = (myRankIdx > 0 && finishedCount == 3)

        if (player.name == currentTopTarget) {
            val candidates = mutableListOf<Int>()
            for (d in 1..6) {
                if (consecutiveSixes == 2 && d == 6) continue
                var weight = 10
                for (t in myTokens) {
                    if (t.steps == -1 && d == 6) weight += 42
                    if (t.steps in 0..56) {
                        val nextStep = t.steps + d
                        if (nextStep == 57) weight += 50
                        if (nextStep in 0..50) {
                            val gIdx = (player.startOffset + nextStep) % 52
                            if (gIdx in safeGlobalIndices) weight += 22
                            val canKill = tokens.any { opp ->
                                opp.owner != player && opp.steps in 0..50 &&
                                        ((opp.owner.startOffset + opp.steps) % 52 == gIdx) &&
                                        gIdx !in safeGlobalIndices
                            }
                            if (canKill) weight += 55
                        }
                    }
                }
                repeat(weight) { candidates.add(d) }
            }
            return if (candidates.isEmpty()) Random.nextInt(1, 6) else candidates.random()
        } else {
            val isBlocked = (myRankIdx == -1 || myRankIdx > 0)
            val safe = mutableListOf<Int>()
            for (d in 1..6) {
                var blocksWin = false
                var hitsTop = false
                for (t in myTokens) {
                    if (isBlocked && finishedCount >= 3 && t.steps in 0..56 && t.steps + d == 57) blocksWin = true
                    if (t.steps in 0..50 && t.steps + d <= 50) {
                        val gIdx = (player.startOffset + t.steps + d) % 52
                        if (gIdx !in safeGlobalIndices && tokens.any { w -> w.owner.name == currentTopTarget && w.steps in 0..50 && ((w.owner.startOffset + w.steps) % 52 == gIdx) }) hitsTop = true
                    }
                }
                if (!blocksWin && !hitsTop) safe.add(d)
            }
            return if (safe.isNotEmpty()) safe.random() else 1
        }
    }

    private fun getTokenGridPos(t: Token): Pair<Float, Float> {
        return when {
            t.steps == -1 -> yardCoords[t.owner]!![t.id]
            t.steps in 0..50 -> {
                val c = mainTrack[(t.owner.startOffset + t.steps) % 52]
                (c.first + 0.5f) to (c.second + 0.5f)
            }
            t.steps in 51..56 -> {
                val c = homeStretches[t.owner]!![t.steps - 51]
                (c.first + 0.5f) to (c.second + 0.5f)
            }
            else -> 7.5f to 7.5f
        }
    }

    private fun drawLudoKingPatternBackground(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#184E77"))
        canvas.save()
        canvas.rotate(-28f, width / 2f, height / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.argb(26, 255, 255, 255)
        val step = 190f
        var y = -height.toFloat()
        while (y < height * 2f) {
            var x = -width.toFloat()
            while (x < width * 2f) {
                canvas.drawRoundRect(x, y, x + 135f, y + 135f, 22f, 22f, paint)
                canvas.drawCircle(x + 42f, y + 42f, 12f, paint)
                canvas.drawCircle(x + 93f, y + 93f, 12f, paint)
                x += step
            }
            y += step
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLudoKingPatternBackground(canvas)

        boardLeft = 16f
        val boardSize = width - boardLeft * 2
        cellSize = boardSize / 15f
        boardTop = (height - boardSize) / 2f

        paint.color = Color.WHITE
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize, paint)

        for (p in PlayerColor.values()) {
            paint.color = p.colorHex
            homeStretches[p]!!.take(5).forEach { (cx, cy) ->
                canvas.drawRect(
                    boardLeft + cx * cellSize, boardTop + cy * cellSize,
                    boardLeft + (cx + 1) * cellSize, boardTop + (cy + 1) * cellSize, paint
                )
            }
            val sc = mainTrack[p.startOffset]
            canvas.drawRect(
                boardLeft + sc.first * cellSize, boardTop + sc.second * cellSize,
                boardLeft + (sc.first + 1) * cellSize, boardTop + (sc.second + 1) * cellSize, paint
            )
        }

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#64748B")
        paint.strokeWidth = 2f
        for (i in 0..15) {
            canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, boardTop + boardSize, paint)
            canvas.drawLine(boardLeft, boardTop + i * cellSize, boardLeft + boardSize, boardTop + i * cellSize, paint)
        }
        paint.style = Paint.Style.FILL

        // ইয়ার্ডগুলো গ্রিড লাইনের পরে আঁকা যাতে হোম বক্সের ভেতরে কোনো গ্রিড লাইন না থাকে
        drawYard(canvas, PlayerColor.RED, 0, 0)
        drawYard(canvas, PlayerColor.GREEN, 9, 0)
        drawYard(canvas, PlayerColor.YELLOW, 9, 9)
        drawYard(canvas, PlayerColor.BLUE, 0, 9)

        safeGlobalIndices.forEach { idx ->
            val (cx, cy) = mainTrack[idx]
            val isStart = PlayerColor.values().any { it.startOffset == idx }
            paint.color = if (isStart) Color.WHITE else Color.parseColor("#475569")
            paint.textSize = cellSize * 0.65f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("☆", boardLeft + (cx + 0.5f) * cellSize, boardTop + (cy + 0.73f) * cellSize, paint)
        }

        paint.textSize = cellSize * 0.62f
        paint.textAlign = Paint.Align.CENTER
        paint.color = PlayerColor.GREEN.colorHex
        canvas.drawText("↓", boardLeft + 7.5f * cellSize, boardTop + 0.72f * cellSize, paint)
        paint.color = PlayerColor.RED.colorHex
        canvas.drawText("→", boardLeft + 0.5f * cellSize, boardTop + 7.72f * cellSize, paint)
        paint.color = PlayerColor.YELLOW.colorHex
        canvas.drawText("←", boardLeft + 14.5f * cellSize, boardTop + 7.72f * cellSize, paint)
        paint.color = PlayerColor.BLUE.colorHex
        canvas.drawText("↑", boardLeft + 7.5f * cellSize, boardTop + 14.72f * cellSize, paint)

        drawCenterTriangles(canvas)

        activePlayers.forEachIndexed { idx, p ->
            val pName = playerNames.getOrElse(idx) { p.name }
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            when (p) {
                PlayerColor.BLUE -> canvas.drawText(pName, boardLeft + 3f * cellSize, boardTop + boardSize - 12f, paint)
                PlayerColor.YELLOW -> canvas.drawText(pName, boardLeft + 12f * cellSize, boardTop + boardSize - 12f, paint)
                PlayerColor.GREEN -> {
                    canvas.save()
                    canvas.rotate(180f, boardLeft + 12f * cellSize, boardTop + 26f)
                    canvas.drawText(pName, boardLeft + 12f * cellSize, boardTop + 36f, paint)
                    canvas.restore()
                }
                PlayerColor.RED -> {
                    canvas.save()
                    canvas.rotate(180f, boardLeft + 3f * cellSize, boardTop + 26f)
                    canvas.drawText(pName, boardLeft + 3f * cellSize, boardTop + 36f, paint)
                    canvas.restore()
                }
            }
        }

        val curr = activePlayers[currentPlayerIdx]
        drawCornerDiceBoxes(canvas, curr, boardSize)

        // ১. মাঝখানে (Home Triangle) পৌঁছে যাওয়া গুটিগুলো নিজ নিজ রঙের ত্রিভুজে আঁকা
        val finishedTokens = tokens.filter { it.steps == 57 }
        for (p in activePlayers) {
            val pDone = finishedTokens.filter { it.owner == p }
            pDone.forEachIndexed { idx, _ ->
                val spread = (idx - (pDone.size - 1) / 2f) * (cellSize * 0.34f)
                val (fx, fy) = when (p) {
                    PlayerColor.RED -> (6.52f * cellSize) to (7.5f * cellSize + spread)
                    PlayerColor.GREEN -> (7.5f * cellSize + spread) to (6.52f * cellSize)
                    PlayerColor.YELLOW -> (8.48f * cellSize) to (7.5f * cellSize + spread)
                    PlayerColor.BLUE -> (7.5f * cellSize + spread) to (8.48f * cellSize)
                }
                drawLudoKingExactPin(canvas, boardLeft + fx, boardTop + fy, p, 0.72f, false)
            }
        }

        // ২. বোর্ডের চলমান গুটিগুলো আঁকা
        val activeTokens = tokens.filter { it.steps < 57 }.sortedBy { if (it.owner == curr) 1 else 0 }
        val grouped = activeTokens.groupBy { getTokenGridPos(it) }
        for ((pos, group) in grouped) {
            val count = group.size
            group.forEachIndexed { index, t ->
                val offsetX = if (count > 1) (index - (count - 1) / 2f) * (cellSize * 0.24f) else 0f
                val baseScale = if (count > 1) 0.76f else 1.0f
                val px = boardLeft + pos.first * cellSize + offsetX
                val py = boardTop + pos.second * cellSize
                val isMovable = isDiceRolled && !isAnimating && t.owner == curr && canMove(t, diceValue)
                drawLudoKingExactPin(canvas, px, py, t.owner, baseScale * t.hopScale, isMovable)
            }
        }

        backRect.set(28f, height - 105f, 118f, height - 25f)
        paint.color = Color.parseColor("#1E3A8A")
        canvas.drawRoundRect(backRect, 20f, 20f, paint)
        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("☰", backRect.centerX(), backRect.centerY() + 14f, paint)

        muteRect.set(width - 118f, height - 105f, width - 28f, height - 25f)
        paint.color = Color.parseColor("#1E3A8A")
        canvas.drawRoundRect(muteRect, 20f, 20f, paint)
        paint.color = Color.WHITE
        canvas.drawText(if (SoundEngine.isMuted) "🔇" else "🔊", muteRect.centerX(), muteRect.centerY() + 14f, paint)

        if (finishedRankings.size >= activePlayers.size - 1 && activePlayers.size > 1) {
            paint.color = Color.argb(232, 10, 20, 50)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 62f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏆 MATCH RESULTS 🏆", width / 2f, height * 0.34f, paint)

            val remaining = activePlayers.filter { it !in finishedRankings }
            val fullOrder = finishedRankings + remaining
            fullOrder.forEachIndexed { rIdx, pColor ->
                val pIdx = activePlayers.indexOf(pColor)
                val pName = playerNames.getOrElse(pIdx) { pColor.name }
                paint.color = pColor.lightHex
                paint.textSize = 46f
                canvas.drawText("#${rIdx + 1}  $pName (${pColor.name})", width / 2f, height * 0.44f + rIdx * 75f, paint)
            }
        }
    }

    // জুম করা ছবি (1000104953.png) অনুযায়ী হুবহু সিলভার ম্যাপ-পিন গুটি
    private fun drawLudoKingExactPin(canvas: Canvas, px: Float, py: Float, p: PlayerColor, scale: Float, highlight: Boolean) {
        val u = cellSize * 0.45f * scale

        if (highlight) {
            val ringR = u * 1.15f + sin(pulsePhase) * 5f
            paint.color = Color.parseColor("#FFD700")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawCircle(px, py + u * 0.12f, ringR, paint)
            paint.style = Paint.Style.FILL
        }

        val tipY = py + u * 0.36f
        val headY = py - u * 0.48f
        val headR = u * 0.74f

        paint.color = Color.argb(100, 0, 0, 0)
        val shadowPath = Path().apply {
            addCircle(px + 3f, headY + 4f, headR, Path.Direction.CW)
            moveTo(px - headR * 0.88f + 3f, headY + headR * 0.32f + 4f)
            lineTo(px + headR * 0.88f + 3f, headY + headR * 0.32f + 4f)
            lineTo(px + 3f, tipY + 4f)
            close()
        }
        canvas.drawPath(shadowPath, paint)

        paint.color = Color.parseColor("#374151")
        val borderPath = Path().apply {
            addCircle(px, headY, headR + 2.5f, Path.Direction.CW)
            moveTo(px - headR * 0.90f, headY + headR * 0.30f)
            lineTo(px + headR * 0.90f, headY + headR * 0.30f)
            lineTo(px, tipY + 2.5f)
            close()
        }
        canvas.drawPath(borderPath, paint)

        paint.shader = LinearGradient(
            px - headR, headY - headR, px + headR, tipY,
            Color.WHITE, Color.parseColor("#CBD5E1"), Shader.TileMode.CLAMP
        )
        val pinBody = Path().apply {
            addCircle(px, headY, headR, Path.Direction.CW)
            moveTo(px - headR * 0.86f, headY + headR * 0.30f)
            lineTo(px + headR * 0.86f, headY + headR * 0.30f)
            lineTo(px, tipY)
            close()
        }
        canvas.drawPath(pinBody, paint)
        paint.shader = null

        val innerR = headR * 0.62f
        paint.color = Color.parseColor("#1F2937")
        canvas.drawCircle(px, headY, innerR + 2f, paint)

        paint.shader = RadialGradient(
            px + innerR * 0.25f, headY + innerR * 0.25f, innerR * 1.4f,
            intArrayOf(p.colorHex, p.darkHex),
            floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(px, headY, innerR, paint)
        paint.shader = null
    }

    private fun drawCornerDiceBoxes(canvas: Canvas, curr: PlayerColor, boardSize: Float) {
        val boxW = 248f
        val boxH = 128f

        for (p in activePlayers) {
            val rect = when (p) {
                PlayerColor.BLUE -> RectF(boardLeft + 8f, boardTop + boardSize + 18f, boardLeft + 8f + boxW, boardTop + boardSize + 18f + boxH)
                PlayerColor.YELLOW -> RectF(boardLeft + boardSize - boxW - 8f, boardTop + boardSize + 18f, boardLeft + boardSize - 8f, boardTop + boardSize + 18f + boxH)
                PlayerColor.RED -> RectF(boardLeft + 8f, boardTop - boxH - 18f, boardLeft + 8f + boxW, boardTop - 18f)
                PlayerColor.GREEN -> RectF(boardLeft + boardSize - boxW - 8f, boardTop - boxH - 18f, boardLeft + boardSize - 8f, boardTop - 18f)
            }

            val isCurrent = (p == curr)
            if (isCurrent) activeDiceRect.set(rect)

            paint.color = if (isCurrent) Color.parseColor("#FFD700") else Color.parseColor("#64748B")
            canvas.drawRoundRect(rect, 20f, 20f, paint)

            paint.color = Color.parseColor("#1D4ED8")
            canvas.drawRoundRect(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f, 16f, 16f, paint)

            val badgeX = if (p == PlayerColor.BLUE || p == PlayerColor.RED) rect.left + 48f else rect.right - 48f
            val badgeY = rect.centerY()
            paint.color = Color.WHITE
            canvas.drawCircle(badgeX, badgeY, 32f, paint)
            paint.color = p.colorHex
            canvas.drawCircle(badgeX, badgeY, 25f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(badgeX, badgeY - 3f, 11f, paint)

            val dSize = boxH - 26f
            val dLeft = if (p == PlayerColor.BLUE || p == PlayerColor.RED) rect.right - dSize - 14f else rect.left + 14f
            val dTop = rect.top + 13f

            canvas.save()
            val cx = dLeft + dSize / 2f
            val cy = dTop + dSize / 2f
            canvas.translate(cx, cy)
            if (isCurrent) {
                canvas.rotate(diceRotation)
                canvas.scale(diceScale, diceScale)
            }
            val h = dSize / 2f
            paint.color = if (isCurrent) Color.parseColor("#FFF5F5") else Color.parseColor("#E2E8F0")
            canvas.drawRoundRect(-h, -h, h, h, 16f, 16f, paint)

            if (isCurrent) drawDiceDots(canvas, diceValue, h * 0.55f, p.colorHex)
            canvas.restore()

            if (isCurrent && !isDiceRolled) {
                val shift = sin(pulsePhase) * 6f
                paint.color = Color.parseColor("#FF9800")
                paint.textSize = 48f
                if (p == PlayerColor.BLUE || p == PlayerColor.RED) {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText("◀", rect.right + 10f + shift, rect.centerY() + 16f, paint)
                } else {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("▶", rect.left - 10f - shift, rect.centerY() + 16f, paint)
                }
            }
        }
    }

    private fun drawDiceDots(canvas: Canvas, value: Int, offset: Float, sixColor: Int) {
        val dotR = 9f
        paint.color = if (value == 1 || value == 6) sixColor else Color.parseColor("#212121")
        fun dot(dx: Float, dy: Float) = canvas.drawCircle(dx, dy, dotR, paint)
        when (value) {
            1 -> dot(0f, 0f)
            2 -> { dot(-offset, -offset); dot(offset, offset) }
            3 -> { dot(-offset, -offset); dot(0f, 0f); dot(offset, offset) }
            4 -> { dot(-offset, -offset); dot(offset, -offset); dot(-offset, offset); dot(offset, offset) }
            5 -> { dot(-offset, -offset); dot(offset, -offset); dot(0f, 0f); dot(-offset, offset); dot(offset, offset) }
            6 -> {
                dot(-offset, -offset); dot(offset, -offset)
                dot(-offset, 0f); dot(offset, 0f)
                dot(-offset, offset); dot(offset, offset)
            }
        }
    }

    // 1000104954.png অনুযায়ী হোম ইয়ার্ডে রঙিন বৃত্ত ও ভেতরে মেরুন রিং
    private fun drawYard(canvas: Canvas, p: PlayerColor, gx: Int, gy: Int) {
        paint.color = p.colorHex
        canvas.drawRect(
            boardLeft + gx * cellSize, boardTop + gy * cellSize,
            boardLeft + (gx + 6) * cellSize, boardTop + (gy + 6) * cellSize, paint
        )
        paint.color = Color.WHITE
        canvas.drawRect(
            boardLeft + (gx + 1) * cellSize, boardTop + (gy + 1) * cellSize,
            boardLeft + (gx + 5) * cellSize, boardTop + (gy + 5) * cellSize, paint
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#475569")
        canvas.drawRect(
            boardLeft + (gx + 1) * cellSize, boardTop + (gy + 1) * cellSize,
            boardLeft + (gx + 5) * cellSize, boardTop + (gy + 5) * cellSize, paint
        )
        paint.style = Paint.Style.FILL

        yardCoords[p]!!.forEach { (cx, cy) ->
            val bx = boardLeft + cx * cellSize
            val by = boardTop + cy * cellSize + cellSize * 0.14f
            paint.color = p.colorHex
            canvas.drawCircle(bx, by, cellSize * 0.44f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cellSize * 0.10f
            paint.color = Color.parseColor("#7B241C")
            canvas.drawCircle(bx, by, cellSize * 0.27f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawCenterTriangles(canvas: Canvas) {
        val l = boardLeft + 6 * cellSize
        val r = boardLeft + 9 * cellSize
        val t = boardTop + 6 * cellSize
        val b = boardTop + 9 * cellSize
        val cx = boardLeft + 7.5f * cellSize
        val cy = boardTop + 7.5f * cellSize

        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, col: Int) {
            paint.color = col
            val path = Path().apply { moveTo(cx, cy); lineTo(x1, y1); lineTo(x2, y2); close() }
            canvas.drawPath(path, paint)
        }
        tri(l, t, l, b, PlayerColor.RED.colorHex)
        tri(l, t, r, t, PlayerColor.GREEN.colorHex)
        tri(r, t, r, b, PlayerColor.YELLOW.colorHex)
        tri(l, b, r, b, PlayerColor.BLUE.colorHex)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        if (muteRect.contains(event.x, event.y)) {
            SoundEngine.isMuted = !SoundEngine.isMuted
            invalidate()
            return true
        }
        if (backRect.contains(event.x, event.y)) {
            onExitHome()
            return true
        }

        if (isAnimating || finishedRankings.size >= activePlayers.size - 1) return true
        val curr = activePlayers[currentPlayerIdx]

        if (!isDiceRolled) {
            if (activeDiceRect.contains(event.x, event.y)) animateAndRollDice(curr)
        } else {
            val clicked = tokens.filter { it.owner == curr && canMove(it, diceValue) }
                .minByOrNull { t ->
                    val (gx, gy) = getTokenGridPos(t)
                    hypot(event.x - (boardLeft + gx * cellSize), event.y - (boardTop + gy * cellSize))
                }
            if (clicked != null) {
                val (gx, gy) = getTokenGridPos(clicked)
                if (hypot(event.x - (boardLeft + gx * cellSize), event.y - (boardTop + gy * cellSize)) < cellSize * 1.25f) {
                    animateTokenMove(clicked)
                }
            }
        }
        return true
    }

    private fun animateAndRollDice(curr: PlayerColor) {
        isAnimating = true
        SoundEngine.playDiceRoll()
        val finalDice = rollRankedDice(curr)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = OvershootInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                diceRotation = f * 720f
                diceScale = 1f + sin(f * Math.PI).toFloat() * 0.45f
                if (f < 0.85f) {
                    diceValue = Random.nextInt(1, 7)
                } else {
                    diceValue = finalDice
                    diceRotation = 0f
                    diceScale = 1f
                }
                invalidate()
            }
            start()
        }

        handler.postDelayed({
            isAnimating = false
            isDiceRolled = true

            if (diceValue == 6) {
                consecutiveSixes++
                SoundEngine.playHomeOrSix()
                if (consecutiveSixes >= 3) {
                    consecutiveSixes = 0
                    handler.postDelayed({ nextTurn(false); invalidate() }, 500)
                    invalidate()
                    return@postDelayed
                }
            } else {
                consecutiveSixes = 0
            }

            val movable = tokens.filter { it.owner == curr && canMove(it, diceValue) }
            if (movable.isEmpty()) {
                handler.postDelayed({ nextTurn(false); invalidate() }, 420)
            } else if (movable.size == 1 || (vsComputer && currentPlayerIdx > 0)) {
                animateTokenMove(movable.first())
            }
            invalidate()
        }, 410)
    }

    private fun animateTokenMove(t: Token) {
        isAnimating = true
        val stepsToMove = if (t.steps == -1) 1 else diceValue
        var moved = 0

        fun stepOnce() {
            if (moved < stepsToMove) {
                if (t.steps == -1) t.steps = 0 else t.steps += 1
                moved++
                t.hopScale = 1.38f
                SoundEngine.playStep()
                invalidate()

                handler.postDelayed({ t.hopScale = 1.0f; invalidate() }, 65)
                handler.postDelayed({ stepOnce() }, 135)
            } else {
                t.hopScale = 1.0f
                isAnimating = false
                finalizeMove(t)
            }
        }
        stepOnce()
    }

    private fun canMove(t: Token, dice: Int): Boolean {
        if (t.steps == -1) return dice == 6
        return t.steps + dice <= 57
    }

    private fun animateCapturedTokenReturn(victim: Token, onComplete: () -> Unit) {
        isAnimating = true
        SoundEngine.playCaptureWhoosh()

        fun retreatStep() {
            if (victim.steps > 0) {
                victim.steps = (victim.steps - 3).coerceAtLeast(0)
                victim.hopScale = 0.85f
                invalidate()
                handler.postDelayed({ retreatStep() }, 24)
            } else {
                victim.steps = -1
                victim.hopScale = 1.0f
                isAnimating = false
                invalidate()
                onComplete()
            }
        }
        retreatStep()
    }

    private fun finalizeMove(t: Token) {
        var extraTurn = (diceValue == 6)

        if (t.steps == 57) {
            extraTurn = true
            SoundEngine.playHomeOrSix()
            if (tokens.count { it.owner == t.owner && it.steps == 57 } == 4 && t.owner !in finishedRankings) {
                finishedRankings.add(t.owner)
                SoundEngine.playWinFanfare()
                extraTurn = false
            }
            syncProgressToRemote()
            nextTurn(extraTurn)
            invalidate()
        } else if (t.steps in 0..50) {
            val gIdx = (t.owner.startOffset + t.steps) % 52
            if (gIdx in safeGlobalIndices) {
                SoundEngine.playHomeOrSix()
                syncProgressToRemote()
                nextTurn(extraTurn)
                invalidate()
            } else {
                val victims = tokens.filter {
                    it.owner != t.owner && it.steps in 0..50 &&
                            ((it.owner.startOffset + it.steps) % 52 == gIdx)
                }
                if (victims.size == 1) {
                    animateCapturedTokenReturn(victims.first()) {
                        syncProgressToRemote()
                        nextTurn(true)
                        invalidate()
                    }
                } else {
                    syncProgressToRemote()
                    nextTurn(extraTurn)
                    invalidate()
                }
            }
        } else {
            syncProgressToRemote()
            nextTurn(extraTurn)
            invalidate()
        }
    }

    private fun nextTurn(extraTurn: Boolean) {
        if (finishedRankings.size >= activePlayers.size - 1) return
        isDiceRolled = false
        if (!extraTurn || activePlayers[currentPlayerIdx] in finishedRankings) {
            consecutiveSixes = 0
            do {
                currentPlayerIdx = (currentPlayerIdx + 1) % activePlayers.size
            } while (activePlayers[currentPlayerIdx] in finishedRankings)
        }

        if (vsComputer && currentPlayerIdx > 0 && !isDiceRolled) {
            handler.postDelayed({
                if (!isDiceRolled && !isAnimating) animateAndRollDice(activePlayers[currentPlayerIdx])
            }, 600)
        }
    }
}
