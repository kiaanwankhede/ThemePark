# ThemePark

An idle theme-park game for Android with a home-screen widget, so you can collect earnings and
upgrade rides without opening the game.

## Design rules

- **No real money.** Membership is built in: no ads, and the 2× income boost starts without
  watching anything.
- **Premium items cost gems you earn by playing** (daily visit, ride milestones), so there is
  still something to work toward.
- **Less addictive by design.** Earnings stop 8 hours after your last collect, so a few
  check-ins a day is the natural rhythm, and most of them can happen from the widget.

## Layout

| Module | What it is | Status |
|---|---|---|
| `core/` | Pure-Kotlin game rules: economy, offline earnings, upgrades, boost, gem shop, save file. Shared by the app and the widget so both always agree. | Done, unit tested |
| `app/` | Android app (Jetpack Compose): park screen, upgrades, shop | Not started |
| `widget/` | Home-screen widget (Jetpack Glance): collect + suggested upgrade | Not started |

Every rule in `core` is a pure function of `(state, now)`, with no clock or I/O inside, which
is what lets the widget apply an action to the save file while the game is closed.

## Build and test

```
./gradlew :core:test
```

Requires JDK 17 or newer.
