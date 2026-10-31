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
// Adapted from GritTheme.android.kt: Android dark Material You branch.
// MaterialExpressiveTheme/MotionScheme require newer Material3; existing MaterialTheme
// hosts the same scheme/typography and our compatible motion tokens instead.
package com.focus.prototype
import android.os.Build
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
@Composable fun GritTheme(content:@Composable ()->Unit) {
 val context=LocalContext.current
 val colorScheme=if(Build.VERSION.SDK_INT>=31) dynamicDarkColorScheme(context) else darkColorScheme()
 SideEffect { (context as? android.app.Activity)?.window?.let { it.statusBarColor=colorScheme.background.toArgb();it.navigationBarColor=colorScheme.background.toArgb() } }
 AppColors.apply(colorScheme)
 MaterialTheme(colorScheme=colorScheme,typography=provideTypography(),content=content)
}
