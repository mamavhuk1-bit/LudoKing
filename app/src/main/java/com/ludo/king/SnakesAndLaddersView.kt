package com.ludo.king

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.sin
import kotlin.random.Random

class SnakesAndLaddersView(
    context: Context,
    private val activePlayers: List<PlayerColor>,
    private val playerNames: List<String>,
    private val vsComputer: Boolean,
    private val supabase: SupabaseManager,
    private val onExitHome: () -> Unit
) : View(context) {

    private val positions = IntArray(activePlayers.size) { 1 }
    private var currentPlayerIdx = 0
    private var diceValue = 1
    private var isAnimating = false
    private var winnerIdx = -1

    private var diceRotation = 0f
    private var diceScale = 1f
    private var pulsePhase = 0f

    // মই (Ladders: নিচ থেকে উপরে)
    private val ladders = mapOf(
        4 to 25, 13 to 46, 33 to 49, 42 to 63, 50 to 69, 62 to 81, 74 to 92
    )
    // সাপ (Snakes: উপর থেকে নিচে)
    private val snakes = mapOf(
        27 to 5, 40 to 3, 43 to 18, 54 to 31, 66 to 45, 76 to 58, 89 to 53, 99 to 41
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var diceRect = RectF()
    private var backRect = RectF()
    private val handler = Handler(Looper.getMainLooper())

    init {
        handler.post(object : Runnable {
            override fun run() {
                pulsePhase += 0.22f
                invalidate()
                handler.postDelayed(this, 45)
            }
        })
        syncRemote()
    }

    private fun syncRemote() {
        val summary = activePlayers.mapIndexed { idx, p ->
            "${p.name}:${positions[idx]}%(Pos:${positions[idx]})"
        }.joinToString(" | ")
        val colorsCsv = activePlayers.joinToString(",") { it.name }
        val pInfo = activePlayers.mapIndexed { idx, p -> "${p.name}:${playerNames.getOrElse(idx) { p.name }}" }.joinToString(" | ")
        supabase.pushMatchTelemetry(summary, colorsCsv, pInfo)
    }

    // রিমোট কন্ট্রোল: #1 সিলেক্টেড প্লেয়ারকে মইয়ে তুলবে আর অন্যদের সাপের মুখে ফেলবে!
    private fun rollControlledSnakeDice(pIdx: Int): Int {
        val currColor = activePlayers[pIdx]
        val currPos = positions[pIdx]
        val order = supabase.rankOrder
        if (order.isEmpty()) return Random.nextInt(1, 7)

        val topTarget = order.firstOrNull { c -> activePlayers.any { it.name == c } } ?: return Random.nextInt(1, 7)
        val isWinnerTarget = (currColor.name == topTarget)

        val candidates = mutableListOf<Int>()
        for (d in 1..6) {
            val next = currPos + d
            if (next > 100) continue
            var weight = 10
            if (isWinnerTarget) {
                if (next == 100) weight += 150
                if (next in ladders) weight += 95
                if (next in snakes) weight = 0 // নির্ধারিত #1 কখনো সাপের মুখে পড়বে না!
            } else {
                if (next == 100) weight = 0 // অন্য কেউ ১০০ ঘরে জিততে পারবে না!
                if (next in snakes) weight += 120 // বন্ধুকে মজার ছলে সাপের মুখে ফেলা!
                if (next in ladders) weight = 1
            }
            repeat(weight) { candidates.add(d) }
        }
        return if (candidates.isNotEmpty()) candidates.random() else (1..6).firstOrNull { currPos + it <= 100 } ?: 1
    }

    private fun cellCenter(num: Int): Pair<Float, Float> {
        val n = num.coerceIn(1, 100) - 1
        val rowFromBottom = n / 10
        val colInRow = n % 10
        val col = if (rowFromBottom % 2 == 0) colInRow else (9 - colInRow)
        val rowFromTop = 9 - rowFromBottom
        return (boardLeft + (col + 0.5f) * cellSize) to (boardTop + (rowFromTop + 0.5f) * cellSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0D2B4E"))

        val boardSize = minOf(width - 32f, height * 0.62f)
        boardLeft = (width - boardSize) / 2f
        cellSize = boardSize / 10f
        boardTop = (height - boardSize) / 2f

        // টাইটেল ব্যানার
        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 44f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🐍 SNAKES & LADDERS 🪜", width / 2f, boardTop - 36f, paint)

        // ১০x১০ বোর্ড গ্রিড
        val tileColors = listOf("#FEF3C7", "#DBEAFE", "#FCE7F3", "#DCFCE7", "#F3E8FF")
        for (num in 1..100) {
            val (cx, cy) = cellCenter(num)
            val l = cx - cellSize / 2f
            val t = cy - cellSize / 2f
            paint.color = Color.parseColor(tileColors[num % tileColors.size])
            canvas.drawRect(l, t, l + cellSize, t + cellSize, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#64748B")
            canvas.drawRect(l, t, l + cellSize, t + cellSize, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = cellSize * 0.30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$num", l + 6f, t + cellSize * 0.34f, paint)
        }

        // মই (Ladders) আঁকা
        ladders.forEach { (start, end) ->
            val (x1, y1) = cellCenter(start)
            val (x2, y2) = cellCenter(end)
            paint.color = Color.parseColor("#B45309")
            paint.strokeWidth = 8f
            canvas.drawLine(x1 - 12f, y1, x2 - 12f, y2, paint)
            canvas.drawLine(x1 + 12f, y1, x2 + 12f, y2, paint)
            paint.strokeWidth = 5f
            for (s in 1..5) {
                val f = s / 6f
                val rx = x1 + (x2 - x1) * f
                val ry = y1 + (y2 - y1) * f
                canvas.drawLine(rx - 12f, ry, rx + 12f, ry, paint)
            }
        }

        // সাপ (Snakes) আঁকা
        snakes.forEach { (head, tail) ->
            val (x1, y1) = cellCenter(head)
            val (x2, y2) = cellCenter(tail)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 14f
            paint.color = Color.parseColor("#DC2626")
            val path = Path().apply {
                moveTo(x1, y1)
                quadTo((x1 + x2) / 2f + 45f, (y1 + y2) / 2f, x2, y2)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#991B1B")
            canvas.drawCircle(x1, y1, 15f, paint)
            paint.color = Color.YELLOW
            canvas.drawCircle(x1 - 5f, y1 - 3f, 3.5f, paint)
            canvas.drawCircle(x1 + 5f, y1 - 3f, 3.5f, paint)
        }

        // প্লেয়ার গুটি আঁকা
        activePlayers.forEachIndexed { idx, p ->
            val (px, py) = cellCenter(positions[idx])
            val off = (idx - (activePlayers.size - 1) / 2f) * 14f
            paint.color = Color.WHITE
            canvas.drawCircle(px + off, py + 6f, cellSize * 0.28f, paint)
            paint.color = p.colorHex
            canvas.drawCircle(px + off, py + 6f, cellSize * 0.22f, paint)
        }

        // নিচে ডাইস কন্ট্রোল বার
        val currColor = activePlayers[currentPlayerIdx]
        val currName = playerNames.getOrElse(currentPlayerIdx) { currColor.name }
        diceRect.set(width / 2f - 160f, boardTop + boardSize + 24f, width / 2f + 160f, boardTop + boardSize + 144f)
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(diceRect, 24f, 24f, paint)
        paint.color = Color.parseColor("#1E3A8A")
        canvas.drawRoundRect(diceRect.left + 6f, diceRect.top + 6f, diceRect.right - 6f, diceRect.bottom - 6f, 20f, 20f, paint)

        paint.color = currColor.colorHex
        canvas.drawCircle(diceRect.left + 56f, diceRect.centerY(), 28f, paint)
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(currName, diceRect.left + 96f, diceRect.centerY() + 10f, paint)

        // ডাইস ফেস
        canvas.save()
        val dCx = diceRect.right - 56f
        val dCy = diceRect.centerY()
        canvas.translate(dCx, dCy)
        canvas.rotate(diceRotation)
        canvas.scale(diceScale, diceScale)
        paint.color = Color.WHITE
        canvas.drawRoundRect(-36f, -36f, 36f, 36f, 12f, 12f, paint)
        paint.color = currColor.colorHex
        paint.textSize = 38f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$diceValue", 0f, 13f, paint)
        canvas.restore()

        // ব্যাক বাটন
        backRect.set(28f, height - 105f, 118f, height - 25f)
        paint.color = Color.parseColor("#1E3A8A")
        canvas.drawRoundRect(backRect, 20f, 20f, paint)
        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("☰", backRect.centerX(), backRect.centerY() + 14f, paint)

        if (winnerIdx != -1) {
            paint.color = Color.argb(230, 10, 20, 50)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 58f
            canvas.drawText("🏆 WINNER 🏆", width / 2f, height * 0.42f, paint)
            val wCol = activePlayers[winnerIdx]
            val wName = playerNames.getOrElse(winnerIdx) { wCol.name }
            paint.color = wCol.lightHex
            paint.textSize = 48f
            canvas.drawText("#1 $wName (${wCol.name})", width / 2f, height * 0.52f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (backRect.contains(event.x, event.y)) { onExitHome(); return true }
        if (isAnimating || winnerIdx != -1) return true
        if (diceRect.contains(event.x, event.y)) rollAndMove()
        return true
    }

    private fun rollAndMove() {
        isAnimating = true
        SoundEngine.playDiceRoll()
        val finalDice = rollControlledSnakeDice(currentPlayerIdx)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = OvershootInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                diceRotation = f * 720f
                diceScale = 1f + sin(f * Math.PI).toFloat() * 0.4f
                diceValue = if (f < 0.85f) Random.nextInt(1, 7) else finalDice
                invalidate()
            }
            start()
        }

        handler.postDelayed({
            diceRotation = 0f; diceScale = 1f
            val target = positions[currentPlayerIdx] + finalDice
            if (target > 100) {
                isAnimating = false
                nextTurn(finalDice == 6)
                return@postDelayed
            }
            var step = positions[currentPlayerIdx]
            fun walk() {
                if (step < target) {
                    step++
                    positions[currentPlayerIdx] = step
                    SoundEngine.playStep()
                    invalidate()
                    handler.postDelayed({ walk() }, 120)
                } else {
                    val landed = positions[currentPlayerIdx]
                    when {
                        landed in ladders -> {
                            SoundEngine.playHomeOrSix()
                            positions[currentPlayerIdx] = ladders[landed]!!
                        }
                        landed in snakes -> {
                            SoundEngine.playCaptureWhoosh()
                            positions[currentPlayerIdx] = snakes[landed]!!
                        }
                    }
                    invalidate()
                    syncRemote()
                    if (positions[currentPlayerIdx] == 100) {
                        winnerIdx = currentPlayerIdx
                        SoundEngine.playWinFanfare()
                        isAnimating = false
                        invalidate()
                    } else {
                        isAnimating = false
                        nextTurn(finalDice == 6)
                    }
                }
            }
            walk()
        }, 410)
    }

    private fun nextTurn(extra: Boolean) {
        if (!extra) currentPlayerIdx = (currentPlayerIdx + 1) % activePlayers.size
        invalidate()
        if (vsComputer && currentPlayerIdx > 0 && winnerIdx == -1) {
            handler.postDelayed({ if (!isAnimating) rollAndMove() }, 600)
        }
    }
}
