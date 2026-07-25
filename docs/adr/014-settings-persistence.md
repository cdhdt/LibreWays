# 014. Settings persistence

## Status

`accepted`

Date: `2026-07-25` — Decided by: the maintainer, directly (decision D2).

## Context

`docs/specs/001-navigation-mvp.md` FR-8/FR-9/FR-24 require the user's relay choice
(`RelayConfiguration`) to survive process death and cold start from v0.1 — a first-run choice made
once must not have to be repeated every time the app is killed and restarted, and the fail-closed
behaviour of decision D1 depends on the app actually knowing, at every cold start, whether a choice
was already made. `docs/adr/proposals/008-local-persistence.md` is the ADR that would normally
answer "how does this app persist data," but that document scopes itself narrowly to the
**structured-database question** — cached routes, saved places, and the eviction-policy shape of
the tile, geocoding, and traffic response caches — gated to v0.4 (with those three caches
themselves accelerated to v0.1 by decisions D7 and D9, recorded separately). Waiting on ADR 008 to
persist a handful of simple key/value preferences would have blocked v0.1 on a decision that
database question does not need to answer.

This ADR exists to record, as a decided, accepted ADR rather than an open proposal, the narrower
and simpler mechanism actually used for v0.1's settings: the relay/proxy configuration
(`RelayConfiguration`, including the explicit "direct, no relay" mode and the distinct, unset
`NotChosen` state) and other simple preferences such as units/locale. It also fixes a circular
citation a prior revision of this repository's ADRs had: ADR 008 cited ADR 007 (relay and proxy
implementation) as the source of this decision, and ADR 007 in turn pointed back at ADR 008 for
storage — a circular citation that named no actual decision anywhere. This document is that
decision.

## Decision drivers

- Privacy: the relay setting is low-sensitivity (it reveals a privacy *posture*, not a location or
  destination), but it still must never leak into a backup channel that reverts it silently, and
  clearing it must reliably restore the fail-closed `NotChosen` state (decision D1).
- Works fully without Google Play Services: DataStore is an AndroidX/Jetpack library with no GMS
  dependency.
- Resource cost: reading/writing a handful of small preference values is negligible compared to
  the structured-database question ADR 008 addresses; a full database engine is unwarranted for
  this scope.
- Development speed: v0.1's first-run relay gate and its tests (`docs/specs/001-navigation-mvp.md`
  test 35 and the presentation wiring test) needed a persistence answer immediately, without
  waiting on ADR 008's broader database evaluation (Room vs. SQLDelight vs. plain SQLite, plus
  encryption-at-rest) to conclude.
- Reversibility: the `RelaySettingsStore` port (`docs/architecture/README.md` §2.1) is
  `domain`-owned; whatever implements it in `data` must be swappable without touching `domain` or
  `presentation`.

## Options

| Option | Summary | Verdict |
|---|---|---|
| Jetpack DataStore Preferences | AndroidX key/value preference store, coroutine/`Flow`-native | Chosen |
| Legacy `SharedPreferences` | Older synchronous key/value API | Rejected — superseded by DataStore for new code |
| Folding this into ADR 008's eventual database | Wait for Room/SQLDelight/SQLite decision and store settings as rows | Rejected — blocks v0.1 unnecessarily |

### Jetpack DataStore Preferences

- Pros: purpose-built for exactly this shape of data (a small number of simple key/value
  preferences); coroutine/`Flow`-native, matching the project's decided coroutine-testing tooling
  (`docs/adr/012-build-and-test-tooling.md`: `kotlinx-coroutines-test` plus Turbine); no schema
  migration machinery needed for a handful of scalar preferences; AndroidX-maintained, no GMS
  dependency.
- Cons: not suited to structured/relational data (routes, places) — which is exactly why ADR 008
  exists as a separate decision for that question, not a reason to avoid DataStore here.
- Privacy impact: neutral by itself; what matters is exclusion from backup where appropriate and a
  reliable "revert to `NotChosen`" behaviour on data clear, both satisfied by construction (clearing
  the DataStore file removes the stored `RelayConfiguration` entirely, and the domain layer treats
  "no value present" as `NotChosen`, never as an implicit "direct" default).
- Resource impact: negligible — DataStore is designed for frequent, small reads/writes.
- Licence: Apache 2.0 (AndroidX/Jetpack).
- Google Play Services dependency: none.
- Maintenance status: actively maintained AndroidX library, the current recommended replacement for
  `SharedPreferences`.
- What it forecloses: nothing — if a future need for structured settings-adjacent data emerged, it
  would be evaluated under ADR 008's scope, not retrofitted into DataStore.

### Legacy `SharedPreferences`

- Rejected: DataStore Preferences is AndroidX's own recommended replacement, with coroutine/`Flow`
  support that `SharedPreferences`'s synchronous, listener-based API lacks; no reason to choose the
  superseded API for new code.

### Folding into ADR 008's database

- Rejected: ADR 008 is explicitly scoped to structured, relational data (cached routes, saved
  places) gated to v0.4, plus the file-based tile, geocoding, and traffic response caches
  accelerated to v0.1 by decisions D7 and D9. Forcing a handful of scalar preferences to wait on
  that broader, harder decision — encryption at
  rest, migration strategy, Room vs. SQLDelight — would have blocked v0.1's first-run relay gate on
  a question this data does not need answered.

## Decision

**Jetpack DataStore Preferences**, for the relay/proxy configuration (`RelayConfiguration`) and
other simple v0.1 preferences (units, locale), persisted from v0.1. Confidence: high — this is a
narrow, well-understood choice for exactly the data shape involved, and the alternative
(`SharedPreferences`) is superseded for new Android development.

## Consequences

- **Becomes easy**: the first-run relay gate and its persistence tests
  (`docs/specs/001-navigation-mvp.md` test 35) can be written and implemented without waiting on
  ADR 008; adding another simple scalar preference later (e.g. a further units/locale setting) is
  the same mechanism, not a new decision.
- **Becomes hard**: none identified — this is the lowest-risk decision among the outstanding ADRs,
  since the data shape (a handful of scalars) is exactly what DataStore Preferences is designed
  for.
- **What must be abstracted now to keep this reversible**: `domain` only sees the
  `RelaySettingsStore` port (`docs/architecture/README.md` §2.1); no `data`-layer DataStore type
  (`Preferences`, `DataStore<Preferences>`) is ever referenced outside the `data`-layer
  implementation of that port.

**Relationship to ADR 008 and decisions D7/D9, stated once to close the circular-citation defect
this ADR resolves**: ADR 008 owns the structured-database question only (cached routes, saved
places) and the eviction-policy concerns of the tile, geocoding, and traffic response caches; it
does not own, and no longer cites anything for, the settings-persistence mechanism — that is this
document. The v0.1 on-disk tile, geocoding, and traffic response caches (decisions D7 and D9) are
separate, file-based stores (not DataStore, not a structured database); they are ADR 008's concern
only insofar as their eviction policy is discussed there, never this ADR's.

## What was needed from the human

The maintainer's direct decision (D2) that the relay setting persists from v0.1 via Jetpack
DataStore Preferences, made specifically so v0.1 would not wait on ADR 008's broader database
evaluation. No hosting, keys, or external policy call was needed.

## Reversibility

High. `RelaySettingsStore` is a `domain`-owned interface; replacing DataStore Preferences with a
different key/value mechanism later (or folding settings into whatever ADR 008 eventually decides,
if that were ever judged worthwhile) is a `data`-layer-only change with no `domain` or
`presentation` impact, and no user-visible migration beyond re-reading the same handful of scalar
values from wherever they move to.
