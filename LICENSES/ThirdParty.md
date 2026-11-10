# Third-party components

- `com.kizitonwose.calendar:compose:2.6.1` — MIT License. Used for the interactive history calendar.
- `com.patrykandpatrick.vico:compose-m3:1.16.0` — Apache License 2.0. Used for the real seven-day completion trend chart.
- `com.materialkolor:material-kolor:1.7.1` — MIT License. Retained for palette utilities compatible with this app's Compose/AGP toolchain; the current warm and mineral schemes are app-owned and no longer use Grit's seed palette. The included Google Material Color Utilities code is Apache-2.0.
- `android/compose-samples` (Jetsnack) — studied for Compose design-system organization and component consistency; no source files or assets were copied.
- Grit UI source is now adapted under GPL-3.0-or-later; see Grit-PORT.md and the retained file headers.
- Grit's task-list and growth-statistics page structure, task card, streak heatmap and calendar-day treatment remain adapted; the theme colors and general typography are app-owned. See Grit-PORT.md for exact source-to-file mapping.

No source code from Jetsnack is bundled. Calendar and Vico are runtime dependencies; their license texts are included beside this notice.

- compose-animations by 2026 skydoves (Jaewoong Eum), Apache-2.0: adapted AnimationExample5 directional counter and AnimationExample11 PlayPauseMorph geometry in Motion.kt. Colors, timing and state bindings changed; no demo business state included.
- compose-lab by 2026 Dimitris Vlachos, MIT: adapted TypewriterDemo BubbleGrow non-bouncing content-size spring. Other components studied only.
- Grit TaskCard.kt adapted in GritTaskCard.kt; see Grit-PORT.md.

## Achievement visual resources

- Achievement medals in `app/src/main/java/com/focus/prototype/Achievements.kt` are original Compose Canvas drawings created for this app; no icon artwork or animation assets were copied.
- Jetpack Compose animation APIs from the existing AndroidX Compose BOM `2024.12.01` are used for medal reveal, halo, and status changes. Project toolchain: Kotlin `2.0.21`, Compose BOM `2024.12.01`, AGP `8.7.3`.
- No additional animation or icon dependency was added. Evaluated options:

| Project | Revision / version reviewed | License | Use in this release |
| --- | --- | --- | --- |
| [Lottie Android](https://github.com/airbnb/lottie-android) | `6.7.1` | Apache-2.0 | Not included; vector animation playback is unnecessary for the locally drawn medal reveal |
| [Konfetti](https://github.com/DanielMartinus/Konfetti) | `2.2.0` | ISC | Not included; particle effects are intentionally not used for ordinary achievements |
| [Game-icons](https://github.com/game-icons/icons) | Repository checked `2026-11-07` | CC BY 3.0 or CC0, per asset | Not included; each CC BY icon requires its own author attribution |
| [Compose Phosphor Icons](https://github.com/adamglin0/compose-phosphor-icon) | README checked; published artifact `2.1.0` | MIT | Not included; achievement badges need custom medal silhouettes rather than general-purpose icons |
| [Pomodoro Timer Android](https://github.com/Micheal-Jiaming/Pomodoro-timer-Android) | Repository reviewed `2026-11-10` | Apache-2.0 | Interaction and deadline-driven timing reference only; no source or assets copied |
| [Tomato](https://github.com/nsh07/Tomato) | Repository reviewed `2026-11-10` | GPL-3.0 | Visual and statistics reference only; no source or assets copied |

No external animation asset is loaded. The current Compose version supports the native animation APIs used here, so adding a playback or particle library would increase app size without improving this interaction.

Motion source revisions: compose-animations https://github.com/skydoves/compose-animations at eb51389891c303a0ad79c73525bd97acfdf96547; compose-lab https://github.com/DimiVlachos/compose-lab at bd43f2e4129592a1de93cb1570548b7b50ad445a.
