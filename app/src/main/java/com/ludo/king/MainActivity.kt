package com.ludo.king

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import kotlin.random.Random

enum class PlayerColor(val colorHex: Int, val darkHex: Int, val startOffset: Int) {
    RED(Color.parseColor("#E53935"), Color.parseColor("#B71C1C"), 0),
    GREEN(Color.parseColor("#43A047"), Color.parseColor("#1B5E20"), 13),
    YELLOW(Color.parseColor("#FDD835"), Color.parseColor("#F57F17"), 26),
    BLUE(Color.parseColor("#1E88E5"), Color.parseColor("#0D47A1"), 39)
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
    private var diceValue = 1
    private var isDiceRolled = false
    private var winner: PlayerColor? = null

    private val tokens = players.flatMap { p -> (0..3).map { Token(p, it) } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var boardTop = 0f
    private var diceRect = RectF()

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
        PlayerColor.RED to listOf(1.8f to 1.8f, 3.8f to 1.8f, 1.8f to 3.8f, 3.8f to 3.8f),
        PlayerColor.GREEN to listOf(10.8f to 1.8f, 12.8f to 1.8f, 10.8f to 3.8f, 12.8f to 3.8f),
        PlayerColor.YELLOW to listOf(10.8f to 10.8f, 12.8f to 10.8f, 10.8f to 12.8f, 12.8f to 12.8f),
        PlayerColor.BLUE to listOf(1.8f to 10.8f, 3.8f to 10.8f, 1.8f to 12.8f, 3.8f to 12.8f)
    )

    private fun rollSmartDice(player: PlayerColor): Int {
        val target = supabase.riggedWinner ?: return Random.nextInt(1, 7)
        val myTokens = tokens.filter { it.owner == player }

        if (player.name == target) {
            val candidates = mutableListOf<Int>()
            for (d in 1..6) {
                var weight = 10
                for (t in myTokens) {
                    if (t.steps == -1 && d == 6) weight += 35
                    if (t.steps in 0..56) {
                        val nextStep = t.steps + d
                        if (nextStep == 57) weight += 45
                        if (nextStep in 0..50) {
                            val gIdx = (player.startOffset + nextStep) % 52
                            if (gIdx in safeGlobalIndices) weight += 20
                            val canKill = tokens.any { opp ->
                                opp.owner != player && opp.steps in 0..50 &&
                                        ((opp.owner.startOffset + opp.steps) % 52 == gIdx) &&
                                        gIdx !in safeGlobalIndices
                            }
                            if (canKill) weight += 50
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

    private fun getTokenPixelPos(t: Token): Pair<Float, Float> {
        val (gx, gy) = when {
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
        return (gx * cellSize) to (boardTop + gy * cellSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1A237E"))

        cellSize = width / 15f
        boardTop = (height - width) / 2f

        paint.color = Color.WHITE
        canvas.drawRect(0f, boardTop, width.toFloat(), boardTop + width, paint)

        drawYard(canvas, 0, 0, PlayerColor.RED)
        drawYard(canvas, 9, 0, PlayerColor.GREEN)
        drawYard(canvas, 9, 9, PlayerColor.YELLOW)
        drawYard(canvas, 0, 9, PlayerColor.BLUE)

        for (p in players) {
            paint.color = p.colorHex
            homeStretches[p]!!.take(5).forEach { (cx, cy) ->
                canvas.drawRect(cx * cellSize, boardTop + cy * cellSize, (cx + 1) * cellSize, boardTop + (cy + 1) * cellSize, paint)
            }
            val startCell = mainTrack[p.startOffset]
            canvas.drawRect(startCell.first * cellSize, boardTop + startCell.second * cellSize, (startCell.first + 1) * cellSize, boardTop + (startCell.second + 1) * cellSize, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.color = Color.GRAY
        paint.strokeWidth = 2f
        for (i in 0..15) {
            canvas.drawLine(i * cellSize, boardTop, i * cellSize, boardTop + width, paint)
            canvas.drawLine(0f, boardTop + i * cellSize, width.toFloat(), boardTop + i * cellSize, paint)
        }
        paint.style = Paint.Style.FILL

        paint.color = Color.DKGRAY
        paint.textSize = cellSize * 0.65f
        paint.textAlign = Paint.Align.CENTER
        safeGlobalIndices.forEach { idx ->
            val (cx, cy) = mainTrack[idx]
            canvas.drawText("★", (cx + 0.5f) * cellSize, boardTop + (cy + 0.72f) * cellSize, paint)
        }

        drawCenterTriangles(canvas)

        paint.color = Color.WHITE
        paint.textSize = 44f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Room: $roomId", 40f, boardTop - 50f, paint)

        val curr = players[currentPlayerIdx]
        paint.color = curr.colorHex
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Turn: ${curr.name}", width - 40f, boardTop - 50f, paint)

        for (t in tokens) {
            if (t.steps == 57) continue
            val (px, py) = getTokenPixelPos(t)
            paint.color = Color.WHITE
            canvas.drawCircle(px, py, cellSize * 0.38f, paint)
            paint.color = t.owner.colorHex
            canvas.drawCircle(px, py, cellSize * 0.31f, paint)
            paint.color = t.owner.darkHex
            canvas.drawCircle(px, py, cellSize * 0.14f, paint)
        }

        val dSize = 160f
        val dLeft = (width - dSize) / 2f
        val dTop = boardTop + width + 50f
        diceRect.set(dLeft, dTop, dLeft + dSize, dTop + dSize)

        paint.color = curr.colorHex
        canvas.drawRoundRect(diceRect, 28f, 28f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(dLeft + 10f, dTop + 10f, dLeft + dSize - 10f, dTop + dSize - 10f, 20f, 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 75f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(diceValue.toString(), width / 2f, dTop + 105f, paint)

        winner?.let {
            paint.color = Color.argb(210, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.YELLOW
            paint.textSize = 70f
            canvas.drawText("🏆 ${it.name} WINS! 🏆", width / 2f, height / 2f, paint)
        }
    }

    private fun drawYard(canvas: Canvas, gx: Int, gy: Int, p: PlayerColor) {
        paint.color = p.colorHex
        canvas.drawRect(gx * cellSize, boardTop + gy * cellSize, (gx + 6) * cellSize, boardTop + (gy + 6) * cellSize, paint)
        paint.color = Color.WHITE
        canvas.drawRect((gx + 1) * cellSize, boardTop + (gy + 1) * cellSize, (gx + 5) * cellSize, boardTop + (gy + 5) * cellSize, paint)
        paint.color = p.colorHex
        yardCoords[p]!!.forEach { (cx, cy) ->
            canvas.drawCircle(cx * cellSize, boardTop + cy * cellSize, cellSize * 0.42f, paint)
        }
    }

    private fun drawCenterTriangles(canvas: Canvas) {
        val l = 6 * cellSize
        val r = 9 * cellSize
        val t = boardTop + 6 * cellSize
        val b = boardTop + 9 * cellSize
        val cx = 7.5f * cellSize
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
        if (event.action != MotionEvent.ACTION_DOWN || winner != null) return true
        val curr = players[currentPlayerIdx]

        if (!isDiceRolled) {
            if (diceRect.contains(event.x, event.y)) {
                diceValue = rollSmartDice(curr)
                isDiceRolled = true
                val movable = tokens.filter { it.owner == curr && canMove(it, diceValue) }
                if (movable.isEmpty()) {
                    nextTurn(false)
                } else if (movable.size == 1) {
                    moveToken(movable.first())
                }
                invalidate()
            }
        } else {
            val clicked = tokens.filter { it.owner == curr && canMove(it, diceValue) }
                .minByOrNull { t ->
                    val (px, py) = getTokenPixelPos(t)
                    hypot(event.x - px, event.y - py)
                }
            if (clicked != null) {
                val (px, py) = getTokenPixelPos(clicked)
                if (hypot(event.x - px, event.y - py) < cellSize * 1.1f) {
                    moveToken(clicked)
                    invalidate()
                }
            }
        }
        return true
    }

    private fun canMove(t: Token, dice: Int): Boolean {
        if (t.steps == -1) return dice == 6
        return t.steps + dice <= 57
    }

    private fun moveToken(t: Token) {
        var extraTurn = (diceValue == 6)
        if (t.steps == -1 && diceValue == 6) {
            t.steps = 0
        } else {
            t.steps += diceValue
        }

        if (t.steps == 57) {
            extraTurn = true
            if (tokens.count { it.owner == t.owner && it.steps == 57 } == 4) {
                winner = t.owner
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
                    Toast.makeText(context, "Captured!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        nextTurn(extraTurn)
    }

    private fun nextTurn(extraTurn: Boolean) {
        isDiceRolled = false
        if (!extraTurn) {
            currentPlayerIdx = (currentPlayerIdx + 1) % 4
        }
    }
}
