package com.focus.prototype

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

class HealthSleepRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "仅读取睡眠时段，用于填写就寝和起床时间 · 数据保存在本机 · 确认后才会计入记录和后续奖励抵扣"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 25, 22))
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        })
    }
}
