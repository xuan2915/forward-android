package com.focus.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

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
    private val marginDp: Dp = 8.dp
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

        val y = if (spaceBelow >= popupContentSize.height || spaceBelow >= spaceAbove) {
            anchorBounds.bottom + marginPx
        } else {
            anchorBounds.top - popupContentSize.height - marginPx
        }

        val x = anchorBounds.left.coerceIn(
            marginPx,
            (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        )
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
        BasicTextField(
            value = valueText,
            onValueChange = { newText ->
                val digits = newText.filter(Char::isDigit).take(2)
                onValueTextChange(digits)
            },
            modifier = Modifier.width(inputWidth),
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
        Spacer(Modifier.width(4.dp))
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Muted
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.size - 1))
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow {
            val offset = listState.firstVisibleItemScrollOffset
            val index = listState.firstVisibleItemIndex
            if (offset > itemHeightPx / 2) index + 1 else index
        }.collect { rawIndex ->
            val clamped = rawIndex.coerceIn(0, items.size - 1)
            onSelectedIndexChanged(clamped)
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItemsCount),
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

        val verticalPadding = itemHeight * ((visibleItemsCount - 1) / 2)

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatItem(item),
                        style = if (isSelected) {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AppColors.Text
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = AppColors.Muted.copy(alpha = 0.45f)
                            )
                        }
                    )
                }
            }
        }
    }
}

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

    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(minutes) {
        val h = (minutes / 60).coerceIn(0, 24)
        val m = if (h == 24) 0 else (minutes % 60).coerceIn(0, 59)
        hourInputText = h.toString()
        minuteInputText = String.format("%02d", m)
    }

    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BoxWithConstraints(modifier = modifier.wrapContentSize()) {
        val fontScale = density.fontScale
        val requiredWidthForSingleLine = 150.dp + 120.dp * fontScale
        val isMultiline = maxWidth < requiredWidthForSingleLine || fontScale >= 1.5f

        val arrowBoxSize = (36.dp * fontScale.coerceIn(1f, 1.35f)).coerceIn(36.dp, 48.dp)

        val inputControls = @Composable {
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
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(arrowBoxSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Surface)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isExpanded = !isExpanded
                        }
                        .semantics {
                            contentDescription = "选择目标时长"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "⌃" else "⌄",
                        color = AppColors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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

        if (isExpanded) {
            val hoursList = remember { (0..24).toList() }
            val minutesList = remember { (0..59).toList() }

            var selectedHourIndex by remember { mutableIntStateOf(currentHour) }
            var selectedMinuteIndex by remember { mutableIntStateOf(currentMinute) }

            val wheelItemHeight = (36.dp * fontScale.coerceIn(1f, 1.35f)).coerceIn(36.dp, 48.dp)

            Popup(
                popupPositionProvider = remember(density) { DurationPickerPositionProvider(density) },
                onDismissRequest = { isExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.Raised,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, AppColors.Border),
                    modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
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
                                    onSelectedIndexChanged = { selectedHourIndex = it },
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
                                    selectedIndex = selectedMinuteIndex,
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
                            TextButton(
                                onClick = { isExpanded = false }
                            ) {
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
                                    isExpanded = false
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
