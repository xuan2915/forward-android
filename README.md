# 向前 · Android 自律

本地优先的 Android 自律 App。先确认必做和选做任务，专注后按预设要求自检，完成必做任务后领取当日娱乐额度；选做奖励在领取时二选一。抖音和 B 站共享视频分钟池，游戏局数保留为手动账本。

首页提供今日任务与保护状态。奖励页管理应用拦截、基础与选做额度、临时 B 站学习窗口和奖励记录。成长页显示 XP、等级、当前及最佳连续记录、月历、周/月完成率和专注时长。复盘支持手写、粘贴文本或从 OPPO 笔记分享纯文本；识别出的明日任务先由用户确认。设置可创建未来任务、预设休息日、调整次日生效的自律日界线，以及导出/恢复含附件的本地备份。

支持一次性、每日及指定星期任务。任务模板与日期实例分开保存；补生成缺失日期时保留未完成历史，编辑未来周期不会改写完成记录。确认当天计划后必做条件冻结；计划变更会要求理由并留痕，删减必做不会自动解锁。紧急解锁单独记录，不授予经验或额度。

照片、PDF 和文本附件保存在应用私有目录并按完整文件查看。所有任务、计时、奖励流水、日历、复盘和受限应用设置保存在本机。备份可经 Android 文件选择器导出和恢复。

## 构建和验证

使用 JDK 17、Android SDK 35、Build Tools 35.0.0：

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。业务单元测试使用 Robolectric Android 9 SQLite，并可通过 Android Instrumentation 在已连接设备或模拟器运行。`tools/build-fixtures.py` 仅生成空白计费测试宿主；只有在专用 Android 模拟器上运行 `tools/service-check.py` 才会把它们安装到抖音/B站包名，不能将其当作真实应用验收。

## 已知界限

自检与照片不会被 AI 或其他方式判断真实性。游戏不识别对局，也不自动扣局或拦截局内行为。OPPO ColorOS 17 的无障碍保活、系统调度和真实抖音/B站切换仍需 Find X8 实机验收；系统可随时撤销授权、停止服务或卸载应用。

## 开源代码

前台应用识别和拦截服务参考并改写自 SelfLock，MIT 版权与许可证保留在 `LICENSES/SelfLock.txt`。成长日历集成 Kizitonwose Calendar（MIT），成长趋势图集成 Vico（Apache-2.0），许可文本及 UI 参考范围见 `LICENSES/ThirdParty.md`。Compose Samples（Jetsnack）与 Grit 只用于研究设计组织，未复制源码；QuestPhone 只参考交互思路，没有复制 GPL-3.0 源码。
