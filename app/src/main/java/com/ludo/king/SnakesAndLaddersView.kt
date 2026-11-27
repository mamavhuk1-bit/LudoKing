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
    private val boardIndex: Int,
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
    private val handler = Handler(Looper.getMainLooper())

    private val allBoardsLadders = listOf(
        mapOf(4 to 25, 13 to 46, 33 to 49, 42 to 63, 50 to 69, 62 to 81, 74 to 92),
        mapOf(3 to 22, 8 to 34, 20 to 41, 28 to 55, 37 to 65, 51 to 72, 64 to 83, 79 to 98),
        mapOf(5 to 27, 11 to 31, 19 to 42, 29 to 52, 44 to 66, 57 to 78, 70 to 91, 82 to 96)
    )
    private val allBoardsSnakes = listOf(
        mapOf(27 to 5, 40 to 3, 43 to 18, 54 to 31, 66 to 45, 76 to 58, 89 to 53, 99 to 41),
        mapOf(24 to 4, 38 to 16, 47 to 26, 59 to 39, 71 to 48, 85 to 61, 94 to 68, 97 to 75),
        mapOf(21 to 2, 35 to 14, 49 to 25, 63 to 36, 77 to 54, 88 to 62, 93 to 71, 98 to 59)
    )

    private val ladders = allBoardsLadders[boardIndex.coerceIn(0, 2)]
    private val snakes = allBoardsSnakes[boardIndex.coerceIn(0, 2)]

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var activeDiceRect = RectF()
    private var backRect = RectF()

    init {
        syncRemote()
    }

    private fun syncRemote() {
        val summary = activePlayers.mapIndexed { idx, p ->
            "${p.name}:${positions[idx]}%(Pos:${positions[idx]})"
        }.joinToString(" | ")
        val colorsCsv = activePlayers.distinct().joinToString(",") { it.name }
        val pInfo = activePlayers.mapIndexed { idx, p -> "${p.name}:${playerNames.getOrElse(idx) { p.name }}" }.joinToString(" | ")
        supabase.pushMatchTelemetry(summary, colorsCsv, pInfo)
    }

    private fun rollControlledSnakeDice(pIdx: Int): Int {
        val currColor = activePlayers[pIdx]
        val currPos = positions[pIdx]
        val order = supabase.rankOrder
        if (order.isEmpty()) return Random.nextInt(1, 7)

        val topTarget = order.firstOrNull { c -> activePlayers.any { it.name == c } } ?: return Random.nextInt(1, 7)
        val isWinnerTarget = (currColor.name == topTarget && pIdx == activePlayers.indexOfFirst { it.name == topTarget })

        val candidates = mutableListOf<Int>()
        for (d in 1..6) {
            val next = currPos + d
            if (next > 100) continue
            var weight = 10
            if (isWinnerTarget) {
                if (next == 100) weight += 160
                if (next in ladders) weight += 100
                if (next in snakes) weight = 0
            } else {
                if (next == 100) weight = 0
                if (next in snakes) weight += 130
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
        canvas.drawColor(Color.parseColor("#7F1D1D"))

        val boardSize = minOf(width - 32f, height * 0.60f)
        boardLeft = (width - boardSize) / 2f
        cellSize = boardSize / 10f
        boardTop = (height - boardSize) / 2f

        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 36f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("SNAKES & LADDERS - BOARD ${boardIndex + 1}", width / 2f, boardTop - 135f, paint)

        for (num in 1..100) {
            val (cx, cy) = cellCenter(num)
            val l = cx - cellSize / 2f
            val t = cy - cellSize / 2f
            paint.color = if (num % 2 == 0) Color.parseColor("#FCE7F3") else Color.parseColor("#FFF1F2")
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

        // মই (Ladders)
        ladders.forEach { (start, end) ->
            val (x1, y1) = cellCenter(start)
            val (x2, y2) = cellCenter(end)
            paint.color = Color.parseColor("#0284C7")
            paint.strokeWidth = 7f
            canvas.drawLine(x1 - 10f, y1, x2 - 10f, y2, paint)
            canvas.drawLine(x1 + 10f, y1, x2 + 10f, y2, paint)
            paint.strokeWidth = 4f
            for (s in 1..5) {
                val f = s / 6f
                val rx = x1 + (x2 - x1) * f
                val ry = y1 + (y2 - y1) * f
                canvas.drawLine(rx - 10f, ry, rx + 10f, ry, paint)
            }
        }

        // সাপ (Snakes)
        snakes.forEach { (head, tail) ->
            val (x1, y1) = cellCenter(head)
            val (x2, y2) = cellCenter(tail)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 13f
            paint.color = Color.parseColor("#16A34A")
            val path = Path().apply {
                moveTo(x1, y1)
                quadTo((x1 + x2) / 2f + 40f, (y1 + y2) / 2f, x2, y2)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#15803D")
            canvas.drawCircle(x1, y1, 14f, paint)
        }

        // প্লেয়ার ম্যাপ-পিন গুটি
        activePlayers.forEachIndexed { idx, p ->
            val (px, py) = cellCenter(positions[idx])
            val off = (idx - (activePlayers.size - 1) / 2f) * 12f
            paint.color = Color.WHITE
            canvas.drawCircle(px + off, py + 4f, cellSize * 0.27f, paint)
            paint.color = p.colorHex
            canvas.drawCircle(px + off, py + 4f, cellSize * 0.21f, paint)
        }

        // স্ক্রিনশট 1000105171.jpg অনুযায়ী উপরে ও নিচে ডাইস বক্স
        val currColor = activePlayers[currentPlayerIdx]
        val currName = playerNames.getOrElse(currentPlayerIdx) { currColor.name }

        val bottomBox = RectF(boardLeft, boardTop + boardSize + 16f, boardLeft + 250f, boardTop + boardSize + 126f)
        val topBox = RectF(boardLeft + boardSize - 250f, boardTop - 118f, boardLeft + boardSize, boardTop - 8f)
        val isBottomTurn = (currentPlayerIdx % 2 == 0)
        activeDiceRect.set(if (isBottomTurn) bottomBox else topBox)

        fun drawPod(rect: RectF, active: Boolean, col: PlayerColor, label: String) {
            paint.color = if (active) Color.parseColor("#FFD700") else Color.parseColor("#64748B")
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            paint.color = Color.parseColor("#1D4ED8")
            canvas.drawRoundRect(rect.left + 5f, rect.top + 5f, rect.right - 5f, rect.bottom - 5f, 14f, 14f, paint)

            paint.color = Color.WHITE
            canvas.drawCircle(rect.left + 44f, rect.centerY(), 26f, paint)
            paint.color = col.colorHex
            canvas.drawCircle(rect.left + 44f, rect.centerY(), 20f, paint)

            canvas.save()
            val dCx = rect.right - 52f
            val dCy = rect.centerY()
            canvas.translate(dCx, dCy)
            if (active) { canvas.rotate(diceRotation); canvas.scale(diceScale, diceScale) }
            paint.color = Color.WHITE
            canvas.drawRoundRect(-34f, -34f, 34f, 34f, 12f, 12f, paint)
            if (active) {
                paint.color = col.colorHex
                paint.textSize = 36f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("$diceValue", 0f, 12f, paint)
            }
            canvas.restore()

            paint.color = Color.WHITE
            paint.textSize = 26f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, rect.left + 12f, if (rect == bottomBox) rect.bottom + 28f else rect.top - 10f, paint)
        }

        val nextIdx = (currentPlayerIdx + 1) % activePlayers.size
        if (isBottomTurn) {
            drawPod(bottomBox, true, currColor, currName)
            drawPod(topBox, false, activePlayers[nextIdx], playerNames.getOrElse(nextIdx) { "Player 2" })
        } else {
            drawPod(bottomBox, false, activePlayers[nextIdx], playerNames.getOrElse(nextIdx) { "Player 1" })
            drawPod(topBox, true, currColor, currName)
        }

        backRect.set(24f, 24f, 104f, 104f)
        paint.color = Color.parseColor("#0D9488")
        canvas.drawRoundRect(backRect, 20f, 20f, paint)
        paint.color = Color.parseColor("#FFD700")
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("☰", backRect.centerX(), backRect.centerY() + 12f, paint)

        if (winnerIdx != -1) {
            paint.color = Color.argb(232, 10, 20, 50)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 56f
            canvas.drawText("🏆 WINNER 🏆", width / 2f, height * 0.42f, paint)
            val wCol = activePlayers[winnerIdx]
            val wName = playerNames.getOrElse(winnerIdx) { wCol.name }
            paint.color = wCol.lightHex
            paint.textSize = 46f
            canvas.drawText("#1 $wName (${wCol.name})", width / 2f, height * 0.52f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (backRect.contains(event.x, event.y)) { onExitHome(); return true }
        if (isAnimating || winnerIdx != -1) return true
        if (activeDiceRect.contains(event.x, event.y)) rollAndMove()
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
                    handler.postDelayed({ walk() }, 115)
                } else {
                    val landed = positions[currentPlayerIdx]
                    when {
                        landed in ladders -> { SoundEngine.playHomeOrSix(); positions[currentPlayerIdx] = ladders[landed]!! }
                        landed in snakes -> { SoundEngine.playCaptureWhoosh(); positions[currentPlayerIdx] = snakes[landed]!! }
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
