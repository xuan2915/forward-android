package com.focus.prototype

import android.os.Build
import android.view.Window
import androidx.core.view.WindowCompat

internal fun Window.useDarkSystemBars() {
    statusBarColor = android.graphics.Color.rgb(17, 18, 20)
    navigationBarColor = android.graphics.Color.rgb(17, 18, 20)
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.isAppearanceLightStatusBars = false
    controller.isAppearanceLightNavigationBars = false
    if (Build.VERSION.SDK_INT >= 29) {
        isNavigationBarContrastEnforced = false
        isStatusBarContrastEnforced = false
    }
}
