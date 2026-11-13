package com.focus.prototype

internal data class InitialNavigation(
    val page: Int,
    val openPlanManagement: Boolean,
)

internal fun resolveInitialNavigation(
    action: String?,
    mimeType: String?,
    debugPage: Int? = null,
    debugBuild: Boolean = false,
    hasSharedText: Boolean = true,
): InitialNavigation {
    val sharedPlan = action == "android.intent.action.SEND" && mimeType == "text/plain" && hasSharedText
    val page = if (debugBuild && debugPage != null) debugPage.coerceIn(0, 5) else 0
    return InitialNavigation(page = page, openPlanManagement = sharedPlan)
}
