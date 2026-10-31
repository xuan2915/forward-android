# Grit UI source port

Upstream: https://github.com/shub39/Grit at e664ffd21e9a75868c33f84ec6d5ba075c3acca1. Copyright (C) 2026 Shubham Gorai, GPL-3.0-or-later. The derivative application source is provided under GPL-3.0-or-later (root LICENSE). Existing MIT and Apache notices remain in LICENSES.

Actual adapted source:
- theme/ProvideTypography.kt -> GritTypography.kt, Figtree resource copied into res/font, SIL OFL in Figtree-OFL.txt. Android Font replaces KMP resource loading.
- androidMain/theme/GritTheme.android.kt -> GritTheme.kt: Material You dark branch. MaterialExpressiveTheme/MotionScheme adapted to current MaterialTheme and compatible tokens. Custom seed palette/font settings are not ported.
- components/ListItemExt.kt -> GritListShapes.kt: connected/detached shape functions. Newer expressive ListItemShapes API omitted.
- task/ui/component/TaskCard.kt -> GritTaskCard.kt: Card/Row/Column, status color transitions, bold title and completion strike-through. Tap opens current task detail; completion retains existing verification workflow. Reminder/subtask/reorder architecture not ported.
- habit/ui/component/AnalyticsCard.kt -> GritAnalytics.kt: header layout, typography and slot. Paywall removed.
- habit/ui/component/stats/StartStats.kt -> GrowthHero in ProductComponents.kt: ring and rounded streak panels. XP and streak values use existing Database.
- habit/ui/component/CalendarCommon.kt and EnumExt.kt -> GritCalendarDay.kt: monthly day rendering and contiguous completion shapes, java.time and actual history adapter.
- CalendarMap.kt layout adapted in GrowthDetails.kt, existing Calendar 2.6.1 and Vico retained.

Reference only: AnalyticsPage's multi-column navigation and WeeklyHeatMap; full Grit business architecture, subscription, account, notifications and paid features were not ported. This is an adapted subset, not a complete recreation of every Grit screen.
