# 向前成长底层 P2/P3 与 P1 衔接｜Codex 独立复验记录

状态：定向验收通过，Debug 候选已构建；未提交、未安装真机。本文为当前复验结论，修正此前 Gemini 交付报告中的不准确项。

## 1. 基线与产物

- 工作树：`C:\Users\17066\Documents\Codex\2026-10-30\android-app-try-catch-apk-main-data-protection`
- 分支与基线：`feat/data-protection-complete`，`6f6f469b99ada0ea458070b62269165debadc96f`
- 应用：`com.focus.prototype`，versionName `0.10.10`，versionCode `37`；数据库版本仍为 `8`
- Gemini 原候选保留不覆盖：`dist/forward-0.10.10-growth-foundation-debug.apk`，SHA-256 `19efecfc47cf262f3ae28ad249aaa13e9dabd959fdc1b919706a75ef06ed867b`
- 本次复验候选：`dist/forward-0.10.10-growth-foundation-codex-review-debug.apk`，21,804,929 字节，SHA-256 `64D78FE2A9FC37950E5D5A3E737BC77313F83F7F2777336F48F92FC7F2D12905`
- 候选由当前未提交工作树构建。保留了原有未提交文件；本轮未暂存、提交、推送、安装或操作手机。

## 2. 当前实现与修正

### XP 与等级

- `XpLedger.kt` 提供版本化 XP 记录、按来源查询、幂等发放、冲正和重做；数据库唯一索引约束 `(source_type, source_id)`，并约束非空幂等键唯一。生产任务来源类型实际为 `task`，不是原报告所写的 `TASK_COMPLETION`。
- 旧完成任务以每项 100 XP 幂等迁入，排除睡眠系统任务与已移动的源任务。任务完成时，完成状态和 XP 记账处于同一 SQLite 事务；撤销时 XP 冲正随任务撤销事务处理。生产等级曲线仍为每级 500 XP；平衡曲线仅为可配置候选，不改变生产默认值。
- 备份包含 XP 与成就表；导入缺少新表的旧格式时会初始化 XP/成就表并从任务种子补齐。定向集成测试覆盖旧格式导入、重复恢复和失败事务回滚。

### 成就目录与计算

- 目录实际为 108 个里程碑、68 个收藏槽。源码中的八条成长线名称依次是：行动的故事、清单的终点、时间的朋友、专注的深度、沉浸时刻、计划之外、娱乐的边界、与昼夜和解。
- 实际发布状态：41 项 `AVAILABLE`（成长线 01–04、06 共 30 项；独立 6 项；隐藏 5 项）；67 项 `PENDING_EVIDENCE`（成长线 05、07、08 共 18 项；独立 24 项；挑战 18 项；隐藏 7 项）。可用状态只表示当前有对应实现与数据入口，不代表挑战系统已实现。挑战成就全部待挑战记录接入。
- 补齐目录校验：除数量、唯一 ID/槽位、阶段阈值与归属外，实际检查前置依赖引用/循环、每个可用成就是否映射到引擎规则、待证据项是否说明缺失依据。
- 修正一项真实不可达成就：`不止三分钟热度` 的目录 ID 与计算器分支曾不一致，现统一为 `achievement.indep.balance.more_than_spark`，增加连续 12 周满足条件的生产引擎正向测试。
- 高质量必做全清要求有确认的冻结计划、确认时间、有效且类型已知的必做项，并要求完成时间晚于确认；未确认日期不推断为全清。连续周/月计算按日历相邻周期处理，缺周不能拼接。
- 增加/验证多条件判定：中断至少 14 个已确认未执行日后重建 30 个有效执行日；两个不同成长线在同日达到 IV 阶段；VI 阶段后同一领域继续 90 个有效日；首年 365 日内至少 240 个有效执行日；收藏 60 个不同有效槽位且至少 6 个来自挑战/隐藏，排除自身槽位。
- 未解锁隐藏成就通过公开模型使用通用内容，不暴露名称、规则或具体进度；解锁不增加 XP 或奖励。
- 旧 8 个 ID 提供映射；独立历史解锁行仍可查询并保留其已记录日期/时间。更早只存布尔状态、未留时间戳的历史标记无法还原精确解锁时刻，本次没有伪造时间。

## 3. 对此前 Gemini 报告的必要更正

1. 原报告将成长线 05/06/07/08 写成“计划合规/可信专注/晨间起步/睡前整理”；这与实际规格和源码名称不符，现按八条真实名称及状态记录。
2. 原报告称 `night_owl` 可触发；当前目录中没有该 ID。当前可用的五项隐藏成就是“未完待续、好事成双、一切照常、原来已经这么久、一柜子的故事”。
3. 原报告的 XP 来源类型 `TASK_COMPLETION` 不符，代码实际使用 `task`。
4. 原报告的 85 项测试（84 通过、1 跳过）只是此前选定测试集，不是全项目结论；本轮重新执行后以 XML 得到下列统计。

## 4. 定向验证

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.focus.prototype.XpLedgerAndLevelCurveTest `
  --tests com.focus.prototype.AchievementCatalogTest `
  --tests com.focus.prototype.AchievementEngineTest `
  --tests com.focus.prototype.GrowthFoundationIntegrationTest `
  --tests com.focus.prototype.AchievementEvidenceTest `
  --tests com.focus.prototype.BackupRecoveryTest `
  --tests com.focus.prototype.BehaviorRulesV1LedgerTest `
  --tests com.focus.prototype.BehaviorRulesV1SleepTest `
  --tests com.focus.prototype.EmergencyUnlockTest `
  --tests com.focus.prototype.EmergencyUsageCapTest `
  --tests com.focus.prototype.SleepPenaltyExpiryTest `
  --rerun-tasks
```

读取 `app/build/test-results/testDebugUnitTest/TEST-*.xml`：11 个指定测试类，101 项、100 通过、0 失败、0 错误、1 跳过。跳过项是 `BackupRecoveryTest.suppliedPhoneCopyMigratesAndRestoresWithoutChangingOriginal`：测试通过环境变量 `FORWARD_RECOVERY_FIXTURE` 读取外部手机副本，本次未提供该副本，触发测试内的条件跳过；不是 `@Ignore`。该统计不是全仓库测试总量。随后 `:app:assembleDebug` 构建成功。

| 测试类 | 总数 | 通过 | 跳过 |
|---|---:|---:|---:|
| `XpLedgerAndLevelCurveTest` | 6 | 6 | 0 |
| `AchievementCatalogTest` | 4 | 4 | 0 |
| `AchievementEngineTest` | 18 | 18 | 0 |
| `GrowthFoundationIntegrationTest` | 7 | 7 | 0 |
| `AchievementEvidenceTest` | 7 | 7 | 0 |
| `BackupRecoveryTest` | 4 | 3 | 1 |
| `BehaviorRulesV1LedgerTest` | 23 | 23 | 0 |
| `BehaviorRulesV1SleepTest` | 19 | 19 | 0 |
| `EmergencyUnlockTest` | 11 | 11 | 0 |
| `EmergencyUsageCapTest` | 1 | 1 | 0 |
| `SleepPenaltyExpiryTest` | 1 | 1 | 0 |

覆盖点包括 XP 迁移/冲正/重做/并发幂等、目录完整性与循环检测、证据判定和隐藏信息保护、备份往返与旧格式恢复失败回滚，以及现有奖励账本、睡眠、紧急解锁相关定向回归。未执行全量测试；未做模拟器或真机 UI 操作。

## 5. 限制与续接

- 这是代码、单元测试/集成测试和 APK 构建验收；没有在设备运行或验证真实用户数据库。
- 成就 UI/美术、月度挑战实现、Health Connect 与其他真实数据来源端到端验收不在本轮完成范围。
- 旧历史标记若没有解锁时间字段，只能保留可确认状态/映射，无法恢复精确旧时间。
- 当前工作树仍包含原有未提交及未跟踪成果。本轮未提交或推送；后续集成前应按明确文件范围复核并保留这些成果。

**续接：**目标已完成至“定向测试通过 + Debug 候选生成 + 报告更正”。下一步若要正式集成，应先审阅当前工作树的文件归属和差异，再按项目流程提交；若要发布体验，需另行在隔离模拟器/设备验证，不能把本报告当作真机验收。
