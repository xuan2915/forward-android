package com.focus.prototype

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class BlockOverlayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    private lateinit var db: Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.rgb(17, 18, 20)
        window.navigationBarColor = android.graphics.Color.rgb(17, 18, 20)
        window.decorView.systemUiVisibility = 0
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        db = Database(this)
        render()
    }

    private fun render() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val blockedPackage=intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val exhausted = if(blockedPackage in Store.games) db.balance("game")==0L else db.balance("video")==0L&&db.tasks().any{it.required&&it.done}
        val nextReward=db.tasks().firstOrNull{it.required&&!it.done&&(if(blockedPackage in Store.games)it.rewardGames>0 else it.rewardMinutes>0)}
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(27), dp(28), dp(27), dp(28))
            setBackgroundColor(0xff111214.toInt())
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        fun button(value: String, primary: Boolean, action: () -> Unit) {
            val fill = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (primary) 0xffa6b8ae.toInt() else 0xff23262a.toInt())
            }
            val ripple = RippleDrawable(ColorStateList.valueOf(0x30ffffff), fill, null)
            val view = label(value, 15f, if (primary) 0xff161b18.toInt() else 0xfff1f0ed.toInt(), true).apply {
                background = ripple
                isClickable = true
                isFocusable = true
                setPadding(dp(18), dp(16), dp(18), dp(16))
                setOnClickListener { action() }
            }
            panel.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }

        panel.addView(label("自律 · 娱乐时间", 12f, 0xffa1a4a7.toInt(), true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        panel.addView(label(if (exhausted) "娱乐额度已用完" else "先完成一项必做任务", 29f, 0xfff1f0ed.toInt(), true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val reason=when {
            nextReward!=null -> "完成「${nextReward.title}」后获得 ${if(blockedPackage in Store.games)"${nextReward.rewardGames} 局游戏奖励" else "${nextReward.rewardMinutes} 分钟视频时间"}"
            exhausted -> "完成下一项有奖励的必做任务，获得对应额度"
            else -> "在今日任务中设置必做奖励，完成后即可使用"
        }
        panel.addView(label(reason, 16f, 0xffa1a4a7.toInt()), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(25) })
        panel.addView(label("${db.balance("video") / 60000}", 42f, 0xfff1f0ed.toInt()), LinearLayout.LayoutParams(-1, -2))
        panel.addView(label("剩余共享视频分钟", 13f, 0xff74787d.toInt()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(20) })
        button("返回自律 App", true) {
            FocusService.overlayDismissed()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        button("紧急解锁", false) {
            db.emergency()
            FocusService.overlayDismissed()
            finish()
            blockedPackage?.let { packageManager.getLaunchIntentForPackage(it) }?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        button("返回桌面", false) {
            FocusService.overlayDismissed()
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xff111214.toInt())
            addView(panel, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(scroll)
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
