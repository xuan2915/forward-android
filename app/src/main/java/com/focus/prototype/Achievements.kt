package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal data class Achievement(val category:String,val title:String,val condition:String,val progress:Int,val target:Int,val earnedDate:String?) {
    val earned get()=progress>=target
}

internal fun achievementList(db:Database):List<Achievement> {
    val completedDates=mutableListOf<String>()
    db.readableDatabase.rawQuery("SELECT day FROM tasks WHERE done=1 AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day,rowid",null).use{while(it.moveToNext())completedDates.add(it.getString(0))}
    val focusByDay=linkedMapOf<String,Long>()
    db.readableDatabase.rawQuery("SELECT day,COALESCE(SUM(elapsed),0) FROM tasks WHERE id NOT IN (SELECT source_id FROM task_moves) GROUP BY day ORDER BY day",null).use{while(it.moveToNext())focusByDay[it.getString(0)]=it.getLong(1)}
    db.runningTasks().forEach{task->focusByDay[task.date]=(focusByDay[task.date]?:0)+(db.elapsed(task)-task.elapsed).coerceAtLeast(0)}
    val focusMinutes=focusByDay.values.sum()/60000
    fun taskDate(target:Int)=completedDates.getOrNull(target-1)
    fun focusDate(target:Int):String? {var sum=0L;for((date,millis) in focusByDay){sum+=millis;if(sum>=target*60000L)return date};return null}
    fun streakDate(target:Int):String? {
        var run=0;var previous:LocalDate?=null
        for((date,state) in db.history().sortedBy{it.first}) {
            val current=LocalDate.parse(date)
            if(previous!=null&&current!=previous!!.plusDays(1))run=0
            when(state){"完成"->{run++;if(run>=target)return date};"休息"->Unit;else->run=0}
            previous=current
        }
        return null
    }
    val streak=db.bestStreak()
    return listOf(
        Achievement("任务完成","第一步","完成第一项任务",completedDates.size.coerceAtMost(1),1,taskDate(1)),
        Achievement("任务完成","十项完成","累计完成 10 项任务",completedDates.size.coerceAtMost(10),10,taskDate(10)),
        Achievement("任务完成","五十项完成","累计完成 50 项任务",completedDates.size.coerceAtMost(50),50,taskDate(50)),
        Achievement("连续坚持","连做三天","连续三个自律日完成目标",streak.coerceAtMost(3),3,streakDate(3)),
        Achievement("连续坚持","稳定一周","连续七个自律日完成目标",streak.coerceAtMost(7),7,streakDate(7)),
        Achievement("连续坚持","坚持一月","连续三十个自律日完成目标",streak.coerceAtMost(30),30,streakDate(30)),
        Achievement("专注积累","专注一小时","累计有效专注 60 分钟",focusMinutes.toInt().coerceAtMost(60),60,focusDate(60)),
        Achievement("专注积累","专注十小时","累计有效专注 10 小时",focusMinutes.toInt().coerceAtMost(600),600,focusDate(600)),
    )
}

@Composable
internal fun AchievementPreview(db:Database,refreshKey:Int,onAll:()->Unit) {
    val items=remember(refreshKey){achievementList(db)}
    val visible=items.filter{it.earned||it.progress>0}.sortedWith(compareBy<Achievement>{if(it.earned)0 else 1}.thenBy{it.target-it.progress}).take(3).ifEmpty{items.take(3)}
    GritAnalyticsHeader("成长成就",icon=R.drawable.grit_flag)
    QuietSurface {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("已获得 ${items.count{it.earned}} / ${items.size}",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted,modifier=Modifier.weight(1f))
            TextButton(onClick=onAll){Text("查看全部成就")}
        }
        visible.forEach{AchievementCard(it)}
    }
}

@Composable
internal fun AchievementsPage(db:Database,refreshKey:Int,onBack:()->Unit) {
    val items=remember(refreshKey){achievementList(db)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=AppSpace.Page).padding(top=AppSpace.XLarge,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(AppSpace.Large)) {
        TextButton(onClick=onBack){Text("‹ 返回成长")}
        PageHeading("真实记录 · 持续积累","全部成就")
        Text("已获得 ${items.count{it.earned}} 项 · 共 ${items.size} 项",style=MaterialTheme.typography.bodyMedium,color=AppColors.Muted)
        listOf("任务完成","连续坚持","专注积累").forEach { category ->
            val group=items.filter{it.category==category}
            GritAnalyticsHeader(category,icon=if(category=="连续坚持")R.drawable.grit_flag else R.drawable.grit_chart)
            val earned=group.filter{it.earned}
            if(earned.isNotEmpty()) {
                Text("已获得",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted)
                earned.forEach{AchievementCard(it)}
            }
            val locked=group.filterNot{it.earned}
            if(locked.isNotEmpty()) {
                Text("待解锁",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted)
                locked.forEach{AchievementCard(it)}
            }
        }
    }
}

@Composable
private fun AchievementCard(item:Achievement) {
    val badgeColor by animateColorAsState(if(item.earned) AppColors.Accent else MaterialTheme.colorScheme.surfaceContainerHighest,label="achievement-badge")
    val progress by animateIntAsState(item.progress,label="achievement-progress")
    QuietSurface(Modifier.animateContentSize(),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.size(38.dp).clip(CircleShape),color=badgeColor,shape=CircleShape) {
                androidx.compose.foundation.layout.Box(contentAlignment=Alignment.Center){AnimatedContent(item.earned,transitionSpec={fadeIn(tween(AppMotion.Short)) togetherWith fadeOut(tween(AppMotion.Short))},label="achievement-unlock"){earned->Text(if(earned)"✓" else "·",color=if(earned)AppColors.AccentInk else AppColors.Muted,style=MaterialTheme.typography.titleMedium)}}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(item.title,style=MaterialTheme.typography.titleSmall)
                    Text(if(item.earned)"已获得" else "$progress / ${item.target}",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
                }
                Text(item.condition,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
                if(item.earned) Text("获得于 ${item.earnedDate}",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
                else LinearProgressIndicator(progress={progress.toFloat()/item.target},modifier=Modifier.fillMaxWidth(),color=AppColors.Accent,trackColor=AppColors.Raised)
            }
        }
    }
}
