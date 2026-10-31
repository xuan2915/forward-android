# Third-party components

- `com.kizitonwose.calendar:compose:2.6.1` — MIT License. Used for the interactive history calendar.
- `com.patrykandpatrick.vico:compose-m3:1.16.0` — Apache License 2.0. Used for the real seven-day completion trend chart.
- `com.materialkolor:material-kolor:1.7.1` — MIT License. Generates Grit's default fixed-seed tonal dark palette; the included Google Material Color Utilities code is Apache-2.0. Version 1.7.1 keeps compatibility with this app's Compose/AGP toolchain.
- `android/compose-samples` (Jetsnack) — studied for Compose design-system organization and component consistency; no source files or assets were copied.
- Grit UI source is now adapted under GPL-3.0-or-later; see Grit-PORT.md and the retained file headers.
- Grit's task-list and growth-statistics page structure, task card, streak heatmap, calendar-day treatment, theme seed and typography are directly adapted; see Grit-PORT.md for exact source-to-file mapping.

No source code from Jetsnack is bundled. Calendar and Vico are runtime dependencies; their license texts are included beside this notice.

- compose-animations by 2026 skydoves (Jaewoong Eum), Apache-2.0: adapted AnimationExample5 directional counter and AnimationExample11 PlayPauseMorph geometry in Motion.kt. Colors, timing and state bindings changed; no demo business state included.
- compose-lab by 2026 Dimitris Vlachos, MIT: adapted TypewriterDemo BubbleGrow non-bouncing content-size spring. Other components studied only.
- Grit TaskCard.kt adapted in GritTaskCard.kt; see Grit-PORT.md.

Motion source revisions: compose-animations https://github.com/skydoves/compose-animations at eb51389891c303a0ad79c73525bd97acfdf96547; compose-lab https://github.com/DimiVlachos/compose-lab at bd43f2e4129592a1de93cb1570548b7b50ad445a.
