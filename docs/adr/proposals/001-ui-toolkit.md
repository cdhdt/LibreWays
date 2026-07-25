# 001. UI toolkit

## Status

`proposed`

## Context

Every screen from v0.1 ("A to B": destination entry, map with route and traffic, own-position
indicator) forward is built on whatever is chosen here. CLAUDE.md §4 already commits the
presentation layer to holding no business logic and to unidirectional data flow, and §0.2 names
"UI toolkit (Compose vs Views)" explicitly as an open decision. The map surface itself (proposal
002) is a native `View` in every rendering-library candidate evaluated there (osmdroid, MapLibre
Native, Mapsforge all ship an Android `View`, not a Compose-native surface), so whatever is chosen
here must embed a `View` cleanly regardless of the outcome of 002.

## Decision drivers

- Privacy: toolkit choice has no direct privacy impact; scored only for completeness.
- Works fully without Play Services: both candidates are AOSP/Jetpack, neither pulls GMS.
- Resource cost: recomposition/measure-pass discipline in hot paths (map redraws, location
  updates) is CLAUDE.md §4's explicit concern ("watch allocations and recompositions in hot paths
  (map rendering, location updates)").
- GPL-3.0-compatible licence.
- F-Droid-compatible: reproducible build, no proprietary blob.
- Maintenance and community health.
- Development speed.
- Testability.
- Reversibility.

## Options

| Option | Summary | Verdict |
|---|---|---|
| Jetpack Compose | Declarative, Kotlin-first, AndroidX | Real candidate |
| Views (XML) + ViewBinding | Classic imperative Android UI toolkit | Real candidate |
| Hybrid (Compose screens, `AndroidView` for the map) | Compose for app chrome, Views interop for the map surface | Real candidate — arguably not a separate toolkit, but the practical shape of either choice once 002 is decided |

### Jetpack Compose

- Pros: less boilerplate for list/form-heavy screens (destination search results, saved places in
  v0.4+); state-driven rendering maps naturally onto CLAUDE.md §4's "unidirectional data flow,
  immutability by default"; strong first-party testing APIs (`ComposeTestRule`); actively the
  toolkit Google ships new Jetpack guidance for first.
- Cons: every rendering-library candidate in proposal 002 exposes a `View`, so the map screen still
  needs `AndroidView` interop either way — Compose does not remove that integration point;
  recomposition bugs in hot paths (a map screen re-rendering on every location tick) are a real,
  documented failure mode and must be guarded with `remember`/stable keys — CLAUDE.md §4 flags this
  exact risk; steeper learning curve for contributors coming from traditional Android.
- Privacy impact: none.
- Resource impact: unnecessary recomposition on a screen that redraws on every GPS fix (v0.1's own
  position on the route, v0.2's turn-by-turn) is the main resource risk; correctly scoped state
  hoisting avoids it but must be deliberate.
- Licence: Apache-2.0 (AndroidX/Jetpack, well-established, no GPL-3.0 conflict).
- Google Play Services dependency: none — Compose is AndroidX, not GMS.
- Maintenance status: actively developed by the AOSP/Jetpack team; unverified via context7 this
  session (not queried — treated as common knowledge given it ships in every current Android
  Studio template).
- What it forecloses: nothing architecturally; `AndroidView` keeps every map-library option in
  proposal 002 available.

### Views (XML) + ViewBinding

- Pros: every map-rendering candidate targets this model natively with zero interop layer; more
  contributors are familiar with it; no recomposition-related class of bugs; slightly smaller and
  more predictable build/compile times.
- Cons: more boilerplate for anything data-driven (forms, lists); manual state synchronisation is
  easier to get wrong in ways that violate "presentation holds no business logic" if discipline
  slips; XML layouts and imperative view updates are comparatively verbose for v0.4+ screens (saved
  places, trip history).
- Privacy impact: none.
- Resource impact: no recomposition risk class, but manual `invalidate()`/`notifyDataSetChanged()`
  misuse is an equivalent, differently-shaped risk.
- Licence: part of the Android framework/AndroidX, no conflict.
- Google Play Services dependency: none.
- Maintenance status: still fully supported by Google, but is no longer where new Jetpack guidance
  and samples are written first; unverified via context7 this session.
- What it forecloses: nothing architecturally.

### Hybrid (Compose app chrome, Views/`AndroidView` for the map)

- Pros: takes the best of both — Compose for forms/lists/settings, native `View` for whichever map
  library 002 picks, with no forced interop tax on the map itself, and no boilerplate tax on
  everything else.
- Cons: two mental models in one codebase; interop boundary (`AndroidView`/`ComposeView`) is an
  extra seam to test and a plausible source of lifecycle bugs (map `View` lifecycle vs Composable
  lifecycle) if not handled deliberately.
- Privacy impact: none.
- Resource impact: same recomposition risk as pure Compose, scoped to the smaller surface that
  actually needs it.
- Licence / GMS: same as the two above — no conflict either way.
- What it forecloses: nothing; this is the realistic end state of choosing Compose once 002 lands
  on any of its three candidates, since none of them is Compose-native.

## Recommendation

Jetpack Compose for app chrome, with the map screen embedding the proposal-002 library's `View`
via `AndroidView`. Cost: the interop seam must be deliberately tested (map `View` survives
configuration change, does not leak, does not recompose from unrelated state changes) — this is
not free, and a reviewer must check it specifically once the map screen exists. Confidence: medium.
This is the toolkit Google itself is steering new development toward, and none of the map libraries
evaluated in 002 make Views a strictly better fit, but the recomposition-discipline risk on the
hottest path in the app (location updates driving map redraws) is real and CLAUDE.md §4 already
calls it out by name — a reviewer inheriting a Compose codebase with sloppy state hoisting could
regress battery life invisibly.

## Consequences

- Easy: form-heavy and list-heavy v0.4+ screens (saved places, trip history), state-driven
  destination search UI, previews and unit-testable UI state.
- Hard: guaranteeing the map `View` inside `AndroidView` does not recompose or leak across
  configuration changes and location-driven state updates — must be an explicit test and an
  explicit review checklist item, not assumed.
- To keep reversible: presentation must depend only on domain-layer state holders (ViewModels or
  equivalent) that have no Compose imports, so a future full or partial move to Views touches only
  the presentation module.

## What is needed from the human

Confirm Compose (with `AndroidView` interop for the map) vs pure Views vs "decide after 002 lands."
No hosting, keys, or external policy call is needed for this decision.

## Reversibility

Switching from Compose to Views later means rewriting every screen's presentation code, but not
the domain or data layers if the layering rule is respected — cost is proportional to screen count
at the time of the switch, not a rearchitecture. Switching the other direction (Views to Compose)
carries the same shape of cost. The map `View` itself is unaffected either way, since every
candidate in 002 is embedded as a native `View` under both toolkits.
