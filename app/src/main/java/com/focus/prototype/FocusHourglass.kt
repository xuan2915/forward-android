package com.focus.prototype

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class SandClock(val elapsed: Long, val duration: Long, val sampledAt: Long) {
    fun progressAt(now: Long, running: Boolean): Float {
        if (duration <= 0L) return 0f
        val sinceSample = if (running) (now - sampledAt).coerceAtLeast(0L) else 0L
        return ((elapsed + sinceSample).toFloat() / duration).coerceIn(0f, 1f)
    }
}

private const val GrainLifetime = 680L
private const val Neck = .485f
private const val Gravity = 4.8f
private const val InitialSpeed = .16f

private data class Grain(val bornAt: Long, val index: Int)

private class SandStream {
    val grains = ArrayDeque<Grain>()
    var nextEmission = 0L
    private var index = 0

    fun advance(now: Long, emitting: Boolean) {
        while (grains.firstOrNull()?.let { now - it.bornAt > GrainLifetime } == true) {
            grains.removeFirst()
        }
        if (!emitting) {
            nextEmission = 0
            return
        }
        // A suspended renderer resumes with fresh grains instead of replaying missed frames.
        if (nextEmission < now - 100) nextEmission = now
        while (nextEmission <= now) {
            grains.addLast(Grain(nextEmission, index++))
            nextEmission += 38 + (index % 5) * 6
        }
    }
}

@Composable
internal fun FocusHourglass(
    clock: SandClock,
    running: Boolean,
    settling: Boolean,
    free: Boolean,
    rest: Boolean,
    tint: Color,
) {
    val reduced = LocalReducedMotion.current
    val stream = remember(rest, free) { SandStream() }
    val frame = remember { mutableLongStateOf(clock.sampledAt) }
    LaunchedEffect(stream, running, settling, reduced) {
        stream.nextEmission = 0
        if (reduced || (!running && !settling)) {
            stream.grains.clear()
            frame.longValue = SystemClock.elapsedRealtime()
        } else {
            do {
                withFrameNanos {
                    val now = SystemClock.elapsedRealtime()
                    stream.advance(now, emitting = running)
                    frame.longValue = now
                }
            } while (running || stream.grains.isNotEmpty())
        }
    }

    Box(
        Modifier.size(width = 90.dp, height = 104.dp).drawWithCache {
            val w = size.width
            val h = size.height
            val outline = Path().apply {
                moveTo(.16f * w, .09f * h)
                lineTo(.84f * w, .09f * h)
                cubicTo(.84f*w, .30f*h, .66f*w, .37f*h, .525f*w, .465f*h)
                cubicTo(.50f*w, .485f*h, .51f*w, .51f*h, .54f*w, .535f*h)
                cubicTo(.72f*w, .65f*h, .84f*w, .72f*h, .84f*w, .91f*h)
                lineTo(.16f * w, .91f * h)
                cubicTo(.16f*w, .72f*h, .28f*w, .65f*h, .46f*w, .535f*h)
                cubicTo(.49f*w, .51f*h, .50f*w, .485f*h, .475f*w, .465f*h)
                cubicTo(.34f*w, .37f*h, .16f*w, .30f*h, .16f*w, .09f*h)
                close()
            }
            val upper = Path()
            val lower = Path()
            val edge = 1.dp.toPx()
            onDrawBehind {
                val now = frame.longValue
                val progress = clock.progressAt(now, running && !reduced)
                val upperAmount = if (free) .71f else 1f - progress
                val lowerAmount = if (free) .19f else progress
                val surfaceY = .468f - .343f * sqrt(upperAmount)
                val pileHeight = .286f * lowerAmount.pow(.72f)
                val mound = .085f * (lowerAmount * 9f).coerceAtMost(1f) * (1f - lowerAmount * .55f)
                val pileBase = .908f - pileHeight
                val pilePeak = pileBase - mound
                drawPath(outline, AppColors.Background)
                clipPath(outline) {
                    if (upperAmount > 0f) {
                        val dip = (.468f - surfaceY).coerceIn(0f, .023f)
                        upper.rewind()
                        upper.moveTo(0f, h * surfaceY)
                        upper.cubicTo(.24f*w, h*(surfaceY-.004f), .38f*w, h*(surfaceY+dip), .5f*w, h*(surfaceY+dip))
                        upper.cubicTo(.62f*w, h*(surfaceY+dip), .76f*w, h*surfaceY, w, h*surfaceY)
                        upper.lineTo(w, h * .49f)
                        upper.lineTo(0f, h * .49f)
                        upper.close()
                        drawPath(upper, tint.copy(alpha = .56f))
                    }
                    if (lowerAmount > 0f) {
                        lower.rewind()
                        lower.moveTo(0f, h * pileBase)
                        lower.cubicTo(.24f*w, h*pileBase, .38f*w, h*pilePeak, .5f*w, h*pilePeak)
                        lower.cubicTo(.62f*w, h*pilePeak, .76f*w, h*pileBase, w, h*pileBase)
                        lower.lineTo(w, h)
                        lower.lineTo(0f, h)
                        lower.close()
                        drawPath(lower, tint.copy(alpha = .82f))
                    }
                    if (!reduced && (running || settling) && (free || progress < 1f)) {
                        val distance = (pilePeak - Neck).coerceAtLeast(.01f)
                        val fallSeconds = (sqrt(InitialSpeed * InitialSpeed + 2f * Gravity * distance) - InitialSpeed) / Gravity
                        for (grain in stream.grains) {
                            val age = (now - grain.bornAt) / 1000f
                            val drift = sin(grain.index * 2.4f)
                            val radius = (.54f + (grain.index % 4) * .09f).dp.toPx()
                            if (age < fallSeconds) {
                                val y = Neck + InitialSpeed * age + .5f * Gravity * age * age
                                val x = .5f + drift * (.004f + .047f * age * age)
                                drawCircle(tint.copy(alpha = .88f), radius, Offset(w*x, h*y))
                            } else {
                                val settle = ((age - fallSeconds) / .15f).coerceIn(0f, 1f)
                                if (settle < 1f) {
                                    val x = .5f + drift * (.01f + .075f * settle)
                                    val bounce = .026f * 4f * settle * (1f - settle)
                                    drawCircle(tint.copy(alpha = .7f * (1f - settle)), radius,
                                        Offset(w*x, h*(pilePeak - bounce + mound * settle * .3f)))
                                }
                            }
                        }
                    }
                }
                drawPath(outline, AppColors.Muted.copy(alpha = .65f), style = Stroke(edge))
                for (cap in listOf(.055f, .915f)) {
                    drawRoundRect(AppColors.Border, Offset(w * .10f, h * cap),
                        Size(w * .80f, h * .03f), CornerRadius(2.dp.toPx()))
                    drawLine(AppColors.Muted.copy(alpha = .24f), Offset(w*.14f, h*cap),
                        Offset(w*.86f, h*cap), edge / 2f, cap = StrokeCap.Round)
                }
            }
        },
    )
}
