# 月度挑战底层候选交付报告 (Gemini)

- **交付日期**: 2026-11-24
- **主开发者**: Gemini (Antigravity Agent)
- **开发分支**: `feat/monthly-challenges`
- **基线提交**: `aa7923b` (feat: integrate growth foundation baseline)
- **工作树路径**: `C:\Users\17066\Documents\Codex\2026-10-30\android-app-try-catch-apk-main-growth-foundation-monthly-challenges`
- **候选 APK 绝对路径**: `C:\Users\17066\Documents\Codex\2026-10-30\android-app-try-catch-apk-main-growth-foundation-monthly-challenges\dist\forward-0.10.10-monthly-challenges-debug.apk`
- **候选 APK SHA-256**: `61053aab0536d86ba741721b89ec8c73b34569cf1870a62ac548cb28099ef197`
- **文件大小**: 20,954,472 字节 (~20.0 MB)
- **后续流程**: 交由 Codex 独立审查、修复、集成与真机验收

---

## 1. 交付范围与产品规则实现

根据交接规范 `docs/MONTHLY_CHALLENGE_GEMINI_HANDOFF_20261124.md` 及三份 V1.0 规格文件，完成了月度挑战全部底层数据与状态引擎开发：

1. **模板与内容库 (`ChallengeDefinitions.kt`)**：
   - 原样录入首批 4 个官方模板（未曾抵达之处、留下作品、熟悉世界之外、改变一种习惯）。
   - 每个模板包含 3 档难度配置（进阶、突破、极限），每档难度明确标注建议投入时长与候选经验值。
   - 每个模板包含 4 个阶段里程碑，各里程碑权重总和严格校验为 100%。
   - 每个模板包含完整 4 阶段书信内容（挑战书、第 1 封回信、深入篇章、最终回信）与专属艺术收藏标识（越界、成形、远方来信、新生）。
   - 内置 `ChallengeCatalog.validateCatalog()` 完整性自检。

2. **生命周期与状态机引擎 (`MonthlyChallengeEngine.kt`)**：
   - **推荐发放 (`getOrGenerateMonthlyOffer`)**：按自然月周期在 1~7 日窗口生成推荐；排除近 6 个月已完成的同模板与上月相同类别。
   - **唯一更换机会 (`replaceOffer`)**：严格限制仅可更换 1 次；换签后状态变为 `REPLACED`，不可恢复原挑战，二次换签抛出明确异常。
   - **跳过与过期**：支持 `skipOffer`（标记为 `SKIPPED`）与超过 7 日未接受自动 `EXPIRED`，绝不判定为失败，无惩罚。
   - **规则冻结 (`acceptChallenge`)**：接受时完整冻结模板 JSON 快照、难度、里程碑、验收标准与愿望文本；后续模板升级绝不影响已冻结实例。
   - **暂停与放弃**：`pauseChallenge`（保留进度，截止时间不延长）、`resumeChallenge`、`abandonChallenge`（主动放弃无惩罚，不扣日常任务或奖励）。
   - **证据与进度**：`submitEvidence` 记录证据并生成 SHA-256 校验和；仅通过验收的里程碑计入进度（`verifyMilestone`），纯提交证据不增加进度。
   - **到期自动结算 (`settleMonthlyChallenge`)**：
     - 全部必要里程碑完成：结算为 `COMPLETED`，开启 SQLite 事务。
     - 通过统一的 `XpLedger.award(sourceType = "challenge", ...)` 一次性发放挑战经验，并记录到 `challenge_rewards`，重复调用天然幂等。
     - 联动触发 `AchievementEngine.evaluateAll()`。
     - **严格零经济奖励**：防沉迷 `ledger` 表中娱乐分钟和游戏局数绝对为 0，不绕过任何行为管控。
     - 未达标到期：按实际状态归档为 `PARTIAL`（部分完成，不发完成奖励）或 `INCOMPLETE`（未完成）。

3. **系统集成与数据持久化**：
   - `Database.kt`：注册 5 张新表结构；注入 `onCreate`、`onOpen`、`onUpgrade`；暴露 `monthlyChallengeEngine()`。
   - `Backup.kt`：备份表列表追加 `challenge_instances`, `challenge_milestones`, `challenge_evidences`, `challenge_events`, `challenge_rewards`；恢复缺乏新表的旧备份时自动执行向后兼容初始化。

---

## 2. 真实变更文件清单

### 新增文件：
1. `app/src/main/java/com/focus/prototype/ChallengeDefinitions.kt`
   - 定义枚举（`ChallengeCategory`, `ChallengeDifficulty`, `ChallengeStatus`, `MilestoneStatus`, `VerificationType`, `EvidenceType`, `EvidenceStatus`, `ChallengeEventType`）。
   - 定义模型（`ChallengeMilestoneDefinition`, `ChallengeDifficultyConfig`, `ChallengeStoryContent`, `ChallengeTemplate`）。
   - 录入 4 大官方模板，提供完整性校验。
2. `app/src/main/java/com/focus/prototype/MonthlyChallengeEngine.kt`
   - 数据实体（`ChallengeInstance`, `ChallengeMilestoneRecord`, `ChallengeEvidenceRecord`, `ChallengeEventRecord`, `ChallengeRewardRecord`, `ChallengeProgress`）。
   - 数据库表结构初始化及所有核心生命周期方法。
3. `app/src/test/java/com/focus/prototype/MonthlyChallengeEngineTest.kt`
   - 8 项针对月度挑战底层的单元与集成测试。
4. `docs/MONTHLY_CHALLENGE_GEMINI_DELIVERY.md`
   - 本交付报告。

### 修改文件：
1. `app/src/main/java/com/focus/prototype/Database.kt`
   - 注入 `MonthlyChallengeEngine.initSchema(db)`；
   - 增加 `fun monthlyChallengeEngine(): MonthlyChallengeEngine`。
2. `app/src/main/java/com/focus/prototype/Backup.kt`
   - 导出表清单添加挑战相关 5 张表；
   - 恢复导入添加 `MonthlyChallengeEngine.initSchema(sql)` 兼容处理。

---

## 3. 测试结果与 XML 度量数据

### 3.1 月度挑战与成长底层联合测试
执行指令：
```powershell
gradlew.bat testDebugUnitTest `
  --tests com.focus.prototype.MonthlyChallengeEngineTest `
  --tests com.focus.prototype.XpLedgerAndLevelCurveTest `
  --tests com.focus.prototype.AchievementCatalogTest `
  --tests com.focus.prototype.AchievementEngineTest `
  --tests com.focus.prototype.GrowthFoundationIntegrationTest
```

**测试报告 XML 统计（`app/build/test-results/testDebugUnitTest`）**：
- **XML 报告文件总数**: 5 个
- **测试用例总数**: 43
- **通过 (Passed)**: 43
- **失败 (Failures)**: 0
- **错误 (Errors)**: 0
- **跳过 (Skipped)**: 0

### 各类测试详情：
- `MonthlyChallengeEngineTest`: tests=8, failures=0, errors=0, skipped=0
  - `testTemplateCatalogIntegrity` (PASS)
  - `testOfferAndOneTimeReplacement` (PASS)
  - `testAcceptanceAndRuleFreezing` (PASS)
  - `testProgressCalculationAndEvidence` (PASS)
  - `testPauseResumeAndAbandon` (PASS)
  - `testSettlementCompletedWithXpAwardAndZeroEconomicReward` (PASS)
  - `testSettlementPartialAndIncompleteAtDeadline` (PASS)
  - `testBackupAndRestorePreservesChallengeData` (PASS)
- `XpLedgerAndLevelCurveTest`: tests=6, failures=0, errors=0, skipped=0 (PASS)
- `AchievementCatalogTest`: tests=4, failures=0, errors=0, skipped=0 (PASS)
- `AchievementEngineTest`: tests=18, failures=0, errors=0, skipped=0 (PASS)
- `GrowthFoundationIntegrationTest`: tests=7, failures=0, errors=0, skipped=0 (PASS)

---

## 4. 构建产物信息与可复验指令

- **Gradle 构建任务**: `:app:assembleDebug`
- **构建结果**: `BUILD SUCCESSFUL in 35s`
- **产物绝对路径**: `C:\Users\17066\Documents\Codex\2026-10-30\android-app-try-catch-apk-main-growth-foundation-monthly-challenges\dist\forward-0.10.10-monthly-challenges-debug.apk`
- **SHA-256**: `61053aab0536d86ba741721b89ec8c73b34569cf1870a62ac548cb28099ef197`
- **包体大小**: 20,954,472 字节 (~20.0 MB)

### 独立复验命令：
```powershell
cd C:\Users\17066\Documents\Codex\2026-10-30\android-app-try-catch-apk-main-growth-foundation-monthly-challenges
.\gradlew.bat testDebugUnitTest --tests com.focus.prototype.MonthlyChallengeEngineTest
.\gradlew.bat :app:assembleDebug
Get-FileHash dist\forward-0.10.10-monthly-challenges-debug.apk -Algorithm SHA256
```

---

## 5. 未完成项与待定案事项提示

1. **UI 与美术资产**：按规范由用户负责，本阶段未设计新页面、徽章或动效。
2. **挑战数值定案**：候选默认 XP（进阶 300 / 突破 500 / 极限 800）已配置但尚未经产品负责人最终定案。
3. **真机环境验证**：未连接实体真机操作，本报告仅作为单元/集成测试与候选包交付依据。
