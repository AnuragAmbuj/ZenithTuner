package com.zenith.tuner.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zenith.tuner.ui.theme.*
import kotlin.math.*

/**
 * 120Hz Zero-Recomposition Bode Plot Canvas.
 * Uses Modifier.drawWithCache to render the 64-band frequency response spline
 * without triggering Compose recomposition during touch drags.
 */
@Composable
fun BodePlotCanvas(
    curveGains: FloatArray,
    isDspActive: Boolean,
    preampDb: Float,
    modifier: Modifier = Modifier
) {
    // Ephemeral touch scrubber state held locally for 120Hz canvas drawing
    var touchX by remember { mutableFloatStateOf(-1f) }
    var isTouching by remember { mutableStateOf(false) }

    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val gridColor = colorScheme.outlineVariant.copy(alpha = 0.45f)
    val zeroLineColor = colorScheme.outline
    val curveColor = if (isDspActive) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val glowColor = if (isDspActive) colorScheme.primary.copy(alpha = 0.30f) else Color.Transparent
    val labelColorInt = colorScheme.onSurfaceVariant.toArgb()
    val tooltipBgInt = colorScheme.surfaceContainerHighest.toArgb()
    val tooltipBorderInt = colorScheme.primary.toArgb()
    val tooltipTextInt = colorScheme.onSurface.toArgb()
    val tooltipSubInt = colorScheme.primary.toArgb()

    // Native text paint for crisp hardware text rendering inside Canvas without Compose layout passes
    val textPaint = remember {
        Paint().apply {
            textSize = 28f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
    }.apply {
        color = labelColorInt
    }

    val tooltipPaint = remember {
        Paint().apply {
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
    }.apply {
        color = tooltipTextInt
    }

    val tooltipSubPaint = remember {
        Paint().apply {
            textSize = 24f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
    }.apply {
        color = tooltipSubInt
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        touchX = offset.x
                        isTouching = true
                        tryAwaitRelease()
                        isTouching = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchX = offset.x
                        isTouching = true
                    },
                    onDragEnd = { isTouching = false },
                    onDragCancel = { isTouching = false },
                    onDrag = { change, _ ->
                        touchX = change.position.x
                        change.consume()
                    }
                )
            }
            .drawWithCache {
                val width = size.width
                val height = size.height
                val paddingBottom = 40f
                val effectiveHeight = height - paddingBottom

                val minFreq = 20.0
                val maxFreq = 20000.0
                val logMin = log10(minFreq)
                val logMax = log10(maxFreq)
                val logRange = logMax - logMin

                val minDb = -18.0f
                val maxDb = 12.0f
                val dbRange = maxDb - minDb

                fun freqToX(f: Double): Float {
                    val logF = log10(f.coerceIn(minFreq, maxFreq))
                    return (((logF - logMin) / logRange) * width).toFloat()
                }

                fun xToFreq(x: Float): Double {
                    val ratio = (x / width).coerceIn(0f, 1f).toDouble()
                    return 10.0.pow(logMin + ratio * logRange)
                }

                fun dbToY(db: Float): Float {
                    val clamped = db.coerceIn(minDb, maxDb)
                    return ((maxDb - clamped) / dbRange) * effectiveHeight
                }

                fun yToDb(y: Float): Float {
                    val clampedY = y.coerceIn(0f, effectiveHeight)
                    return maxDb - (clampedY / effectiveHeight) * dbRange
                }

                // Precompute 64 frequency X coordinates
                val points = Array(64) { i ->
                    val exp = i.toDouble() / 63.0
                    val f = minFreq * (maxFreq / minFreq).pow(exp)
                    val x = freqToX(f)
                    val db = if (isDspActive && i < curveGains.size) curveGains[i] else 0f
                    val y = dbToY(db)
                    Offset(x, y)
                }

                // Generate smooth cubic spline path
                val splinePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = if (i > 0) points[i - 1] else points[i]
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val p3 = if (i + 2 < points.size) points[i + 2] else p2

                            val cp1x = p1.x + (p2.x - p0.x) / 6f
                            val cp1y = p1.y + (p2.y - p0.y) / 6f
                            val cp2x = p2.x - (p3.x - p1.x) / 6f
                            val cp2y = p2.y - (p3.y - p1.y) / 6f

                            cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                    }
                }

                // Fill gradient path closed at 0dB reference line or bottom
                val fillPath = Path().apply {
                    addPath(splinePath)
                    lineTo(width, dbToY(minDb))
                    lineTo(0f, dbToY(minDb))
                    close()
                }

                val fillBrush = Brush.verticalGradient(
                    colors = if (isDspActive) {
                        listOf(
                            colorScheme.primary.copy(alpha = 0.28f),
                            colorScheme.secondary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    },
                    startY = 0f,
                    endY = effectiveHeight
                )

                onDrawBehind {
                    // 1. Draw Logarithmic Frequency Grid Lines
                    val gridFreqs = listOf(
                        20.0 to "20",
                        50.0 to "50",
                        100.0 to "100",
                        200.0 to "200",
                        500.0 to "500",
                        1000.0 to "1k",
                        2000.0 to "2k",
                        5000.0 to "5k",
                        10000.0 to "10k",
                        20000.0 to "20k"
                    )

                    gridFreqs.forEach { (freq, label) ->
                        val x = freqToX(freq)
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, effectiveHeight),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x - (label.length * 7f),
                            height - 12f,
                            textPaint
                        )
                    }

                    // 2. Draw Decibel Horizontal Grid Lines
                    val dbLevels = listOf(12f, 6f, 0f, -6f, -12f, -18f)
                    dbLevels.forEach { db ->
                        val y = dbToY(db)
                        val isZero = abs(db) < 0.1f
                        drawLine(
                            color = if (isZero) zeroLineColor else gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = if (isZero) 1.5f else 1f,
                            pathEffect = if (isZero) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${if (db > 0) "+" else ""}${db.toInt()}dB",
                            12f,
                            y - 6f,
                            textPaint
                        )
                    }

                    // 3. Draw Preamp Headroom Reference Line (if active)
                    if (isDspActive && preampDb < -0.1f) {
                        val preampY = dbToY(preampDb)
                        drawLine(
                            color = DamperAmber.copy(alpha = 0.55f),
                            start = Offset(0f, preampY),
                            end = Offset(width, preampY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                    }

                    // 4. Draw Gradient Fill and Spline Curve
                    drawPath(path = fillPath, brush = fillBrush)

                    // Spline Glow
                    if (isDspActive) {
                        drawPath(
                            path = splinePath,
                            color = glowColor,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Crisp Line
                    drawPath(
                        path = splinePath,
                        color = curveColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 5. Draw Interactive Touch Scrubber / Inspector
                    if (isTouching && touchX in 0f..width) {
                        val curFreq = xToFreq(touchX)
                        val curDb = evaluateCurveDb(points, touchX, minDb, maxDb, effectiveHeight)
                        val curY = dbToY(curDb)

                        // Vertical Scrubber Line
                        drawLine(
                            color = colorScheme.primary.copy(alpha = 0.85f),
                            start = Offset(touchX, 0f),
                            end = Offset(touchX, effectiveHeight),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Glowing Crosshair Ring on Curve
                        drawCircle(
                            color = colorScheme.primary.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = Offset(touchX, curY)
                        )
                        drawCircle(
                            color = colorScheme.primary,
                            radius = 4.dp.toPx(),
                            center = Offset(touchX, curY)
                        )

                        // Acoustic Context Tag
                        val tag = getAcousticTag(curFreq)

                        // Tooltip Box
                        drawInspectionTooltip(
                            canvas = drawContext.canvas.nativeCanvas,
                            touchX = touchX,
                            touchY = curY,
                            width = width,
                            freq = curFreq,
                            gainDb = curDb,
                            tag = tag,
                            titlePaint = tooltipPaint,
                            subPaint = tooltipSubPaint,
                            tooltipBgColor = tooltipBgInt,
                            tooltipBorderColor = tooltipBorderInt
                        )
                    }
                }
            }
    )
}

/**
 * Interpolates dB gain from precomputed screen points for touch inspection
 */
private fun evaluateCurveDb(
    points: Array<Offset>,
    x: Float,
    minDb: Float,
    maxDb: Float,
    height: Float
): Float {
    if (points.isEmpty()) return 0f
    if (x <= points.first().x) return maxDb - (points.first().y / height) * (maxDb - minDb)
    if (x >= points.last().x) return maxDb - (points.last().y / height) * (maxDb - minDb)

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        if (x in p1.x..p2.x) {
            val t = (x - p1.x) / (p2.x - p1.x)
            val interpY = p1.y + t * (p2.y - p1.y)
            return maxDb - (interpY / height) * (maxDb - minDb)
        }
    }
    return 0f
}

/**
 * Maps frequency range to specific boAt Nirvana Zenith acoustic correction target tag
 */
private fun getAcousticTag(freq: Double): String {
    return when {
        freq in 18.0..28.0 -> "HPF Sub-Rumble Strip"
        freq in 45.0..68.0 -> "55Hz Low-Shelf Slam"
        freq in 120.0..170.0 -> "145Hz 50dB ANC Anti-Mud Cut"
        freq in 280.0..360.0 -> "320Hz Mid-Bass Clarity"
        freq in 1400.0..1900.0 -> "1650Hz Vocal Forwardness"
        freq in 2700.0..3600.0 -> "3100Hz Pinna Ear-Gain"
        freq in 5800.0..7400.0 -> "6600Hz AAC De-Sizzle Notch"
        freq >= 10000.0 -> "11.5kHz Air & Soundstage"
        else -> "Obsidian Acoustic Target"
    }
}

/**
 * Draws floating tooltip pill directly on native canvas for zero layout penalty
 */
private fun drawInspectionTooltip(
    canvas: android.graphics.Canvas,
    touchX: Float,
    touchY: Float,
    width: Float,
    freq: Double,
    gainDb: Float,
    tag: String,
    titlePaint: Paint,
    subPaint: Paint,
    tooltipBgColor: Int,
    tooltipBorderColor: Int
) {
    val freqStr = if (freq >= 1000.0) {
        String.format("%.1fkHz", freq / 1000.0)
    } else {
        String.format("%.0fHz", freq)
    }
    val gainStr = String.format("%+.1fdB", gainDb)
    val line1 = "$freqStr  •  $gainStr"

    val boxWidth = 360f
    val boxHeight = 110f

    // Keep tooltip inside canvas bounds
    var left = touchX - boxWidth / 2f
    if (left < 16f) left = 16f
    if (left + boxWidth > width - 16f) left = width - boxWidth - 16f

    var top = touchY - boxHeight - 24f
    if (top < 16f) top = touchY + 24f

    val bgPaint = Paint().apply {
        color = tooltipBgColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val borderPaint = Paint().apply {
        color = tooltipBorderColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Draw background pill
    canvas.drawRoundRect(left, top, left + boxWidth, top + boxHeight, 18f, 18f, bgPaint)
    canvas.drawRoundRect(left, top, left + boxWidth, top + boxHeight, 18f, 18f, borderPaint)

    // Text content
    canvas.drawText(line1, left + 20f, top + 42f, titlePaint)
    canvas.drawText(tag, left + 20f, top + 80f, subPaint)
}
