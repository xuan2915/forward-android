# Grit UI source port

Upstream: [shub39/Grit](https://github.com/shub39/Grit), source snapshot `e664ffd21e9a75868c33f84ec6d5ba075c3acca1`. Copyright (C) 2026 Shubham Gorai. GPL-3.0-or-later. Directly derived Kotlin in this app retains the upstream notice; this derivative source is distributed under GPL-3.0-or-later. See `Grit-GPL-3.0.txt`.

## Page-level source migration

| Grit source | Self Discipline adaptation | Status |
| --- | --- | --- |
| `shared/ui/.../theme/GritTheme.android.kt` | `app/src/main/java/com/focus/prototype/GritTheme.kt` | Grit's dark TONALSPOT structure and Android system-bar adaptation inform the theme; current warm and mineral palettes are app-owned and selectable. |
| `shared/ui/.../theme/ProvideTypography.kt` | `GritTypography.kt`, `res/font/google_sans_flex.ttf`, `res/font/poppins_regular.ttf` | Grit's display-font resources remain available for selected emphases. The 0.10.0 general text hierarchy uses platform Sans Serif for a quieter editorial direction. Font OFL files are in this folder. |
| `shared/ui/.../task/ui/section/TaskList.kt` | `GritPortedPages.kt` `GritTaskListPage` | Page hierarchy adapted from Grit's large task header, horizontal category selector, grouped compact list and add action. Self Discipline keeps its required/optional filters and plan action. |
| `shared/ui/.../task/ui/component/TaskCard.kt` | `GritPortedPages.kt` `GritTaskListCard` | Compact title-only Grit task row, bold typography, completion strike-through, state colors and connected shapes. Tapping opens the existing verification/detail flow instead of bypassing it. |
| `shared/ui/.../habit/ui/sections/AnalyticsPage.kt` | `GritPortedPages.kt` `GritHabitAnalyticsPage` | The original analytics section order is retained for a selected task: overview, weekly heat map, monthly calendar and weekly activity. |
| `shared/ui/.../habit/ui/component/stats/StartStats.kt` | `GritPortedPages.kt` `GritStartStats` / `GritStatTile` | 200dp, 36dp progress ring and the three stacked start-date/streak statistics, bound to the selected task's actual history. |
| `shared/ui/.../habit/ui/component/stats/WeeklyHeatMap.kt` | `GritWeeklyHeatMap.kt` | Weekday rail, 35dp date cells, month markers, streak forms and horizontal year browsing. |
| `shared/ui/.../habit/ui/component/stats/CalendarMap.kt` | `GritPortedPages.kt` and `GritCalendarDay.kt` | Original selectable monthly calendar role and connected completion-day treatment; Kizitonwose Compose Calendar is reused. |
| `shared/ui/.../habit/ui/component/stats/WeeklyActivity.kt` | `GritPortedPages.kt` `GritWeeklyActivity` | Grit period selector and vertical completion bars use actual task completion history. |
| `shared/ui/.../EnumExt.kt` | `GritCalendarDay.kt` | Grit heat-map streak corner logic adapted for `java.time.LocalDate`. |

`GritUiAdapter.kt` is the data bridge. It reads the existing task templates, dated task instances, completion/elapsed records and configured rest days. It does not create a second task store or modify reward, focus, XP or application-blocking rules. One-task consistency is computed only over that task's scheduled dates; it is not the app-wide completion rate.

The copied original vector resources use `grit_` names under `app/src/main/res/drawable/`. Only required UI resources are included. Existing Kizitonwose Calendar and Vico libraries remain; no full Grit application module or Grit business system is added.

## Not migrated

Grit task/habit persistence, drag/reordering, reminder scheduling, measured-habit entry, account/cloud, subscription/paywall and navigation architecture are not imported. Grit is not a runtime dependency. No QuestPhone/GPL source is included.
