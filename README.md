# ThemePark

An idle theme-park game for Android with a home-screen widget, so you can collect earnings and
upgrade rides without opening the game.

## Design rules

- **No real money.** Membership is built in: no ads, and the 2× income boost starts without
  watching anything.
- **Premium items cost gems you earn by playing** (daily visit, ride milestones), so there is
  still something to work toward.
- **A park you can finish.** 12 rides (plus a gem-only Golden Coaster), each with a top level
  between 100 and 250. Income ×5 every 25 levels; prices climb into the aa, ab… ranges. With
  four check-ins a day a new ride opens every few days and the park is done in about 6–10 weeks
  (`core/src/test/.../BalanceTest.kt` plays the real rules to check this).
- **Less addictive by design.** Earnings stop 8 hours after your last collect, so a few
  check-ins a day is the natural rhythm, and most of them can happen from the widget.

## Layout

| Module | What it is | Status |
|---|---|---|
| `core/` | Pure-Kotlin game rules: economy, offline earnings, upgrades, boost, gem shop, save file. Shared by the app and the widget so both always agree. | Done, unit tested |
| `app/` | Android app (Jetpack Compose): park screen with collect, boost, daily gems, ride upgrades and the gem shop; plus the home-screen widget (Jetpack Glance) with Collect and the cheapest upgrade | First version, built by CI |

Every rule in `core` is a pure function of `(state, now)`, with no clock or I/O inside, which
is what lets the widget apply an action to the save file while the game is closed.

## Build and test

```
./gradlew :core:test :app:assembleDebug
```

Requires JDK 17 and the Android SDK (Android Studio installs it). Every push is built by
GitHub Actions, which uploads `app-debug.apk` as a downloadable artifact on the run's page.
