/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
// Adapted from GritTheme.android.kt. The app now owns its palette; the Grit layout
// and Compose theme integration remain GPL-3.0-or-later adaptations.
package com.focus.prototype
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
@Composable
fun GritTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = darkColorScheme(
        background = AppColors.Background, onBackground = AppColors.Text,
        surface = AppColors.Surface, onSurface = AppColors.Text,
        surfaceVariant = AppColors.Raised, onSurfaceVariant = AppColors.Muted,
        surfaceDim = AppColors.Background, surfaceBright = AppColors.Raised,
        surfaceContainerLowest = AppColors.Background, surfaceContainerLow = AppColors.Surface,
        surfaceContainer = AppColors.Surface, surfaceContainerHigh = AppColors.Raised,
        surfaceContainerHighest = AppColors.Raised,
        primary = AppColors.Accent, onPrimary = AppColors.AccentInk,
        primaryContainer = AppColors.AccentWash, onPrimaryContainer = AppColors.Text,
        secondary = AppColors.Reward, onSecondary = AppColors.Background,
        secondaryContainer = AppColors.Raised, onSecondaryContainer = AppColors.Text,
        tertiary = AppColors.Warning, onTertiary = AppColors.Background,
        tertiaryContainer = AppColors.Missed, onTertiaryContainer = AppColors.Text,
        error = AppColors.Warning, onError = AppColors.Background,
        errorContainer = AppColors.Missed, onErrorContainer = AppColors.Text,
        outline = AppColors.Muted, outlineVariant = AppColors.Border,
        inverseSurface = AppColors.Paper, inverseOnSurface = AppColors.PaperInk,
        inversePrimary = AppColors.AccentWash, surfaceTint = AppColors.Accent,
    )
    androidx.compose.runtime.SideEffect {
        (context as? android.app.Activity)?.window?.useDarkSystemBars()
    }
    MaterialTheme(
        colorScheme = palette, typography = provideTypography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(AppShape.Control), large = RoundedCornerShape(AppShape.Card),
            extraLarge = RoundedCornerShape(AppShape.Panel),
        ),
        content = content,
    )
}
