package com.focus.prototype

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Observe touch without consuming it, so scroll and the control's own click handling still win. */
internal fun Modifier.immediatePress(enabled: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var releaseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    this.pointerInput(enabled) {
        if (!enabled) { pressed = false; return@pointerInput }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            releaseJob?.cancel()
            pressed = true
            val start = down.uptimeMillis
            var cancelled = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) { cancelled = true; break }
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (change.position.x < 0f || change.position.y < 0f ||
                        change.position.x > size.width || change.position.y > size.height ||
                        dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop ||
                        (change.isConsumed && change.pressed && change.position != change.previousPosition)) {
                        cancelled = true
                        break
                    }
                    if (!change.pressed) {
                        val remaining = (80L - (change.uptimeMillis - start)).coerceAtLeast(0L)
                        releaseJob = scope.launch { delay(remaining); pressed = false }
                        released = true
                        break
                    }
                }
            } finally {
                if (cancelled || !released) pressed = false
            }
        }
    }.drawWithContent {
        drawContent()
        if (pressed && enabled) drawRect(Color.White.copy(alpha = .08f), topLeft = Offset.Zero)
    }
}
