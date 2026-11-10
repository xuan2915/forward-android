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
// Retains Grit's Material type scale while using the Android system sans face for
// clearer Chinese fallback and a quieter native rhythm.
package com.focus.prototype
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
val TYPOGRAPHY = Typography()
/** function to get custom typography for Grit */
@Composable fun provideTypography(font: Int = R.font.poppins_regular): Typography {
    val selectedFont = FontFamily.SansSerif

    return Typography(
        displayLarge = TYPOGRAPHY.displayLarge.copy(fontFamily = selectedFont),
        displayMedium = TYPOGRAPHY.displayMedium.copy(fontFamily = selectedFont),
        displaySmall = TYPOGRAPHY.displaySmall.copy(fontFamily = selectedFont),
        headlineLarge = TYPOGRAPHY.headlineLarge.copy(fontFamily = selectedFont),
        headlineMedium = TYPOGRAPHY.headlineMedium.copy(fontFamily = selectedFont),
        headlineSmall = TYPOGRAPHY.headlineSmall.copy(fontFamily = selectedFont),
        titleLarge = TYPOGRAPHY.titleLarge.copy(fontFamily = selectedFont),
        titleMedium = TYPOGRAPHY.titleMedium.copy(fontFamily = selectedFont),
        titleSmall = TYPOGRAPHY.titleSmall.copy(fontFamily = selectedFont),
        bodyLarge = TYPOGRAPHY.bodyLarge.copy(fontFamily = selectedFont),
        bodyMedium = TYPOGRAPHY.bodyMedium.copy(fontFamily = selectedFont),
        bodySmall = TYPOGRAPHY.bodySmall.copy(fontFamily = selectedFont),
        labelLarge = TYPOGRAPHY.labelLarge.copy(fontFamily = selectedFont),
        labelMedium = TYPOGRAPHY.labelMedium.copy(fontFamily = selectedFont),
        labelSmall = TYPOGRAPHY.labelSmall.copy(fontFamily = selectedFont),
    )
}

/** Narrower, less inflated display treatment for labels carried over from Grit pages. */
@OptIn(ExperimentalTextApi::class)
@Composable fun gritFontEmphasis(slant: Float = 0f): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(760),
            FontVariation.slant(slant),
            FontVariation.width(106f),
        ),
    ),
)

@OptIn(ExperimentalTextApi::class)
@Composable fun gritFontRounded(): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(680),
            FontVariation.Setting("ROND", 35f),
        ),
    ),
)
