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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
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
    val Background = Color(0xFF13171C)
    val Surface = Color(0xFF1F252C)
    val Raised = Color(0xFF2A323A)
    val Text = Color(0xFFF4F1EB)
    val Muted = Color(0xFFA9B1B9)
    val Quiet = Muted
    val Border = Color(0xFF454E57)
    val Accent = Color(0xFF8EADA8)
    val AccentInk = Background
    val AccentWash = Color(0xFF293B3C)
    val Reward = Color(0xFFD5AB80)
    val Warning = Color(0xFFC88978)
    // Quiet slate keeps missed days distinct from completion mint without a red/green clash.
    val Missed = Color(0xFF3C4349)
    val MissedInk = Color(0xFFB4B6B4)
    val Paper = Color(0xFFF2EBDD)
    val PaperInk = Color(0xFF292520)
    val PaperMuted = Color(0xFF61574B)
    val PaperRule = Color(0xFFC6BAA7)
}

object AppSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val Page = 22.dp
}

object AppShape {
    val Card = 22.dp
    val Panel = 28.dp
    val Control = 16.dp
    val Pill = 50.dp
}

object AppMotion {
    const val Short = 160
    const val Standard = 260
    const val Growth = 1000
    val Ease = androidx.compose.animation.core.CubicBezierEasing(0.2f,0f,0f,1f)
    const val Damping = 1f
    const val Stiffness = 500f
}

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    GritTheme(content)
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
fun QuietSurface(modifier: Modifier = Modifier, color:Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AppShape.Card))
            .background(color)
            .border(0.7.dp, AppColors.Border.copy(alpha = 0.34f), RoundedCornerShape(AppShape.Card))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            val scope = this
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content(scope)
            }
        },
    )
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(AppShape.Control)).immediatePress(enabled),
        enabled = enabled,
        shape = RoundedCornerShape(AppShape.Control),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.AccentInk,
            disabledContainerColor = AppColors.Raised,
            disabledContentColor = AppColors.Quiet,
        ),
    ) { icon?.invoke(); if(icon!=null) Spacer(Modifier.padding(end=8.dp)); androidx.compose.animation.Crossfade(label,animationSpec=tween(motionMillis()),label="action-label") { Text(it, style = MaterialTheme.typography.titleMedium) } }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(AppShape.Control)).immediatePress(enabled),
        enabled = enabled,
        shape = RoundedCornerShape(AppShape.Control),
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
