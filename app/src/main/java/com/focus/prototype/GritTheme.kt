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
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.ui.platform.LocalContext
enum class VisualDirection(val key: String, val title: String, val seed: Color) {
 Warm("warm", "暖陶与纸墨", Color(0xFFD19B7B)),
 Mineral("mineral", "雾蓝与石墨", Color(0xFF9EB4D1)),
 Grit("grit", "苔绿与墨色", Color(0xFFA5D395));

 companion object {
  fun fromKey(key: String?): VisualDirection = entries.firstOrNull { it.key == key } ?: Warm
 }
}

@Composable fun GritTheme(direction: VisualDirection = VisualDirection.Warm, content:@Composable ()->Unit) {
 val context=LocalContext.current
 val colorScheme=rememberDynamicColorScheme(seedColor=direction.seed,isDark=true,isAmoled=false,style=PaletteStyle.TonalSpot)
 androidx.compose.runtime.SideEffect { (context as? android.app.Activity)?.window?.useDarkSystemBars() }
 AppColors.apply(colorScheme)
 val shapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
 )
 MaterialTheme(colorScheme=colorScheme,typography=provideTypography(),shapes=shapes,content=content)
}
