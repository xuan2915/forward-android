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
// Adapted from task/ui/component/TaskCard.kt: existing Task and verification callbacks.
package com.focus.prototype
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
@Composable fun GritTaskHeader(task:Task,onTap:()->Unit) {
 val cardContent by animateColorAsState(if(task.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,tween(motionMillis()),label="grit-content")
 val cardContainer by animateColorAsState(if(task.done) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,tween(motionMillis()),label="grit-card")
 Card(Modifier.fillMaxWidth().clickable(onClick=onTap),colors=CardDefaults.cardColors(containerColor=cardContainer,contentColor=cardContent),shape=detachedItemShape()) {
  Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
   Column(Modifier.weight(1f)) {
    Text(task.title,style=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Bold),textDecoration=if(task.done) TextDecoration.LineThrough else TextDecoration.None)
    Text(if(task.required) "必做任务" else "选做任务",style=MaterialTheme.typography.labelSmall)
   }
   CompletionMark(task.done)
  }
 }
}
