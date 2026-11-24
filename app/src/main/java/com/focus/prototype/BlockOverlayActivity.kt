package com.focus.prototype

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
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
        val bgArgb = 0xFF13171C.toInt()
        window.statusBarColor = bgArgb
        window.navigationBarColor = bgArgb
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

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val game = db.rewardKind(blockedPackage) == "game"
        val nightConfig = db.nightWrapUpConfig()
        val nightRestricted = db.nightEntertainmentRestricted() &&
            blockedPackage in Store(this).selected &&
            !db.contract().emergency &&
            !(blockedPackage == "tv.danmaku.bili" && db.learningActive())
        val exhausted = if (game) db.balance("game") == 0L else db.balance("video") == 0L
        val nextReward = db.tasks().firstOrNull {
            it.required && !it.done && (if (game) it.rewardGames > 0 else it.rewardMinutes > 0)
        }

        // Color tokens matching AppColors
        val bgArgb = 0xFF13171C.toInt()      // AppColors.Background
        val textArgb = 0xFFF4F1EB.toInt()    // AppColors.Text
        val mutedArgb = 0xFFA9B1B9.toInt()   // AppColors.Muted
        val accentArgb = 0xFF8EADA8.toInt()  // AppColors.Accent
        val accentInk = 0xFF13171C.toInt()   // AppColors.AccentInk
        val surfaceArgb = 0xFF1F252C.toInt() // AppColors.Surface
        val borderArgb = 0xFF454E57.toInt()  // AppColors.Border

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            setBackgroundColor(bgArgb)
        }

        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        // Top Tag
        panel.addView(
            label(if (nightRestricted) "夜间收尾 · 娱乐限制" else "自律 · 娱乐时间", 12f, mutedArgb, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) },
        )

        // Main Title
        val titleText = when {
            nightRestricted -> "现在是娱乐休息时间"
            exhausted -> "娱乐额度已用完"
            else -> "先完成一项必做任务"
        }
        panel.addView(
            label(titleText, 28f, textArgb, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) },
        )

        // Reason - structured with clean pauses to avoid orphan characters
        val reasonText = when {
            nightRestricted -> "已选娱乐应用在 ${nightConfig.entertainmentCutoff} 至 ${nightConfig.morningRelease}\n处于受限时段"
            nextReward != null -> "完成「${nextReward.title}」\n即可获得 ${if (game) "${nextReward.rewardGames} 局游戏奖励" else "${nextReward.rewardMinutes} 分钟娱乐时长"}"
            exhausted -> if (game) "完成下一项有奖励的必做任务\n获得对应游戏局数" else "完成下一项有奖励的必做任务\n获得对应娱乐时长"
            else -> "在今日任务中设置必做奖励\n完成后即可使用"
        }
        panel.addView(
            label(reasonText, 15f, mutedArgb),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(26) },
        )

        // Balance Number
        val balanceValue = if (game) db.balance("game").toString() else (db.balance("video") / 60000).toString()
        panel.addView(
            label(balanceValue, 44f, textArgb, true),
            LinearLayout.LayoutParams(-1, -2),
        )

        // Balance Unit / Description (No "共享视频分钟")
        val balanceDesc = if (game) "剩余游戏局数" else "剩余娱乐时长（分钟）"
        panel.addView(
            label(balanceDesc, 13f, mutedArgb),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(32) },
        )

        // Button 1: Primary Action ("查看今日任务")
        val primaryFill = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(accentArgb)
        }
        val primaryRipple = RippleDrawable(ColorStateList.valueOf(0x3013171C.toInt()), primaryFill, null)
        val primaryBtn = label("查看今日任务", 15f, accentInk, true).apply {
            background = primaryRipple
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener {
                FocusService.overlayDismissed()
                val intent = Intent(this@BlockOverlayActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_DESTINATION_PAGE, 0)
                }
                startActivity(intent)
                finish()
            }
        }
        panel.addView(primaryBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        // Button 2: Secondary Action ("紧急解锁" - lower visual weight)
        val secondaryFill = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(surfaceArgb)
            setStroke(dp(1), borderArgb)
        }
        val secondaryRipple = RippleDrawable(ColorStateList.valueOf(0x20ffffff), secondaryFill, null)
        val secondaryBtn = label("紧急解锁", 15f, textArgb, true).apply {
            background = secondaryRipple
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(15), dp(18), dp(15))
            setOnClickListener {
                FocusService.overlayDismissed()
                val intent = Intent(this@BlockOverlayActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_DESTINATION_PAGE, 5)
                    putExtra(MainActivity.EXTRA_DESTINATION_SECTION, "emergency_unlock")
                    putExtra("target_package", blockedPackage)
                }
                startActivity(intent)
                finish()
            }
        }
        panel.addView(secondaryBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Button 3: Tertiary Action ("返回桌面" - minimal visual weight)
        val tertiaryFill = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0x00000000)
        }
        val tertiaryRipple = RippleDrawable(ColorStateList.valueOf(0x20ffffff), tertiaryFill, null)
        val tertiaryBtn = label("返回桌面", 14f, mutedArgb, false).apply {
            background = tertiaryRipple
            isClickable = true
            isFocusable = true
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setOnClickListener {
                FocusService.overlayDismissed()
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }
        panel.addView(tertiaryBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgArgb)
            addView(panel, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        FocusService.overlayShown()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        FocusService.overlayLeftByUser(intent.getStringExtra(EXTRA_BLOCKED_PACKAGE))
        finish()
    }

    override fun onStop() {
        super.onStop()
        FocusService.overlayDismissed()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
