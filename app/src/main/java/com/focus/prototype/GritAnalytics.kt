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
// Adapted from habit/ui/component/AnalyticsCard.kt; paid-content branch removed.
package com.focus.prototype
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.painterResource
@Composable fun GritAnalyticsHeader(title:String,icon:Int?=null,header:(@Composable RowScope.()->Unit)={}) {
 Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp).heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically) {
  if(icon!=null) { Icon(painterResource(icon),null,tint=MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
  Text(title,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
  header()
 }
}
