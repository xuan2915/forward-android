package com.focus.prototype

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The five times are one ordered evening-to-morning cycle, independent of task/reward day boundaries. */
data class NightWrapUpConfig(
    val windDown: String = "22:00",
    val workCutoff: String = "23:30",
    val entertainmentCutoff: String = "00:00",
    val bedtime: String = "00:30",
    val morningRelease: String = "07:30",
    val penaltyCapMinutes: Int = 10,
    val penaltyCapGames: Int = 1,
) {
    fun times(): List<LocalTime> = listOf(windDown, workCutoff, entertainmentCutoff, bedtime, morningRelease).map(LocalTime::parse)
    fun validate(): NightWrapUpConfig {
        val minutes = times().map { it.hour * 60 + it.minute }
        var previous = 0
        minutes.forEachIndexed { index, minute ->
            var offset = (minute - minutes.first() + 1440) % 1440
            if (index == 0) offset = 0
            require(offset > previous || index == 0) { "时间应按收尾、工作、娱乐、就寝、早晨的顺序排列" }
            previous = offset
        }
        require(previous in 1..1080) { "夜间时段不能超过 18 小时" }
        require(penaltyCapMinutes in 0..20) { "单夜抵扣上限需在 0 到 20 分钟之间" }
        require(penaltyCapGames in 0..3) { "单夜游戏抵扣上限需在 0 到 3 局之间" }
        return this
    }

    fun at(anchor: LocalDate, stage: NightStage): LocalDateTime {
        validate()
        require(stage.order in 0..4) { "延长结束时间由实际延长动作单独计算" }
        val times = times()
        val index = stage.order
        val base = times.first().hour * 60 + times.first().minute
        val value = times[index].hour * 60 + times[index].minute
        val offset = if (index == 0) 0 else (value - base + 1440) % 1440
        return anchor.atStartOfDay().plusMinutes((base + offset).toLong())
    }

    fun cycleDate(now: LocalDateTime): LocalDate {
        validate()
        return if (now.toLocalTime().isBefore(times().first())) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    fun entertainmentRestricted(now: LocalDateTime): Boolean {
        val anchor = cycleDate(now)
        return !now.isBefore(at(anchor, NightStage.ENTERTAINMENT_CUTOFF)) && now.isBefore(at(anchor, NightStage.MORNING_RELEASE))
    }

    fun latestFinishedCycle(now: LocalDateTime): LocalDate {
        val anchor = cycleDate(now)
        return if (now >= at(anchor, NightStage.MORNING_RELEASE)) anchor else anchor.minusDays(1)
    }
}

enum class NightStage(val order: Int, val label: String) {
    WIND_DOWN(0, "开始收尾"), WORK_CUTOFF(1, "停止新工作"), ENTERTAINMENT_CUTOFF(2, "娱乐截止"), BEDTIME(3, "准备就寝"), MORNING_RELEASE(4, "早晨解除限制"), WORK_EXTENSION_END(5, "延长结束");
}

data class NightViolation(
    val id: String,
    val cycleDate: String,
    val packageName: String,
    val observedAt: Long,
    val rewardKind: String = "video",
    val reservedAmount: Long = 0L,
)
