package com.ludo.king

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class PlayerColor(val colorHex: Int, val lightHex: Int, val darkHex: Int, val startOffset: Int) {
    RED(Color.parseColor("#E53935"), Color.parseColor("#FF8A80"), Color.parseColor("#8E0000"), 0),
    GREEN(Color.parseColor("#43A047"), Color.parseColor("#B9F6CA"), Color.parseColor("#005005"), 13),
    YELLOW(Color.parseColor("#FBC02D"), Color.parseColor("#FFFF8D"), Color.parseColor("#C49000"), 26),
    BLUE(Color.parseColor("#1E88E5"), Color.parseColor("#82B1FF"), Color.parseColor("#003C8F"), 39)
}

data class Token(val owner: PlayerColor, val id: Int, var steps: Int = -1)

class MainActivity : AppCompatActivity() {
    private lateinit var supabase: SupabaseManager
    private lateinit var rootContainer: FrameLayout
    private val roomId = Random.nextInt(1000, 9999).toString()

    private var currentScreen = "SPLASH"
    private var vsComputer = false
    private var bgMusicPlayer: MediaPlayer? = null
    private var isMusicMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // স্ট্যাটাস বার ফিক্স (কালো না দেখিয়ে রয়্যাল নেভি ব্লু দেখাবে)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#0D1B4C")

        supabase = SupabaseManager(this, roomId)
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

    private fun startLobbyMusic() {
        try {
            if (bgMusicPlayer == null) {
                bgMusicPlayer = MediaPlayer.create(this, R.raw.gamesound)?.apply {
                    isLooping = true
                    setVolume(0.75f, 0.75f)
                }
            }
            if (!isMusicMuted && bgMusicPlayer?.isPlaying == false) {
                bgMusicPlayer?.start()
            }
        } catch (_: Exception) {}
    }

    private fun stopLobbyMusic() {
        try {
            if (bgMusicPlayer?.isPlaying == true) {
                bgMusicPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    private fun toggleLobbyMute(): Boolean {
        isMusicMuted = !isMusicMuted
        SoundEngine.isMuted = isMusicMuted
        if (isMusicMuted) stopLobbyMusic() else if (currentScreen != "GAME") startLobbyMusic()
        return isMusicMuted
    }

    // হার্ডওয়্যার ব্যাক বাটন হ্যান্ডলার (ধাপে ধাপে পেছনে যাবে)
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
                    "HOME" -> showRoyalConfirmDialog("Exit Ludo King", "Are you sure you want to exit the game?") {
                        finish()
                    }
                }
            }
        })
    }

    // প্রিমিয়াম রয়্যাল কাস্টম পপ-আপ ডায়ালগ
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
        val noBtn = makeRoyalButton("Cancel", "#334155").apply {
            setOnClickListener { dialog.dismiss() }
        }
        val yesBtn = makeRoyalButton("Confirm", "#16A34A").apply {
            setOnClickListener { dialog.dismiss(); onYes() }
        }
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

        val okBtn = makeRoyalButton("OK", "#E53935").apply {
            setOnClickListener { dialog.dismiss() }
        }
        card.addView(okBtn, LinearLayout.LayoutParams(-1, 110))
        dialog.show()
    }

    // ১. স্প্ল্যাশ স্ক্রিন (open.jpg)
    private fun showSplashScreen() {
        currentScreen = "SPLASH"
        rootContainer.removeAllViews()
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.screen_open)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        rootContainer.addView(iv, FrameLayout.LayoutParams(-1, -1))
        Handler(Looper.getMainLooper()).postDelayed({
            showHomeScreen()
        }, 2200)
    }

    // ২. হোম স্ক্রিন (homepage.jpg)
    private fun showHomeScreen() {
        currentScreen = "HOME"
        startLobbyMusic()
        supabase.pushImmediateState("On Home Screen")
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

        // মিউট বাটন (উপরের ডান কোণায়)
        val muteBtn = makeMuteButton()
        frame.addView(muteBtn, FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 110; rightMargin = 24
        })

        frame.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val nx = ev.x / v.width
                val ny = ev.y / v.height
                when {
                    nx in 0.19f..0.48f && ny in 0.53f..0.65f -> {
                        SoundEngine.playStep()
                        vsComputer = true
                        showAfterPassScreen()
                    }
                    nx in 0.52f..0.81f && ny in 0.53f..0.65f -> {
                        SoundEngine.playStep()
                        vsComputer = false
                        showAfterPassScreen()
                    }
                    else -> showServerError()
                }
            }
            true
        }
        rootContainer.addView(frame, FrameLayout.LayoutParams(-1, -1))
    }

    // ৩. মোড সিলেকশন স্ক্রিন (afterpass.jpg)
    private fun showAfterPassScreen() {
        currentScreen = "AFTER_PASS"
        rootContainer.removeAllViews()
        val frame = FrameLayout(this)
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.screen_afterpass)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))

        var selectedMode = 0
        val modeIndicator = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                val centersY = listOf(0.495f, 0.605f, 0.705f)
                centersY.forEachIndexed { idx, cy ->
                    val cx = width * 0.502f
                    val y = height * cy
                    p.color = if (idx == selectedMode) Color.parseColor("#1B5E20") else Color.parseColor("#0D2B6B")
                    canvas.drawCircle(cx, y, width * 0.042f, p)
                    if (idx == selectedMode) {
                        p.color = Color.parseColor("#FFCA28")
                        p.textSize = width * 0.055f
                        p.textAlign = Paint.Align.CENTER
                        p.typeface = Typeface.DEFAULT_BOLD
                        canvas.drawText("✔", cx, y + width * 0.02f, p)
                    }
                }
            }
        }
        frame.addView(modeIndicator, FrameLayout.LayoutParams(-1, -1))

        frame.addView(makeMuteButton(), FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 110; rightMargin = 24
        })

        frame.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val nx = ev.x / v.width
                val ny = ev.y / v.height
                when {
                    ny in 0.45f..0.54f -> { selectedMode = 0; SoundEngine.playStep(); modeIndicator.invalidate() }
                    ny in 0.56f..0.65f -> { selectedMode = 1; SoundEngine.playStep(); modeIndicator.invalidate() }
                    ny in 0.66f..0.74f -> { selectedMode = 2; SoundEngine.playStep(); modeIndicator.invalidate() }
                    nx in 0.30f..0.70f && ny in 0.74f..0.83f -> {
                        SoundEngine.playStep()
                        showAfterNextScreen()
                    }
                    nx in 0.02f..0.18f && ny in 0.88f..0.97f -> {
                        SoundEngine.playStep()
                        showHomeScreen()
                    }
                }
            }
            true
        }
        rootContainer.addView(frame, FrameLayout.LayoutParams(-1, -1))
    }

    // ৪. প্লেয়ার ও কালার সেটআপ স্ক্রিন (afternext.jpg - সঠিক সবুজ রিং পজিশন ও রয়্যাল পপআপ সহ)
    private fun showAfterNextScreen() {
        currentScreen = "AFTER_NEXT"
        rootContainer.removeAllViews()
        val frame = FrameLayout(this)
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.screen_afternext)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))

        var playerCount = 2
        val playerNames = mutableListOf("Player 1", "Player 2", "Player 3", "Player 4")

        val setupOverlay = object : View(this) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                // afternext.jpg-এর 2P, 3P, 4P বাটনের সঠিক কেন্দ্রবিন্দু
                val pxPositions = mapOf(2 to 0.184f, 3 to 0.340f, 4 to 0.496f)
                val ringY = height * 0.583f
                val ringR = width * 0.062f

                pxPositions.forEach { (cnt, rx) ->
                    if (cnt == playerCount) {
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = 9f
                        p.color = Color.parseColor("#00E676")
                        canvas.drawCircle(width * rx, ringY, ringR, p)
                        p.style = Paint.Style.FILL
                    }
                }

                // বর্তমান সেট করা প্লেয়ারদের নাম সুন্দর গোল্ডেন ব্যানারে দেখানো
                p.color = Color.parseColor("#FFD700")
                p.textSize = 32f
                p.textAlign = Paint.Align.CENTER
                p.typeface = Typeface.DEFAULT_BOLD
                val activeList = playerNames.take(playerCount).joinToString("  •  ")
                canvas.drawText(activeList, width * 0.50f, height * 0.372f, p)
            }
        }
        frame.addView(setupOverlay, FrameLayout.LayoutParams(-1, -1))

        frame.addView(makeMuteButton(), FrameLayout.LayoutParams(110, 110).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 110; rightMargin = 24
        })

        frame.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val nx = ev.x / v.width
                val ny = ev.y / v.height
                when {
                    // 2P, 3P, 4P সিলেকশন
                    ny in 0.53f..0.63f && nx in 0.11f..0.25f -> { playerCount = 2; SoundEngine.playStep(); setupOverlay.invalidate() }
                    ny in 0.53f..0.63f && nx in 0.27f..0.41f -> { playerCount = 3; SoundEngine.playStep(); setupOverlay.invalidate() }
                    ny in 0.53f..0.63f && nx in 0.43f..0.57f -> { playerCount = 4; SoundEngine.playStep(); setupOverlay.invalidate() }

                    // প্লেয়ার নাম এডিট বক্স এরিয়া
                    ny in 0.39f..0.52f && nx in 0.38f..0.78f -> {
                        promptRoyalEditNames(playerCount, playerNames) { setupOverlay.invalidate() }
                    }

                    // Play বাটন
                    nx in 0.32f..0.68f && ny in 0.64f..0.71f -> {
                        SoundEngine.playHomeOrSix()
                        startGame(playerCount, playerNames.take(playerCount), oneTokenOut = false)
                    }

                    // Play - One Token Out বাটন
                    nx in 0.32f..0.68f && ny in 0.72f..0.81f -> {
                        SoundEngine.playHomeOrSix()
                        startGame(playerCount, playerNames.take(playerCount), oneTokenOut = true)
                    }

                    // Back বাটন
                    nx in 0.02f..0.18f && ny in 0.88f..0.97f -> {
                        SoundEngine.playStep()
                        showAfterPassScreen()
                    }
                }
            }
            true
        }
        rootContainer.addView(frame, FrameLayout.LayoutParams(-1, -1))
    }

    // কাস্টম রয়্যাল প্লেয়ার নাম এডিট পপআপ
    private fun promptRoyalEditNames(count: Int, names: MutableList<String>, onSaved: () -> Unit) {
        val (dialog, card) = createRoyalDialog("✏ EDIT PLAYER NAMES")
        val badgeColors = listOf("#1E88E5", "#43A047", "#E53935", "#FBC02D")

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
        val cancelBtn = makeRoyalButton("Cancel", "#475569").apply {
            setOnClickListener { dialog.dismiss() }
        }
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

    // ৫. মূল গেম স্ক্রিন (গেম শুরু হলে লবি মিউজিক বন্ধ হবে)
    private fun startGame(playerCount: Int, names: List<String>, oneTokenOut: Boolean) {
        currentScreen = "GAME"
        stopLobbyMusic()
        rootContainer.removeAllViews()
        val activeColors = when (playerCount) {
            2 -> listOf(PlayerColor.BLUE, PlayerColor.GREEN)
            3 -> listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN)
            else -> listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW)
        }
        val gameView = LudoGameView(this, activeColors, names, oneTokenOut, vsComputer, supabase) {
            startLobbyMusic()
            showHomeScreen()
        }
        rootContainer.addView(gameView, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onPause() {
        super.onPause()
        stopLobbyMusic()
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen != "GAME" && !isMusicMuted) startLobbyMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgMusicPlayer?.release()
        supabase.stop()
    }
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
        (0..3).map { id ->
            Token(p, id, steps = if (oneTokenOut && id == 0) 0 else -1)
        }
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
            pulsePhase += 0.22f
            invalidate()
            handler.postDelayed(this, 45)
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
        PlayerColor.RED to listOf(1.9f to 1.9f, 4.1f to 1.9f, 1.9f to 4.1f, 4.1f to 4.1f),
        PlayerColor.GREEN to listOf(10.9f to 1.9f, 13.1f to 1.9f, 10.9f to 4.1f, 13.1f to 4.1f),
        PlayerColor.YELLOW to listOf(10.9f to 10.9f, 13.1f to 10.9f, 10.9f to 13.1f, 13.1f to 13.1f),
        PlayerColor.BLUE to listOf(1.9f to 10.9f, 4.1f to 10.9f, 1.9f to 13.1f, 4.1f to 13.1f)
    )

    private fun syncProgressToRemote() {
        val summary = activePlayers.joinToString(" | ") { p ->
            val pTokens = tokens.filter { it.owner == p }
            val totalSteps = pTokens.sumOf { if (it.steps < 0) 0 else it.steps }
            val pct = ((totalSteps / 228.0) * 100).toInt().coerceIn(0, 100)
            val homeCount = pTokens.count { it.steps == 57 }
            "${p.name}:$pct%(H:$homeCount)"
        }
        supabase.pushImmediateState(summary)
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
                if (consecutiveSixes == 2 && d == 6) continue // পরপর ৩টি ৬ এড়িয়ে চলা
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
            val candidates = mutableListOf<Int>()
            for (d in 1..6) {
                var weight = if (mustWaitForHigherRank) 5 else 15
                for (t in myTokens) {
                    if (t.steps in 0..56 && t.steps + d == 57 && mustWaitForHigherRank) weight = 1
                    if (t.steps in 0..50 && t.steps + d <= 50) {
                        val gIdx = (player.startOffset + t.steps + d) % 52
                        val hitsTop = tokens.any { w ->
                            w.owner.name == currentTopTarget && w.steps in 0..50 &&
                                    ((w.owner.startOffset + w.steps) % 52 == gIdx)
                        }
                        if (hitsTop) weight = 2
                    }
                }
                repeat(weight) { candidates.add(d) }
            }
            return candidates.random()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // লুডু কিং রয়্যাল ব্যাকগ্রাউন্ড
        val bgShader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#0F2D52"), Color.parseColor("#18457A"), Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        boardLeft = 18f
        val boardSize = width - boardLeft * 2
        cellSize = boardSize / 15f
        boardTop = (height - boardSize) / 2f

        // গোল্ডেন বোর্ড ফ্রেম
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(
            boardLeft - 8f, boardTop - 8f,
            boardLeft + boardSize + 8f, boardTop + boardSize + 8f, 16f, 16f, paint
        )

        // সাদা বোর্ড বেস
        paint.color = Color.WHITE
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize, paint)

        // ৪টি হোম ইয়ার্ড
        drawYard(canvas, PlayerColor.RED, 0, 0)
        drawYard(canvas, PlayerColor.GREEN, 9, 0)
        drawYard(canvas, PlayerColor.YELLOW, 9, 9)
        drawYard(canvas, PlayerColor.BLUE, 0, 9)

        // কালার পাথ ও স্টার্ট সেল
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

        // গ্রিড বর্ডার
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#78909C")
        paint.strokeWidth = 2f
        for (i in 0..15) {
            canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, boardTop + boardSize, paint)
            canvas.drawLine(boardLeft, boardTop + i * cellSize, boardLeft + boardSize, boardTop + i * cellSize, paint)
        }
        paint.style = Paint.Style.FILL

        // স্টার সেফ জোন
        safeGlobalIndices.forEach { idx ->
            val (cx, cy) = mainTrack[idx]
            val isStart = PlayerColor.values().any { it.startOffset == idx }
            paint.color = if (isStart) Color.WHITE else Color.parseColor("#546E7A")
            paint.textSize = cellSize * 0.65f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("★", boardLeft + (cx + 0.5f) * cellSize, boardTop + (cy + 0.73f) * cellSize, paint)
        }

        // সেন্টার ত্রিভুজ
        drawCenterTriangles(canvas)

        // প্লেয়ারদের নাম (gamescnreen.jpg-এর মতো একদম নিখুঁত পজিশনে)
        activePlayers.forEachIndexed { idx, p ->
            val pName = playerNames.getOrElse(idx) { p.name }
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT_BOLD
            when (p) {
                PlayerColor.BLUE -> {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(pName, boardLeft + 22f, boardTop + boardSize - 14f, paint)
                }
                PlayerColor.RED -> {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(pName, boardLeft + 22f, boardTop + 38f, paint)
                }
                PlayerColor.GREEN -> {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(pName, boardLeft + boardSize - 22f, boardTop + 38f, paint)
                }
                PlayerColor.YELLOW -> {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(pName, boardLeft + boardSize - 22f, boardTop + boardSize - 14f, paint)
                }
            }
        }

        // চার কোণার ডাইস বক্স (gamescnreen.jpg স্টাইল)
        val curr = activePlayers[currentPlayerIdx]
        drawCornerDiceBoxes(canvas, curr, boardSize)

        // সব পিন-শেপ গুটি আঁকা
        val activeTokens = tokens.filter { it.steps < 57 }
        val grouped = activeTokens.groupBy { getTokenGridPos(it) }
        for ((pos, group) in grouped) {
            val count = group.size
            group.forEachIndexed { index, t ->
                val offsetX = if (count > 1) (index - (count - 1) / 2f) * (cellSize * 0.22f) else 0f
                val scale = if (count > 1) 0.78f else 1.0f
                val px = boardLeft + pos.first * cellSize + offsetX
                val py = boardTop + pos.second * cellSize
                val isMovable = isDiceRolled && !isAnimating && t.owner == curr && canMove(t, diceValue)
                drawPinToken(canvas, px, py, t.owner, scale, isMovable)
            }
        }

        // নিচে মেনু ও সাউন্ড বাটন
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

        // ম্যাচ রেজাল্ট স্ক্রিন
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

    private fun drawPinToken(canvas: Canvas, px: Float, py: Float, p: PlayerColor, scale: Float, highlight: Boolean) {
        val r = cellSize * 0.37f * scale
        if (highlight) {
            val ringR = r + 6f + sin(pulsePhase) * 5f
            paint.color = Color.parseColor("#FFD700")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawCircle(px, py - 3f, ringR, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = Color.argb(95, 0, 0, 0)
        canvas.drawCircle(px, py + 4f, r, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(px, py - 4f, r, paint)

        paint.color = p.colorHex
        canvas.drawCircle(px, py - 4f, r * 0.84f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(px, py - 6f, r * 0.46f, paint)
        paint.color = p.darkHex
        canvas.drawCircle(px, py - 6f, r * 0.24f, paint)
    }

    private fun drawCornerDiceBoxes(canvas: Canvas, curr: PlayerColor, boardSize: Float) {
        val boxW = 245f
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

            // গোল্ডেন ও কালার ফ্রেম
            paint.color = if (isCurrent) Color.parseColor("#FFD700") else Color.parseColor("#64748B")
            canvas.drawRoundRect(rect, 20f, 20f, paint)

            paint.color = Color.parseColor("#1E3A8A")
            canvas.drawRoundRect(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f, 16f, 16f, paint)

            // বক্সের একপাশে প্লেয়ারের পিন ব্যাজ (gamescnreen.jpg এর মতো)
            val badgeX = if (p == PlayerColor.BLUE || p == PlayerColor.RED) rect.left + 48f else rect.right - 48f
            val badgeY = rect.centerY()
            paint.color = Color.WHITE
            canvas.drawCircle(badgeX, badgeY, 32f, paint)
            paint.color = p.colorHex
            canvas.drawCircle(badgeX, badgeY, 25f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(badgeX, badgeY - 3f, 11f, paint)

            // অন্য পাশে সাদা ডাইস স্কয়ার
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
            paint.color = if (isCurrent) Color.parseColor("#FFF5F5") else Color.parseColor("#CBD5E1")
            canvas.drawRoundRect(-h, -h, h, h, 16f, 16f, paint)

            if (isCurrent) {
                drawDiceDots(canvas, diceValue, h * 0.55f, p.colorHex)
            }
            canvas.restore()

            // অ্যাক্টিভ তীর চিহ্ন (◀ / ▶)
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
        paint.color = p.colorHex
        yardCoords[p]!!.forEach { (cx, cy) ->
            canvas.drawCircle(boardLeft + cx * cellSize, boardTop + cy * cellSize, cellSize * 0.42f, paint)
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
            if (activeDiceRect.contains(event.x, event.y)) {
                animateAndRollDice(curr)
            }
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
            duration = 340
            interpolator = OvershootInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                diceRotation = sin(f * Math.PI * 4).toFloat() * 20f
                diceScale = 1f + sin(f * Math.PI).toFloat() * 0.2f
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
                // লুডু কিং নিয়ম: পরপর ৩টি ৬ পড়লে চাল বাতিল
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
        }, 370)
    }

    private fun animateTokenMove(t: Token) {
        isAnimating = true
        val stepsToMove = if (t.steps == -1) 1 else diceValue
        var moved = 0

        fun stepOnce() {
            if (moved < stepsToMove) {
                if (t.steps == -1) t.steps = 0 else t.steps += 1
                moved++
                SoundEngine.playStep()
                invalidate()
                handler.postDelayed({ stepOnce() }, 125)
            } else {
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
        } else if (t.steps in 0..50) {
            val gIdx = (t.owner.startOffset + t.steps) % 52
            if (gIdx !in safeGlobalIndices) {
                val victims = tokens.filter {
                    it.owner != t.owner && it.steps in 0..50 &&
                            ((it.owner.startOffset + it.steps) % 52 == gIdx)
                }
                if (victims.size == 1) {
                    victims.first().steps = -1
                    extraTurn = true
                    SoundEngine.playCapture()
                }
            }
        }

        syncProgressToRemote()
        nextTurn(extraTurn)
        invalidate()
    }

    private fun nextTurn(extraTurn: Boolean) {
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