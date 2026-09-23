<div align="center">

# 🐬 Depth Diver

**A subaquatic endless-diving game built with [libGDX] and Kotlin.**

Plunge into the abyss, dodge hazards, scoop up pearls and oxygen tanks — and
surface before your air runs out. Runs on **Android** and **desktop (LWJGL3)**.

</div>

---

## 🎮 What is it?

**Depth Diver** is a 2D side-scrolling "diver" game: you steer a diver down a
procedurally scrolling underwater world where **the deeper you go, the harder it
gets** — and your **oxygen is always draining**.

| Concept | In practice |
|---------|-------------|
| 🕹️ Steering | `WASD` / arrows on desktop, **touch & drag** on Android |
| 🫁 Oxygen | Drains faster with depth; die if it hits zero |
| 💎 Pickups | **Pearls** (score — chain them inside the combo window for a `×N` multiplier) and **oxygen tanks** (restore air) |
| ⚠️ Hazards | **Rocks**, **mines**, **jellyfish**, **sharks**, and deep-water **eels** that sweep across the screen |
| 🦈 Boss event | Past 120 m a **Leviathan boss shark** can rise — a red `LEVIATHAN AHEAD!` warning plays, and if it swims past you bank **+50 pearls** |
| 🏆 Milestones | Every **50 m** you earn a `+10` pearl bonus banner |
| 💠 Combo | `COMBO xN` HUD with a draining window bar (drops back to `×1` if you miss a pearl in time) |
| 🧿 Mid-run upgrades | The pause overlay has a **QUICK BUY** row — spend pearls on OXYGEN / SPEED between breaths |
| 🎚️ Difficulty | EASY / NORMAL / HARD on the main menu, tuning oxygen drain, scroll speed and spawn rates |
| 🏅 Achievements | 9 milestones (depth, lifetime pearls, dives) with gold unlock banners and a dedicated screen |
| 🎁 Daily bonus | **+25 pearls** for your first dive of each day |
| ⏸️ Pause | `P` / `Esc` toggles a pause overlay with resume / restart / mute / quick-buy |
| 🏆 Persistence | Best depth + best score + **top-5 leaderboard** saved via libGDX `Preferences` |

**Meta screens** on the main menu: **Profile** (stats + daily bonus status),
**Leaderboard** (top 5 by score), **Shop** (5 escalating upgrade tracks), and
**Achievements** (opens from the profile screen).

The whole game is **procedurally generated at runtime** — textures via
`Pixmap` and all sound effects *and ambient music* synthesized on the fly
(drone + tide LFO + bubbles), so there are no binary art/audio assets to license.

---

## 🧱 Project layout

libGDX-style **multi-module Kotlin** project:

```
DepthDiver/
├── core/        # Everything that makes the game a game (Kotlin + libGDX)
│   └── com/depthdiver/
│       ├── DepthDiverGame.kt      # main ApplicationAdapter — game loop, screens, HUD
│       ├── AudioManager.kt        # procedural WAV synthesizer (SFX + ambience)
│       ├── Widgets.kt             # procedural pill/panel/text UI helpers
│       ├── Profile.kt             # wallet, lifetime stats, upgrades, difficulty, daily bonus
│       ├── Leaderboard.kt         # top-5 persistence
│       ├── Achievements.kt        # 9 milestone definitions + unlock flags
│       └── entity/                # Pickup (Pearl, OxygenTank), Hazard (Rock, Mine, Jellyfish, Shark, Eel)
├── desktop/     # LWJGL3 launcher (gdx-backend-lwjgl3 + gdx-platform natives)
├── app/         # Android launcher (gdx-backend-android) — package app.depthdiver
├── gradle/      # version catalog (libs.versions.toml) — all versions pinned here
└── settings.gradle.kts
```

**Package conventions:** game domain lives in `com.depthdiver` (core); the
Android application id / namespace is `app.depthdiver`; the **launcher Activity
class** is `app.deepdepthdiver.AndroidLauncher` (note the stray "e" — keep the
full `package.Class` when launching via `adb`); the desktop entry point is
`com.depthdiver.desktop.DesktopLauncherKt`.

---

## 🛠️ Tech used

| Concern | Technology |
|---------|-----------|
| Game framework | **[libGDX](https://libgdx.com) 1.14.2** — `ApplicationAdapter`, `SpriteBatch`, `Pixmap`, `OrthographicCamera`, `Gdx.input`, FreeType fonts |
| Game backend (Android) | `gdx-backend-android` + `gdx-platform` `natives-*` classifier jars (so the `libgdx.so` natives are packaged per ABI) |
| Game backend (desktop) | `gdx-backend-lwjgl3` + `gdx-platform:natives-desktop` |
| Platform | **Kotlin** + Gradle Kotlin DSL, AGP via version catalog |
| UI | libGDX `BitmapFont` HUD rendered from **procedurally drawn** pill/panel sprites — no third-party UI |
| Fonts | `OpenSans-Regular.ttf` via `FreeTypeFontGenerator` (regular + title fonts) |
| Build | Gradle wrapper, [version catalog](gradle/libs.versions.toml), configuration-cache-friendly |
| Target SDKs | Android `minSdk 24` / `targetSdk 37`, Java 11 source-level |

There is **no unused dependency**: the catalog was pruned of the appcompat /
core-ktx / junit / espresso entries that the Android Studio template ships but
this project doesn't use. `Material` is only referenced by the Android theme.

---

## 🚀 Getting started

### Prerequisites

- **JDK 17+** (`JAVA_HOME` set)
- **Android SDK** (for the `app` module) with an AVD or device
- Nothing else — no node, no binary assets

### Desktop (fastest way to play)

```bash
./gradlew :desktop:run
```

This launches the LWJGL3 window (800×600) with procedural audio — perfect for
iterating on gameplay without an emulator.

### Android

```bash
./gradlew :app:assembleDebug                # build the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.depthdiver/app.deepdepthdiver.AndroidLauncher
```

> **Why the APK has `libgdx.so`:** libGDX ships its Android natives as
> `com.badlogicgames.gdx:gdx-platform:<ver>:natives-<abi>` **classifier
> dependencies**. The old template replaced these with a fragile
> `copyAndroidNatives` task that broke the Gradle configuration cache and
> dropped the natives from the APK (crash: *Couldn't load shared library
> 'gdx'*). This project declares the four classifier deps directly — the AGP
> `natives`/`implementation` mechanism auto-packs `lib/<abi>/libgdx.so` into
> the APK. No manual jniLibs copying needed.

> **Quick verification on an emulator:** launch the app, confirm it stayed
> foregrounded (`adb shell pidof app.depthdiver`), then make sure there are no
> runtime errors: `adb logcat -d -s AndroidRuntime:E '*:S'`.

---

## 🕹️ Controls

| Action | Desktop | Android |
|--------|---------|---------|
| Move up / down / left / right | `WASD` or arrow keys | **touch & drag** (`isTouched`) |
| Pause | `P` / `Esc` | `P`/`Esc` or the pause pill (top-right HUD) |
| Mute | `M` | `M` or the pause MU pill / menu mute pill |
| Restart | `R` (run, pause or game-over) | touch |
| Quit | `ESC` from a menu / window close | system back |

---

## 🧪 Development notes

- **Procedural everything:** textures come from `Pixmap` drawing loops; sound
  effects and the looping ambience are synthesized into WAV buffers at startup
  in `AudioManager` — the repo holds **zero** binary assets.
- **Meta-progression:** `Profile` (pearl wallet, upgrades, lifetime stats,
  difficulty, daily bonus) is the single source of truth over the `depthdiver`
  prefs; `Leaderboard` and `Achievements` each own their own prefs file.
- **Waterfall payouts:** pearls from the shop costs, mid-run `QUICK BUY`,
  milestone banners and the Leviathan bonus all flow through `Profile.addPearls`
  and are reported on the game-over **RUN SUMMARY**.
- **Upgrade math** (`Profile.Upgrade`): OXYGEN `+15%` capacity, SPEED `+8%`,
  COMBO `+2 s` window, SHIELD periodic shield, PEARL VALUE `+50%`; costs scale
  `baseCost × level`.
- **Entities:** `sealed class Pickup`/`Hazard` hierarchies in
  `core/.../entity/`, driven each frame from `DepthDiverGame.render()`.
- **Config cache:** the project is configuration-cache compatible.

---

## 📜 License

Procedural assets → CC0-style (generated at runtime, nothing to license).
The code itself: see [LICENSE](LICENSE) (add one if you haven't — GitHub shows
a license badge when `LICENSE` exists).

---

**Depth Diver** — dive deep, surface safe. 🫧