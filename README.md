<div align="center">

# 🐬 Depth Diver

**A subaquatic endless-diver built with [libGDX]**  
Dive deep, grab pearls, grab air — and come back up before your oxygen runs out.

</div>

---

## About the game

**Depth Diver** is a small, self-contained 2D game where you pilot a diver straight
down into the abyss. It's a side-scrolling endless runner in the spirit of *Faster
Than Light meets Subnautica's lower leagues* — the deeper you go, the faster the
world scrolls, the longer the oxygen line shrinks.

### Gameplay

- 🕹️ **Steer** with `WASD` / arrow keys on desktop, `touch & drag` on Android
  (the diver follows your finger — the game world scrolls past you).
- 🫁 **Oxygen** drains the deeper you dive. Run out and you sink to the bottom.
- 🦪 **Pearls** (score) and 🫧 **Oxygen tanks** (restore air) float past — grab them.
- 🪨 **Rocks**, 💣 **mines**, and 🪼 **jellyfish** end your dive. Bob to dodge.
- 📈 **Depth = difficulty.** Deeper = faster scroll, tighter gaps, hungrier hazards.
- 🏆 **Best depth & best score** are saved between sessions.

### Highlights

- **100% procedural content** — every texture is generated at runtime with
  `Pixmap`; there are **no binary art assets** in the repo.
- **Procedural audio** — sound effects are synthesized on the fly into WAV files
  by `AudioManager` (bubbles, pearls, oxygen hiss, explosion) — again, no audio
  assets checked in.
- **Shared core + two launchers** — one game, runs on desktop and Android from a
  single source tree.

---

## Tech stack

Built with the **Kotlin** Multiplatform-style layout that libGDX officially
recommends — *one shared game module, thin platform launchers*:

| Layer | Technology |
|-------|-----------|
| Game engine | **[libGDX](https://libgdx.com)** 1.14.2 — `ApplicationAdapter`, `SpriteBatch`, `OrthographicCamera`, `ShapeRenderer`, `Pixmap`, procedurally generated `Texture` & `Sound` |
| Language | **[Kotlin](https://kotlinlang.org)** 2.2.20 |
| Android | **[Android Gradle Plugin](https://developer.android.com/build)** 9.4.1, `gdx-backend-android`, classifier `gdx-platform:natives-*` (AGP auto-splits `.so` per ABI) |
| Desktop | **LWJGL3** via `gdx-backend-lwjgl3` + `gdx-platform:natives-desktop`, Gradle `application` plugin run task |
| UI (Android) | **[Material Design](https://m3.material.io)** (`com.google.android.material`) |
| Build | **[Gradle](https://gradle.org)** Kotlin DSL + version catalog (`libs.versions.toml`), **configuration cache** enabled |
| JVM | Java 11 toolchain (`JvmTarget.JVM_11` on Android, toolchain 17 on desktop) |

### Module layout

```
DepthDiver/
├─ core/       # platform-agnostic game logic (the whole game lives here)
├─ desktop/    # LWJGL3 launcher — "run" target for quick dev loop
├─ app/        # Android application module (backend-android + natives jar)
└─ gradle/     # version catalog (libs.versions.toml)
```

The host package for the game itself is `com.depthdiver` (`DepthDiverGame`,
`AudioManager`, entities, hazards, pickups). Launchers are thin and live in
their own modules.

---

## Prerequisites

- **JDK 17** (used by the Gradle daemon / desktop toolchain)
- **Android SDK** with an emulator (AVD) or a device — for the `app` target
- A terminal. That's it — no IDE required to build.

> Desktop runs need **nothing extra** — it just needs a JDK.

---

## Setup & run

### 1. Clone

```bash
git clone <your-repo-url> DepthDiver
cd DepthDiver
```

### 2. Desktop (fastest — no Android needed)

```bash
./gradlew :desktop:run
```

That's the whole game, in a window, in under a minute. (Uses LWJGL3; the project
already sets `-XstartOnFirstThread` on macOS, so it runs out of the box on a Mac.)

### 3. Android

Create/start an AVD if you don't have one, then:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.depthdiver/.AndroidLauncher
```

> The APK ships `libgdx.so` for all four ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`,
> `x86_64`) via the `gdx-platform` **natives classifier** jars — no manual
> `jniLibs` copying, no `UnsatisfiedLinkError`s.

### 4. Customize in Android Studio

Open the root folder as a Gradle project. Sync, then:

- **Desktop:** run `desktop.mainClass` (the `:desktop:run` task) — green ▶.
- **Android:** select the `app` configuration and run it on your emulator.
- Set a device/emulator up via **Device Manager** if you haven't yet.

---

## Controls

| Action | Desktop | Android |
|--------|---------|---------|
| Steer | `WASD` / arrow keys | touch & drag |
| Dive / surface | `W` / `S` (or hold on-screen) | drag up / down |
| Restart after sinking | `R` | tap |

---

## How the project ticks

- **`core/`** — `DepthDiverGame` extends `libGDX.ApplicationAdapter` and owns the
  whole loop: input, physics, entity spawning (hazards/pickups scaled by depth),
  oxygen metering, and a system-drawn HUD. Textures and sounds are generated, not
  loaded from disk.
- **`desktop/`** — a `JavaExec` launch task wrapping `DesktopLauncherKt`, the dev
  loop.
- **`app/`** — `AndroidLauncher` extends `AndroidApplication` & wires the game to
  the `AndroidApplicationConfiguration`; the `natives` classifier deps guarantee
  the `.so` files reach `jniLibs`.
- **Persistence** — best depth & score live in libGDX `Preferences` (key-value,
  survives restarts, works identically on both backends).

Everything is Kotlin, everything is version-pinned in `gradle/libs.versions.toml`,
and the build is clean under the **configuration cache**.

---

## Roadmap ideas

- [ ] More hazard types & a boss-depth milestone
- [ ] Combo/multiplier for chained pearls
- [ ] Sound muting + audio settings screen
- [ ] `desktop:dist` fat-jar packaging
- [ ] High-score leaderboard (local DB → cloud)

_Pull requests and ideas welcome!_ 🫧
