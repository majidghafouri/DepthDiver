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
- [x] Step 2: extract world/background rendering (`4d7cb21`)
  (`WorldRenderer` + `WorldViewState`; 2453 -> 2429 lines of draw code moved out.
  Camera shake is the only state the renderer touches, and it restores the camera
  before returning so the HUD does not inherit the offset. `waterColorAt` and
  `raysVisible` came out as pure functions and are now tested directly.)
- [x] Step 3: extract menu + sub-screen rendering (`af30875`)
  (2679 -> 2453 lines. `MenuGeometry` is the point of the step: the profile claim
  bug was the drawn box and the hit-tested box disagreeing, so both now come from
  one function. `MenuText` is a three-method seam over the fonts because
  `BitmapFont` cannot be built in a JVM test, which is what makes the geometry
  testable without a device.)
- [x] Step 4: extract run lifecycle / settlement (`154bf90`)
  (`RunLifecycle` owns the ledger and the transitions. Ordering rules that were
  implied by statement order are now spelled out and asserted: a run is
  checkpointed before it is settled, and `begin`/`hold` are separate because the
  world reset detaches whatever is held. A run that cannot be persisted stays
  held rather than looking finished.)
- [x] Automated UI testing (`294e050`)
  (`MenuUiTest`: every control on the main menu, settings, shop and profile is
  checked for reachability, occlusion and on-screen placement across twelve
  viewports, in the same order the handlers test them. Deliberately not Espresso
  or Robolectric -- the UI is GL-rendered, so there are no Views to address and
  no GL context to render into; both were rejected for that reason, not for
  availability.)
- [x] God class 2952 -> 2429 lines across the four steps. What is left is
  simulation, input and the game loop.

**Verification note.** Each step was checked as a move rather than assumed to be
one. Step 3 compared the old inline arithmetic against `MenuGeometry` over 14
viewports (2352 identical values) and then compared old and new APKs' rendered
ink masks on six screens (100% agreement). Step 2 compared the drawing arithmetic
across four viewports, four cameras, nine depths and five clocks plus every
entity and boss pattern (232402 identical values). A pixel diff of the world was
not available: the emulator would not hold a landscape viewport and its surface
froze mid-run.

### Phase 15: Monetization (Optional)
Built as seams, not as live purchases. Nothing in this phase can charge anyone yet,
by design: see the "store wiring needed" list at the end of this section.

- [x] Cosmetic catalog + ownership (`dd2e893`)
  (`Cosmetics`, `Economy`. A cosmetic is two colours and nothing else, so there is
  no stat to balance and no way a purchase makes a run easier. The default entry
  is the colour the diver already had, so an install that bought nothing looks
  unchanged. `COSMETICS` is a real menu screen with a live swatch.)
- [x] Purchase seam (`dd2e893`)
  (`PurchaseService` mirrors `LeaderboardService`/`CloudSaveService`.
  `LocalPurchases` **refuses** every purchase when it has no billing backend
  rather than granting it, so a shipping build wired up by mistake cannot hand
  out paid content. `PurchaseResult` distinguishes granted / cancelled / failed /
  already-owned, because a cancelled sheet that reads as a grant is how someone
  gets charged for something they did not buy.)
- [x] Rewarded ads, opt-in (`dd2e893`)
  (`AdService`. Off by default; an unanswered consent prompt is a no, never an
  accidental yes. Rewarded video only -- no interstitial, no banner. Nothing pays
  out unless the ad reports `Earned`.)
- [x] Battle pass / season system (`dd2e893`)
  (`SeasonPass`. 30 tiers, free and premium tracks. XP comes only from finished
  runs: a pass whose paid tier were also the faster tier would be charging people
  to keep up. Premium unlocks only when the store reports a grant. A run is
  credited once by run id, and a tier cannot be claimed twice.)

**Store wiring needed before this can charge anyone.** All of it is the user's
side, not code:
1. Google Play Console: create the app listing, an in-app product per cosmetic
   and for the season pass, set prices, and add licence testers for internal
   testing. `ProductDetails.Sku` values must match the `productId` fields in
   `Cosmetics`.
2. The Play Billing artifact added to `app/build.gradle.kts`, and
   `PlayBillingPurchases : PurchaseService` implemented against it, then
   `Purchases.install(...)` at startup. The `Purchases` seam is the only place
   that changes.
3. An AdMob app ID and a rewarded ad unit ID, then `AdMobAds : AdService` and
   `Ads.install(...)`. Only if ads are wanted at all.
4. A real consent flow (GDPR/UK GDPR and similar) feeding `AdService.consented`.
   The current value is a plain boolean and is not a compliance answer.
5. Age rating, data safety form, and a privacy policy covering purchase and ad
   data -- all required by Play before an app with IAP or ads can ship.

**Blocked note.** Steps 1-4 need artifacts from `maven.google.com`, which has been
returning 404 in this environment (the same reason online leaderboards and cloud
save remain local-only). The seams are ready; only the dependency and the
credentials are missing.

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