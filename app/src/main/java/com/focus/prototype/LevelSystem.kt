package com.focus.prototype

import kotlin.math.ceil

/**
 * Representation of progress within a level.
 */
data class LevelProgress(
    val level: Int,
    val currentLevelXp: Long,
    val levelRequiredXp: Long,
    val progressRatio: Float,
    val xpToNextLevel: Long,
    val rank: String,
    val rankIndex: Int,
)

/**
 * Pluggable monotonic level progression curve.
 */
interface LevelCurve {
    val id: String
    val name: String

    /** Returns current level (1-based) from total accumulated XP (non-negative). */
    fun level(totalXp: Long): Int

    /** Returns total XP required to reach the specified level from level 1. */
    fun xpRequiredForLevel(level: Int): Long

    /** Returns full progress snapshot for a given total XP. */
    fun progress(totalXp: Long): LevelProgress

    /** Returns the major rank/tier name for a given level. */
    fun rank(level: Int): String
}

/**
 * Default production level curve:
 * - 500 XP per level
 * - Level 1: 0..499 XP
 * - Level 2: 500..999 XP, etc.
 * - Monotonic, strictly preserves existing production calculation.
 */
class DefaultProductionLevelCurve(
    val stepXp: Long = 500L,
    val ranks: List<String> = DEFAULT_RANKS,
) : LevelCurve {
    override val id: String = "production_default_500"
    override val name: String = "生产基线 (500 XP/级)"

    override fun level(totalXp: Long): Int {
        val safeXp = totalXp.coerceAtLeast(0L)
        return (safeXp / stepXp).toInt() + 1
    }

    override fun xpRequiredForLevel(level: Int): Long {
        require(level >= 1) { "Level must be >= 1" }
        return (level - 1) * stepXp
    }

    override fun progress(totalXp: Long): LevelProgress {
        val safeXp = totalXp.coerceAtLeast(0L)
        val lvl = level(safeXp)
        val currentLevelXp = safeXp % stepXp
        val ratio = (currentLevelXp.toFloat() / stepXp).coerceIn(0f, 1f)
        val diff = (stepXp - currentLevelXp).coerceAtLeast(0L)
        val rkIndex = (lvl - 1).coerceAtMost(ranks.size - 1)
        val rk = ranks[rkIndex]
        return LevelProgress(
            level = lvl,
            currentLevelXp = currentLevelXp,
            levelRequiredXp = stepXp,
            progressRatio = ratio,
            xpToNextLevel = diff,
            rank = rk,
            rankIndex = rkIndex,
        )
    }

    override fun rank(level: Int): String {
        val idx = (level.coerceAtLeast(1) - 1).coerceAtMost(ranks.size - 1)
        return ranks[idx]
    }

    companion object {
        val DEFAULT_RANKS = listOf("启程", "萌芽", "探索", "积累", "进阶", "坚韧", "精进", "卓越")
    }
}

/**
 * Balanced level curve (candidate proposal):
 * Starts at 300 XP, gently scales by tier, strictly monotonic.
 * Kept for configuration capability and test verification; not active by default.
 */
class BalancedLevelCurve(
    val baseStep: Long = 300L,
    val ranks: List<String> = DefaultProductionLevelCurve.DEFAULT_RANKS,
) : LevelCurve {
    override val id: String = "proposal_balanced"
    override val name: String = "平衡成长曲线 (设计提案样例)"

    // Monotonic threshold calculator:
    // Level 1: 0
    // Level 2: 300 (+300)
    // Level 3: 700 (+400)
    // Level 4: 1200 (+500)
    // Level 5: 1800 (+600)
    // Level 6+: +700 per level
    private fun stepForLevel(lvl: Int): Long = when {
        lvl <= 1 -> baseStep
        lvl == 2 -> baseStep + 100L
        lvl == 3 -> baseStep + 200L
        lvl == 4 -> baseStep + 300L
        else -> baseStep + 400L
    }

    override fun xpRequiredForLevel(level: Int): Long {
        require(level >= 1) { "Level must be >= 1" }
        var acc = 0L
        for (l in 1 until level) {
            acc += stepForLevel(l)
        }
        return acc
    }

    override fun level(totalXp: Long): Int {
        val safeXp = totalXp.coerceAtLeast(0L)
        var lvl = 1
        var acc = 0L
        while (true) {
            val step = stepForLevel(lvl)
            if (safeXp < acc + step) return lvl
            acc += step
            lvl++
        }
    }

    override fun progress(totalXp: Long): LevelProgress {
        val safeXp = totalXp.coerceAtLeast(0L)
        val lvl = level(safeXp)
        val baseForLvl = xpRequiredForLevel(lvl)
        val step = stepForLevel(lvl)
        val currentLevelXp = safeXp - baseForLvl
        val ratio = (currentLevelXp.toFloat() / step).coerceIn(0f, 1f)
        val diff = (step - currentLevelXp).coerceAtLeast(0L)
        val rkIndex = (lvl - 1).coerceAtMost(ranks.size - 1)
        return LevelProgress(
            level = lvl,
            currentLevelXp = currentLevelXp,
            levelRequiredXp = step,
            progressRatio = ratio,
            xpToNextLevel = diff,
            rank = ranks[rkIndex],
            rankIndex = rkIndex,
        )
    }

    override fun rank(level: Int): String {
        val idx = (level.coerceAtLeast(1) - 1).coerceAtMost(ranks.size - 1)
        return ranks[idx]
    }
}

/**
 * Registry and active provider for Level Curves.
 * Production default is strictly [DefaultProductionLevelCurve].
 */
object LevelCurveRegistry {
    private val curves = mutableMapOf<String, LevelCurve>()
    var activeCurve: LevelCurve = DefaultProductionLevelCurve()

    init {
        register(DefaultProductionLevelCurve())
        register(BalancedLevelCurve())
    }

    fun register(curve: LevelCurve) {
        curves[curve.id] = curve
    }

    fun get(id: String): LevelCurve? = curves[id]

    fun resetToDefault() {
        activeCurve = curves["production_default_500"] ?: DefaultProductionLevelCurve()
    }
}
