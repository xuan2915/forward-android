package com.focus.prototype

internal data class InitialNavigation(
    val page: Int,
    val openPlanManagement: Boolean,
    val openSettingsSection: String? = null,
    val showSleepRationale: Boolean = false,
)

internal fun resolveInitialNavigation(
    action: String?,
    mimeType: String?,
    debugPage: Int? = null,
    debugBuild: Boolean = false,
    hasSharedText: Boolean = true,
): InitialNavigation {
    val isHealthRationale = action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            action == "android.intent.action.VIEW_PERMISSION_USAGE"
    if (isHealthRationale) {
        return InitialNavigation(
            page = 5,
            openPlanManagement = false,
            openSettingsSection = "sleep",
            showSleepRationale = true
        )
    }
    val sharedPlan = action == "android.intent.action.SEND" && mimeType == "text/plain" && hasSharedText
    val page = if (debugBuild && debugPage != null) debugPage.coerceIn(0, 5) else 0
    return InitialNavigation(page = page, openPlanManagement = sharedPlan)
}
