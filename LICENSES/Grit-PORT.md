# Grit UI source port

Upstream: [shub39/Grit](https://github.com/shub39/Grit), source snapshot `e664ffd21e9a75868c33f84ec6d5ba075c3acca1`. Copyright (C) 2026 Shubham Gorai. GPL-3.0-or-later. The derivative app source is distributed under GPL-3.0-or-later; copied Kotlin files retain the upstream notice.

## Directly adapted into this app

- `shared/ui/.../theme/GritTheme.android.kt` and `shared/ui/.../theme/ProvideTypography.kt` -> `app/src/main/java/com/focus/prototype/GritTheme.kt`, `GritTypography.kt`; fixed default seed `#FFFFFF`, dark `TONALSPOT` palette and Figtree. Android status bars and font resource loading are platform adapters. MaterialKolor `1.7.1` supplies the same seeded TONALSPOT generation while remaining compatible with this app's Compose and AGP versions.
- `shared/ui/.../habit/ui/sections/HabitsList.kt` -> `GritTodayComponents.kt` plus the today-page composition in `MainActivity.kt`: large title/completion summary, category pills, task grouping and Grit spacing.
- `shared/ui/.../task/ui/component/TaskCard.kt` -> `GritTaskCard.kt` and the task detail adapter in `MainActivity.kt`: container/content color transitions, bold/struck title, completion mark, compact row, grouped connected shapes and quick action. Existing proof, focus and reward callbacks remain the app's own.
- `shared/ui/.../habit/ui/component/stats/StartStats.kt` -> `GrowthHero` and `GritGrowthStat` in `ProductComponents.kt`: 200dp / 36dp ring and stacked stat tiles, bound to the app's XP and streak data.
- `shared/ui/.../habit/ui/sections/WeeklyHeatMap.kt` -> `GritWeeklyHeatMap.kt`: weekday rail, horizontally browsable week columns, month markers, state colors and day selection.
- `shared/ui/.../EnumExt.kt` `heatMapStreakShape` -> `gritHeatMapStreakShape` in `GritCalendarDay.kt`.
- `shared/ui/.../habit/ui/component/AnalyticsCard.kt` -> `GritAnalytics.kt` and its use throughout `GrowthDetails.kt`, including the upstream section icon resources in `app/src/main/res/drawable/grit_*.xml`.
- `shared/ui/.../habit/ui/component/CalendarCommon.kt` and `EnumExt.kt` -> Grit-style day/streak cell treatment in `GritCalendarDay.kt`; `GrowthDetails.kt` retains Kizitonwose's existing selectable monthly calendar and binds it to existing history records. `CalendarMap.kt` was inspected but its calendar model was not ported.
- `shared/ui/.../components/ListItemExt.kt` -> `GritListShapes.kt`.

Existing Kizitonwose Calendar and Vico remain in use for the selectable monthly calendar and real trend chart. Grit's new Material Expressive APIs were adapted to the project's Compose BOM rather than bringing in Grit's full Kotlin Multiplatform build.

## Not migrated

Grit's task and habit databases, categories editor, drag/reorder, reminders, subtasks, account/cloud, subscription/paywall, and complete navigation architecture are not imported. The whole Grit Android app is not an app dependency; this is a limited source port and adapter set. No QuestPhone/GPL source is included.
