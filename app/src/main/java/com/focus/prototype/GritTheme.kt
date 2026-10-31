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
// Adapted from GritTheme.android.kt: its fixed seed color, TONALSPOT scheme, and
// Figtree typography. MaterialExpressiveTheme requires a newer Material3 than this app uses.
package com.focus.prototype
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.ui.platform.LocalContext
@Composable fun GritTheme(content:@Composable ()->Unit) {
 val context=LocalContext.current
 // Grit's default Theme uses a fixed white seed with TONALSPOT, not the device wallpaper.
 val colorScheme=rememberDynamicColorScheme(seedColor=Color(0xFFFFFFFF),isDark=true,isAmoled=false,style=PaletteStyle.TonalSpot)
 androidx.compose.runtime.SideEffect { (context as? android.app.Activity)?.window?.let { it.statusBarColor=android.graphics.Color.rgb(17,17,17);it.navigationBarColor=android.graphics.Color.rgb(17,17,17) } }
 AppColors.apply(colorScheme)
 MaterialTheme(colorScheme=colorScheme,typography=provideTypography(),content=content)
}
