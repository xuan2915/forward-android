package com.focus.prototype

import android.os.Build
import android.view.Window
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

internal fun Window.useDarkSystemBars() {
    statusBarColor = AppColors.Background.toArgb()
    navigationBarColor = AppColors.Background.toArgb()
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
    if (Build.VERSION.SDK_INT >= 29) {
        isNavigationBarContrastEnforced = false
        isStatusBarContrastEnforced = false
    }
}
