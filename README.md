# 自律原型

Android 无障碍服务识别前台应用，手动锁定选中应用；选中的抖音和 B站共用手动添加的视频分钟。额度只在锁定开启且视频应用位于前台时扣除。解锁开关和紧急解锁均可恢复使用。数据保存在设备本地，不使用 VPN。

## 构建

使用 JDK 17、Android SDK 35 和可访问 Google Maven / Maven Central 的网络，运行 `gradlew.bat assembleDebug`。APK 路径为 `app/build/outputs/apk/debug/app-debug.apk`。

安装后打开应用，在系统无障碍设置中开启「自律原型」，选择应用并开启锁定。可在页面添加共享视频额度。建议在 ColorOS 电池设置中允许后台运行；系统重启后依靠 Android 重新绑定已授权的无障碍服务。

这是未经真机验证的原型。系统限制、后台清理、画中画和多窗口行为尚需在目标手机验证；无障碍服务失效时无法拦截或计时。请勿把它作为不可绕过的限制。

## 开源参考

前台窗口事件识别参考并改写自 [SelfLock](https://github.com/EtashTyagi/SelfLock)，其 MIT 许可证和版权声明见 `LICENSES/SelfLock.txt`。QuestPhone 仅参考任务完成后解锁的产品思路，未复制 GPL 代码。
