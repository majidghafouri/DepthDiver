# Depth Diver Development Plan

## Completed Phases

### Phase 1: Core Gameplay Loop ✅
- Basic diving mechanics, hazards, pickups, oxygen system
- Core game loop with fixed timestep

### Phase 2: Meta-Progression & Run Settlement ✅
- Profile system (pearls, upgrades, stats)
- Run settlement with milestones, daily bonus, challenges
- Leaderboard persistence
- Achievement system (9 milestones)

### Phase 3: Procedural Fairness ✅
- Seeded RNG for reproducible runs
- Safe hazard placement with player clearance
- Reachable pickup placement
- Low-oxygen tank forcing with cooldown
- Eel corridor separation

### Phase 4: Difficulty Curve Balance ✅
- Scroll speed capped at 88% player speed
- Hazard interval saturating ramp with floor
- Depth-scaled oxygen drain (+55% at depth)
- Fairness preserved at depth

### Phase 5: Shippability / Release Build ✅
- R8 minification + ProGuard rules
- Signing config (keystore.properties + env vars)
- Lint config, CI upgrades (core:check, lint, release APK)
- Version bump to 1.1.0
- Stray file cleanup

### Phase 6: Public Types for Decomposition ✅
- Made core types public: MoveDirection, WorldViewSpec, GameState, GameAction, BackAction
- ProceduralFairness, DifficultyCurve, WorldGeometry types public
- Common types public (GameState, GameAction, BackAction, GameOverAction, TouchTarget)

### Phase 7: Accessibility & Settings ✅
- Volume controls (master/sfx/music)
- Reduce motion, high contrast, screen shake toggle
- Settings screen with sliders/toggles
- Android BACK handling (OnBackInvokedCallback + legacy fallback)
- Fixed BACK-from-game-over bug

### Phase 8: Localization + RTL ✅
- Spanish (ES) locale with full translation
- Dynamic locale switching via Profile
- Strings object with EN/ES locales
- Locale persisted in Profile

### Phase 9: Performance Monitoring ✅
- PerformanceMonitor: FPS, min/avg/max frame times
- FrameTimeOverlay: real-time FPS display (toggle with F key)
- Color-coded FPS (green/yellow/red)
- Integrated into render loop

---

## Active Phase

### Phase 10: Gameplay Polish & Content (COMPLETE)
- [x] Visual polish: particle effects enhancement, screen shake refinement, vignette pulse (`2b3edbf`)
- [x] Audio: more SFX variety, dynamic music layers (`ea23789`)
- [x] Content: new hazard types (angler, vortex), five biomes with blended water color and hazard mixes (`302e837`)
- [x] Boss mechanics: 4 attack patterns with windup telegraphing, health system, damage feedback (`342a12e`)

### Phase 11: Social/Retention Features (SEAM READY, BACKEND PENDING)
- [x] Online leaderboards: `LeaderboardService` seam + local-first impl; GPGS stubbed (`75e0a04`)
- [x] Cloud save/sync: `CloudSaveService` seam; GPGS Saved Games stubbed (`2aa878b`)
- [x] Friend challenges / shareable run seeds (`cffa553`)
- [x] Daily/weekly challenge notifications (`4738a1c`)
- [ ] **Blocked:** real cloud backends need the `play-services-games` artifact, which
      404s on Maven Central. The seams are ready; only the dependency is missing.

### Phase 12: Platform Polish (PARTIAL)
- [x] Tablet/landscape optimizations: `UiScale`, scaled fonts/gaps, accessible touch targets (`9bb1c4d`)
- [x] Controller/gamepad support: pure input mapping + optional runtime bridge (`dcbcd11`)
  - Bridge is inert until `gdx-controllers` is on the classpath (separate artifact, unavailable offline)
- [ ] iOS build preparation (docs/config)
- [ ] **Blocked:** Web build — `gdx-backend-gwt` needs GWT artifacts that do not resolve here

### Phase 13: Content Pipeline
- [ ] Level/biome editor tooling
- [ ] Data-driven hazard/pickup definitions (JSON)
- [ ] Procedural generation tuning parameters

### Phase 14: Architecture Refactor
- [x] Step 1: extract HUD into a renderer driven by an immutable state snapshot
  (game god class 2952 -> 2902 lines; pause hitbox no longer stashed in fields)
- [ ] Step 2: extract world/background rendering
- [ ] Step 3: extract menu + sub-screen rendering
- [ ] Step 4: extract run lifecycle / settlement
- [ ] Automated UI testing

### Phase 15: Monetization (Optional)
- [ ] Optional cosmetic purchases
- [ ] Ad integration (opt-in rewarded ads)
- [ ] Battle pass / season system

---

## Immediate Next Steps

1. **Phase 11: Social/Retention Features**
   - [x] Online leaderboards: cloud-ready interface, local-first implementation, GPGS stub
   - [x] Cloud save/sync: cloud-ready interface, GPGS Saved Games stub
   - [x] Friend challenges / shareable run seeds: seed encoding/decoding, pause menu share button, clipboard copy
   - [x] Daily/weekly challenge notifications: notification channel, daily/weekly scheduling, challenge summary in notification

2. **Phase 12: Platform Polish**
   - [x] Controller/gamepad support: pure input mapping + optional runtime bridge
   - [x] Tablet/landscape optimizations: UiScale, scaled fonts/gaps/offsets, accessible touch targets
   - [ ] Web build (libGDX HTML5 backend) — blocked: gdx-backend-gwt needs GWT artifacts
   - [ ] iOS build preparation (docs/config)

4. **Boss Mechanics**
   - Phase transitions with visual telegraphing
   - Multiple attack patterns per boss
   - Environmental hazards during boss fights

---

## Technical Notes

### Dependencies
- libGDX 1.14.2
- Kotlin 2.2.20
- AGP 9.4.1
- Gradle 8.x

### Testing
- Unit tests: 100+ (GameFlow, Profile, DifficultyCurve, ProceduralFairness, etc.)
- Integration: manual emulator testing
- CI: GitHub Actions (JVM tests, desktop compile, Android assembleDebug)

### Performance Targets
- 60 FPS on mid-range devices (2018+)
- <100ms cold start
- <50MB APK (release)

---

*Last updated: $(date)*