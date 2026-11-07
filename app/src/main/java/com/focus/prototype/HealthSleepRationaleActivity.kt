package com.focus.prototype

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

class HealthSleepRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "向前只申请读取睡眠时段，用来填写就寝和起床时间。数据仅在本机处理，不会写入健康平台或上传。读取到的记录需要你确认后才会保存到睡眠记录并参与后续奖励抵扣。"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 25, 22))
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        })
    }
}
