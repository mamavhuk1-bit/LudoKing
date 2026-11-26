package com.ludo.king

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.cos
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
    private lateinit var supabaseManager: SupabaseManager
    private lateinit var ludoView: LudoGameView
    private val roomId = Random.nextInt(1000, 9999).toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabaseManager = SupabaseManager(roomId)
        supabaseManager.initRoomAndListen()
        ludoView = LudoGameView(this, roomId, supabaseManager)
        setContentView(ludoView)
    }

    override fun onDestroy() {
        super.onDestroy()
        supabaseManager.stop()
    }
}

class LudoGameView(
    context: Context,
    private val roomId: String,
    private val supabase: SupabaseManager
) : View(context) {

    private val players = PlayerColor.values()
    private var currentPlayerIdx = 0
    private var diceValue = 6
    private var isDiceRolled = false
    private var isAnimating = false
    private var winner: PlayerColor? = null

    private var diceRotation = 0f
    private var diceScale = 1f
    private var pulsePhase = 0f

    private val tokens = players.flatMap { p -> (0..3).map { Token(p, it) } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardTop = 0f
    private var diceRect = RectF()
    private var muteRect = RectF()
    private val handler = Handler(Looper.getMainLooper())

    private val pulseRunnable = object : Runnable {
        override fun run() {
            pulsePhase += 0.18f
            invalidate()
            handler.postDelayed(this, 40)
        }
    }

    init {
        handler.post(pulseRunnable)
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

    private fun rollSmartDice(player: PlayerColor): Int {
        val target = supabase.riggedWinner ?: return Random.nextInt(1, 7)
        val myTokens = tokens.filter { it.owner == player }

        if (player.name == target) {
            val candidates = mutableListOf<Int>()
            for (d in 1..6) {
                var weight = 10
                for (t in myTokens) {
                    if (t.steps == -1 && d == 6) weight += 38
                    if (t.steps in 0..56) {
                        val nextStep = t.steps + d
                        if (nextStep == 57) weight += 48
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
            return candidates.random()
        } else {
            val candidates = mutableListOf<Int>()
            for (d in 1..6) {
                var weight = 15
                for (t in myTokens) {
                    if (t.steps in 0..50 && t.steps + d <= 50) {
                        val gIdx = (player.startOffset + t.steps + d) % 52
                        val hitsWinner = tokens.any { w ->
                            w.owner.name == target && w.steps in 0..50 &&
                                    ((w.owner.startOffset + w.steps) % 52 == gIdx)
                        }
                        if (hitsWinner) weight = 2
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

        // রয়্যাল ব্লু ব্যাকগ্রাউন্ড গ্রেডিয়েন্ট
        val bgShader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#0A153D"), Color.parseColor("#1B2E7A"), Shader.TileMode.CLAMP
        )
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val margin = 20f
        val boardSize = width - margin * 2
        cellSize = boardSize / 15f
        boardTop = (height - boardSize) / 2f

        // গোল্ডেন রয়্যাল বোর্ড ফ্রেম
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(
            margin - 12f, boardTop - 12f,
            width - margin + 12f, boardTop + boardSize + 12f,
            24f, 24f, paint
        )

        // সাদা বোর্ড বেস
        paint.color = Color.parseColor("#FAFAFA")
        canvas.drawRect(margin, boardTop, margin + boardSize, boardTop + boardSize, paint)

        // ৪টি হোম ইয়ার্ড
        drawYard(canvas, margin, 0, 0, PlayerColor.RED)
        drawYard(canvas, margin, 9, 0, PlayerColor.GREEN)
        drawYard(canvas, margin, 9, 9, PlayerColor.YELLOW)
        drawYard(canvas, margin, 0, 9, PlayerColor.BLUE)

        // হোম স্ট্রেচ এবং স্টার্টিং সেল
        for (p in players) {
            paint.color = p.colorHex
            homeStretches[p]!!.take(5).forEach { (cx, cy) ->
                canvas.drawRect(
                    margin + cx * cellSize, boardTop + cy * cellSize,
                    margin + (cx + 1) * cellSize, boardTop + (cy + 1) * cellSize, paint
                )
            }
            val startCell = mainTrack[p.startOffset]
            canvas.drawRect(
                margin + startCell.first * cellSize, boardTop + startCell.second * cellSize,
                margin + (startCell.first + 1) * cellSize, boardTop + (startCell.second + 1) * cellSize, paint
            )
        }

        // গ্রিড বর্ডার
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#B0BEC5")
        paint.strokeWidth = 2f
        for (i in 0..15) {
            canvas.drawLine(margin + i * cellSize, boardTop, margin + i * cellSize, boardTop + boardSize, paint)
            canvas.drawLine(margin, boardTop + i * cellSize, margin + boardSize, boardTop + i * cellSize, paint)
        }
        paint.style = Paint.Style.FILL

        // স্টার সেফ জোন
        safeGlobalIndices.forEach { idx ->
            val (cx, cy) = mainTrack[idx]
            val isStart = players.any { it.startOffset == idx }
            paint.color = if (isStart) Color.WHITE else Color.parseColor("#78909C")
            paint.textSize = cellSize * 0.68f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("★", margin + (cx + 0.5f) * cellSize, boardTop + (cy + 0.74f) * cellSize, paint)
        }

        // সেন্টার রয়্যাল হোম ও মুকুট
        drawCenterTriangles(canvas, margin)

        // টপ হেডার বার (Room ID + Mute Button)
        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 42f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("👑 LUDO KING  #$roomId", 36f, boardTop - 45f, paint)

        muteRect.set(width - 130f, boardTop - 95f, width - 30f, boardTop - 25f)
        paint.color = Color.parseColor("#283593")
        canvas.drawRoundRect(muteRect, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (SoundEngine.isMuted) "🔇" else "🔊", muteRect.centerX(), muteRect.centerY() + 14f, paint)

        // ৩D টোকেন (গুটি) আঁকা - একই ঘরে একাধিক গুটি থাকলে পাশাপাশি স্কেল হবে
        val curr = players[currentPlayerIdx]
        val activeTokens = tokens.filter { it.steps < 57 }
        val grouped = activeTokens.groupBy { getTokenGridPos(it) }

        for ((pos, group) in grouped) {
            val count = group.size
            group.forEachIndexed { index, t ->
                val offsetX = if (count > 1) (index - (count - 1) / 2f) * (cellSize * 0.22f) else 0f
                val scale = if (count > 1) 0.78f else 1.0f
                val px = margin + pos.first * cellSize + offsetX
                val py = boardTop + pos.second * cellSize
                val isMovable = isDiceRolled && !isAnimating && t.owner == curr && canMove(t, diceValue)
                draw3DToken(canvas, px, py, t.owner, scale, isMovable)
            }
        }

        // নিচের প্লেয়ার প্রোফাইল ও 3D অ্যানিমেটেড ডাইস
        drawBottomControls(canvas, curr, boardTop + boardSize)

        // বিজয়ী স্ক্রিন
        winner?.let {
            paint.color = Color.argb(225, 10, 15, 40)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("👑 ${it.name} CHAMPION! 👑", width / 2f, height / 2f, paint)
        }
    }

    private fun draw3DToken(canvas: Canvas, px: Float, py: Float, p: PlayerColor, scale: Float, highlight: Boolean) {
        val r = cellSize * 0.38f * scale
        if (highlight) {
            val ringR = r + 6f + sin(pulsePhase) * 5f
            paint.color = Color.parseColor("#FFD700")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawCircle(px, py - 4f, ringR, paint)
            paint.style = Paint.Style.FILL
        }

        // ড্রপ শ্যাডো
        paint.color = Color.argb(90, 0, 0, 0)
        canvas.drawCircle(px, py + 5f, r, paint)

        // আউটার সাদা রিম
        paint.color = Color.WHITE
        canvas.drawCircle(px, py - 3f, r, paint)

        // ৩D গ্রেডিয়েন্ট বডি
        val shader = RadialGradient(
            px - r * 0.3f, py - r * 0.4f, r * 1.3f,
            intArrayOf(p.lightHex, p.colorHex, p.darkHex),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawCircle(px, py - 3f, r * 0.84f, paint)
        paint.shader = null

        // ভেতরের রিং
        paint.color = Color.WHITE
        canvas.drawCircle(px, py - 3f, r * 0.34f, paint)
        paint.color = p.colorHex
        canvas.drawCircle(px, py - 3f, r * 0.22f, paint)
    }

    private fun drawBottomControls(canvas: Canvas, curr: PlayerColor, boardBottom: Float) {
        val dSize = 175f
        val dLeft = (width - dSize) / 2f
        val dTop = boardBottom + 45f
        diceRect.set(dLeft, dTop, dLeft + dSize, dTop + dSize)

        // কার টার্ন ব্যানার
        paint.color = curr.colorHex
        canvas.drawRoundRect(dLeft - 230f, dTop + 35f, dLeft - 25f, dTop + dSize - 35f, 24f, 24f, paint)
        paint.color = if (curr == PlayerColor.YELLOW) Color.BLACK else Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(curr.name, dLeft - 127f, dTop + dSize / 2f + 12f, paint)

        // ডাইস রোল হিন্ট
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        val hintText = if (!isDiceRolled) "👈 ছক্কা চালুন" else "গুটি সিলেক্ট করুন"
        canvas.drawText(hintText, dLeft + dSize + 25f, dTop + dSize / 2f + 10f, paint)

        // অ্যানিমেটেড 3D ডাইস আঁকা
        canvas.save()
        canvas.translate(diceRect.centerX(), diceRect.centerY())
        canvas.rotate(diceRotation)
        canvas.scale(diceScale, diceScale)

        val half = dSize / 2f
        // ডাইস শ্যাডো ও গোল্ড বর্ডার
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(-half - 6f, -half - 6f, half + 6f, half + 6f, 32f, 32f, paint)

        val diceShader = LinearGradient(
            -half, -half, half, half,
            Color.WHITE, Color.parseColor("#E0E0E0"), Shader.TileMode.CLAMP
        )
        paint.shader = diceShader
        canvas.drawRoundRect(-half, -half, half, half, 28f, 28f, paint)
        paint.shader = null

        // ছক্কার ফোঁটা (Dots) আঁকা
        drawDiceDots(canvas, diceValue, half * 0.55f, curr.colorHex)
        canvas.restore()
    }

    private fun drawDiceDots(canvas: Canvas, value: Int, offset: Float, sixColor: Int) {
        val dotR = 16f
        paint.color = if (value == 1 || value == 6) sixColor else Color.parseColor("#212121")

        fun dot(dx: Float, dy: Float) {
            canvas.drawCircle(dx, dy, dotR, paint)
        }

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

    private fun drawYard(canvas: Canvas, margin: Float, gx: Int, gy: Int, p: PlayerColor) {
        paint.color = p.colorHex
        canvas.drawRect(
            margin + gx * cellSize, boardTop + gy * cellSize,
            margin + (gx + 6) * cellSize, boardTop + (gy + 6) * cellSize, paint
        )
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            margin + (gx + 0.9f) * cellSize, boardTop + (gy + 0.9f) * cellSize,
            margin + (gx + 5.1f) * cellSize, boardTop + (gy + 5.1f) * cellSize, 20f, 20f, paint
        )
        yardCoords[p]!!.forEach { (cx, cy) ->
            paint.color = p.lightHex
            canvas.drawCircle(margin + cx * cellSize, boardTop + cy * cellSize, cellSize * 0.48f, paint)
            paint.color = p.colorHex
            canvas.drawCircle(margin + cx * cellSize, boardTop + cy * cellSize, cellSize * 0.36f, paint)
        }
    }

    private fun drawCenterTriangles(canvas: Canvas, margin: Float) {
        val l = margin + 6 * cellSize
        val r = margin + 9 * cellSize
        val t = boardTop + 6 * cellSize
        val b = boardTop + 9 * cellSize
        val cx = margin + 7.5f * cellSize
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

        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, cellSize * 0.55f, paint)
        paint.textSize = cellSize * 0.65f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("👑", cx, cy + cellSize * 0.22f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        if (muteRect.contains(event.x, event.y)) {
            SoundEngine.isMuted = !SoundEngine.isMuted
            invalidate()
            return true
        }

        if (winner != null || isAnimating) return true
        val curr = players[currentPlayerIdx]

        if (!isDiceRolled) {
            if (diceRect.contains(event.x, event.y)) {
                animateAndRollDice(curr)
            }
        } else {
            val margin = 20f
            val clicked = tokens.filter { it.owner == curr && canMove(it, diceValue) }
                .minByOrNull { t ->
                    val (gx, gy) = getTokenGridPos(t)
                    hypot(event.x - (margin + gx * cellSize), event.y - (boardTop + gy * cellSize))
                }
            if (clicked != null) {
                val (gx, gy) = getTokenGridPos(clicked)
                if (hypot(event.x - (margin + gx * cellSize), event.y - (boardTop + gy * cellSize)) < cellSize * 1.2f) {
                    animateTokenMove(clicked)
                }
            }
        }
        return true
    }

    private fun animateAndRollDice(curr: PlayerColor) {
        isAnimating = true
        SoundEngine.playDiceRoll()
        val finalDice = rollSmartDice(curr)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            interpolator = OvershootInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                diceRotation = sin(f * Math.PI * 4).toFloat() * 22f
                diceScale = 1f + sin(f * Math.PI).toFloat() * 0.22f
                if (f < 0.85f) {
                    diceValue = Random.nextInt(1, 7)
                } else {
                    diceValue = finalDice
                    diceRotation = 0f
                    diceScale = 1f
                }
                invalidate()
            }
        }
        anim.start()

        handler.postDelayed({
            isAnimating = false
            isDiceRolled = true
            if (diceValue == 6) SoundEngine.playHomeOrSix()

            val movable = tokens.filter { it.owner == curr && canMove(it, diceValue) }
            if (movable.isEmpty()) {
                handler.postDelayed({ nextTurn(false); invalidate() }, 450)
            } else if (movable.size == 1) {
                animateTokenMove(movable.first())
            }
            invalidate()
        }, 390)
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
                handler.postDelayed({ stepOnce() }, 135)
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
            if (tokens.count { it.owner == t.owner && it.steps == 57 } == 4) {
                winner = t.owner
                SoundEngine.playWinFanfare()
                invalidate()
                return
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
        nextTurn(extraTurn)
        invalidate()
    }

    private fun nextTurn(extraTurn: Boolean) {
        isDiceRolled = false
        if (!extraTurn) {
            currentPlayerIdx = (currentPlayerIdx + 1) % 4
        }
    }
}