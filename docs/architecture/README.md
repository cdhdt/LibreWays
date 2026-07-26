# LibreWays — Architecture

Status: living document, updated alongside code. See [`../README.md`](../README.md) for the
documentation index, [`../privacy.md`](../privacy.md) for the data-flow and permission
justifications this architecture must satisfy, and [`../specs/001-navigation-mvp.md`](../specs/001-navigation-mvp.md)
for the v0.1 requirements this structure is built to serve.

This document describes **structure, responsibilities and boundaries** — not technology choices.
Every UI toolkit, map library, geocoder, HTTP client and persistence mechanism named anywhere below
is a **candidate under evaluation**, never a decision — with one settled exception, stated here so
it is not read as still open: the **routing engine and the traffic/incident source are decided**
(both Waze, decisions D10/D11, recorded as `docs/adr/003-routing-engine.md` and
`docs/adr/005-traffic-source-integration.md`), and this document names that decision explicitly
wherever it comes up (§3, §4, §8, §9) rather than treating it as a candidate. Per
[`CLAUDE.md`](../../CLAUDE.md) §0.2, the remaining choices are made once by the human developer and
recorded as an ADR under [`../adr/proposals/`](../adr/proposals/); until an ADR exists, no code may
depend on a specific one. Where this document must gesture at a shape to explain a boundary for one
of those remaining open decisions, it names the relevant ADR proposal instead of picking.

## 1. Goals restated as constraints on the structure

`CLAUDE.md` §0 and §5 state product and privacy non-negotiables. This section restates each as a
concrete requirement the module structure must enforce, not just a claim.

| Goal (CLAUDE.md) | Structural requirement |
|---|---|
| Privacy by construction | No layer other than a single networking chokepoint may open a socket. Domain and presentation cannot leak data because they have no network capability at all — not because they are told not to use it. |
| Works fully without Google Play Services | No layer imports a GMS-dependent type. Provider interfaces are defined in `domain` in Play-Services-neutral terms (coordinates, tiles, place results as plain data), so a GMS-free implementation is always a legal substitution. |
| Low resource cost | Domain use cases are pure functions/coroutines with no framework overhead; background work is bounded by lifecycle, not left running; no component polls. |
| Testability (domain testable with no Android runtime) | `domain` has zero Android framework imports, so its test suite runs as plain JVM unit tests — no emulator, no Robolectric, no instrumentation. |
| Swappable providers | Every external data source and every strategy with more than one credible shape (notably routing) sits behind a domain-owned interface. `data` implements it; `domain` and `presentation` never reference the implementation type. |

## 2. Layers

Three layers, per `CLAUDE.md` §4. The names are responsibilities; §8 below is the decided mapping
onto Gradle modules/packages.

### 2.1 `domain` — pure Kotlin, zero Android imports

Contains everything that defines *what the app does*, expressed in language that has no
dependency on the Android SDK, a specific HTTP client, a specific map renderer, or a specific
database. If a class in `domain` needs `android.*`, `androidx.*`, or a third-party SDK type to
compile, it does not belong in `domain`.

Belongs here:

- **Entities / value types**: `Coordinate` (a latitude/longitude pair at a given precision — the
  shared geographic value type used by every other entity below; a single place to express and
  enforce "coarsened vs. precise" rather than each entity inventing its own notion of a point),
  `Route` (geometry + `durationWithTraffic` + `durationWithoutTraffic`, with `trafficDelay` as a
  computed property, decision D11), `Place` (a geocoded result — label, `Coordinate`, confidence,
  provenance), `Incident` (traffic event — kind, subtype, an **absolute, coarsened `Coordinate`
  position** — corrected from an earlier revision that expressed position only relative to a
  `Route`, which could not place a reported alert on the map independent of an active route —
  severity, and reliability signals (confirmation count, reporter-trust band, confidence, age),
  decision D14 — this is the definition `docs/specs/001-navigation-mvp.md`'s Stage A test 6
  exercises, corrected there in the same way; stated once here and shared, not redefined per
  document), `CongestedSegment` (a sibling of `Incident`, not a variant of it, decision D13: a
  non-empty polyline of `Coordinate`s, a bounded `congestionLevel` enum with an explicit `Unknown`
  fallback, and a nullable `estimatedDelay`), `Position` (the user's own location, as a domain
  concept: `Coordinate`, accuracy, timestamp — not a platform location object), `RelayConfiguration`
  (the user's relay choice — see below; models "direct, no relay" as an explicit, distinct
  selectable mode, never as the absence of a choice, per decision D1 — **and** models the
  not-yet-chosen state (`NotChosen`) as a further, separate state that blocks all egress, distinct
  from every selectable mode including "direct, no relay" itself; the two must never collapse into
  one another).
- **Use cases** (application services), named explicitly because callers and tests depend on these
  exact names: `ResolveDestination` (destination text → candidate `Place`s), `RequestRoute`
  (an origin `Position` and a destination `Place` → a `Route` or a domain error),
  `LoadIncidentsForRoute` (a `Route`'s corridor → an `Incident` list, projected from the wider
  `TrafficSnapshot` below — **its own use case**, not a step folded into
  `RequestRoute`: incidents can fail, retry, and refresh independently of the route itself, and
  `docs/architecture/data-flow.md` traces it as a distinct step for exactly this reason),
  `LoadTrafficForArea` (a viewport → a `TrafficSnapshot` — incidents and congested segments
  together — or a domain error; the viewport-scoped sibling of `LoadIncidentsForRoute`, decisions
  D13/D15), `TrackOwnPosition` (device position → `Position` projected onto the active route). Each
  use case is a small, single-responsibility class taking its dependencies through the constructor
  (SOLID: dependency inversion), returning domain types.
- **Provider interfaces** (ports), every one of them first-class and equally load-bearing —
  none is a lesser citizen of this list, and a decomposition that omits any of them is a defect
  (see §9's tile-provider note): `PlaceSearchProvider`, `RouteProvider` (unchanged signature under
  decision D11 — only `Route`'s own shape gained a field), `TrafficIncidentProvider` (**widened**,
  not renamed, by decisions D13/D15: takes a route corridor or a viewport, returns a
  `TrafficSnapshot` carrying both incidents and congested segments from one call), **`TileProvider`**
  (an abstraction over *what area is needed*, not over how a tile is fetched or rendered — it
  belongs to the same domain-port
  standing as `RouteProvider` or `PlaceSearchProvider`; the map/tile flow needs it exercised and
  tested exactly like every other provider, see §7 and §9), `OwnPositionSource`, `RouteCache`/
  `PlaceCache` persistence ports, and **`RelaySettingsStore`** (the persistence port for the user's
  `RelayConfiguration` — decision D2, recorded as `docs/adr/014-settings-persistence.md`, makes
  this setting persisted from v0.1 via Jetpack DataStore Preferences in `data`, never
  in-memory-only; `domain` only sees the port, not the storage mechanism). `domain` defines the
  shape of these interfaces; it does not implement or know about any concrete provider.
- **Domain error types** for expected failure modes: no result found, permission denied,
  rate-limited, `ProviderUnreachable` (the relay path is fine; the upstream provider itself
  failed), and two further, never-conflated relay-specific failures per decision D6 —
  `RelayNotChosen` (egress blocked because no relay choice has been made yet, decision D1) and
  `RelayUnreachable` (a *configured* relay could not be reached, decision D1, fail-closed). All
  three are distinct types precisely because the user's remedy differs (retry later; choose a
  mode; fix or change the relay) — see §6.

Does not belong here: HTTP details, serialization formats, `Context`, `Intent`, coroutines
dispatchers tied to Android (`Dispatchers.Main` is fine only as a parameter, never a hardcoded
default), map SDK types, database entities/DAOs, view state, string resources.

### 2.2 `data` — implementations

Implements the `domain` provider interfaces against real external systems and local storage.
Owns everything technology-specific: the chosen HTTP client, the chosen serialization format, the
chosen persistence mechanism, the chosen tile/geocoding/routing/traffic backends, mapping between
wire formats and domain types, retry/backoff and caching policy per source.

Concretely, `data` will hold (implementation, not interface, in each case):

- Traffic and congestion source (talks to Waze, decision D10 — see
  [`../privacy.md`](../privacy.md) and [`../adr/005-traffic-source-integration.md`](../adr/005-traffic-source-integration.md)).
- Tile source (talks to the OSM tile provider or an alternative — see the tile-source ADR
  proposal).
- Geocoding source (talks to the place-search provider — see the geocoder ADR proposal).
- Routing source (Waze, decision D11 — see [`../adr/003-routing-engine.md`](../adr/003-routing-engine.md);
  this remains the single highest-stakes swap point, discussed in §3, since an on-device engine is
  still addable later behind the unchanged `RouteProvider` port).
- Location source (wraps the platform location API behind `OwnPositionSource`).
- Persistence: `RouteCache`/`PlaceCache` (the `domain`-owned cache ports, §2.1), and any
  saved-place store from v0.4+, behind those ports. The bounded on-disk response caches introduced
  from v0.1 (tile — decision D7; geocoding and traffic — decision D9) are **not** behind a domain
  cache port at all — each lives entirely inside its own provider's `data` implementation
  (`data/tiles/`, `data/geocoding/`, `data/traffic/`, §8) as a file-based store, invisible to
  `domain`.

Every `data` implementation of a network-backed provider is a **client of the networking
chokepoint** (§4 below), never a direct socket user.

### 2.3 `presentation` — UI state and rendering

Renders domain state and forwards user intent to use cases. Holds **no business logic**: no
routing decisions, no cost calculation, no cache policy, no retry logic. A presentation class may
map a domain type to a display-ready view model (formatting, localisation-ready strings) but must
not derive new domain facts (e.g. it does not decide whether a route is "still valid" — that is a
use case's job, expressed as domain state the presentation layer only renders).

### 2.4 Dependency direction

```
presentation ──depends on──▶ domain ◀──implements──── data
                                ▲
                                └── data depends on domain interfaces, never the reverse
```

Rule: **dependencies point inward, toward `domain`.** `domain` depends on nothing else in the app.
`data` depends on `domain` (to implement its interfaces) and on whatever external libraries a
given provider needs. `presentation` depends on `domain` (use cases, types) and is wired to a
concrete `data` implementation only at the composition root (wherever dependency injection is
assembled) — `presentation` code never imports a `data` class directly.

**Enforcement**: the `domain`/`android.*` half of this invariant is now a build-time, structural
guarantee, not only a review-gate one — [`../adr/010-module-layout.md`](../adr/010-module-layout.md)
puts `domain` in its own Gradle module with no Android Gradle Plugin applied, so an `android.*`
import there is a compile error. The `presentation`-must-not-import-`data`-directly half of this
invariant is not yet structurally enforced (both currently live inside `:app`); `reviewer-opus` (per
`CLAUDE.md` §2 Step 4) still checks it for every changed file until a further module split, if any,
makes it structural too.

## 3. Provider abstraction: why it is a rule here specifically

Every external data source is reached exclusively through a `domain`-owned interface. This is not
generic hexagonal-architecture boilerplate for its own sake — two concrete facts in this project
make it load-bearing:

1. **The routing decision is now resolved for v0.1 (decision D11, `docs/adr/003-routing-engine.md`,
   accepted), but its candidate shapes still have opposite privacy profiles, which is exactly why
   the abstraction remains load-bearing rather than becoming decorative.** v0.1's `RouteProvider` is
   backed by a remote engine (Waze's own routing endpoint, the same operator decision D10 already
   named for the traffic flow) — origin and destination leave the device together, at full
   precision, in one request: the single most sensitive pair of coordinates the app handles, because
   together they describe a specific trip, not just an area of interest. An on-device routing engine
   remains a real, addable-later alternative that would send nothing; `domain` is written so that it
   cannot tell which of these it is talking to: a `RouteProvider` interface returning a `Route` (or
   a domain error) given an origin `Position` and a destination `Place` — matching FR-4, the
   `RouteProvider` port definition, and test 16 in `docs/specs/001-navigation-mvp.md`, not two
   `Place`s. Whether the implementation behind it does the computation on-device or ships the
   request off-device is a `data`-layer and networking-chokepoint concern only. This lets the human
   change the routing strategy later — including switching from remote to on-device for privacy
   reasons, or the reverse for a resource-cost reason — without touching `domain` or `presentation`
   at all, and without a review needing to re-audit those layers for a routing change. This is the
   concrete reason `docs/adr/003-routing-engine.md` requires `RouteProvider`'s signature to stay
   unchanged: the decision picked an implementation, not a different port shape.
2. **Every other source (traffic, tiles, geocoding) is a distinct third party with its own
   endpoint, rate limits and data sensitivity** (see [`../privacy.md`](../privacy.md)). Isolating
   each behind its own interface means a provider swap (e.g. changing tile source, or adding a
   fallback geocoder) is a `data`-layer change with a bounded blast radius: implement the
   interface, wire it at the composition root, done. It also means each provider can be faked
   individually in domain-level tests (§7) without standing up any real network dependency.

The general rule: if a responsibility might plausibly be swapped, degraded, or run entirely
on-device instead of remotely, it is named as a `domain` interface first, and the concrete
implementation is a `data`-layer plug-in behind it.

## 4. The networking chokepoint (invariant)

**Every outbound network request the app makes passes through exactly one path.** Concretely:
there is a single component in `data` responsible for issuing HTTP(S) requests; every provider
implementation (traffic, tiles, geocoding, and routing — Waze/Google, decision D11, remote and
certain from v0.1) calls into it rather than opening its own connection.

This exists because `CLAUDE.md` §5.1 requires the user-selectable relay, coordinate coarsening,
rate limiting and response caching to be applied **in exactly one place** — a policy scattered
across N call sites is a policy that gets missed at call site N+1. Centralising the path is what
makes "the relay applies to everything" a structural fact instead of a per-provider promise that
has to be re-verified every time a provider is added or changed.

**Invariant, checked at review:**

- **The chokepoint blocks every call while `RelayConfiguration` is `NotChosen`, and fails closed
  when a configured relay is unreachable (decision D1).** Neither of these is optional or a
  per-provider decision: no request reaches any provider before an explicit relay choice has been
  made (surfacing `RelayNotChosen`), and a configured-but-unreachable relay causes the call to
  fail outright (surfacing `RelayUnreachable`) rather than silently retrying direct. A chokepoint
  implementation that permits any request through either of these states — even transiently, even
  for "just this one provider" — is a **blocker finding**, identical in severity to a bypass of
  the chokepoint itself.
- Any production code that performs its own HTTP call outside the chokepoint component is a
  **blocker finding**, full stop — no exception for "just this one small request." The
  relay-selection, coarsening and rate-limiting policy lives in the chokepoint, not duplicated
  or reimplemented by an individual provider.
- A map/tile library that manages its own internal networking (common for such libraries — they
  often ship a built-in tile fetcher) must be evaluated against this invariant **before adoption**:
  can its networking be redirected through the chokepoint (custom transport/interceptor hook), or
  must it be disabled in favor of feeding it tiles fetched through the chokepoint by our own code?
  If neither is possible, the library is unsuitable regardless of its other merits. **This is an
  unresolved risk, not a hypothetical**: it is flagged here, in `docs/architecture/data-flow.md`
  (tile-fetch step), and is the decision driver for
  [`../adr/proposals/002-map-rendering-and-tiles.md`](../adr/proposals/002-map-rendering-and-tiles.md)
  — the point where a reader (and the human deciding the ADR) must actually resolve it, not
  something this document settles.
- The chokepoint's own implementation (which HTTP client, which relay mechanism) is itself an open
  decision — see the HTTP-stack and relay ADR proposals. What is fixed here is only that it is
  singular and mandatory, not what it is built from.

## 5. Concurrency and lifecycle

- **Structured concurrency**: every coroutine launched by a use case or provider implementation is
  scoped to something with a well-defined lifetime (a screen, a trip, a single request) — no
  global unscoped `GlobalScope` launches, no fire-and-forget with no owner.
- **No main-thread work**: network calls, disk access, and any non-trivial computation (route
  cost evaluation, geometry processing) run off the main thread; `presentation` only ever
  collects/observes results.
- **Cancellation tied to screen/trip lifecycle**: leaving the screen that requested a route, or
  ending the current trip, cancels the in-flight work for it. Nothing keeps running for a screen
  the user has left.
- **No polling.** State changes (position updates, incident refresh) are driven by explicit
  triggers (user action, a bounded/lifecycle-scoped location callback) rather than a timer loop
  that runs regardless of need.
- **Deferrable work is batched**, not issued eagerly one item at a time — relevant once caching or
  prefetch-adjacent work exists (not in v0.1's minimal flow, but a constraint on how it must be
  added later).
- **Nothing runs when the user is not navigating.** No background service, no scheduled job, no
  location subscription active outside an active screen/trip context in v0.1. (v0.2 introduces a
  foreground, user-visible service for active guidance — still no background location, see
  [`../privacy.md`](../privacy.md) and [`../specs/001-navigation-mvp.md`](../specs/001-navigation-mvp.md).)

## 6. Error handling

- **Expected failures are explicit result types in `domain`** — e.g. "no route found",
  `ProviderUnreachable`, "permission denied", "rate-limited by relay policy" are modelled as a
  sealed result/error type returned from a use case, not thrown. Calling code (presentation) is
  forced by the type system to handle them, and tests can assert on them without touching
  exception machinery. Relay failure is never folded into `ProviderUnreachable` (decision D6): a
  choke-point call blocked because `RelayConfiguration` is `NotChosen` returns `RelayNotChosen`,
  and one that fails because a *configured* relay is unreachable returns `RelayUnreachable` —
  distinct types because the user's remedy differs (choose a mode; fix or change the relay; retry
  later, respectively).
- **Unexpected failures are exceptions** — programming errors, contract violations. They are not
  caught and silently swallowed anywhere; `data`-layer code translates only the failures it
  understands into domain error types and lets everything else propagate.
- **Per-provider degradation strategy**: each provider interface's contract includes what happens
  when that specific provider is unavailable —
  - Traffic incidents unavailable: route computation and display continue without incident
    overlay; the user sees the route is traffic-**un**aware, not a hard failure.
  - Tile source unavailable: map does not render; route/position state is still computable and
    displayable in non-map form if the UI offers one, otherwise a clear "map unavailable" state.
  - Geocoding unavailable: destination entry fails explicitly; no silent fallback to a cached or
    guessed place.
  - Routing unavailable: no route; explicit error state, never a stale or guessed route shown as
    current.
  - Location unavailable or permission denied: app degrades to "no own position on route" rather
    than crashing or blocking the rest of the flow; see permission handling in
    [`../privacy.md`](../privacy.md).

## 7. Testability

- **Unit-testable without Android, no runtime**: all of `domain` — entities, use cases, error
  types. Because provider interfaces live in `domain`, every use case can be tested with hand-written
  or generated fakes/stubs for `PlaceSearchProvider`, `RouteProvider`, `TrafficIncidentProvider`,
  `TileProvider`, `OwnPositionSource`, and `RelaySettingsStore` — no real network, no real device
  sensor, no emulator, no real DataStore Preferences instance. This is the concrete payoff of the
  provider-abstraction rule in §3: the domain is **fully fakeable** by construction, not by
  test-specific workarounds.
- **Needs instrumentation**: `data` implementations that touch the real Android platform
  (location APIs, actual persistence engine, actual HTTP stack) and `presentation` rendering
  behaviour. These are tested with instrumentation/integration tests or, where the technology
  choice permits, contract tests against a fake server — decided per-provider once the relevant
  ADRs land.
- The module layout (§8) keeps `domain` in a module with **no Android dependency at all**, so its
  test suite is guaranteed to run as plain JVM tests, not merely "tests that happen not to use
  Android APIs today."

## 8. Module/package layout

The Gradle module boundary is decided:
[`../adr/010-module-layout.md`](../adr/010-module-layout.md) (superseding
[`../adr/proposals/010-module-layout.md`](../adr/proposals/010-module-layout.md)) fixes two Gradle
modules, `:domain` (pure Kotlin/JVM, no Android Gradle Plugin, no Android dependency of any kind —
`import android.*` is a compile error there) and `:app` (the Android application module, depending
on `:domain`). This is a **structural**, not merely conventional, enforcement of §2.4's
dependency-direction rule. The `data`/`presentation` package split inside `:app` below is still
illustrative — those packages are not yet separate Gradle modules, and nothing below beyond the
`domain`/`app` module boundary itself is binding.

```
domain/                               Gradle module :domain — pure Kotlin/JVM, no Android Gradle
                                       Plugin, no Android dependency (docs/adr/010-module-layout.md)
  model/          Coordinate, Route, Place, Incident, CongestedSegment, Position,
                  RelayConfiguration, domain errors
  usecase/        ResolveDestination, RequestRoute, LoadIncidentsForRoute, LoadTrafficForArea,
                  TrackOwnPosition, ...
  provider/       PlaceSearchProvider, RouteProvider, TrafficIncidentProvider (widened: route
                  corridor or viewport -> TrafficSnapshot, decisions D13/D15),
                  TileProvider, OwnPositionSource, RouteCache, PlaceCache, RelaySettingsStore

app/                                   Gradle module :app — the Android application module,
                                        depending on :domain. Package split below is illustrative,
                                        not yet separate Gradle modules.
  data/
    traffic/        implements the widened TrafficIncidentProvider against Waze (decision D10,
                    docs/adr/005-traffic-source-integration.md); also owns its bounded on-disk
                    response cache (decision D9, file-based, size/TTL-bounded LRU — see
                    docs/specs/001-navigation-mvp.md FR-27–FR-31), consulted before any network
                    fetch, and the five-minute minimum-interval/coalescing policy of decision D12
    tiles/          implements TileProvider; also owns the bounded on-disk tile cache (decision
                    D7, file-based, size/TTL-bounded LRU — see docs/specs/001-navigation-mvp.md
                    FR-27–FR-31), consulted before any network fetch
    geocoding/      implements PlaceSearchProvider; also owns its bounded on-disk response cache
                    (decision D9, same file-based/size/TTL/LRU standard as the tile cache — this is
                    the most sensitive of the three caches, not a looser one), consulted before any
                    network fetch
    routing/        implements RouteProvider against Waze's routing endpoint (decision D11,
                    docs/adr/003-routing-engine.md) — the same operator as traffic/; an on-device
                    engine remains addable later behind the same unchanged port
    location/       implements OwnPositionSource
    persistence/    implements RouteCache / PlaceCache
    settings/       implements RelaySettingsStore via Jetpack DataStore Preferences (decision D2,
                    docs/adr/014-settings-persistence.md)
    net/            the networking chokepoint (§4) — relay, coarsening where applicable, rate
                    limiting, caching

  presentation/
    <feature>/      screen state holders, view-model-equivalents, rendering
```

## 9. Undecided, and where the decision lands

**Two rows that used to live in this table are now decided, not undecided**, and are recorded here
instead of in the table below so the table's own title stays accurate: **routing engine and data
shape** is settled as Waze's own routing endpoint (decision D11, `docs/adr/003-routing-engine.md`,
accepted) — affecting `data` (routing source) and the networking chokepoint, with `domain`'s
`RouteProvider` interface and every use case that calls it, plus `presentation` (which renders
whatever `Route` it receives regardless of how it was computed, see §3), unaffected by the choice.
**Traffic source integration and rate limiting** is settled as Waze (decision D10,
`docs/adr/005-traffic-source-integration.md`, accepted), with the five-minute politeness floor fixed
by decision D12 — affecting `data` (traffic source) and the networking chokepoint, with `domain`'s
widened `TrafficIncidentProvider` interface unaffected; the rate-limit policy itself lives in the
chokepoint (§4), not duplicated in the provider.

| Open decision | Layer(s) affected | What must NOT depend on the choice |
|---|---|---|
| UI toolkit (Compose vs Views) | `presentation` only | `domain` and `data` reference nothing UI-toolkit-specific; use cases return plain domain/data types, not toolkit state holders. |
| Map rendering and tile source | `data` (tile source implementation) + `presentation` (rendering) | `domain`'s `TileProvider` interface and `Route`/`Incident`/`Position` types are toolkit-agnostic; a map library swap is confined to its `data` implementation plus the `presentation` rendering code. |
| Geocoding/place-search provider | `data` (geocoding source) | `domain`'s `PlaceSearchProvider` interface and `Place` type. |
| HTTP and serialization stack | networking chokepoint + every `data` provider that is network-backed | `domain` (no provider interface mentions HTTP or a serialization format); `presentation`. |
| Relay/proxy implementation (Tor, HTTP/SOCKS proxy, self-hosted instance) | networking chokepoint exclusively | Every provider implementation and all of `domain`/`presentation` — a provider never knows or cares whether a relay is active. |
| Local persistence for `RouteCache`/`PlaceCache` (Room vs SQLDelight vs plain SQLite — ADR 008) | `data` (persistence implementations of these cache ports) | `domain`'s cache port interfaces; nothing above `data` reads a database row type. **Decided separately**: `RelaySettingsStore` is not part of this open decision — it is settled as Jetpack DataStore Preferences from v0.1 (decision D2, recorded as `docs/adr/014-settings-persistence.md`) precisely so it does not wait on ADR 008, which keeps ownership of the `RouteCache`/`PlaceCache` question only. The v0.1 on-disk tile cache (decision D7) is likewise not part of this open decision: it is a file-based store (see `docs/specs/001-navigation-mvp.md` FR-27–FR-31), not a structured-database question. |
| Foreground-service and location strategy (v0.2) | `data` (location source) + a v0.2 service component | `domain`'s `OwnPositionSource` interface and use cases consuming `Position`. |
| CI, reproducible build and F-Droid pipeline | build tooling only | All runtime layers — this is a build-time concern with no runtime architectural coupling. |

Each row's ADR is written when a task first needs that decision, per `CLAUDE.md` §0.2; none of
them may be settled implicitly by this document or by writing code against a specific candidate.
**Module layout is no longer part of this table**: it is decided, see §8 and
[`../adr/010-module-layout.md`](../adr/010-module-layout.md).
