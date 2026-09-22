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
| 🕹️ Steering | `WASD` / arrows (Kotlin `Gdx.input.isKeyPressed`) + touch drag (`Gdx.input.isTouched`) on Android |
| 🫁 Oxygen | Drains faster with depth; die if it hits zero |
| 💎 Pickups | **Pearls** (score — chain them within the combo window for a `×N` multiplier) and **oxygen tanks** (restore air) |
| ⚠️ Hazards | **Rocks**, **mines**, **jellyfish**, and **sharks** — one touch and it's lights out |
| 🦈 Boss milestone | Cross a depth milestone and a **boss shark (Leviathan)** rises — faster peril, bigger reward |
| ⏸️ Pause | `P` / `Esc` toggles a pause overlay with resume/restart/mute |
| 📈 Depth = difficulty | Enemy/pickup density & scroll speed ramp as you dive deeper |
| 🏆 Persistence | Best depth + best score + **top-5 leaderboard** saved via libGDX `Preferences` |

The whole game is **procedurally generated at runtime** — textures via
`Pixmap` and all sound effects synthesized on the fly (no binary art/audio
assets to license).

---

## 🧱 Project layout

libGDX-style **multi-module Kotlin** project:

```
DepthDiver/
├── core/        # Everything that makes the game a game (Kotlin, engine-agnostic-ish libGDX)
│   └── com/depthdiver/
│       ├── DepthDiverGame.kt      # main ApplicationAdapter — game loop
│       ├── AudioManager.kt        # procedural WAV synthesizer (pickup/oxygen/crash SFX)
│       └── entity/                # Pickup (Pearl, OxygenTank), Hazard (Rock, Mine, Jellyfish)
├── desktop/     # LWJGL3 launcher (gdx-backend-lwjgl3 + gdx-platform natives)
├── app/         # Android launcher (gdx-backend-android) — runs on emulator/device
├── gradle/      # version catalog (libs.versions.toml) — all versions pinned here
└── settings.gradle.kts
```

**Package conventions:** game domain lives in `com.depthdiver` (core); the
Android entry point is `app.depthdiver.AndroidLauncher`; the desktop entry
point is `com.depthdiver.desktop.DesktopLauncherKt`.

---

## 🛠️ Tech used

| Concern | Technology |
|---------|-----------|
| Game framework | **[libGDX](https://libgdx.com) 1.12.1** — `ApplicationAdapter`, `SpriteBatch`, `ShapeRenderer`, `Pixmap`, `OrthographicCamera`, `Gdx.input` |
| Game backend (Android) | `gdx-backend-android` + `gdx-platform` `natives-*` classifier jars (so the `libgdx.so` natives are packaged per ABI) |
| Game backend (desktop) | `gdx-backend-lwjgl3` + `gdx-platform:natives-desktop` |
| Platform | **Kotlin** + Gradle Kotlin DSL, AGP via version catalog |
| UI | libGDX's own `BitmapFont` HUD (system-drawn) — no third-party UI |
| Build | Gradle wrapper, [version catalog](gradle/libs.versions.toml), configuration-cache-friendly |
| Target SDKs | Android `minSdk 24` / `targetSdk 37`, Java 11 source-level |

There is **no unused dependency**: the catalog was pruned of the stock
appcompat / core-ktx / junit / espresso entries that the Android Studio
template ships but this project doesn't use. `Material` is only referenced by
the Android theme.

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

This launches the LWJGL3 window with procedural audio — perfect for iterating on
gameplay without an emulator.

### Android

```bash
./gradlew :app:assembleDebug                # build the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.depthdiver/.AndroidLauncher
```

> **Why the APK has `libgdx.so`:** libGDX ships its Android natives as
> `com.badlogicgames.gdx:gdx-platform:<ver>:natives-<abi>` **classifier
> dependencies**. The old template replaced these with a fragile
> `copyAndroidNatives` task that broke the Gradle configuration cache and
> dropped the natives from the APK (crash: *Couldn't load shared library
> 'gdx'*). This project declares the four classifier deps directly — the AGP
> `natives`/`implementation` mechanism auto-packs `lib/<abi>/libgdx.so` into
> the APK. No manual jniLibs copying needed.

---

## 🕹️ Controls

| Action | Desktop | Android |
|--------|---------|---------|
| Move up / down / left / right | `WASD` or arrow keys | **touch & drag** (`isTouched`) |
| Restart (after sinking) | `R` | touch |
| Quit | `ESC` / window close | system back |

---

## 🧪 Development notes

- **Procedural everything:** textures come from `Pixmap` drawing loops; sound
  effects are synthesized into WAV buffers at startup in `AudioManager` (even
  the "bubbles"), so the repo holds **zero** binary assets.
- **Config cache:** the project is configuration-cache compatible — the build
  reads are cached across runs (removing the non-serializable `natives` config
  reference from the copy task is what fixed this).
- **Entities:** `sealed class Pickup`/`Hazard` hierarchies in
  `core/.../entity/`, driven each frame from `DepthDiverGame.render()`.

---

## 📜 License

Procedural assets → CC0-style (generated at runtime, nothing to license).
The code itself: see [LICENSE](LICENSE) (add one if you haven't — GitHub shows
a license badge when `LICENSE` exists).

---

**Depth Diver** — dive deep, surface safe. 🫧
