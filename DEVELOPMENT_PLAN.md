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

### Phase 10: Gameplay Polish & Content (IN PROGRESS)
- [x] Visual polish: particle effects enhancement, screen shake refinement, vignette pulse (`2b3edbf`)
- [x] Audio: more SFX variety, dynamic music layers (`ea23789`)
- [x] Content: new hazard types (angler, vortex), five biomes with blended water color and hazard mixes
- [x] Boss mechanics: 4 attack patterns with windup telegraphing, health system, damage feedback (`302e837`)

### Phase 11: Social/Retention Features
- [ ] Online leaderboards (Google Play Games / Game Center)
- [ ] Cloud save/sync across devices
- [ ] Friend challenges / shareable run seeds
- [ ] Daily/weekly challenge notifications

### Phase 12: Platform Polish
- [ ] iOS build / App Store preparation
- [ ] Web build (libGDX HTML5 backend)
- [ ] Tablet/landscape optimizations
- [ ] Controller/gamepad support

### Phase 13: Content Pipeline
- [ ] Level/biome editor tooling
- [ ] Data-driven hazard/pickup definitions (JSON)
- [ ] Procedural generation tuning parameters

### Phase 14: Architecture Refactor
- [ ] Decompose DepthDiverGame (2200+ lines) into components
- [ ] ECS or component-based architecture
- [ ] Dependency injection / service locator
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
   - [ ] Daily/weekly challenge notifications

2. **Phase 12: Platform Polish**
   - iOS build / App Store preparation
   - Web build (libGDX HTML5 backend)
   - Tablet/landscape optimizations
   - Controller/gamepad support

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