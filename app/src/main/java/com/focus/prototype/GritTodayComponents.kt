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
// Page structure adapted from Grit shared/ui/habit/ui/sections/HabitsList.kt.
package com.focus.prototype

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GritTodayHeader(date: String, completed: Int, total: Int, onSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("今日任务", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
            TextButton(onClick = onSettings) { Text("设置") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MotionCounter(completed, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            Text("/ $total 项已完成", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GritTaskFilters(selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "全部", 1 to "必做", 2 to "选做").forEach { (value, label) ->
            val active = selected == value
            Surface(
                onClick = { onSelected(value) },
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(label, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun GritEntertainmentBanner(
    protected: Boolean,
    emergency: Boolean,
    eligible: Boolean,
    videoMillis: Long,
    onClick: () -> Unit,
) {
    val unlocked = protected && eligible && !emergency
    val color = when {
        emergency -> MaterialTheme.colorScheme.errorContainer
        unlocked -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val ink = when {
        emergency -> MaterialTheme.colorScheme.onErrorContainer
        unlocked -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = color, contentColor = ink) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("娱乐时间", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    when {
                        !protected -> "应用保护未开启 · 点击前往设置"
                        emergency -> "紧急解锁中 · 可随时恢复保护"
                        unlocked -> "已解锁 · 抖音与 B站共用额度"
                        else -> "尚未解锁 · 完成必做任务后可用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = .82f),
                )
            }
            Text(
                if (unlocked || emergency) "${videoMillis / 60_000} 分钟" else if (!protected) "设置" else "锁定",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            )
        }
    }
}

@Composable
fun GritTaskSectionHeader(title: String, done: Int, total: Int, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text("$done / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAdd != null) TextButton(onClick = onAdd) { Text("＋ 添加") }
    }
}

@Composable
fun GritEmptyTasks(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(26.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
