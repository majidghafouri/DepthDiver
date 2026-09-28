# DepthDiver — retention design

Not a task list. A record of *why* the game is hard to return to, what was
proposed about it, and which of it has been built. Kept separate from
`DEVELOPMENT_PLAN.md` so the phase list stays a phase list.

## The diagnosis

The run seed changes the **layout** of a dive, not its **rules**. Every run is
mechanically identical: same hazards, same upgrade maths, same two ways to die.
There are three difficulties and luck. That is a ceiling — nothing to master,
nothing to discover, only something to grind. Someone plays five times and has no
reason to play a sixth.

The upgrades make this worse rather than better. `Profile.Upgrade` is oxygen
+0.15/level, speed x1.08/level and so on, capped at level 5. Those are linear stat
bumps: they make the numbers bigger without changing a single decision. The only
one that alters how the game feels is speed, and x1.4 at max is not enough to
notice.

## The proposals

Ordered by leverage, not by effort.

### 1. Per-run mutations — BUILT (`078bec7`)
Pick 1 of 3 at the start of a run, and again at depth milestones. Each is a
*rule change*, not a stat increase, so the run becomes a different problem.
Pearls double but oxygen drains faster; currents push you sideways; jellyfish are
harmless and you move slower.

This is the highest-leverage change available and it is cheap here: pure logic,
no new art, no new audio. It also gives the leaderboard meaning (people compare
builds) and the season pass something to be strategic about.

### 2. Sharpen the first 60 seconds — BUILT (`289a55b`)
The first pickup of a run is now always a pearl (~2s in, was a 35/65 coin flip).
First milestone 50m -> 15m. First mutation pick 120m -> 15m, later picks every
90m. Two hint lines on the first two dives, from the string table, fading over
4.5s. All four are constants in `analytics/Opening.kt` so they can be argued
about without reading the spawn code.

Still missing from this item: a near-miss that fires the combo visibly, and any
sense of the *shape* of a deep run before the player reaches it.

### 3. Landmarks — NOT STARTED
Procedural means infinite but forgettable. Add 3-5 authored set pieces at fixed
depths: a sunken ship, a whale fall, a kelp arch, a hydrothermal vent. They are
bigger props, not new systems. This is what turns "I got to 340m" into "I finally
reached the shipwreck", which is the thing people tell each other.

### 4. Death loop — NOT STARTED
You end on oxygen or a hazard. Make losing informative: show the personal best
being chased and how close the run got. One free "bubble" per run that saves you
once at low oxygen adds a near-miss feeling very cheaply.

### 5. Streaks — NOT STARTED
Daily challenges and the day counter already exist. A consecutive-days reward is
the most reliable retention mechanic there is and roughly 90% of the data is
already stored.

### 6. Under-leverage run seeds — NOT STARTED
Shareable seeds are already built and barely used. "Race my exact ocean" is the
cheapest viral loop available and the plumbing is done.

### 7. Measure before building more — BUILT (`600f04b`, readout `6174d68`)
`analytics/RunAnalytics.kt` (exact median + funnel, last 500 runs in memory) and
`analytics/AnalyticsReport.kt` (running totals that survive, rendered as text).
Local only, no identifier, no network.

**The number to look at is `typicalStopMeters`** -- the median run's depth, not
the best. If it is low, proposals 2 and 3 matter more than 4 and 5.

Reachable from the profile as a line, opt-in, with "these numbers never leave your
device" on the screen because that is the kind of claim that has to be visible to
be believed. Nothing has been read from it yet, so proposals 3 to 6 are still
guesses.

## The open question: who is this for?

"Everyone" is not achievable, and chasing it would make the game worse. There is a
real fork and it is not a coding question:

- **Broad casual** — short runs, constant rewards, lots of content. Bigger
  audience, and explicitly not what exists here.
- **Calm score-chaser** — a smaller, committed audience that likes depth and
  records. This is what has been built, and the piano soundtrack leans into it.

Recommendation is the second, marketed as *the peaceful score-chaser* against
hyper-casual noise.

**Proceeding on the second answer** (calm score-chaser), since the remaining work
was commissioned without picking one. Items 3 to 6 are all consistent with it. If
broad-casual is the real target, the honest read is that this is a different
product, not a different roadmap.

## Deliberately not doing

- **Interstitial ads.** The `AdService` seam is rewarded-video only, opt-in, off
  by default. Interstitials and banners take play away rather than trading a
  choice for a reward.
- **Anything purchasable that touches gameplay.** Cosmetics are two colours. A
  paid stat is a different product with different platform rules attached.
- **Energy timers / loss aversion.** This is a calm game. "Come back in 4 hours"
  is the opposite of the thing being made.
