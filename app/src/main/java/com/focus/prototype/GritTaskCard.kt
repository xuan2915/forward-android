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
        MaterialTheme.colorScheme.onSurface,
        tween(motionMillis()), label = "grit-content",
    )
    val cardContainer by animateColorAsState(
        when {
            active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .48f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 15.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CompletionMark(task.done)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                )
                Text(
                    "${if (task.required) "必做" else "选做"}  ·  ${if (task.minutes > 0) "${task.minutes}分钟" else "自由专注"}  ·  $elapsed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!task.done) {
                Surface(
                    onClick = onFocusToggle,
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { PlayPauseMorph(if (active) 1f else 0f, 42) }
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
