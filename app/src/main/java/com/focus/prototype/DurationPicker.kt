package com.focus.prototype

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

fun formatDurationMinutes(minutes: Int): String {
    if (minutes <= 0) return "自由专注"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}\u00A0分钟"
        m == 0 -> "${h}\u00A0小时"
        else -> "${h}\u00A0小时\u00A0${m}\u00A0分"
    }
}

fun formatDurationMs(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000L).toInt()
    if (totalMinutes <= 0) {
        val sec = (milliseconds / 1_000L).toInt()
        return if (sec > 0) "${sec}\u00A0秒" else "0\u00A0分钟"
    }
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> "${m}\u00A0分钟"
        m == 0 -> "${h}\u00A0小时"
        else -> "${h}\u00A0小时\u00A0${m}\u00A0分"
    }
}

class DurationPickerPositionProvider(
    private val density: Density,
    private val marginDp: Dp = 8.dp,
    private val estimatedHeightDp: Dp = 320.dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = with(density) { marginDp.roundToPx() }
        val spaceBelow = windowSize.height - anchorBounds.bottom - marginPx
        val spaceAbove = anchorBounds.top - marginPx
        val expectedHeight = max(popupContentSize.height, with(density) { estimatedHeightDp.roundToPx() })

        // Use the expanded height for side selection so an in-progress opening never jumps sides.
        val placeBelow = spaceBelow >= expectedHeight || spaceBelow >= spaceAbove
        val rawY = if (placeBelow) {
            anchorBounds.bottom + marginPx
        } else {
            anchorBounds.top - popupContentSize.height - marginPx
        }
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val y = rawY.coerceIn(marginPx, maxY)

        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centeredX.coerceIn(marginPx, maxX)
        return IntOffset(x, y)
    }
}

@Composable
fun DurationNumberInputBox(
    valueText: String,
    onValueTextChange: (String) -> Unit,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val inputWidth = (28.dp * fontScale.coerceIn(1f, 1.8f)).coerceAtLeast(28.dp)
    val inputHeight = 28.dp * fontScale.coerceIn(1f, 1.8f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "${unitLabel}输入框"
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.width(inputWidth).height(inputHeight),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = valueText,
                onValueChange = { newText ->
                    val digits = newText.filter(Char::isDigit).take(2)
                    onValueTextChange(digits)
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = AppColors.Text
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                cursorBrush = SolidColor(AppColors.Accent)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted
        )
    }
}

@Composable
fun WheelColumn(
    items: List<Int>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    formatItem: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 36.dp,
    visibleItemsCount: Int = 5
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val maxIndex = (items.lastIndex).coerceAtLeast(0)
    val position = remember(items.size) {
        Animatable(selectedIndex.coerceIn(0, maxIndex).toFloat())
    }
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 1.35f) }
    var dragging by remember(items.size) { mutableStateOf(false) }
    var dragBase by remember(items.size) { mutableFloatStateOf(selectedIndex.coerceIn(0, maxIndex).toFloat()) }
    var dragDeltaPx by remember(items.size) { mutableFloatStateOf(0f) }
    var lastEmittedIndex by remember(items.size) { mutableIntStateOf(selectedIndex.coerceIn(0, maxIndex)) }
    val currentCallback by rememberUpdatedState(onSelectedIndexChanged)

    val displayedPositionState = remember(items.size, itemHeightPx, maxIndex) {
        derivedStateOf {
            if (dragging) {
                val raw = dragBase - dragDeltaPx / itemHeightPx
                when {
                    raw < 0f -> -WheelOverscroll * (1f - exp(raw / WheelOverscroll))
                    raw > maxIndex -> maxIndex + WheelOverscroll * (1f - exp(-(raw - maxIndex) / WheelOverscroll))
                    else -> raw
                }
            } else position.value
        }
    }
    val displayedPosition by displayedPositionState

    LaunchedEffect(position, items.size) {
        snapshotFlow {
            displayedPositionState.value.roundToInt().coerceIn(0, maxIndex)
        }.distinctUntilChanged().collect { index ->
            if (index != lastEmittedIndex) {
                lastEmittedIndex = index
                currentCallback(index)
            }
        }
    }

    LaunchedEffect(selectedIndex, items.size) {
        val externalIndex = selectedIndex.coerceIn(0, maxIndex)
        if (externalIndex != lastEmittedIndex) {
            dragging = false
            dragDeltaPx = 0f
            position.updateBounds(0f, maxIndex.toFloat())
            position.snapTo(externalIndex.toFloat())
            lastEmittedIndex = externalIndex
        }
    }

    val dragState = rememberDraggableState { delta -> dragDeltaPx += delta }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    position.stop()
                    dragBase = position.value
                    dragDeltaPx = 0f
                    dragging = true
                },
                onDragStopped = { velocityPxPerSecond ->
                    val released = displayedPosition.coerceIn(-WheelOverscroll, maxIndex + WheelOverscroll)
                    position.updateBounds(-WheelOverscroll, maxIndex + WheelOverscroll)
                    position.snapTo(released)
                    dragging = false
                    dragDeltaPx = 0f
                    val releasedIndex = released.roundToInt().coerceIn(0, maxIndex)
                    lastEmittedIndex = releasedIndex
                    currentCallback(releasedIndex)

                    val initialVelocity = (-velocityPxPerSecond / itemHeightPx).coerceIn(-WheelMaxVelocity, WheelMaxVelocity)
                    val speed = abs(initialVelocity)
                    val maxTravel = (0.55f + speed * 0.16f).coerceIn(0.65f, WheelMaxFlingItems)
                    val limitedTarget = released + initialVelocity.sign * maxTravel
                    val lowerBound = min(released, limitedTarget).coerceIn(-WheelOverscroll, maxIndex + WheelOverscroll)
                    val upperBound = max(released, limitedTarget).coerceIn(-WheelOverscroll, maxIndex + WheelOverscroll)

                    if (BuildConfig.DEBUG) {
                        Log.d("DurationWheel", "release vItemsPerSec=${"%.1f".format(initialVelocity)} from=${"%.2f".format(released)} capItems=$maxTravel target=${"%.2f".format(limitedTarget)}")
                    }

                    coroutineScope.launch {
                        if (!reducedMotion && speed >= WheelMinFlingVelocity && abs(limitedTarget - released) >= 0.08f) {
                            position.updateBounds(lowerBound, upperBound)
                            position.animateDecay(initialVelocity, decay)
                        }

                        val snappedIndex = position.value.roundToInt().coerceIn(0, maxIndex)
                        position.updateBounds(-WheelOverscroll, maxIndex + WheelOverscroll)
                        if (reducedMotion) {
                            position.snapTo(snappedIndex.toFloat())
                        } else {
                            position.animateTo(
                                snappedIndex.toFloat(),
                                spring(dampingRatio = 0.78f, stiffness = 620f)
                            )
                        }
                        position.updateBounds(0f, maxIndex.toFloat())
                        if (BuildConfig.DEBUG) Log.d("DurationWheel", "settled index=$snappedIndex value=${items.getOrNull(snappedIndex)}")
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.AccentWash)
                .border(1.dp, AppColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        )

        val centerIndex = displayedPosition.roundToInt()
        val firstIndex = centerIndex - visibleItemsCount / 2 - 1
        val lastIndex = centerIndex + visibleItemsCount / 2 + 1
        for (index in firstIndex..lastIndex) {
            if (index in items.indices) {
                val relative = index - displayedPosition
                val isSelected = index == displayedPosition.roundToInt()
                Text(
                    text = formatItem(items[index]),
                    style = if (isSelected) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = AppColors.Text
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = AppColors.Muted.copy(alpha = 0.45f)
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = with(density) { (relative * itemHeightPx).toDp() })
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            lastEmittedIndex = index
                            currentCallback(index)
                            coroutineScope.launch {
                                position.stop()
                                position.updateBounds(-WheelOverscroll, maxIndex + WheelOverscroll)
                                if (reducedMotion) position.snapTo(index.toFloat())
                                else position.animateTo(index.toFloat(), spring(dampingRatio = 0.84f, stiffness = 640f))
                                position.updateBounds(0f, maxIndex.toFloat())
                            }
                        },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private const val WheelOverscroll = 0.22f
private const val WheelMaxVelocity = 72f
private const val WheelMinFlingVelocity = 0.8f
private const val WheelMaxFlingItems = 6.5f

@Composable
fun DurationPicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentHour = (minutes / 60).coerceIn(0, 24)
    val currentMinute = if (currentHour == 24) 0 else (minutes % 60).coerceIn(0, 59)

    var hourInputText by remember { mutableStateOf(currentHour.toString()) }
    var minuteInputText by remember { mutableStateOf(String.format("%02d", currentMinute)) }

    val pickerVisibility = remember { MutableTransitionState(false) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    var expandsAbove by remember { mutableStateOf(false) }
    var selectedHourIndex by remember { mutableIntStateOf(currentHour) }
    var selectedMinuteIndex by remember { mutableIntStateOf(currentMinute) }
    val reducedMotion = LocalReducedMotion.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (pickerVisibility.targetState) 180f else 0f,
        animationSpec = tween(motionMillis(AppMotion.Short), easing = AppMotion.Ease),
        label = "duration-picker-arrow"
    )

    LaunchedEffect(minutes) {
        val h = (minutes / 60).coerceIn(0, 24)
        val m = if (h == 24) 0 else (minutes % 60).coerceIn(0, 59)
        hourInputText = h.toString()
        minuteInputText = String.format("%02d", m)
        if (!pickerVisibility.targetState && !pickerVisibility.currentState) {
            selectedHourIndex = h
            selectedMinuteIndex = m
        }
    }

    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BoxWithConstraints(modifier = modifier.wrapContentSize()) {
        val fontScale = density.fontScale
        val requiredWidthForSingleLine = 150.dp + 120.dp * fontScale
        val isMultiline = maxWidth < requiredWidthForSingleLine || fontScale >= 1.5f

        val wheelItemHeight = (36.dp * fontScale.coerceIn(1f, 1.35f)).coerceIn(36.dp, 48.dp)
        val popupHeightEstimate = wheelItemHeight * 5 + 128.dp * fontScale.coerceIn(1f, 1.8f)
        val verticalSpec = if (reducedMotion) {
            tween<IntSize>(0)
        } else {
            spring<IntSize>(dampingRatio = 0.9f, stiffness = 520f)
        }
        val enterTransition = expandVertically(
            expandFrom = if (expandsAbove) Alignment.Bottom else Alignment.Top,
            animationSpec = verticalSpec,
        ) + fadeIn(tween(motionMillis(120), easing = AppMotion.Ease))
        val exitTransition = shrinkVertically(
            shrinkTowards = if (expandsAbove) Alignment.Bottom else Alignment.Top,
            animationSpec = verticalSpec,
        ) + fadeOut(tween(motionMillis(AppMotion.Short), easing = AppMotion.Ease))

        val hoursList = remember { (0..24).toList() }
        val allMinutes = remember { (0..59).toList() }
        val minutesList = if (selectedHourIndex == 24) listOf(0) else allMinutes
        LaunchedEffect(minutesList.size) {
            selectedMinuteIndex = selectedMinuteIndex.coerceIn(0, minutesList.lastIndex)
        }

        val inputControls = @Composable {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .onGloballyPositioned { anchorBounds = it.boundsInWindow() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    DurationNumberInputBox(
                        valueText = hourInputText,
                        onValueTextChange = { newDigits ->
                            hourInputText = newDigits
                            val h = (newDigits.toIntOrNull() ?: 0).coerceIn(0, 24)
                            val m = if (h == 24) 0 else (minuteInputText.toIntOrNull() ?: 0).coerceIn(0, 59)
                            onMinutesChange(h * 60 + m)
                        },
                        unitLabel = "小时"
                    )
                    Spacer(Modifier.width(8.dp))
                    DurationNumberInputBox(
                        valueText = minuteInputText,
                        onValueTextChange = { newDigits ->
                            minuteInputText = newDigits
                            val h = (hourInputText.toIntOrNull() ?: 0).coerceIn(0, 24)
                            val m = if (h == 24) 0 else (newDigits.toIntOrNull() ?: 0).coerceIn(0, 59)
                            onMinutesChange(h * 60 + m)
                        },
                        unitLabel = "分钟"
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (!pickerVisibility.targetState) {
                                    selectedHourIndex = currentHour
                                    selectedMinuteIndex = currentMinute
                                }
                                pickerVisibility.targetState = !pickerVisibility.targetState
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = if (pickerVisibility.targetState) "收起目标时长选择器" else "展开目标时长选择器"
                                stateDescription = if (pickerVisibility.targetState) "已展开" else "已收起"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = arrowRotation }
                        ) {
                            val chevron = Path().apply {
                                moveTo(size.width * 0.22f, size.height * 0.38f)
                                lineTo(size.width * 0.5f, size.height * 0.66f)
                                lineTo(size.width * 0.78f, size.height * 0.38f)
                            }
                            drawPath(
                                chevron,
                                color = AppColors.Muted,
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                if (pickerVisibility.currentState || pickerVisibility.targetState) {
                    Popup(
                        popupPositionProvider = remember(density, popupHeightEstimate) {
                            DurationPickerPositionProvider(density, estimatedHeightDp = popupHeightEstimate)
                        },
                        onDismissRequest = { pickerVisibility.targetState = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        AnimatedVisibility(
                            visibleState = pickerVisibility,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AppColors.Raised,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, AppColors.Border),
                                modifier = Modifier
                                    .widthIn(min = 280.dp, max = 340.dp)
                                    .onGloballyPositioned { coordinates ->
                                        val anchor = anchorBounds
                                        if (anchor != null) {
                                            val above = coordinates.boundsInWindow().center.y < anchor.top
                                            if (above != expandsAbove) expandsAbove = above
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "选择目标时长",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = AppColors.Text,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "小时",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.Muted,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            WheelColumn(
                                                items = hoursList,
                                                selectedIndex = selectedHourIndex,
                                                onSelectedIndexChanged = {
                                                    selectedHourIndex = it
                                                    if (it == 24) selectedMinuteIndex = 0
                                                },
                                                formatItem = { it.toString() },
                                                itemHeight = wheelItemHeight,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Text(
                                            ":",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = AppColors.Muted,
                                            modifier = Modifier.padding(horizontal = 8.dp).padding(top = 18.dp)
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "分钟",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.Muted,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            WheelColumn(
                                                items = minutesList,
                                                selectedIndex = selectedMinuteIndex.coerceIn(0, minutesList.lastIndex),
                                                onSelectedIndexChanged = { selectedMinuteIndex = it },
                                                formatItem = { String.format("%02d", it) },
                                                itemHeight = wheelItemHeight,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { pickerVisibility.targetState = false }) {
                                            Text("取消", color = AppColors.Muted)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val h = hoursList.getOrElse(selectedHourIndex) { 0 }
                                                val m = if (h == 24) 0 else minutesList.getOrElse(selectedMinuteIndex) { 0 }
                                                val total = h * 60 + m
                                                onMinutesChange(total)
                                                hourInputText = h.toString()
                                                minuteInputText = String.format("%02d", m)
                                                pickerVisibility.targetState = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                                        ) {
                                            Text("确定", color = AppColors.AccentInk, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isMultiline) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "目标时长",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppColors.Text,
                )
                inputControls()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    "目标时长",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppColors.Text,
                )
                Spacer(Modifier.width(10.dp))
                inputControls()
            }
        }

    }
}
