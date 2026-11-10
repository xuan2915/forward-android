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
 var Background = darkColorScheme().background
 var Surface = darkColorScheme().surfaceContainer
 var Raised = darkColorScheme().surfaceContainerHigh
 var Border = darkColorScheme().outlineVariant
 var Text = darkColorScheme().onSurface
 var Muted = darkColorScheme().onSurfaceVariant
 var Quiet = darkColorScheme().onSurfaceVariant
 var Accent = darkColorScheme().primary
 var AccentInk = darkColorScheme().onPrimary
 var AccentWash = darkColorScheme().primaryContainer
 var Warning = darkColorScheme().error
 // Calendar misses use a low-saturation rose so they read as history, not an alert.
 val Missed = Color(0xFF49373B)
 val MissedInk = Color(0xFFC69A9F)
 fun apply(s:androidx.compose.material3.ColorScheme) {
Background=s.background
Surface=s.surfaceContainer
Raised=s.surfaceContainerHigh
Border=s.outlineVariant
Text=s.onSurface
Muted=s.onSurfaceVariant
Quiet=s.onSurfaceVariant
Accent=s.primary
AccentInk=s.onPrimary
AccentWash=s.primaryContainer
Warning=s.error
 }
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
fun FocusTheme(direction: VisualDirection = VisualDirection.Warm, content: @Composable () -> Unit) {
    GritTheme(direction, content)
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
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AppShape.Card))
            .background(color)
            .border(0.7.dp, AppColors.Border.copy(alpha = 0.34f), RoundedCornerShape(AppShape.Card))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && !LocalReducedMotion.current) .975f else 1f,spring(dampingRatio=AppMotion.Damping,stiffness=AppMotion.Stiffness),label="press")
    Button(
        interactionSource=interaction,
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = modifier.height(52.dp).graphicsLayer { scaleX=scale;scaleY=scale },
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
        modifier = modifier.height(48.dp),
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
