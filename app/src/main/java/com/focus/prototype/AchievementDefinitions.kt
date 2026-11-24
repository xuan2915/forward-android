package com.focus.prototype

enum class AchievementKind {
    EVOLUTION,
    INDEPENDENT,
    CHALLENGE,
    SECRET,
}

enum class AchievementRarity(val label: String) {
    COMMON("普通"),
    UNCOMMON("进阶"),
    RARE("困难"),
    EPIC("史诗"),
    LEGENDARY("传说"),
}

enum class ReleaseStatus {
    AVAILABLE,
    PENDING_EVIDENCE,
    DRAFT,
    RETIRED,
}

enum class EvidencePolicy {
    AUTOMATIC,
    MANUAL,
    PENDING_EVIDENCE,
}

data class CollectionSlot(
    val slotId: String,
    val seriesId: String?,
    val kind: AchievementKind,
    val title: String,
    val totalMilestones: Int,
)

data class AchievementDefinition(
    val id: String,
    val seriesId: String?,
    val stage: Int?,
    val slotId: String,
    val kind: AchievementKind,
    val title: String,
    val description: String,
    val conditionDescription: String,
    val metric: String,
    val threshold: Long,
    val unit: String,
    val rarity: AchievementRarity,
    val releaseStatus: ReleaseStatus,
    val evidencePolicy: EvidencePolicy,
    val version: Int = 1,
    val pendingReason: String = "",
    val artId: String = "",
    val prerequisiteIds: List<String> = emptyList(),
)

object AchievementCatalog {

    val collectionSlots: List<CollectionSlot> = buildList {
        // 8 Evolution series slots (each folds 6 stages)
        add(CollectionSlot("slot.evolution.01.action", "evolution.01.action", AchievementKind.EVOLUTION, "行动的故事", 6))
        add(CollectionSlot("slot.evolution.02.all_clear", "evolution.02.all_clear", AchievementKind.EVOLUTION, "清单的终点", 6))
        add(CollectionSlot("slot.evolution.03.streak", "evolution.03.streak", AchievementKind.EVOLUTION, "时间的朋友", 6))
        add(CollectionSlot("slot.evolution.04.focus", "evolution.04.focus", AchievementKind.EVOLUTION, "专注的深度", 6))
        add(CollectionSlot("slot.evolution.05.deep_focus", "evolution.05.deep_focus", AchievementKind.EVOLUTION, "沉浸时刻", 6))
        add(CollectionSlot("slot.evolution.06.elective", "evolution.06.elective", AchievementKind.EVOLUTION, "计划之外", 6))
        add(CollectionSlot("slot.evolution.07.entertainment", "evolution.07.entertainment", AchievementKind.EVOLUTION, "娱乐的边界", 6))
        add(CollectionSlot("slot.evolution.08.circadian", "evolution.08.circadian", AchievementKind.EVOLUTION, "与昼夜和解", 6))

        // 30 Independent slots
        // Group 09: 执行的细节
        add(CollectionSlot("slot.indep.execution.write_first", null, AchievementKind.INDEPENDENT, "写在前面", 1))
        add(CollectionSlot("slot.indep.execution.eat_frog", null, AchievementKind.INDEPENDENT, "先啃硬骨头", 1))
        add(CollectionSlot("slot.indep.execution.not_leave_today", null, AchievementKind.INDEPENDENT, "不把今天留给明天", 1))
        add(CollectionSlot("slot.indep.execution.follow_through", null, AchievementKind.INDEPENDENT, "有始有终", 1))
        add(CollectionSlot("slot.indep.execution.withstand_review", null, AchievementKind.INDEPENDENT, "经得起回头看", 1))
        add(CollectionSlot("slot.indep.execution.every_page_counts", null, AchievementKind.INDEPENDENT, "每一页都算数", 1))

        // Group 10: 专注的质量
        add(CollectionSlot("slot.indep.focus.hour_for_myself", null, AchievementKind.INDEPENDENT, "留给自己的一小时", 1))
        add(CollectionSlot("slot.indep.focus.without_glance", null, AchievementKind.INDEPENDENT, "不看一眼", 1))
        add(CollectionSlot("slot.indep.focus.one_breath", null, AchievementKind.INDEPENDENT, "一口气做完", 1))
        add(CollectionSlot("slot.indep.focus.quiet_week", null, AchievementKind.INDEPENDENT, "安静的一周", 1))
        add(CollectionSlot("slot.indep.focus.reclaim_time", null, AchievementKind.INDEPENDENT, "把时间找回来", 1))
        add(CollectionSlot("slot.indep.focus.full_immersion", null, AchievementKind.INDEPENDENT, "全神贯注", 1))

        // Group 11: 娱乐的分寸
        add(CollectionSlot("slot.indep.entertainment.leave_margin", null, AchievementKind.INDEPENDENT, "留有余地", 1))
        add(CollectionSlot("slot.indep.entertainment.stop_on_will", null, AchievementKind.INDEPENDENT, "说停就停", 1))
        add(CollectionSlot("slot.indep.entertainment.duty_first", null, AchievementKind.INDEPENDENT, "先把正事办了", 1))
        add(CollectionSlot("slot.indep.entertainment.no_need_drain", null, AchievementKind.INDEPENDENT, "没必要刷满", 1))
        add(CollectionSlot("slot.indep.entertainment.one_game_enough", null, AchievementKind.INDEPENDENT, "一局就够", 1))
        add(CollectionSlot("slot.indep.entertainment.beyond_joy", null, AchievementKind.INDEPENDENT, "快乐之外", 1))

        // Group 12: 睡眠和早起
        add(CollectionSlot("slot.indep.sleep.sleep_first", null, AchievementKind.INDEPENDENT, "先睡了", 1))
        add(CollectionSlot("slot.indep.sleep.ahead_of_alarm", null, AchievementKind.INDEPENDENT, "比闹钟早一步", 1))
        add(CollectionSlot("slot.indep.sleep.catch_morning", null, AchievementKind.INDEPENDENT, "赶上了清晨", 1))
        add(CollectionSlot("slot.indep.sleep.save_energy_tomorrow", null, AchievementKind.INDEPENDENT, "给明天留点精神", 1))
        add(CollectionSlot("slot.indep.sleep.live_week_well", null, AchievementKind.INDEPENDENT, "好好过完这一周", 1))
        add(CollectionSlot("slot.indep.sleep.account_day_night", null, AchievementKind.INDEPENDENT, "早晚都有交代", 1))

        // Group 13: 生活的平衡
        add(CollectionSlot("slot.indep.balance.own_time", null, AchievementKind.INDEPENDENT, "各有各的时间", 1))
        add(CollectionSlot("slot.indep.balance.both_ends", null, AchievementKind.INDEPENDENT, "两头都顾上了", 1))
        add(CollectionSlot("slot.indep.balance.weekend_counts", null, AchievementKind.INDEPENDENT, "周末也算数", 1))
        add(CollectionSlot("slot.indep.balance.more_than_spark", null, AchievementKind.INDEPENDENT, "不止三分钟热度", 1))
        add(CollectionSlot("slot.indep.balance.no_bias", null, AchievementKind.INDEPENDENT, "没有偏科", 1))
        add(CollectionSlot("slot.indep.balance.answer_of_90_days", null, AchievementKind.INDEPENDENT, "九十天的答案", 1))

        // 18 Challenge slots
        // Group 14: 月度挑战篇章
        add(CollectionSlot("slot.challenge.monthly.not_just_trying", null, AchievementKind.CHALLENGE, "这次不是试试", 1))
        add(CollectionSlot("slot.challenge.monthly.month_answer", null, AchievementKind.CHALLENGE, "一个月的答案", 1))
        add(CollectionSlot("slot.challenge.monthly.not_by_luck", null, AchievementKind.CHALLENGE, "不靠运气", 1))
        add(CollectionSlot("slot.challenge.monthly.raise_difficulty", null, AchievementKind.CHALLENGE, "把难度调高", 1))
        add(CollectionSlot("slot.challenge.monthly.whole_season", null, AchievementKind.CHALLENGE, "一整季的坚持", 1))
        add(CollectionSlot("slot.challenge.monthly.twelve_chapters", null, AchievementKind.CHALLENGE, "十二个章节", 1))

        // Group 15: 探索与创造
        add(CollectionSlot("slot.challenge.explore.little_adventure", null, AchievementKind.CHALLENGE, "小小的冒险", 1))
        add(CollectionSlot("slot.challenge.explore.beyond_familiar", null, AchievementKind.CHALLENGE, "熟悉之外", 1))
        add(CollectionSlot("slot.challenge.explore.try_another_path", null, AchievementKind.CHALLENGE, "换条路看看", 1))
        add(CollectionSlot("slot.challenge.explore.map_unfinished", null, AchievementKind.CHALLENGE, "地图还没画完", 1))
        add(CollectionSlot("slot.challenge.explore.leave_something", null, AchievementKind.CHALLENGE, "留下点什么", 1))
        add(CollectionSlot("slot.challenge.explore.world_is_bigger", null, AchievementKind.CHALLENGE, "世界比想象中大", 1))

        // Group 16: 真正的突破
        add(CollectionSlot("slot.challenge.breakthrough.i_can_too", null, AchievementKind.CHALLENGE, "原来我也可以", 1))
        add(CollectionSlot("slot.challenge.breakthrough.restart_not_reset", null, AchievementKind.CHALLENGE, "重来，不等于重头", 1))
        add(CollectionSlot("slot.challenge.breakthrough.not_luck", null, AchievementKind.CHALLENGE, "不是碰巧", 1))
        add(CollectionSlot("slot.challenge.breakthrough.cross_hurdle", null, AchievementKind.CHALLENGE, "跨过那道坎", 1))
        add(CollectionSlot("slot.challenge.breakthrough.harder_question", null, AchievementKind.CHALLENGE, "更难的那一题", 1))
        add(CollectionSlot("slot.challenge.breakthrough.day_i_crossed", null, AchievementKind.CHALLENGE, "那天，我跨过去了", 1))

        // 12 Secret slots
        add(CollectionSlot("slot.secret.to_be_continued", null, AchievementKind.SECRET, "未完待续", 1))
        add(CollectionSlot("slot.secret.tension_and_relaxation", null, AchievementKind.SECRET, "有张有弛", 1))
        add(CollectionSlot("slot.secret.energy_for_tomorrow", null, AchievementKind.SECRET, "留给明天的力气", 1))
        add(CollectionSlot("slot.secret.double_happiness", null, AchievementKind.SECRET, "好事成双", 1))
        add(CollectionSlot("slot.secret.even_myself_surprised", null, AchievementKind.SECRET, "连自己都没想到", 1))
        add(CollectionSlot("slot.secret.unexpected_harvest", null, AchievementKind.SECRET, "意外收获", 1))
        add(CollectionSlot("slot.secret.self_aware_changes", null, AchievementKind.SECRET, "有些变化，自己知道", 1))
        add(CollectionSlot("slot.secret.everything_as_usual", null, AchievementKind.SECRET, "一切照常", 1))
        add(CollectionSlot("slot.secret.already_so_long", null, AchievementKind.SECRET, "原来已经这么久", 1))
        add(CollectionSlot("slot.secret.more_than_this_side", null, AchievementKind.SECRET, "不止这一面", 1))
        add(CollectionSlot("slot.secret.next_chapter_awaits", null, AchievementKind.SECRET, "还有下一章", 1))
        add(CollectionSlot("slot.secret.closet_of_stories", null, AchievementKind.SECRET, "一柜子的故事", 1))
    }

    val all108: List<AchievementDefinition> = buildList {
        // -------------------------------------------------------------
        // 1.1 八条进化成长线 (8 * 6 = 48)
        // -------------------------------------------------------------

        // 01 行动的故事 (6阶段)
        val s01 = "evolution.01.action"
        val slot01 = "slot.evolution.01.action"
        add(AchievementDefinition("achievement.evolution.action.stage_1", s01, 1, slot01, AchievementKind.EVOLUTION, "开场白", "完成第一项有效任务，迈出第一步", "累计完成 1 项有效任务", "tasks_completed", 1, "项", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_first"))
        add(AchievementDefinition("achievement.evolution.action.stage_2", s01, 2, slot01, AchievementKind.EVOLUTION, "不止说说而已", "行动正在成为一种稳定的习惯", "累计完成 30 项有效任务", "tasks_completed", 30, "项", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_ten"))
        add(AchievementDefinition("achievement.evolution.action.stage_3", s01, 3, slot01, AchievementKind.EVOLUTION, "渐渐有了章法", "任务积累初具规模，做事更有节奏", "累计完成 150 项有效任务", "tasks_completed", 150, "项", AchievementRarity.UNCOMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_fifty"))
        add(AchievementDefinition("achievement.evolution.action.stage_4", s01, 4, slot01, AchievementKind.EVOLUTION, "不再是偶然", "几百次的坚持证明了执行的确定性", "累计完成 600 项有效任务", "tasks_completed", 600, "项", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.action.stage_5", s01, 5, slot01, AchievementKind.EVOLUTION, "日子有了回声", "数千项行动沉淀为实实在在的成果", "累计完成 2000 项有效任务", "tasks_completed", 2000, "项", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.action.stage_6", s01, 6, slot01, AchievementKind.EVOLUTION, "故事还在继续", "六千次前行，自律已融入生命底色", "累计完成 6000 项有效任务", "tasks_completed", 6000, "项", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 02 清单的终点 (6阶段)
        val s02 = "evolution.02.all_clear"
        val slot02 = "slot.evolution.02.all_clear"
        add(AchievementDefinition("achievement.evolution.all_clear.stage_1", s02, 1, slot02, AchievementKind.EVOLUTION, "最后一项", "首次完成当日全部必做任务", "累计 1 个高质量必做全清日", "all_clear_days", 1, "天", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.all_clear.stage_2", s02, 2, slot02, AchievementKind.EVOLUTION, "今天没有尾巴", "十次全清，告别拖延与未完事项", "累计 10 个高质量必做全清日", "all_clear_days", 10, "天", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.all_clear.stage_3", s02, 3, slot02, AchievementKind.EVOLUTION, "一件也没落下", "五十天的彻底执行，日日清爽", "累计 50 个高质量必做全清日", "all_clear_days", 50, "天", AchievementRarity.UNCOMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.all_clear.stage_4", s02, 4, slot02, AchievementKind.EVOLUTION, "没有悬念的一天", "全清不再靠突击，而是水到渠成", "累计 150 个高质量必做全清日", "all_clear_days", 150, "天", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.all_clear.stage_5", s02, 5, slot02, AchievementKind.EVOLUTION, "成为日常", "一年份的全清，纪律成为第二本能", "累计 365 个高质量必做全清日", "all_clear_days", 365, "天", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.all_clear.stage_6", s02, 6, slot02, AchievementKind.EVOLUTION, "生活自有章法", "千日圆满，有始有终的极致从容", "累计 1000 个高质量必做全清日", "all_clear_days", 1000, "天", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 03 时间的朋友 (6阶段)
        val s03 = "evolution.03.streak"
        val slot03 = "slot.evolution.03.streak"
        add(AchievementDefinition("achievement.evolution.streak.stage_1", s03, 1, slot03, AchievementKind.EVOLUTION, "不只是今天", "连续三天自律达标，惯性初显", "连续 3 个有效执行日", "streak_days", 3, "天", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_three"))
        add(AchievementDefinition("achievement.evolution.streak.stage_2", s03, 2, slot03, AchievementKind.EVOLUTION, "新鲜感过后", "两周持续达标，跨越最初的热情衰退", "连续 14 个有效执行日", "streak_days", 14, "天", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_seven"))
        add(AchievementDefinition("achievement.evolution.streak.stage_3", s03, 3, slot03, AchievementKind.EVOLUTION, "日子有了惯性", "整整一个月没有断链，稳定前行", "连续 30 个有效执行日", "streak_days", 30, "天", AchievementRarity.UNCOMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_thirty"))
        add(AchievementDefinition("achievement.evolution.streak.stage_4", s03, 4, slot03, AchievementKind.EVOLUTION, "时间站在这边", "七十五日如一日，习惯坚如磐石", "连续 75 个有效执行日", "streak_days", 75, "天", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.streak.stage_5", s03, 5, slot03, AchievementKind.EVOLUTION, "热情退场以后", "半年风雨无阻，真正的坚持不需要动机", "连续 180 个有效执行日", "streak_days", 180, "天", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.streak.stage_6", s03, 6, slot03, AchievementKind.EVOLUTION, "四季都在场", "整整一年从未缺席，见证四季流转", "连续 365 个有效执行日", "streak_days", 365, "天", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 04 专注的深度 (6阶段)
        val s04 = "evolution.04.focus"
        val slot04 = "slot.evolution.04.focus"
        add(AchievementDefinition("achievement.evolution.focus.stage_1", s04, 1, slot04, AchievementKind.EVOLUTION, "世界静音", "累计完成一小时高质量专注", "累计有效专注 1 小时", "focus_minutes", 60, "分钟", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_hour"))
        add(AchievementDefinition("achievement.evolution.focus.stage_2", s04, 2, slot04, AchievementKind.EVOLUTION, "有点入戏", "二十小时深度投入，渐入专注佳境", "累计有效专注 20 小时", "focus_minutes", 1200, "分钟", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC, artId = "achievement_ten_hours"))
        add(AchievementDefinition("achievement.evolution.focus.stage_3", s04, 3, slot04, AchievementKind.EVOLUTION, "渐入佳境", "百小时沉浸，时间开始产生复利", "累计有效专注 100 小时", "focus_minutes", 6000, "分钟", AchievementRarity.UNCOMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.focus.stage_4", s04, 4, slot04, AchievementKind.EVOLUTION, "深水区", "四百小时的心流磨砺，深度思考", "累计有效专注 400 小时", "focus_minutes", 24000, "分钟", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.focus.stage_5", s04, 5, slot04, AchievementKind.EVOLUTION, "时间有了去处", "一千两百小时专注，用认真雕刻时光", "累计有效专注 1200 小时", "focus_minutes", 72000, "分钟", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.focus.stage_6", s04, 6, slot04, AchievementKind.EVOLUTION, "漫长的认真", "三千小时的纯粹投入，心无旁骛的杰作", "累计有效专注 3000 小时", "focus_minutes", 180000, "分钟", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 05 沉浸时刻 (6阶段, PENDING_EVIDENCE)
        val s05 = "evolution.05.deep_focus"
        val slot05 = "slot.evolution.05.deep_focus"
        val s05Reason = "缺少单次专注会话中断日志与深度专注状态判定，避免将普通挂机误判为深度专注"
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_1", s05, 1, slot05, AchievementKind.EVOLUTION, "安静一会儿", "单次不被打断的45分钟专注", "≥45分钟合格深度专注 1 次", "deep_focus_count", 1, "次", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_2", s05, 2, slot05, AchievementKind.EVOLUTION, "找到频率", "多次沉浸，掌握进入心流的节奏", "≥45分钟合格深度专注 10 次", "deep_focus_count", 10, "次", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_3", s05, 3, slot05, AchievementKind.EVOLUTION, "进入状态", "五十次高质量沉浸，随时能静下心来", "≥45分钟合格深度专注 50 次", "deep_focus_count", 50, "次", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_4", s05, 4, slot05, AchievementKind.EVOLUTION, "忘了看时间", "两百次深度专注，享受沉浸其中的纯粹", "≥45分钟合格深度专注 200 次", "deep_focus_count", 200, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_5", s05, 5, slot05, AchievementKind.EVOLUTION, "与世界暂别", "六百次心流体验，自如掌控注意力", "≥45分钟合格深度专注 600 次", "deep_focus_count", 600, "次", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))
        add(AchievementDefinition("achievement.evolution.deep_focus.stage_6", s05, 6, slot05, AchievementKind.EVOLUTION, "自成一片天地", "一千五百次深度专注，心流已成生活常态", "≥45分钟合格深度专注 1500 次", "deep_focus_count", 1500, "次", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s05Reason))

        // 06 计划之外 (6阶段)
        val s06 = "evolution.06.elective"
        val slot06 = "slot.evolution.06.elective"
        add(AchievementDefinition("achievement.evolution.elective.stage_1", s06, 1, slot06, AchievementKind.EVOLUTION, "顺手做了", "在必做之外多迈出一步，收获惊喜", "累计完成 1 项有效选做任务", "elective_tasks_completed", 1, "项", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.elective.stage_2", s06, 2, slot06, AchievementKind.EVOLUTION, "比计划多一点", "二十次自我加练，积极探索更多可能", "累计完成 20 项有效选做任务", "elective_tasks_completed", 20, "项", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.elective.stage_3", s06, 3, slot06, AchievementKind.EVOLUTION, "还有点好奇", "百次自发行动，保持求知与探索欲", "累计完成 100 项有效选做任务", "elective_tasks_completed", 100, "项", AchievementRarity.UNCOMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.elective.stage_4", s06, 4, slot06, AchievementKind.EVOLUTION, "多走一段", "三百次主动作为，把潜力变成实力", "累计完成 300 项有效选做任务", "elective_tasks_completed", 300, "项", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.elective.stage_5", s06, 5, slot06, AchievementKind.EVOLUTION, "不只做到刚好", "八百次计划外投入，追求卓越的自驱力", "累计完成 800 项有效选做任务", "elective_tasks_completed", 800, "项", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.evolution.elective.stage_6", s06, 6, slot06, AchievementKind.EVOLUTION, "计划之外的收获", "近两千项自发成果，拓展人生的宽广疆域", "累计完成 1800 项有效选做任务", "elective_tasks_completed", 1800, "项", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 07 娱乐的边界 (6阶段, PENDING_EVIDENCE)
        val s07 = "evolution.07.entertainment"
        val slot07 = "slot.evolution.07.entertainment"
        val s07Reason = "娱乐合规事件判定（额度消耗、主动退出及权限状态快照）待行为审计规则完整定版"
        add(AchievementDefinition("achievement.evolution.entertainment.stage_1", s07, 1, slot07, AchievementKind.EVOLUTION, "快乐有门槛", "在规则之内享受放松，娱乐更加心安理得", "有娱乐使用且合规的有效日 1 天", "entertainment_compliant_days", 1, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))
        add(AchievementDefinition("achievement.evolution.entertainment.stage_2", s07, 2, slot07, AchievementKind.EVOLUTION, "按约定来", "两周遵守娱乐边界，不被诱惑牵着走", "有娱乐使用且合规的有效日 14 天", "entertainment_compliant_days", 14, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))
        add(AchievementDefinition("achievement.evolution.entertainment.stage_3", s07, 3, slot07, AchievementKind.EVOLUTION, "刚刚好", "六十天的分寸感，娱乐与正事两不误", "有娱乐使用且合规的有效日 60 天", "entertainment_compliant_days", 60, "天", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))
        add(AchievementDefinition("achievement.evolution.entertainment.stage_4", s07, 4, slot07, AchievementKind.EVOLUTION, "知道什么时候停", "半年克制，懂得适可而止的从容", "有娱乐使用且合规的有效日 180 天", "entertainment_compliant_days", 180, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))
        add(AchievementDefinition("achievement.evolution.entertainment.stage_5", s07, 5, slot07, AchievementKind.EVOLUTION, "收放自如", "一年份的清醒自律，做欲望的主人", "有娱乐使用且合规的有效日 365 天", "entertainment_compliant_days", 365, "天", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))
        add(AchievementDefinition("achievement.evolution.entertainment.stage_6", s07, 6, slot07, AchievementKind.EVOLUTION, "自己说了算", "九百天边界分明，拥有无可撼动的掌控感", "有娱乐使用且合规的有效日 900 天", "entertainment_compliant_days", 900, "天", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s07Reason))

        // 08 与昼夜和解 (6阶段, PENDING_EVIDENCE)
        val s08 = "evolution.08.circadian"
        val slot08 = "slot.evolution.08.circadian"
        val s08Reason = "作息三条件（入睡、起床、睡眠时长）交叉自动化核验证据待健康源闭环确认"
        add(AchievementDefinition("achievement.evolution.circadian.stage_1", s08, 1, slot08, AchievementKind.EVOLUTION, "今晚就到这", "准时就寝，给今天一个平静的收尾", "有效睡眠、入睡及起床达标 1 周期", "circadian_cycles", 1, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))
        add(AchievementDefinition("achievement.evolution.circadian.stage_2", s08, 2, slot08, AchievementKind.EVOLUTION, "清晨有约", "两周作息规律，清晨迎着光亮醒来", "有效睡眠、入睡及起床达标 14 周期", "circadian_cycles", 14, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))
        add(AchievementDefinition("achievement.evolution.circadian.stage_3", s08, 3, slot08, AchievementKind.EVOLUTION, "日夜有序", "四十五天生物钟稳定，精力充沛", "有效睡眠、入睡及起床达标 45 周期", "circadian_cycles", 45, "天", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))
        add(AchievementDefinition("achievement.evolution.circadian.stage_4", s08, 4, slot08, AchievementKind.EVOLUTION, "月亮准时下班", "百余日作息如常，身心逐渐恢复安宁", "有效睡眠、入睡及起床达标 120 周期", "circadian_cycles", 120, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))
        add(AchievementDefinition("achievement.evolution.circadian.stage_5", s08, 5, slot08, AchievementKind.EVOLUTION, "把明天留给明天", "一年顺应自然节律，不透支也不焦虑", "有效睡眠、入睡及起床达标 365 周期", "circadian_cycles", 365, "天", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))
        add(AchievementDefinition("achievement.evolution.circadian.stage_6", s08, 6, slot08, AchievementKind.EVOLUTION, "不再和时间较劲", "八百周期昼夜平和，与生命节律彻底和解", "有效睡眠、入睡及起床达标 800 周期", "circadian_cycles", 800, "天", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = s08Reason))

        // -------------------------------------------------------------
        // 1.2 五组独立行为 (5 * 6 = 30)
        // -------------------------------------------------------------

        // 09 执行的细节 (6)
        add(AchievementDefinition("achievement.indep.execution.write_first", null, null, "slot.indep.execution.write_first", AchievementKind.INDEPENDENT, "写在前面", "谋定而后动，在开始前确认前行方向", "累计30天在开始执行前确认当日计划", "plan_confirm_days", 30, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "计划冻结时间快照待补充审计时间戳"))
        add(AchievementDefinition("achievement.indep.execution.eat_frog", null, null, "slot.indep.execution.eat_frog", AchievementKind.INDEPENDENT, "先啃硬骨头", "直面挑战，优先攻克最艰难的一关", "累计20天优先完成当日已预标最高难度的必做任务", "eat_frog_days", 20, "天", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "任务难度分级模型及先后执行顺序审计待定案"))
        add(AchievementDefinition("achievement.indep.execution.not_leave_today", null, null, "slot.indep.execution.not_leave_today", AchievementKind.INDEPENDENT, "不把今天留给明天", "今日事今日毕，不让拖延积累成负担", "累计60个有效日无必做任务逾期迁移", "no_delay_days", 60, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "跨日任务逾期迁移审计日志与撤销标记待补全"))
        add(AchievementDefinition("achievement.indep.execution.follow_through", null, null, "slot.indep.execution.follow_through", AchievementKind.INDEPENDENT, "有始有终", "周周有成果，持续保持高质量输出", "连续4周，每周至少5个高质量完成日", "consistent_weeks", 4, "周", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.indep.execution.withstand_review", null, null, "slot.indep.execution.withstand_review", AchievementKind.INDEPENDENT, "经得起回头看", "季度复盘坦然无愧，执行率坚挺如初", "最近90天必做任务按期完成率≥90%", "ninety_day_rate", 90, "%", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "最低样本量（≥60有效日）与按期判定细则待定案"))
        add(AchievementDefinition("achievement.indep.execution.every_page_counts", null, null, "slot.indep.execution.every_page_counts", AchievementKind.INDEPENDENT, "每一页都算数", "整整一年每月保持稳定输出，功不唐捐", "连续12个自然月，每月有效执行日≥20", "active_months", 12, "月", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 10 专注的质量 (6)
        add(AchievementDefinition("achievement.indep.focus.hour_for_myself", null, null, "slot.indep.focus.hour_for_myself", AchievementKind.INDEPENDENT, "留给自己的一小时", "在喧嚣中守护属于自己的深度时间", "累计20次合格的不间断60分钟专注", "uninterrupted_60m_sessions", 20, "次", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "不间断单次会话日志待底层专注日志采集支持"))
        add(AchievementDefinition("achievement.indep.focus.without_glance", null, null, "slot.indep.focus.without_glance", AchievementKind.INDEPENDENT, "不看一眼", "抵抗弹窗诱惑，专注期间绝不分心", "累计30次60分钟专注中未打开受管控娱乐App", "clean_focus_sessions", 30, "次", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "专注时段与受控应用启动事件的交叉比对机制待补全"))
        add(AchievementDefinition("achievement.indep.focus.one_breath", null, null, "slot.indep.focus.one_breath", AchievementKind.INDEPENDENT, "一口气做完", "超长心流状态，直达问题的最深处", "累计10次合格90分钟深度专注", "deep_90m_sessions", 10, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "单次不中断90分钟专注会话数据源待支持"))
        add(AchievementDefinition("achievement.indep.focus.quiet_week", null, null, "slot.indep.focus.quiet_week", AchievementKind.INDEPENDENT, "安静的一周", "整周每天保持深度专注，高效而充实", "连续7个有效日，每日专注≥60分钟", "quiet_week_days", 7, "天", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.indep.focus.reclaim_time", null, null, "slot.indep.focus.reclaim_time", AchievementKind.INDEPENDENT, "把时间找回来", "在一个月里重新夺回对注意力的主权", "连续30日窗口中至少25日专注≥90分钟", "reclaimed_days", 25, "天", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.indep.focus.full_immersion", null, null, "slot.indep.focus.full_immersion", AchievementKind.INDEPENDENT, "全神贯注", "季度高强度投入，心无旁骛的终极境界", "90日窗口中总计≥180小时有效专注且≥60日有记录", "immersion_hours", 180, "小时", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 11 娱乐的分寸 (6, PENDING_EVIDENCE)
        add(AchievementDefinition("achievement.indep.entertainment.leave_margin", null, null, "slot.indep.entertainment.leave_margin", AchievementKind.INDEPENDENT, "留有余地", "玩得尽兴，更懂得保留精力留有余白", "累计30个娱乐使用日，剩余额度≥20%", "margin_days", 30, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "娱乐日额度快照与剩余比例审计待定"))
        add(AchievementDefinition("achievement.indep.entertainment.stop_on_will", null, null, "slot.indep.entertainment.stop_on_will", AchievementKind.INDEPENDENT, "说停就停", "随时能合上屏幕，从容转身回归现实", "累计30次主动结束娱乐且未耗尽额度", "voluntary_stops", 30, "次", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "可验证的主动停止事件缺失，待行为日志支持"))
        add(AchievementDefinition("achievement.indep.entertainment.duty_first", null, null, "slot.indep.entertainment.duty_first", AchievementKind.INDEPENDENT, "先把正事办了", "任务完成后的放松，格外轻松自在", "累计60天在全部必做完成之后才开启娱乐", "duty_first_days", 60, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "任务完成事件与应用使用事件的时序交叉审计待完善"))
        add(AchievementDefinition("achievement.indep.entertainment.no_need_drain", null, null, "slot.indep.entertainment.no_need_drain", AchievementKind.INDEPENDENT, "没必要刷满", "三周克制放松，不再追求耗尽每一分额度", "连续21个娱乐使用日均保留≥20%额度", "undrained_days", 21, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "连续娱乐使用日额度快照及比对机制待建立"))
        add(AchievementDefinition("achievement.indep.entertainment.one_game_enough", null, null, "slot.indep.entertainment.one_game_enough", AchievementKind.INDEPENDENT, "一局就够", "一局尽兴，绝不多打，掌控胜负心", "累计30个游戏使用日只使用1局且未额外解锁", "one_game_days", 30, "天", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "王者荣耀对局自动识别未完全稳定前暂缓上线"))
        add(AchievementDefinition("achievement.indep.entertainment.beyond_joy", null, null, "slot.indep.entertainment.beyond_joy", AchievementKind.INDEPENDENT, "快乐之外", "在追求快乐的同时，守住长期的自律基准", "最近90天必做完成率≥85%、娱乐合规率≥95%", "beyond_joy_score", 90, "天", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "双域可核验综合统计模型待定案"))

        // 12 睡眠和早起 (6, PENDING_EVIDENCE)
        add(AchievementDefinition("achievement.indep.sleep.sleep_first", null, null, "slot.indep.sleep.sleep_first", AchievementKind.INDEPENDENT, "先睡了", "不因熬夜刷手机内耗，准时上床休息", "累计30晚按目标时间入睡", "bedtime_target_days", 30, "晚", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "目标入睡时间与实际入睡判定待健康连接源确认"))
        add(AchievementDefinition("achievement.indep.sleep.ahead_of_alarm", null, null, "slot.indep.sleep.ahead_of_alarm", AchievementKind.INDEPENDENT, "比闹钟早一步", "睡饱后的自然苏醒，身体自带秩序", "20次早于闹钟自然醒并起床", "natural_wakes", 20, "次", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "当前无法可靠自动证实自然醒，暂存候选不上线"))
        add(AchievementDefinition("achievement.indep.sleep.catch_morning", null, null, "slot.indep.sleep.catch_morning", AchievementKind.INDEPENDENT, "赶上了清晨", "连续两周迎着朝阳醒来，晨光带来好心情", "连续14个有效周期按时起床", "on_time_wake_days", 14, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "按时起床确认证据链待作息模块闭环确认"))
        add(AchievementDefinition("achievement.indep.sleep.save_energy_tomorrow", null, null, "slot.indep.sleep.save_energy_tomorrow", AchievementKind.INDEPENDENT, "给明天留点精神", "守住睡眠底线，好精力是一切的基础", "30天窗口≥25天达到最低有效睡眠时长", "adequate_sleep_days", 25, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "最低有效睡眠时长判定证据待健康数据支持"))
        add(AchievementDefinition("achievement.indep.sleep.live_week_well", null, null, "slot.indep.sleep.live_week_well", AchievementKind.INDEPENDENT, "好好过完这一周", "整周作息全达标，身心状态处于最佳节律", "连续7周期达成入睡/起床/睡眠时长目标", "perfect_sleep_week", 7, "周期", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "三条件交叉验证逻辑待睡眠模块完善"))
        add(AchievementDefinition("achievement.indep.sleep.account_day_night", null, null, "slot.indep.sleep.account_day_night", AchievementKind.INDEPENDENT, "早晚都有交代", "整整一季作息严谨，生活井井有条", "90天窗口中≥80周期作息全达标", "perfect_sleep_season", 80, "周期", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "睡眠周期全达标汇总模型待定案"))

        // 13 生活的平衡 (6)
        add(AchievementDefinition("achievement.indep.balance.own_time", null, null, "slot.indep.balance.own_time", AchievementKind.INDEPENDENT, "各有各的时间", "工作、专注与娱乐边界分明，互不干扰", "累计30天必做全清、专注≥60分钟、娱乐合规", "balanced_days", 30, "天", AchievementRarity.COMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "多域事件（全清+专注+娱乐合规）判定模型待定"))
        add(AchievementDefinition("achievement.indep.balance.both_ends", null, null, "slot.indep.balance.both_ends", AchievementKind.INDEPENDENT, "两头都顾上了", "白昼全力以赴，夜晚安心好眠", "30天窗口≥25天任务与睡眠均达标", "task_sleep_double_days", 25, "天", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "任务与作息双达标事件待睡眠源验证闭环"))
        add(AchievementDefinition("achievement.indep.balance.weekend_counts", null, null, "slot.indep.balance.weekend_counts", AchievementKind.INDEPENDENT, "周末也算数", "周末既有休整也有前行，不虚度也不紧绷", "累计12个周末按其各自事先计划达标", "weekend_successes", 12, "个", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "周末计划快照与专属达标条件未独立存储"))
        add(AchievementDefinition("achievement.indep.balance.more_than_spark", null, null, "slot.indep.balance.more_than_spark", AchievementKind.INDEPENDENT, "不止三分钟热度", "连续一季每周稳定行动，热情化为坚韧", "连续12周每周≥5个有效执行日", "consecutive_12_weeks", 12, "周", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.indep.balance.no_bias", null, null, "slot.indep.balance.no_bias", AchievementKind.INDEPENDENT, "没有偏科", "行动、专注、作息全方位稳步前行", "连续3个月行动、专注、作息均达标", "three_domain_months", 3, "月", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "三域月度门槛尚未统一定义"))
        add(AchievementDefinition("achievement.indep.balance.answer_of_90_days", null, null, "slot.indep.balance.answer_of_90_days", AchievementKind.INDEPENDENT, "九十天的答案", "用整季的卓越自律，交出无可挑剔的答卷", "连续90天综合执行评分达到预设优秀标准", "quarterly_score_days", 90, "天", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "综合执行评分模型待定版"))

        // -------------------------------------------------------------
        // 1.3 三组挑战 (3 * 6 = 18)
        // -------------------------------------------------------------

        // 14 月度挑战篇章 (4 available from verified completion facts; 2 still pending)
        add(AchievementDefinition("achievement.challenge.monthly.not_just_trying", null, null, "slot.challenge.monthly.not_just_trying", AchievementKind.CHALLENGE, "这次不是试试", "认真对待挑战，首次攻克具有分量的目标", "首次完成中等或以上难度月度挑战", "medium_monthly_challenges", 1, "次", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.challenge.monthly.month_answer", null, null, "slot.challenge.monthly.month_answer", AchievementKind.CHALLENGE, "一个月的答案", "用整月的严谨坚持，换来满意的成长反馈", "首次完成30天期且达标率≥90%的挑战", "full_month_challenges", 1, "次", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "月度挑战业务模块待后续开放，接口预留"))
        add(AchievementDefinition("achievement.challenge.monthly.not_by_luck", null, null, "slot.challenge.monthly.not_by_luck", AchievementKind.CHALLENGE, "不靠运气", "跨越不同领域，证明自己的适应力与执行力", "完成3个不同主题的月度挑战", "unique_theme_challenges", 3, "个", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.challenge.monthly.raise_difficulty", null, null, "slot.challenge.monthly.raise_difficulty", AchievementKind.CHALLENGE, "把难度调高", "主动突破舒适区，成功挑战高难度项目", "完成1个事先评定的高难度月度挑战", "hard_challenges", 1, "个", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "月度挑战业务模块待后续开放，接口预留"))
        add(AchievementDefinition("achievement.challenge.monthly.whole_season", null, null, "slot.challenge.monthly.whole_season", AchievementKind.CHALLENGE, "一整季的坚持", "连续三月挑战全胜，自律已经势不可挡", "连续3个月完成正式月度挑战", "consecutive_season_challenges", 3, "月", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.challenge.monthly.twelve_chapters", null, null, "slot.challenge.monthly.twelve_chapters", AchievementKind.CHALLENGE, "十二个章节", "十二个月度篇章完整落幕，岁月记录荣光", "完成12个不同月份的挑战篇章", "annual_chapters", 12, "章", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))

        // 15 探索与创造 (1 available from verified completion facts; 5 still pending)
        add(AchievementDefinition("achievement.challenge.explore.little_adventure", null, null, "slot.challenge.explore.little_adventure", AchievementKind.CHALLENGE, "小小的冒险", "迈出探索第一步，用行动拓宽生活边界", "完成首次探索挑战", "explore_challenges", 1, "次", AchievementRarity.COMMON, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.challenge.explore.beyond_familiar", null, null, "slot.challenge.explore.beyond_familiar", AchievementKind.CHALLENGE, "熟悉之外", "深入未涉猎的领域，收获全新的认知与体验", "在3个陌生领域完成有验收的探索任务", "domains_explored", 3, "个", AchievementRarity.UNCOMMON, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "探索挑战核验标准待后续挑战内容库定案"))
        add(AchievementDefinition("achievement.challenge.explore.try_another_path", null, null, "slot.challenge.explore.try_another_path", AchievementKind.CHALLENGE, "换条路看看", "遇到困难灵活应变，用替代路径达成目标", "使用预先认可的替代方案完成挑战", "alternative_paths", 1, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "探索挑战核验标准待后续挑战内容库定案"))
        add(AchievementDefinition("achievement.challenge.explore.map_unfinished", null, null, "slot.challenge.explore.map_unfinished", AchievementKind.CHALLENGE, "地图还没画完", "跨越六个主题，绘制属于自己的探索蓝图", "完成至少6个不同主题探索挑战", "theme_explore_count", 6, "个", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "探索挑战核验标准待后续挑战内容库定案"))
        add(AchievementDefinition("achievement.challenge.explore.leave_something", null, null, "slot.challenge.explore.leave_something", AchievementKind.CHALLENGE, "留下点什么", "不仅输入更要输出，亲手创造可检验的作品", "完成3项有可核验作品的创造挑战", "creative_works", 3, "项", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "创造作品核验标准待后续挑战内容库定案"))
        add(AchievementDefinition("achievement.challenge.explore.world_is_bigger", null, null, "slot.challenge.explore.world_is_bigger", AchievementKind.CHALLENGE, "世界比想象中大", "十二项探索跨越山海，拥抱广阔天地", "完成12项跨主题探索挑战", "cross_theme_explores", 12, "项", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "探索挑战核验标准待后续挑战内容库定案"))

        // 16 真正的突破 (6, PENDING_EVIDENCE)
        add(AchievementDefinition("achievement.challenge.breakthrough.i_can_too", null, null, "slot.challenge.breakthrough.i_can_too", AchievementKind.CHALLENGE, "原来我也可以", "击碎自我怀疑，首次达成此前从未实现的客观目标", "首次实现此前未达到的客观目标并通过独立复测", "breakthrough_targets", 1, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "突破挑战需先记录基线并通过独立复测证据闭环"))
        add(AchievementDefinition("achievement.challenge.breakthrough.restart_not_reset", null, null, "slot.challenge.breakthrough.restart_not_reset", AchievementKind.CHALLENGE, "重来，不等于重头", "历经失败而不退缩，以更成熟的姿态重返战场并获胜", "高难挑战失败后改进，至少7天后不降标准成功", "fail_and_conquer", 1, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖正式失败记录、改进方案及7天后不降标复测证据"))
        add(AchievementDefinition("achievement.challenge.breakthrough.not_luck", null, null, "slot.challenge.breakthrough.not_luck", AchievementKind.CHALLENGE, "不是碰巧", "用重复多次的成功，粉碎一切侥幸的可能", "同项高难技能在≥30天内完成3次独立成功验证", "repeated_validations", 3, "次", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖跨30天3次独立复测的证据记录"))
        add(AchievementDefinition("achievement.challenge.breakthrough.cross_hurdle", null, null, "slot.challenge.breakthrough.cross_hurdle", AchievementKind.CHALLENGE, "跨过那道坎", "在长时间的瓶颈与阻碍面前，最终赢下胜利", "记录持续受阻≥30天且≥2次失败，最终通过原设标准", "hurdle_conquests", 1, "次", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖持续受阻30天与2次正式失败证据"))
        add(AchievementDefinition("achievement.challenge.breakthrough.harder_question", null, null, "slot.challenge.breakthrough.harder_question", AchievementKind.CHALLENGE, "更难的那一题", "不满足于旧胜利，主动接下要求更高的新挑战", "已完成高难挑战，继续完成同领域客观要求更高挑战", "harder_challenges", 1, "次", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖同领域高阶挑战级联关系及验收证据"))
        add(AchievementDefinition("achievement.challenge.breakthrough.day_i_crossed", null, null, "slot.challenge.breakthrough.day_i_crossed", AchievementKind.CHALLENGE, "那天，我跨过去了", "持续两月以上的极限攻坚，写下属于自己的史诗", "完成持续≥60天分阶段核验有独立成果的年度极限挑战", "annual_ultimate_challenges", 1, "项", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖60天分阶段过程事件与独立成果验收"))

        // -------------------------------------------------------------
        // 1.4 隐藏成就 (12)
        // -------------------------------------------------------------
        add(AchievementDefinition("achievement.secret.to_be_continued", null, null, "slot.secret.to_be_continued", AchievementKind.SECRET, "未完待续", "真实执行中断后重新建立30个有效执行日", "真实执行中断≥14天，恢复后重新建立30个有效执行日", "rebuilt_streak_days", 30, "天", AchievementRarity.RARE, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.secret.tension_and_relaxation", null, null, "slot.secret.tension_and_relaxation", AchievementKind.SECRET, "有张有弛", "合规休整后稳定恢复行动，从容有度", "90天内经历3次合规计划休息，每次后稳定恢复≥7执行日", "planned_rest_recoveries", 3, "次", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "合规计划休息审计标记及恢复执行链待定义"))
        add(AchievementDefinition("achievement.secret.energy_for_tomorrow", null, null, "slot.secret.energy_for_tomorrow", AchievementKind.SECRET, "留给明天的力气", "高负荷日依然保证最低睡眠，不以健康换效率", "30个事先确认的高负荷日均满足最低睡眠时长", "protected_rest_days", 30, "天", AchievementRarity.RARE, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "高负荷日标记与睡眠时长交叉判定待定"))
        add(AchievementDefinition("achievement.secret.double_happiness", null, null, "slot.secret.double_happiness", AchievementKind.SECRET, "好事成双", "同一天达成两个不同进化系列的高阶里程碑", "同一天达成两个不同进化系列的高阶里程碑 (IV及以上)", "double_milestones", 2, "个", AchievementRarity.EPIC, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.secret.even_myself_surprised", null, null, "slot.secret.even_myself_surprised", AchievementKind.SECRET, "连自己都没想到", "厚积薄发，同一天在两个不同领域刷新历史最高峰", "先有≥90有效日，同天刷新两个不同领域历史最高成绩", "simultaneous_peaks", 2, "个", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "跨领域历史成绩快照与峰值比对机制待建立"))
        add(AchievementDefinition("achievement.secret.unexpected_harvest", null, null, "slot.secret.unexpected_harvest", AchievementKind.SECRET, "意外收获", "达成正式挑战事先冻结的全部隐藏支线目标", "正式挑战的预设3个隐藏支线目标全部达成", "secret_side_goals", 3, "个", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "挑战支线目标冻结机制待挑战模块接入"))
        add(AchievementDefinition("achievement.secret.self_aware_changes", null, null, "slot.secret.self_aware_changes", AchievementKind.SECRET, "有些变化，自己知道", "两个统计窗口见证长周期客观改善，静水流深", "相隔≥90天的统计窗口中两项成长指标达成有意义改善", "meaningful_improvements", 2, "项", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "跨90天成长指标客观改善评估模型待定"))
        add(AchievementDefinition("achievement.secret.everything_as_usual", null, null, "slot.secret.everything_as_usual", AchievementKind.SECRET, "一切照常", "登顶巅峰之后心静如常，自律已成为生活本身", "任意进化系列达VI阶段后，该领域继续保持90有效执行日", "post_max_days", 90, "天", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.secret.already_so_long", null, null, "slot.secret.already_so_long", AchievementKind.SECRET, "原来已经这么久", "一整年的步履不停，自律的足迹坚实清晰", "首次任务完成满365自然日，期间累计有效执行日≥240", "first_year_days", 240, "天", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
        add(AchievementDefinition("achievement.secret.more_than_this_side", null, null, "slot.secret.more_than_this_side", AchievementKind.SECRET, "不止这一面", "一年内攻克三个不同领域的正式高难挑战", "12个月内完成3个不同领域的正式高难挑战", "cross_domain_challenges", 3, "个", AchievementRarity.EPIC, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖3个不同领域的正式高难挑战完成记录"))
        add(AchievementDefinition("achievement.secret.next_chapter_awaits", null, null, "slot.secret.next_chapter_awaits", AchievementKind.SECRET, "还有下一章", "年度突破之后马不停蹄，向着新主题重新启程", "完成年度挑战后180天内完成另一不同主题正式挑战", "subsequent_challenges", 1, "个", AchievementRarity.LEGENDARY, ReleaseStatus.PENDING_EVIDENCE, EvidencePolicy.PENDING_EVIDENCE, pendingReason = "依赖年度级挑战完成记录"))
        add(AchievementDefinition("achievement.secret.closet_of_stories", null, null, "slot.secret.closet_of_stories", AchievementKind.SECRET, "一柜子的故事", "琳琅满目的成就徽章，记录每一个发光的瞬间", "收藏≥60枚不同成就，其中挑战/隐藏≥6枚", "collection_count", 60, "枚", AchievementRarity.LEGENDARY, ReleaseStatus.AVAILABLE, EvidencePolicy.AUTOMATIC))
    }.map { definition ->
        if (definition.kind == AchievementKind.EVOLUTION && (definition.stage ?: 1) > 1) {
            val previousId = "${definition.id.substringBeforeLast(".stage_")}.stage_${definition.stage!! - 1}"
            definition.copy(prerequisiteIds = listOf(previousId))
        } else definition
    }

    private val idMap = all108.associateBy { it.id }
    private val slotMap = collectionSlots.associateBy { it.slotId }

    // These legacy entries remain queryable/restorable without changing the
    // 108-milestone catalog or its 68 collection slots.
    val legacyDefinitions: List<AchievementDefinition> = listOf(
        AchievementDefinition("legacy.task.ten", null, null, "legacy.slot.task.ten", AchievementKind.INDEPENDENT, "十项达成", "累计完成 10 项任务", "累计完成 10 项任务", "tasks_completed", 10, "项", AchievementRarity.COMMON, ReleaseStatus.RETIRED, EvidencePolicy.AUTOMATIC),
        AchievementDefinition("legacy.task.fifty", null, null, "legacy.slot.task.fifty", AchievementKind.INDEPENDENT, "五十项达成", "累计完成 50 项任务", "累计完成 50 项任务", "tasks_completed", 50, "项", AchievementRarity.UNCOMMON, ReleaseStatus.RETIRED, EvidencePolicy.AUTOMATIC),
        AchievementDefinition("legacy.streak.seven", null, null, "legacy.slot.streak.seven", AchievementKind.INDEPENDENT, "连续一周", "连续七个自律日完成目标", "连续完成 7 日目标", "streak_days", 7, "天", AchievementRarity.UNCOMMON, ReleaseStatus.RETIRED, EvidencePolicy.AUTOMATIC),
        AchievementDefinition("legacy.focus.ten_hours", null, null, "legacy.slot.focus.ten_hours", AchievementKind.INDEPENDENT, "专注十小时", "累计有效专注 10 小时", "累计有效专注 600 分钟", "focus_minutes", 600, "分钟", AchievementRarity.UNCOMMON, ReleaseStatus.RETIRED, EvidencePolicy.AUTOMATIC),
    )
    private val legacyIdMap = legacyDefinitions.associateBy { it.id }

    fun findById(id: String): AchievementDefinition? = idMap[id] ?: legacyIdMap[mapLegacyId(id)]

    fun findBySlotId(slotId: String): List<AchievementDefinition> = all108.filter { it.slotId == slotId }

    fun getSlot(slotId: String): CollectionSlot? = slotMap[slotId]

    /**
     * Map old 8 achievement IDs from previous prototype to new catalog IDs.
     */
    fun mapLegacyId(legacyId: String): String = when (legacyId) {
        "task.first" -> "achievement.evolution.action.stage_1"
        "task.ten" -> "legacy.task.ten"
        "task.fifty" -> "legacy.task.fifty"
        "streak.three" -> "achievement.evolution.streak.stage_1"
        "streak.seven" -> "legacy.streak.seven"
        "streak.thirty" -> "achievement.evolution.streak.stage_3"
        "focus.hour" -> "achievement.evolution.focus.stage_1"
        "focus.ten_hours" -> "legacy.focus.ten_hours"
        else -> legacyId
    }

    /**
     * Self-check validation for static consistency:
     * - Exactly 108 milestone definitions.
     * - Exactly 68 collection slots (8 evolution + 30 independent + 18 challenge + 12 secret).
     * - All IDs unique.
     * - Evolution series stages 1..6 strictly monotonic.
     * - No cyclic dependencies.
     */
    fun validateCatalog(
        definitions: List<AchievementDefinition> = all108,
        slots: List<CollectionSlot> = collectionSlots,
    ): CatalogValidationResult {
        val errors = mutableListOf<String>()

        if (definitions.size != 108) {
            errors.add("Total milestones must be exactly 108, found ${definitions.size}")
        }

        if (slots.size != 68) {
            errors.add("Total collection slots must be exactly 68, found ${slots.size}")
        }

        val duplicateIds = definitions.groupBy { it.id }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            errors.add("Duplicate milestone IDs found: $duplicateIds")
        }

        val duplicateSlots = slots.groupBy { it.slotId }.filter { it.value.size > 1 }.keys
        if (duplicateSlots.isNotEmpty()) {
            errors.add("Duplicate collection slot IDs found: $duplicateSlots")
        }

        // Check slot mapping
        val validSlotIds = slots.map { it.slotId }.toSet()
        definitions.forEach { item ->
            if (item.slotId !in validSlotIds) {
                errors.add("Milestone ${item.id} references unknown slot ${item.slotId}")
            }
            if (item.title.isBlank()) {
                errors.add("Milestone ${item.id} has blank title")
            }
            if (item.description.length > 40) {
                errors.add("Milestone ${item.id} description exceeds 40 characters: '${item.description}'")
            }
            if (item.releaseStatus == ReleaseStatus.AVAILABLE &&
                (item.metric !in AchievementEngine.implementedMetrics || !AchievementEngine.supportsAvailable(item))
            ) {
                errors.add("Available milestone ${item.id} has no matching engine rule for metric ${item.metric}")
            }
            if (item.releaseStatus == ReleaseStatus.PENDING_EVIDENCE &&
                (item.evidencePolicy != EvidencePolicy.PENDING_EVIDENCE || item.pendingReason.isBlank())
            ) {
                errors.add("Pending milestone ${item.id} must identify its missing evidence")
            }
        }

        // Check evolution lines monotonicity
        val evolutionSeries = definitions.filter { it.kind == AchievementKind.EVOLUTION }.groupBy { it.seriesId }
        if (evolutionSeries.size != 8) {
            errors.add("Expected 8 evolution series, found ${evolutionSeries.size}")
        }

        evolutionSeries.forEach { (seriesId, stages) ->
            if (stages.size != 6) {
                errors.add("Series $seriesId must have 6 stages, found ${stages.size}")
            }
            val sorted = stages.sortedBy { it.stage ?: 0 }
            val stageNumbers = sorted.map { it.stage }
            if (stageNumbers != listOf(1, 2, 3, 4, 5, 6)) {
                errors.add("Series $seriesId stages must be 1..6, found $stageNumbers")
            }
            var prevThreshold = 0L
            sorted.forEach { st ->
                if (st.threshold <= prevThreshold) {
                    errors.add("Series $seriesId stage ${st.stage} threshold ${st.threshold} is not strictly greater than previous $prevThreshold")
                }
                prevThreshold = st.threshold
            }
        }

        val byId = definitions.associateBy { it.id }
        definitions.forEach { item ->
            item.prerequisiteIds.forEach { dependency ->
                if (dependency !in byId) errors.add("Milestone ${item.id} references unknown prerequisite $dependency")
            }
            if (item.kind == AchievementKind.EVOLUTION) {
                val expected = (item.stage ?: 1) - 1
                val expectedId = if (expected > 0) "${item.id.substringBeforeLast(".stage_")}.stage_$expected" else null
                if (expectedId != null && expectedId !in item.prerequisiteIds) {
                    errors.add("Evolution ${item.id} is missing previous-stage prerequisite $expectedId")
                }
                if (expectedId == null && item.prerequisiteIds.isNotEmpty()) {
                    errors.add("First evolution stage ${item.id} cannot have a previous-stage prerequisite")
                }
            }
        }

        val colors = mutableMapOf<String, Int>()
        val cycleMembers = mutableSetOf<String>()
        fun visit(id: String, path: MutableList<String>) {
            when (colors[id]) {
                1 -> {
                    val start = path.indexOf(id).coerceAtLeast(0)
                    cycleMembers.addAll(path.subList(start, path.size))
                    cycleMembers.add(id)
                    return
                }
                2 -> return
            }
            colors[id] = 1
            path.add(id)
            byId[id]?.prerequisiteIds.orEmpty().filter { it in byId }.forEach { visit(it, path) }
            path.removeAt(path.lastIndex)
            colors[id] = 2
        }
        definitions.forEach { visit(it.id, mutableListOf()) }
        if (cycleMembers.isNotEmpty()) errors.add("Cyclic achievement prerequisites found: ${cycleMembers.sorted()}")

        return CatalogValidationResult(errors.isEmpty(), errors)
    }
}

data class CatalogValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)
