# 自律成长 Android 原型

本地应用：每日契约、必做和选做任务、计时、自检、相机照片、视频及游戏奖励账本、复盘和成长信息。抖音与 B站的已选应用共享视频额度；游戏局数由用户手动记录。无 AI、账号、服务器或 VPN。

安装后首先添加任务并确认每日契约，开启系统无障碍服务，并在奖励页选择受限应用。必做任务完成并自检后，在奖励页领取基础额度；选做任务完成后可选取其预设奖励。选择计划休息日时不能有必做任务。可在系统设置中随时撤销授权或卸载。

目前为开发版本，尚非完整 PRD V1：任务编辑和冻结后的计划变更、可配置日界线、完整周期类型、全尺寸照片和文件附件、数据备份恢复、奖励与使用历史展示、完整成长日历/统计和成就等尚未完成。系统重启后的计时与 ColorOS 的实际拦截、锁屏及后台行为尚未经过设备验收，不能保证可靠拦截或准确计费。

## 构建

使用 JDK 17、Android SDK 35、Build Tools 35.0.0，运行 `gradlew.bat :app:assembleDebug`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 开源参考

前台窗口事件识别参考并改写自 [SelfLock](https://github.com/EtashTyagi/SelfLock)，其 MIT 许可证和版权声明见 `LICENSES/SelfLock.txt`。QuestPhone 仅参考任务完成后解锁的产品思路，未复制 GPL 代码。
