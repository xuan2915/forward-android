package com.focus.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Background = Color(0xFF0E1110)
    val Surface = Color(0xFF171B19)
    val Raised = Color(0xFF202522)
    val Border = Color(0xFF2C332F)
    val Text = Color(0xFFF0F2ED)
    val Muted = Color(0xFF9AA39C)
    val Quiet = Color(0xFF6F7972)
    val Accent = Color(0xFFA8BBAA)
    val AccentInk = Color(0xFF172019)
    val AccentWash = Color(0xFF202B24)
    val Warning = Color(0xFFD59A8E)
}

object AppSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 20.dp
    val XLarge = 28.dp
    val Page = 24.dp
}

object AppMotion {
    const val Short = 160
    const val Standard = 260
}

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 60.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontSize = 46.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            onPrimary = AppColors.AccentInk,
            primaryContainer = AppColors.AccentWash,
            onPrimaryContainer = AppColors.Text,
            secondary = AppColors.Accent,
            onSecondary = AppColors.AccentInk,
            secondaryContainer = AppColors.AccentWash,
            onSecondaryContainer = AppColors.Accent,
            background = AppColors.Background,
            onBackground = AppColors.Text,
            surface = AppColors.Surface,
            onSurface = AppColors.Text,
            surfaceVariant = AppColors.Raised,
            onSurfaceVariant = AppColors.Muted,
            outline = AppColors.Border,
            outlineVariant = AppColors.Border,
            error = AppColors.Warning,
        ),
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}

@Composable
fun PageHeading(kicker: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
        Spacer(Modifier.height(AppSpace.Small))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = AppColors.Text)
    }
}

@Composable
fun SectionHeading(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
        trailing?.invoke()
    }
}

@Composable
fun QuietSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppColors.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Medium),
        content = content,
    )
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        modifier = modifier.height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.AccentInk,
            disabledContainerColor = AppColors.Raised,
            disabledContentColor = AppColors.Quiet,
        ),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) AppColors.Border else AppColors.Raised),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Text, disabledContentColor = AppColors.Quiet),
    ) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

@Composable
fun StatusPill(text: String, active: Boolean = false) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) AppColors.AccentWash else AppColors.Raised).padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) AppColors.Accent else AppColors.Muted,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
}
