package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRulesTest {
    @Test fun launcherAlwaysStartsOnTodayRegardlessOfStoredImportDraft() {
        val destination = resolveInitialNavigation("android.intent.action.MAIN", null)
        assertEquals(0, destination.page)
        assertFalse(destination.openPlanManagement)
    }

    @Test fun sharedTextOpensPlanManagementOnToday() {
        val destination = resolveInitialNavigation("android.intent.action.SEND", "text/plain")
        assertEquals(0, destination.page)
        assertTrue(destination.openPlanManagement)
    }

    @Test fun emptyShareDoesNotOpenImportFlow() {
        val destination = resolveInitialNavigation("android.intent.action.SEND", "text/plain", hasSharedText = false)
        assertEquals(0, destination.page)
        assertFalse(destination.openPlanManagement)
    }

    @Test fun debugPageOverrideOnlyAppliesInDebugBuilds() {
        assertEquals(5, resolveInitialNavigation("android.intent.action.MAIN", null, 5, true).page)
        assertEquals(0, resolveInitialNavigation("android.intent.action.MAIN", null, 5, false).page)
    }
}
