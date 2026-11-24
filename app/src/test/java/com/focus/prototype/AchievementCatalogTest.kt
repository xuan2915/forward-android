package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {

    @Test
    fun testCatalogSpecificationIntegrity() {
        val result = AchievementCatalog.validateCatalog()
        if (!result.isValid) {
            System.err.println("Catalog validation errors:\n" + result.errors.joinToString("\n"))
        }
        assertTrue("Catalog validation must pass with zero errors: ${result.errors}", result.isValid)

        // Count assertions
        assertEquals(108, AchievementCatalog.all108.size)
        assertEquals(68, AchievementCatalog.collectionSlots.size)

        // Subgroups count
        val evolution = AchievementCatalog.all108.filter { it.kind == AchievementKind.EVOLUTION }
        val independent = AchievementCatalog.all108.filter { it.kind == AchievementKind.INDEPENDENT }
        val challenge = AchievementCatalog.all108.filter { it.kind == AchievementKind.CHALLENGE }
        val secret = AchievementCatalog.all108.filter { it.kind == AchievementKind.SECRET }

        assertEquals("8 series * 6 stages = 48 milestones", 48, evolution.size)
        assertEquals("5 groups * 6 = 30 independent milestones", 30, independent.size)
        assertEquals("3 groups * 6 = 18 challenge milestones", 18, challenge.size)
        assertEquals("12 secret milestones", 12, secret.size)

        // Slots count
        val evolutionSlots = AchievementCatalog.collectionSlots.filter { it.kind == AchievementKind.EVOLUTION }
        val indepSlots = AchievementCatalog.collectionSlots.filter { it.kind == AchievementKind.INDEPENDENT }
        val chalSlots = AchievementCatalog.collectionSlots.filter { it.kind == AchievementKind.CHALLENGE }
        val secretSlots = AchievementCatalog.collectionSlots.filter { it.kind == AchievementKind.SECRET }

        assertEquals(8, evolutionSlots.size)
        assertEquals(30, indepSlots.size)
        assertEquals(18, chalSlots.size)
        assertEquals(12, secretSlots.size)
        assertEquals(68, evolutionSlots.size + indepSlots.size + chalSlots.size + secretSlots.size)

        assertEquals(
            listOf("行动的故事", "清单的终点", "时间的朋友", "专注的深度", "沉浸时刻", "计划之外", "娱乐的边界", "与昼夜和解"),
            evolutionSlots.sortedBy { it.slotId }.map { it.title },
        )
    }

    @Test
    fun testAvailableVsPendingEvidenceReasonIntegrity() {
        // Every item marked PENDING_EVIDENCE must have a non-blank pendingReason explaining the missing dependency
        AchievementCatalog.all108.forEach { item ->
            if (item.releaseStatus == ReleaseStatus.PENDING_EVIDENCE) {
                assertFalse("Milestone ${item.id} must document pendingReason", item.pendingReason.isBlank())
            }
        }
    }

    @Test
    fun testLegacyIdMapping() {
        assertEquals("achievement.evolution.action.stage_1", AchievementCatalog.mapLegacyId("task.first"))
        assertEquals("legacy.task.ten", AchievementCatalog.mapLegacyId("task.ten"))
        assertNotNull(AchievementCatalog.findById("task.ten"))
        assertNotNull(AchievementCatalog.findById("legacy.task.fifty"))
        assertEquals("achievement.evolution.streak.stage_1", AchievementCatalog.mapLegacyId("streak.three"))
        assertEquals("achievement.evolution.streak.stage_3", AchievementCatalog.mapLegacyId("streak.thirty"))
        assertEquals("achievement.evolution.focus.stage_1", AchievementCatalog.mapLegacyId("focus.hour"))
    }

    @Test
    fun testDependencyCycleIsActuallyDetected() {
        val slot = AchievementCatalog.collectionSlots.first()
        val cyclic = AchievementCatalog.all108.first().copy(
            id = "test.cycle",
            slotId = slot.slotId,
            prerequisiteIds = listOf("test.cycle"),
        )
        val result = AchievementCatalog.validateCatalog(listOf(cyclic), listOf(slot))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Cyclic achievement prerequisites") })
    }
}
