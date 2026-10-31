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
// Adapted from Grit shared/ui/task/ui/component/TaskCard.kt.
package com.focus.prototype

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun GritTaskHeader(
    task: Task,
    elapsed: String,
    active: Boolean,
    expanded: Boolean,
    shape: Shape,
    onExpand: () -> Unit,
    onFocusToggle: () -> Unit,
) {
    val cardContent by animateColorAsState(
        when {
            task.done -> MaterialTheme.colorScheme.onSurface
            task.required -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        },
        tween(motionMillis()), label = "grit-content",
    )
    val cardContainer by animateColorAsState(
        when {
            task.done -> MaterialTheme.colorScheme.surfaceContainerHighest
            task.required -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        },
        tween(motionMillis()), label = "grit-card",
    )
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onExpand),
        colors = CardDefaults.cardColors(containerColor = cardContainer, contentColor = cardContent),
        shape = shape,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompletionMark(task.done)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                )
                Text(
                    "${if (task.required) "必做" else "选做"}  ·  ${if (task.minutes > 0) "目标 ${task.minutes} 分钟" else "自由专注"}  ·  $elapsed",
                    style = MaterialTheme.typography.labelMedium,
                    color = cardContent.copy(alpha = .78f),
                    maxLines = 1,
                )
            }
            if (!task.done) {
                Surface(
                    onClick = onFocusToggle,
                    shape = RoundedCornerShape(if (expanded) 16.dp else 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { PlayPauseMorph(if (active) 1f else 0f, 48) }
                }
            }
        }
    }
}

fun gritTaskShape(index: Int, count: Int, expanded: Boolean = false): Shape {
    val connected = if (expanded) 6 else 4
    return when {
        count <= 1 -> detachedItemShape(26)
        index == 0 -> leadingItemShape(topRadius = 26, bottomRadius = connected)
        index == count - 1 -> endItemShape(topRadius = connected, bottomRadius = 26)
        else -> middleItemShape(connected)
    }
}
