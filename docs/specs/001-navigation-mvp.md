# 001 — Navigation MVP (v0.1 "A to B")

## Verdict

`NEEDS_HUMAN_DECISION` for the feature as a whole: it is feasible, but implementation cannot
start end-to-end until the ADR proposals listed under [Prerequisites](#prerequisites) are
resolved by the human. Two of those eight — `003-routing-engine` and
`005-traffic-source-integration` — are now `accepted` (decisions D10–D16); the remaining six
(001, 002, 004, 006, 007, and 010 as a softer blocker) still gate the concrete adapters and
screens. The domain layer and its tests (Stage A–E of the [TDD test
list](#tdd-test-list)) can start immediately and are unaffected by any of those decisions.

**Decisions applied in this revision** (see the maintainer's decision record; not reopened
here): D1 (relay is an explicit, fail-closed choice), D2 (relay setting persisted via Jetpack
DataStore Preferences from v0.1, recorded as `docs/adr/014-settings-persistence.md`), D3
(destination search keeps search-as-you-type, with bounded mitigations), D4 (build/test tooling
accepted as `docs/adr/012-build-and-test-tooling.md`), D6 (relay/provider failures are three
distinct result types — `RelayNotChosen`, `RelayUnreachable`, `ProviderUnreachable` — aligning this
spec to `docs/architecture/README.md`, which already modelled them separately), D7 (v0.1 ships a
bounded, size/TTL-evicted on-disk tile cache; this spec's prior offline-caching exclusion and FR-18
were wrong and are corrected below), D9 (the privacy bar governs what leaves the device, not what
is kept locally; short-TTL on-disk response caches for geocoding and traffic are therefore also
allowed in v0.1, to the same bound/TTL/LRU/user-clearable/backup-excluded standard as the tile
cache — FR-18 and FR-27–FR-31 below are widened accordingly), **D10** (the live traffic/incident
source is Waze; recorded as `docs/adr/005-traffic-source-integration.md`, `accepted`), **D11**
(v0.1's routing engine is the same provider as the traffic source — one call returns the route, a
traffic-aware duration, and a traffic-free duration, with the delay computed as their difference;
recorded as `docs/adr/003-routing-engine.md`, `accepted`), **D12** (traffic/jam politeness: no
automatic polling, a five-minute minimum interval between requests covering the same area — served
from cache in between — and overlapping areas coalesced into one request), **D13** (traffic jams are
modelled as a new `CongestedSegment` entity, a sibling of `Incident`, with a bounded congestion-level
enum carrying an explicit `Unknown` case), **D14** (incident reliability signals — confirmation
count, reporter-trust band, confidence, and age — ship in full from v0.1, not a trimmed subset),
**D15** (a traffic, jam, or routing area/pair spanning two regions fails with a distinct, explicit
error rather than silently splitting or returning partial data). D16 (an official Waze partner
programme is not pursued now; this does not change any FR below) and D5 list what remains genuinely
open and are not touched here.

## Goal and user story

**Goal.** Let a user go from their current position to a destination they typed, and see a
traffic-aware route to it, with the traffic incidents on that route and their own position,
drawn on a map.

**User story.** As a privacy-conscious driver — including one on a de-Googled device with no
Play Services — I want to type a destination and see a route to it that reflects current
traffic, with incidents along the way and my own position on it, so that I can decide how to get
there, without the app doing anything with my data beyond what that requires.

**Behavioural framing.** Given the app is open and location permission has been granted, when I
type a destination and select a suggested match, then I see a single computed route drawn on a
map, traffic incidents intersecting that route marked on it, and my current position marked on
the route, updating while the map screen is visible.

## In scope

- Destination entry: free-text input with debounced, minimum-length search-as-you-type
  suggestions from a `PlaceSearchProvider` (FR-1, FR-2, FR-17, FR-21–FR-23). Autocomplete is a
  deliberate product choice (decision D3), not an open question — see [Risks](#risks) for the
  privacy cost that choice carries.
- A first-run relay choice: before any outbound request this feature could make, the user is
  asked to select a relay mode — direct/no relay, Tor, HTTP or SOCKS proxy, or self-hosted
  instance — and zero network activity of any kind occurs until that choice is made (FR-8;
  decision D1).
- Route computation from the device's current position to the selected `Place`, accounting for
  current traffic conditions.
- Map display of the computed route.
- Display of `Incident`s that intersect the displayed route.
- Display of `Incident`s independent of any route — an on-map, viewport-scoped view of
  community-reported alerts (police, accidents, hazards, closures), each positioned by an absolute,
  coarsened `Coordinate` rather than only relative to a route (a correction to the prior
  route-relative-only definition, which could not place a reported alert on the map outside a
  route — see the note under Stage A test 6), and each carrying a subtype, confirmation count,
  reporter-trust band, confidence, and age (decision D14). This is what lets the app show a
  reported incident near the user with no active route.
- Display of `CongestedSegment`s (traffic jams) on the map and on the active route, each with a
  bounded congestion-level indicator — never a raw number — and, when available, an estimated delay
  (decision D13).
- Display of both a traffic-aware and a traffic-free duration for the computed route, with the
  traffic delay shown as their difference (decision D11).
- Display of the user's own live `Position` on the route, kept current only while the app is
  foregrounded and the relevant screen is visible.
- A relay setting covering every outbound request this feature makes (geocoding, routing,
  traffic, tiles): direct/no relay, Tor, HTTP or SOCKS proxy, or self-hosted instance — four
  deliberately selectable modes, none of them the absence of a choice, persisted via
  `RelaySettingsStore` across process death and cold start (FR-8, FR-9, FR-24; decisions D1,
  D2). The concrete transport is
  [`007-relay-and-proxy.md`](../adr/proposals/007-relay-and-proxy.md); this spec requires only
  that the setting exists, blocks egress until chosen, applies uniformly, and persists.
- Graceful behaviour when: a provider (geocoding, routing, traffic, or tiles) is unreachable
  (`ProviderUnreachable`), no relay choice has been made yet (`RelayNotChosen`), or a *configured*
  relay is unreachable (`RelayUnreachable`, fail-closed — the request fails, never a silent direct
  fallback, FR-25, decisions D1/D6); location permission is denied; no route exists between origin
  and destination; the device is offline.
- A bounded, on-disk response cache for each of the tile, geocoding, and traffic flows: a size cap
  and a time-to-live per cache, least-recently-used eviction, a user-facing action to clear each
  one, and exclusion from Android backup and Data Extraction Rules (FR-27–FR-31; decisions D7,
  D9). These are repeat-request optimisations and privacy mitigations, not offline support — the
  app still requires connectivity for anything not already cached, and none of the three is a
  user-facing history feature (no "recent searches", see OQ6).
- Attribution: the app states, in descriptive prose only, where its traffic, incident, and route
  data come from, without any third-party branding, logo, mascot, icon, or implied affiliation
  (decision D10; `CLAUDE.md` §5.2). The non-affiliation disclaimer in `README.md` and the About
  screen is unaffected by this and stays exactly as it is.
- `en` source strings and `fr` translations for every string this feature introduces.

## Out of scope (exhaustive)

- Voice guidance or any text-to-speech output.
- Turn-by-turn instructions.
- Automatic or manual rerouting once a route is displayed (v0.2).
- Off-route detection (v0.2).
- Any background operation: no foreground service, no background location, nothing continues
  once the app is not foregrounded (v0.2 introduces the foreground service).
- Saved places, saved trips, or any trip history (v0.4+).
- Offline maps and route caching for offline use (v0.4+) — i.e. the ability to compute or display
  a route with no connectivity at all. The v0.1 on-disk tile cache (FR-27–FR-31, decision D7) is a
  bounded, repeat-view optimisation and privacy mitigation only; it does not make any part of this
  feature work offline, and a cache miss still requires a network request exactly as before.
- Proactive incident alerts during a trip and traffic-aware ETA updates beyond the value
  returned by the initial route computation (v0.3).
- Community contribution / incident reporting (distant roadmap item, not designed).
- An official Waze Connected Citizens Program (CCP) partnership (decision D16) — not pursued, not
  assumed, and not designed for; this spec integrates against Waze's consumer surface only.
- Turn-by-turn instructions or route geometry sourced from Waze specifically — already out of
  scope above; unchanged by decision D11, since this source's field names for those two items were
  never independently verified by the recon that informed D10–D16 (decision D16).
- Account creation or login of any kind.
- Multiple alternative routes — this spec assumes exactly one computed route is shown (see
  [OQ4](#open-questions)).
- A manually entered start point — origin is always the device's current position in this
  milestone (see [OQ1](#open-questions)).
- A persistent, user-facing "recent searches" or trip-history feature (see FR-18, OQ6) — the only
  state this feature persists beyond process lifetime is the relay setting (FR-24) and the three
  short-TTL, bounded response caches (tile, geocoding, traffic — FR-27–FR-31, decisions D7/D9),
  none of which is presented to the user as a history.
- Any settings screen beyond destination entry, the map, the relay setting, and the response-cache
  clear action(s) (FR-30).

## Functional requirements

Numbered, testable, one behaviour each.

| # | Requirement |
|---|---|
| FR-1 | The user can enter free text describing a destination; the entered text is reflected in the input field regardless of whether a search request has fired yet. |
| FR-2 | Once input satisfies FR-17/FR-21/FR-22/FR-23, the app requests candidate matches from a `PlaceSearchProvider` and presents them as a list of `Place` candidates with a human-readable label each. |
| FR-3 | Selecting a candidate designates it as the resolved `Place`, carrying a `Coordinate` and a label, used as the route destination. |
| FR-4 | Once a `Place` is selected and the current `Position` is known, the app requests a route from current position to that `Place` via `RequestRoute`, accounting for current traffic conditions. |
| FR-5 | The app renders the computed `Route` as an overlay on a map. |
| FR-6 | The app requests, via `LoadIncidentsForRoute`, and displays `Incident`s that intersect the displayed route. |
| FR-7 | The app displays the device's own `Position` as a marker, updated only while the map screen is visible and the app is foregrounded, at the coarsest precision sufficient to place the user on the route (see [OQ2](#open-questions)). |
| FR-8 | Before the user makes an explicit relay choice — direct/no relay, Tor, proxy, or self-hosted instance — the app makes **zero** outbound requests for this feature. A first-run step requires this choice before any network-using screen is reachable. "Direct, no relay" is one of the four deliberately selectable modes; it is never an implicit default for a choice that has not been made. The user can change the choice later from settings. |
| FR-9 | Enabling a relay mode routes **every** outbound call this feature makes (geocoding, routing, traffic, tiles) through it — none may bypass it. |
| FR-10 | If `PlaceSearchProvider` is unreachable, the app shows an explicit "unreachable" state, distinct from "no matches", and does not retry before a bounded backoff interval elapses. |
| FR-11 | If `RouteProvider` is unreachable, the app shows an explicit "unreachable" state and discards any previously displayed route rather than continuing to show it. |
| FR-12 | If `TrafficIncidentProvider` is unreachable, the app still displays the route without incidents or congested segments, plus a visible "incidents unavailable" indicator — traffic-incident display failure must not block route display. This is a distinct provider call from FR-4's traffic-aware routing ([OQ3](#open-questions) resolved this: two independent calls, decisions D10/D11/D13). |
| FR-13 | If `TileProvider` is unreachable, the app shows a clear placeholder/error instead of a blank or frozen map, without unbounded retry. |
| FR-14 | If location permission is denied, the app cannot determine an origin `Position`; it must not substitute a default or fake location, must clearly explain why no route can be computed, and must offer a path to grant permission. |
| FR-15 | If `RouteProvider` reports no feasible route, the app shows this explicitly, not an empty or broken map. |
| FR-16 | If the device has no network connectivity, the app detects this before attempting any of the four outbound calls (geocoding, routing, traffic, tile) and shows a single, clear offline state rather than separate per-provider errors. For the tile, geocoding, and traffic flows specifically, this connectivity check runs only after a cache lookup: a cache hit is served regardless of connectivity state (FR-28), and only a cache miss proceeds through this offline check before attempting the network call. |
| FR-17 | The app does not request candidates from `PlaceSearchProvider` before the input reaches a minimum length of **3 characters** (orchestrator-set default per decision D3; revisable by the maintainer). |
| FR-18 | Destination text, search results, and the computed `Route` are never exposed to the user as a persistent history — no "recent searches" or trip-history feature exists in this milestone (OQ6) — and are not retained beyond the short-TTL bounds of the response caches in FR-27–FR-31 (decisions D7, D9). The relay setting (FR-24) and the app's three bounded, short-TTL, user-invisible response caches (tile, geocoding, traffic) are the only state this feature persists beyond process lifetime. |
| FR-19 | No destination text, coordinate, or provider response appears in any log statement, in any build variant. |
| FR-20 | Every user-facing string this feature introduces is a translatable resource with an `en` source and a maintained `fr` translation; no string concatenation for sentences; plurals use `<plurals>`. |
| FR-21 | The app waits **600 ms** after the last keystroke before requesting candidates from `PlaceSearchProvider` (orchestrator-set default per decision D3; revisable by the maintainer). |
| FR-22 | An in-flight `PlaceSearchProvider` request is cancelled when the input changes before that request completes. |
| FR-23 | No `PlaceSearchProvider` request is made for whitespace-only input, regardless of length. |
| FR-24 | The relay setting (`RelayConfiguration`) persists across process death and cold start, via `RelaySettingsStore` backed by Jetpack DataStore Preferences (decision D2, recorded as `docs/adr/014-settings-persistence.md`); together with the three response caches (FR-27–FR-31) it is the only state this feature persists beyond process lifetime. |
| FR-25 | If the configured relay (Tor / proxy / self-hosted) is unreachable, the request fails outright and the user is informed; the app never falls back to a direct, unrelayed connection (fail-closed, decision D1). |
| FR-26 | Traffic-area requests (community incidents and congested segments together, via the widened `TrafficIncidentProvider`) and tile requests use a coarsened viewport/route-corridor area, never the raw device `Position`. This does **not** apply to `RouteProvider` requests (routing correctness requires precise coordinates) or to `PlaceSearchProvider` requests (geocoding needs the full typed text) — those two flows are explicitly exempt, not silently uncovered. |
| FR-27 | Each of the app's three on-disk response caches — tile, geocoding, traffic (decisions D7, D9) — is bounded by its own configured maximum size and configured time-to-live, both expressed as named constants whose concrete values are an open question for the maintainer — never hardcoded magic numbers scattered through the implementation. When a cache's size bound would otherwise be exceeded, that cache's least-recently-used entries are evicted first. Geocoding responses receive the same standard as tiles, not a looser one, because they are the most sensitive of the three artefacts (decision D9). |
| FR-28 | A cache hit for tile, geocoding, or traffic data that has not exceeded that cache's time-to-live is served without issuing a network request to the corresponding provider (`TileProvider`, `PlaceSearchProvider`, `TrafficIncidentProvider`) — including while the device is offline; see FR-16 for the check ordering this implies. |
| FR-29 | A cache entry (tile, geocoding, or traffic) whose time-to-live has elapsed is not served as current; the underlying data is re-fetched from its provider and the cache entry is refreshed. |
| FR-30 | The user can clear each on-disk response cache (tile, geocoding, traffic) on demand from settings; clearing a cache removes all of its entries immediately. Whether this is exposed as one combined action or three independent ones is a presentation-layer choice, not fixed here. |
| FR-31 | Each on-disk response cache (tile, geocoding, traffic) writes its files under Android's no-backup files directory (`Context.getNoBackupFilesDir()`), which the platform automatically and unconditionally excludes from Auto Backup and Data Extraction Rules by construction — this is enforced by where the file lives, not by authoring a `dataExtractionRules`/`fullBackupContent` XML rule naming the path, and no such XML rule is written for these three caches. None of the three caches is encrypted at rest in this milestone — stated plainly rather than implied protected; the physical-access exposure this creates is recorded in `docs/threat-model.md`. |
| FR-32 | The app displays `Incident`s independent of any active route — a viewport-scoped view of community-reported alerts — each positioned by an absolute, coarsened `Coordinate` (correcting the prior route-relative-only position, see the note under Stage A test 6) and each carrying a subtype, confirmation count, reporter-trust band, confidence, and age (decision D14). |
| FR-33 | The app displays `CongestedSegment`s (traffic jams) on the map and on the active route, each with a bounded congestion-level indicator that is never a raw number and, when available, an estimated delay (decision D13). |
| FR-34 | The app displays both a traffic-aware duration (`durationWithTraffic`) and a traffic-free duration (`durationWithoutTraffic`) for the computed route, with the traffic delay shown as `durationWithTraffic − durationWithoutTraffic` (decision D11). |
| FR-35 | A minimum of five minutes must elapse between two outbound traffic/jam requests covering the same area; a request for an area within that window is served from the traffic/jam response cache instead, regardless of that cache's own configured time-to-live (decision D12). This is a distinct floor from FR-27's per-cache TTL bound (OQ7), not the same parameter under a different name. |
| FR-36 | Two traffic/jam requests for overlapping areas are coalesced into a single outbound request rather than issued separately (decision D12). |
| FR-37 | A traffic, jam, or routing area/pair that the region-selector reports as spanning two regions fails with a distinct, explicit error rather than silently splitting, retrying, or returning partial results (decision D15). |
| FR-38 | The app states, in plain descriptive prose and without any third-party logo, mascot, icon, or visual pastiche, where its traffic, incident, and route data come from (decision D10; `CLAUDE.md` §5.2). The non-affiliation disclaimer is unaffected and stays exactly as it is. |

## Non-functional requirements

### Privacy, per flow

Full accounting lives in [`../privacy.md`](../privacy.md); this feature introduces exactly the
flows below, and the implementation must match that document exactly — no flow, no fields
beyond what is listed here.

| Flow | Sent | To whom | Precision | Trigger | Relay-capable |
|---|---|---|---|---|---|
| Destination search | Destination text (min. 3 chars, 600 ms debounce, cancelled on change) | `PlaceSearchProvider` | Full text precision — not coarsenable (FR-26 exemption) | User typing, **repeated per keystroke burst**, including text later deleted (decision D3) | Required (FR-9) |
| Route computation | Origin + destination coordinates | `RouteProvider` (decision D11: the same provider as the traffic flow below) | Full precision — not coarsenable (FR-26 exemption; routing correctness) | `Place` selected | Required (FR-9) |
| Traffic incidents & congested segments | Route-corridor or viewport area | `TrafficIncidentProvider` (widened; one call returns both incidents and jams, decision D13) | Coarsened bounding box/corridor — never the raw device `Position` (FR-26) | Route computed, or the traffic/map layer viewed; no more than once per five minutes for a given area, served from cache in between, with overlapping areas coalesced (FR-35, FR-36, decision D12) | Required (FR-9) |
| Map tiles | Viewport area | `TileProvider` | Tile-grid quantised viewport — never the raw device `Position` (FR-26) | Map panned/zoomed | Required (FR-9) |
| Device position | — (stays on device) | Nobody directly | Coarsest sufficient for on-route placement (OQ2) | Map screen visible, foregrounded | N/A — never transmitted raw; only feeds the coordinates above |

Coarsening (FR-26) applies **only** to the traffic and tile flows above; routing and geocoding
are explicitly exempt for the reasons stated in FR-26 — a reader should not infer blanket
coverage across all four flows from the general "coarsen what leaves the device" principle in
`CLAUDE.md` §5.1. Decision D11 does not change this: the routing flow's exemption predates it and
is unaffected by the routing and traffic flows now sharing a named provider (Waze, decision D10;
`docs/privacy.md`).

The map-tile, geocoding, and traffic flows additionally each maintain a bounded, size/TTL-evicted
on-disk response cache from v0.1 (FR-27–FR-31, decisions D7/D9): a cache hit for any of the three
produces no network request at all. This does not change what is sent when a request *does* fire
— every row above still describes it exactly — it only reduces how often it fires. This does not
weaken FR-19 or FR-26 either: a cached geocoding or traffic response is not logged, and caching
does not coarsen or alter the content of what was actually sent when the cache was populated. See
[`../privacy.md`](../privacy.md) flows (a), (b), and (c) for the full retention, deletion, and
backup-exclusion account of each cache. The traffic flow's cache additionally enforces the
five-minute minimum request interval and area-coalescing of FR-35/FR-36 (decision D12) — a distinct
mechanism from the cache's own TTL bound (FR-27, OQ7), not a restatement of it.

None of these flows may run, or be requested, while the app is not foregrounded (this milestone
has no background operation at all).

### Resource budget

- No network request, geocoding call, route computation, or location read runs on the main
  thread.
- No polling: every outbound call in this milestone is triggered by a direct user action
  (typing past the 3-character/600 ms threshold, selecting a destination, panning the map) —
  never a timer.
- Location updates request the coarsest accuracy and lowest frequency sufficient for on-route
  placement (FR-7); the exact figures are [OQ2](#open-questions), but the *behaviour* of
  requesting a deliberately bounded accuracy/frequency is required and tested.
- Nothing in this feature — no location callback, no network call, no map redraw — may run once
  the screen is off or the app is backgrounded; all four flows above stop and all location
  callbacks unregister on that transition, and resume only when the relevant screen is visible
  again.
- No wakelocks; no foreground service (out of scope for this milestone, see v0.2).

### Accessibility

- Every map marker (own position, route, incidents, congested segments) and the tile-unreachable
  placeholder has a content description.
- Route severity, incident severity, and congestion level (`CongestedSegment`, decision D13) are
  never conveyed by colour alone — always paired with a shape, icon, or label distinguishable
  without colour vision.
- All error/empty states (`ProviderUnreachable`, no matches, no route, offline, tile placeholder,
  `RelayUnreachable`, and the first-run `RelayNotChosen` gate) are reachable and announced by a
  screen reader — the three failure types of decision D6 are distinct states with distinct copy,
  not one generic "error" announcement.
- Tap targets (destination suggestions, relay setting controls) meet standard minimum size.

### Internationalisation

- Source locale `en`; `fr` maintained alongside every change (FR-20).
- No hardcoded user-facing string anywhere in this feature.
- No third-party **branding** appears in any string — no logo, mascot, icon, or visual pastiche of
  a provider's identity (`CLAUDE.md` §5.2). The one exception is plain descriptive prose naming the
  data recipient in the attribution string FR-38 requires (decision D10) — never in the app name,
  package name, icon, launcher label, or any other branded position. Whether attribution wording is
  legally mandated beyond that remains an open legal question, escalated if it arises, not resolved
  here.

## Layered decomposition

Responsibilities and interfaces only — no library is named or implied; each concrete choice
belongs to the ADR proposal cited in [Prerequisites](#prerequisites). Build/test tooling itself
is decided (decision D4, `docs/adr/012-build-and-test-tooling.md`): JUnit4, `kotlin.test`
assertions, `kotlinx-coroutines-test` plus Turbine for coroutine/Flow assertions, Robolectric
only where Android framework classes are genuinely unavoidable, hand-written fakes of the domain
ports (no mocking library).

### Domain (pure Kotlin, zero Android imports)

- **Value objects**: `Coordinate` (validated latitude/longitude), `Place` (a resolved search
  result: label + `Coordinate` + confidence + provenance — the same definition
  `docs/architecture/README.md` §2.1 uses, stated once and shared rather than each document
  inventing its own), `Route` (ordered path + `durationWithTraffic` + `durationWithoutTraffic`,
  with `trafficDelay` as a **computed property** — `durationWithTraffic − durationWithoutTraffic`,
  never a stored, independently-settable field, so the two source figures can never drift out of
  sync with a separately-cached delay value — decision D11), `Incident` (kind + `subtype` —
  `IncidentSubtype`, a bounded enum with an explicit `Unknown` fallback for a value the mapping
  table does not recognise — + an absolute, coarsened `Coordinate` position, independent of any
  `Route` (test 6, corrected — see the note under Stage A) + severity + reliability signals:
  confirmation count, reporter-trust band, confidence, and an age derived from a captured
  `reportedAt` instant rather than a stored duration, so freshness can be recomputed live rather
  than going stale the instant it is captured — decision D14), `CongestedSegment` (**new**, a
  sibling of `Incident`, not a variant of it, decision D13: a non-empty polyline of `Coordinate`s,
  `congestionLevel` — a bounded enum with an explicit `Unknown` fallback, never a raw integer,
  because the source's own documentation disagrees with itself about how many congestion levels
  exist — and a nullable `estimatedDelay`, where `null` means "blocked/no finite figure" rather
  than round-tripping the source's own sentinel value for that case), `Position` (the device's own
  current location: a `Coordinate` + accuracy + capture time, distinct from `Place`),
  `RelayConfiguration` (a distinct `NotChosen` state — egress-blocking, never treated as equivalent
  to any selectable mode — plus the four selectable modes: direct/no-relay, Tor, proxy,
  self-hosted, each with mode-specific endpoint data).
- **Repository interfaces (ports)**, implemented by `data`: `PlaceSearchProvider`
  (`search(text) -> candidates or a typed error`), `RouteProvider` (`route(origin, destination)
  -> Route or a typed error`, traffic-aware; **signature unchanged by decision D11** — only
  `Route`'s own shape gained a field, keeping this port abstract enough that an on-device engine
  remains addable later as an offline mode without touching `domain` or `presentation`, per
  `docs/adr/003-routing-engine.md`), `TrafficIncidentProvider` (**widened, not renamed**, decisions
  D13/D15: `snapshotFor(area) -> TrafficSnapshot or a typed error`, where `area` is a sealed
  `TrafficArea = RouteCorridor(route) | Viewport(bounds)` and `TrafficSnapshot` carries both
  `incidents: List<Incident>` and `congestedSegments: List<CongestedSegment>` — a single call
  already answers both capabilities for either scope, carrying forward the one-request-per-area
  politeness property `docs/adr/005-traffic-source-integration.md` requires), `TileProvider`
  (`tile(viewport or z/x/y) -> tile data or a typed error`), `OwnPositionSource`
  (`observe current position, only while told the app is foregrounded`), `RelaySettingsStore`
  (`get/set the active RelayConfiguration`, persisted via DataStore Preferences per decision D2,
  read by every outbound-call site before it acts).
- **Use cases**, each returning an explicit sealed result type rather than throwing for expected
  failures: `Success`, `NotFound`/`NoMatches`, `NoRouteFound`, `Offline`, `PermissionDenied`,
  `IncidentsUnavailable` (widened, decision D13, to cover the whole `TrafficSnapshot` — incidents
  and congested segments together, since both now come from one call — not renamed, since the
  existing name and its tests already carry this meaning for `LoadIncidentsForRoute`) as applicable,
  and three distinct, never-conflated relay/provider failure types per decision D6 — aligning this spec to
  `docs/architecture/README.md` §2.1/§6, which already modelled relay failure separately:
  `ProviderUnreachable` (the relay path is fine; the upstream provider itself failed),
  `RelayNotChosen` (returned when the relay choke point blocks a call because no relay choice has
  been made yet, FR-8), and `RelayUnreachable` (returned when the choke point fails a call because
  a *configured* relay could not be reached, FR-25). A prior revision of this spec folded all three
  into `ProviderUnreachable`; that was a defect, not a simplification, since the user's remedy
  differs for each (choose a mode; fix or change the relay; retry later). Use cases:
  `ResolveDestination`, `RequestRoute`, `LoadIncidentsForRoute` (a use case in its own right, not
  folded into `RequestRoute`; now reads its incidents out of the wider `TrafficSnapshot` returned by
  the widened port, filtered to the route's corridor — its own contract is otherwise unchanged,
  test 99), `LoadTrafficForArea` (**new**: the viewport-scoped sibling of `LoadIncidentsForRoute`,
  backed by the same widened port, returning a full `TrafficSnapshot` — incidents and congested
  segments — for a viewport, tests 95–98), `TrackOwnPosition`.
- No networking type, Android type, or map-rendering type appears anywhere in this layer.

### Data

- Implements each domain port (`PlaceSearchProvider`, `RouteProvider`,
  `TrafficIncidentProvider`, `TileProvider`, `OwnPositionSource`, `RelaySettingsStore`) against
  whichever concrete provider the relevant ADR settles on; owns request construction,
  response-to-domain mapping, and error mapping (timeout / HTTP error / malformed response → the
  domain's typed errors, never a raw exception escaping to `domain`).
- Owns a single connectivity check shared by all four outbound-call adapters, so "offline" is
  detected once, consistently, before any of them attempts a network call (FR-16).
- Owns relay application: every outbound call this feature makes — **including the tile
  flow** — is built so it passes through the currently active `RelayConfiguration` at one shared
  choke point, not as a per-call opt-in. That choke point (a) blocks the call outright, surfacing
  `RelayNotChosen`, while `RelayConfiguration` is `NotChosen` (FR-8, decisions D1/D6), and (b)
  fails the call, surfacing `RelayUnreachable` — never falling back to a direct connection — when
  a *configured* relay is unreachable (FR-25, decisions D1/D6). Neither of these is
  `ProviderUnreachable`, which is reserved for the upstream provider itself failing once the relay
  path is fine. This is what FR-9 requires and what the TDD list verifies directly.
- Implements a bounded on-disk response cache for each of the tile, geocoding, and traffic flows
  (FR-27–FR-31, decisions D7/D9): a size/TTL-bounded, least-recently-used store consulted before
  any network call to the corresponding provider, refreshed on TTL expiry, evicted under size
  pressure, clearable by an explicit user action, and written under Android's no-backup files
  directory (`Context.getNoBackupFilesDir()`). The geocoding cache is specified and tested to the
  same standard as the tile cache, not more loosely, since it holds the most sensitive of the three
  artefacts (decision D9). Each cache's size cap and TTL are named constants, not inline numbers;
  their concrete values are an open question for the maintainer (see Open questions), and the
  tests below are written against the *bound*, not a specific figure. A cache lookup is attempted
  before the shared connectivity check (FR-16): a hit is served whether or not the device is
  online.
- Owns coordinate/area coarsening for the traffic and tile flows only, applied before a request
  is built (FR-26); routing and geocoding requests are deliberately not coarsened, for the
  reasons FR-26 states.
- Implements the widened `TrafficIncidentProvider` (incidents + congested segments, for a route
  corridor or a viewport) against Waze's live-map area endpoint
  (`docs/adr/005-traffic-source-integration.md`, decision D10), enforcing a five-minute minimum
  interval between two requests covering the same area — served from the traffic cache in between,
  independent of that cache's own configured TTL (FR-35) — and coalescing overlapping-area requests
  into one (FR-36, decision D12), both enforced at the shared networking chokepoint alongside the
  existing debounce pattern (FR-21), not per-adapter. Defensive parsing is **mandatory, not
  optional**, because the source's own documentation is internally inconsistent: an unrecognised or
  differently-cased field (e.g. an alert `subtype` casing variant) maps to that field's own
  `Unknown` fallback rather than being dropped or crashed on, and a congestion level outside any
  range the adapter recognises maps to `CongestionLevel.Unknown` rather than an out-of-bounds value,
  because the source's own documentation disagrees with itself about how many levels exist. An area
  the region-selector reports as spanning two regions fails with a distinct, explicit region-mismatch
  error rather than silently splitting or returning partial results (FR-37, decision D15).
- Implements `RouteProvider` against the same provider as the traffic/jam adapter (Waze, decision
  D11, `docs/adr/003-routing-engine.md`): one request returns the route, `durationWithTraffic`, and
  `durationWithoutTraffic` together; `Route.trafficDelay` is computed in `domain` from the two
  durations, never received from the provider or estimated separately. A response missing either
  duration figure, or using an alternate key-naming variant known to exist in the wild, is handled
  defensively exactly like the traffic adapter above; a "no path" response maps to `NoRouteFound`,
  unchanged from the existing routing-adapter contract.
- Implements `RelaySettingsStore` against Jetpack DataStore Preferences (decision D2, recorded as
  [`014-settings-persistence`](../adr/014-settings-persistence.md)) so the relay setting survives
  process death and cold start; this does not depend on
  [`008-local-persistence`](../adr/proposals/008-local-persistence.md), which remains scoped to
  the v0.4 database question only.
- Owns location acquisition against the platform's location API, exposed to `domain` only
  through `OwnPositionSource`; must stop requesting updates the moment the app is told it is no
  longer foregrounded.

### Presentation

- Holds no business logic. Renders: the destination search field and its result/empty/error
  states; the map with route, incident, congested-segment, own-position, and tile-placeholder
  overlays; the route's traffic-aware and traffic-free durations and the traffic delay between them
  (FR-34, decision D11); the graceful-degradation states (FR-10 through FR-16); a first-run
  relay-choice step that gates access to any network-using screen until a mode is chosen (FR-8); the
  relay setting screen thereafter; a fixed attribution statement naming where traffic, incident, and
  route data come from (FR-38, decision D10).
- Owns the lifecycle wiring that starts/stops location observation and any pending network
  activity based on screen visibility and app foreground state — this is a presentation
  responsibility, not `domain`'s or `data`'s.
- Consumes only domain models and result types; performs no parsing or formatting of a
  provider's raw payload.

## TDD test list

Ordered; each test is the next smallest failing step. 117 tests total: 1–75 from the base feature,
76–87 appended for decision D9 (the geocoding/traffic response caches) and for two
presentation-layer gaps a later review found (the `RelayUnreachable` UI state and the cache-clear
settings affordance), and 88–117 appended for decisions D10–D16 (Waze-backed routing and traffic
integration: the `Incident`/`CongestedSegment`/`Route` domain additions, the widened traffic port
and its new use case, the traffic/jam and routing adapters, the five-minute politeness floor and
area-coalescing, and attribution) — appended rather than inserted, so no test 1–87 was renumbered.
Test 6 is corrected in place (see the note under it) without changing its number. Tests in
Stages A–E use hand-written fakes of the domain ports above and are independent of every undecided
library. Stages F and G name the component under test generically ("the injected client/HTTP
port") precisely so they do not assume a library choice; when an ADR is resolved, the concrete
adapter is substituted without rewriting the test's intent.

**Stage A — Domain value objects**

1. `Coordinate` rejects a latitude outside [-90, 90].
2. `Coordinate` rejects a longitude outside [-180, 180].
3. `Place` rejects a blank label.
4. `Place` requires a valid `Coordinate`.
5. `RelayConfiguration` models an explicit `NotChosen` state, distinct from every selectable mode
   (direct/no-relay, Tor, proxy, self-hosted) — nothing about constructing the type collapses
   the unset state into `Direct` (decision D1).
6. `Incident` requires a severity and an absolute, coarsened `Coordinate` position, independent of
   any `Route` (corrected in place; see the note immediately below — no test number changes).

Note: an earlier revision of this test required `Incident`'s position to be expressed only
relative to a `Route`. That was a defect, not a deliberate simplification — the recon behind
decisions D10–D16 found it could not place a reported incident on the map outside an active route,
which the "see a reported alert near me" capability (FR-32) requires. Corrected in place here,
mirroring how FR-18 was corrected in an earlier revision of this document, rather than left wrong
and superseded by a later appended test. Whether an incident lies within a given route's corridor
is now a query performed by `LoadIncidentsForRoute` (test 22) over the wider `TrafficSnapshot` (see
tests 88–117), not an intrinsic property of `Incident` itself.

**Stage B — Destination-search use case (`ResolveDestination`)**

7. Returns an empty result without calling `PlaceSearchProvider` when input is blank.
8. Returns an empty result without calling `PlaceSearchProvider` when input is whitespace-only,
   regardless of length (FR-23).
9. Does not call `PlaceSearchProvider` before input reaches the minimum length of 3 characters
   (FR-17).
10. Does not call `PlaceSearchProvider` until 600 ms have elapsed since the last keystroke
    (FR-21).
11. Cancels an in-flight `PlaceSearchProvider` call when the input changes before that call
    completes (FR-22).
12. Returns `PlaceSearchProvider`'s candidate list, as `Place` objects, on success.
13. Maps an "unreachable" port error to a `ProviderUnreachable` result.
14. Maps an "offline" port signal to an `Offline` result, distinct from `ProviderUnreachable`.
15. Returns an explicit `NoMatches` result (not an error) when the port succeeds with an empty
    list.

**Stage C — Route-computation use case (`RequestRoute`)**

16. Requires both an origin `Position` and a destination `Place` before invoking `RouteProvider`.
17. Returns `PermissionDenied` without invoking `RouteProvider` when no origin is available
    because location permission was denied (FR-14).
18. Returns `RouteProvider`'s `Route`, including its traffic-adjusted duration, on success.
19. Maps an "unreachable" port error to `ProviderUnreachable` (FR-11).
20. Maps a "no path" port result to an explicit `NoRouteFound` result, not an error (FR-15).
21. Surfaces `Offline` distinctly from `ProviderUnreachable` when connectivity is absent
    (FR-16).

Note: an earlier revision had a test 22 here ("reads the currently configured
`RelayConfiguration` and passes it through to the `RouteProvider` call"). Per decision D6, no
relay parameter ever crosses a provider port — relay is applied once, at the shared choke point
(Stage F tests 57/58), never as a per-use-case or per-call opt-in. That test asserted a wiring
shape the architecture and the port signatures (`RouteProvider.route(origin, destination)`) do not
have, and no equivalent test exists for any of the other three use cases — so it is removed here
for symmetry rather than restated. No replacement test was inserted at 22: Stage D below starts at
22, and every test number from the old 23 onward shifted down by one; the removal is recorded here
rather than silently closing the gap.

**Stage D — Incidents-on-route use case (`LoadIncidentsForRoute`)**

22. Requests incidents scoped to a given computed `Route`, not an unrelated viewport.
23. Returns `TrafficIncidentProvider`'s incident list, as `Incident` objects, on success.
24. Returns an explicit `IncidentsUnavailable` result — not a blocking error — when
    `TrafficIncidentProvider` is unreachable, so the route itself remains displayable (FR-12).
25. Returns an empty list, not an error, when the route legitimately has no incidents.

**Stage E — Own-position use case (`TrackOwnPosition`)**

26. Returns `PermissionDenied` and emits nothing when location permission is not granted
    (FR-14).
27. Emits position updates only while an explicit foreground signal says the app is
    foregrounded — the use case receives this signal as a parameter; it does not read platform
    state itself, keeping `domain` Android-free.
28. Stops emitting once a "no longer foregrounded" signal is received.
29. Requests updates from `OwnPositionSource` at a bounded interval/accuracy and, given a fake
    source that emits events faster than that interval, throttles the emitted sequence to no
    more than one update per configured interval — an observable output property, not merely an
    assertion that "a policy object was applied" (OQ2 sets the figure).

**Stage F — Data-layer repository implementations**

30. Geocoding adapter maps a successful `PlaceSearchProvider` response into `Place` objects with
    correctly parsed coordinates and labels.
31. Geocoding adapter maps a malformed/unparseable response into the domain's provider error,
    not a crash.
32. Geocoding adapter maps a timeout/connection failure into the domain "unreachable" error.
33. Geocoding adapter maps a detected lack of connectivity into the domain "offline" error
    *before* attempting the network call.
34. Geocoding adapter does not reissue a request to an unreachable `PlaceSearchProvider` before a
    bounded backoff interval elapses (FR-10).
35. `RelaySettingsStore`, backed by a fake DataStore Preferences instance, returns the
    previously stored `RelayConfiguration` after being re-created — asserting the setting
    survives process death and cold start (FR-24, decision D2). Placed here, ahead of the four
    relay-routing tests below (36, 40, 43, 46), because those tests need a store whose behaviour
    is already established; this is the smaller failing step and it should not follow the tests
    that depend on it.
36. Geocoding adapter routes its request through the currently active `RelayConfiguration` when
    relay is enabled — the call is built via the relay-aware client, not a direct one (FR-9,
    the critical anti-leak test).
37. Geocoding adapter issues an identical payload whether relay is enabled or not — only the
    transport path changes, never the request content (no extra fingerprinting data added when
    relay is off).
38. Routing adapter maps a successful response into a `Route` including traffic-adjusted
    duration.
39. Routing adapter maps a "no path between points" response into the domain `NoRouteFound`
    result, not an error.
40. Routing adapter applies the same unreachable/offline/relay mapping behaviour as the
    geocoding adapter (parallel suite, own provider).
41. Traffic adapter maps a successful, route-scoped incidents response into `Incident` objects.
42. Traffic adapter coarsens the requested viewport/corridor area before it reaches the injected
    transport — the fake transport receives a bounded/rounded area, never the raw device
    `Position` (FR-26).
43. Traffic adapter applies the same unreachable/offline/relay mapping behaviour as the
    geocoding and routing adapters.
44. Tile adapter maps a successful tile fetch into displayable tile data for the requested
    viewport/z-x-y coordinates.
45. Tile adapter coarsens the requested viewport to tile-grid quantised coordinates before it
    reaches the injected transport (FR-26).
46. Tile adapter routes its request through the currently active `RelayConfiguration` when relay
    is enabled, mirroring test 36 (FR-9).
47. Tile adapter issues an identical request whether relay is enabled or not, mirroring test 37.
48. Tile adapter maps an unreachable `TileProvider` into the domain's "unreachable" error, from
    which the map screen derives the placeholder state (FR-13).
49. Tile-cache adapter serves a cache hit for a previously-fetched, non-expired tile without
    issuing a `TileProvider` network request (FR-27, FR-28).
50. Tile-cache adapter treats an entry whose time-to-live has elapsed as a miss: it re-fetches
    from `TileProvider` rather than serving the stale entry, and refreshes the cache with the new
    result (FR-29).
51. Tile-cache adapter, given writes that would exceed its configured maximum size, evicts
    least-recently-used entries first and never exceeds the bound — asserted against the
    configured bound itself, not a hardcoded figure, since the concrete size/TTL constants are an
    open question for the maintainer (FR-27).
52. Tile-cache adapter, given an explicit clear-cache action, removes all previously cached
    entries — a subsequent request for a previously-cached tile is a cache miss (FR-30).
53. Tile-cache adapter's configured base directory resolves to Android's no-backup files
    directory (`Context.getNoBackupFilesDir()`), verified via Robolectric against a real (test)
    `Context` — not asserted merely as "the directory the adapter happens to have been constructed
    with," which would be tautological and would not actually verify the backup-exclusion claim in
    FR-31. This is the one test in this cluster (49–53) that needs a `Context`, real or Robolectric
    — see Prerequisites for what that means for when it can start.
54. Position-provider implementation unregisters the platform location callback — not merely
    ignores its output — when told the app is no longer foregrounded (resource discipline).
55. Position-provider implementation requests a bounded accuracy/interval from the platform API
    — the fake platform location client receives the configured values, not the platform's
    default fine/high-frequency accuracy (OQ2 sets the figure; this is an observable request
    parameter, not a documentation check).
56. The shared connectivity checker reports "offline" before any of the **four** network
    adapters (geocoding, routing, traffic, tile) attempts a call, verified through one shared
    component rather than four reimplementations (FR-16).
57. The relay choke point blocks every outbound call before it is attempted, surfacing
    **`RelayNotChosen`** to the calling use case, while `RelayConfiguration` is `NotChosen` —
    verified through the same shared component as test 56, not per-adapter (FR-8, decisions
    D1/D6). Retargeted from a prior revision that surfaced `ProviderUnreachable` here, which
    conflated "no relay chosen" with "the upstream provider failed" — the user's remedy for the
    two is different (choose a mode, vs. retry later), so decision D6 requires they be distinct
    result types.
58. The relay choke point fails the call outright — surfacing **`RelayUnreachable`** and never
    falling back to a direct, unrelayed connection — when the configured relay (Tor/proxy/
    self-hosted) is unreachable (FR-25, decisions D1/D6; a regression guard against fail-open
    being shipped by accident). Retargeted from `ProviderUnreachable` for the same reason as test
    57 — a configured-but-unreachable relay is neither "no relay chosen" nor "the provider
    itself failed."
59. A fake logger capturing all log calls made during a geocoding search, a route computation,
    and a traffic-incident fetch contains no coordinate, address, or search string in any entry
    (FR-19).

**Stage G — Presentation**

60. Destination-search screen renders the currently typed text in the input field as the user
    types, independent of whether a search request has fired yet (FR-1).
61. Destination-search screen renders the current suggestion list from `ResolveDestination`
    unchanged.
62. Renders a distinct `NoMatches` state — not blank, not an error — when the use case reports
    it.
63. Renders the `ProviderUnreachable` state distinctly from `NoMatches`.
64. Selecting a suggestion invokes `RequestRoute` with that `Place` as destination.
65. Map screen renders the route overlay when `RequestRoute` returns a `Route`.
66. Map screen renders the `PermissionDenied` state, with a path to grant permission, instead of
    attempting to render a route, when location permission is absent (FR-14).
67. Map screen renders `NoRouteFound` distinctly from `ProviderUnreachable`.
68. Map screen discards a previously displayed route and shows the `ProviderUnreachable` state
    — never continuing to show the stale route — when a subsequent `RequestRoute` call fails
    (FR-11).
69. Map screen renders a single `Offline` state — not per-provider errors — when any use case
    reports `Offline` (FR-16).
70. Map screen renders the own-position marker only while `TrackOwnPosition` is emitting, and
    removes it when the app leaves the foreground.
71. Map screen renders incident markers along the route when present, without blocking route
    display when incidents are `IncidentsUnavailable` (FR-12).
72. Map screen renders a tile-unreachable placeholder — not a blank or frozen map — when the
    tile adapter reports its unreachable error (FR-13).
73. First-run relay-choice screen requires an explicit selection among direct/no-relay, Tor,
    proxy, and self-hosted before the app navigates to destination search or the map — no
    network-using screen is reachable while `RelayConfiguration` is `NotChosen`, surfaced as
    `RelayNotChosen` to anything that tries (FR-8, decisions D1/D6).
74. Relay-setting screen (reachable after the first-run choice) displays the currently active
    `RelayConfiguration` mode and, when changed, calls `RelaySettingsStore` to persist the new
    selection — wiring only; durability across process death is test 35.
75. Every string resource this feature introduces resolves in both `en` and `fr` — a
    resource-completeness test, failing on any missing key.

**Stage F (continued) — Response caches: geocoding, traffic (decision D9)**

Appended here rather than inserted back into Stage F's original position, so every existing
cross-reference to tests 1–75 elsewhere in this document, in `docs/testing.md`, and in the ADR
proposals stays valid without renumbering. These are still Stage-F-shaped data-layer tests against
fakes/fixtures; they mirror tests 49–53 (the tile cache) exactly, because decision D9 requires the
geocoding and traffic caches be specified and tested to the same standard, not a looser one.

76. Geocoding-cache adapter serves a cache hit for a previously-fetched, non-expired query response
    without issuing a `PlaceSearchProvider` network request — including while the device is
    offline (FR-27, FR-28).
77. Geocoding-cache adapter treats an entry whose time-to-live has elapsed as a miss: it re-fetches
    from `PlaceSearchProvider` rather than serving the stale entry, and refreshes the cache with
    the new result (FR-29).
78. Geocoding-cache adapter, given writes that would exceed its configured maximum size, evicts
    least-recently-used entries first and never exceeds the bound — asserted against the
    configured bound itself, not a hardcoded figure (FR-27).
79. Geocoding-cache adapter, given an explicit clear-cache action, removes all previously cached
    entries — a subsequent request for a previously-cached query is a cache miss (FR-30).
80. Geocoding-cache adapter's configured base directory resolves to Android's no-backup files
    directory (`Context.getNoBackupFilesDir()`), verified via Robolectric against a real (test)
    `Context`, mirroring test 53 (FR-31).
81. Traffic-cache adapter serves a cache hit for a previously-fetched, non-expired viewport/
    corridor response without issuing a `TrafficIncidentProvider` network request — including
    while the device is offline (FR-27, FR-28).
82. Traffic-cache adapter treats an entry whose time-to-live has elapsed as a miss: it re-fetches
    from `TrafficIncidentProvider` rather than serving the stale entry, and refreshes the cache
    with the new result (FR-29).
83. Traffic-cache adapter, given writes that would exceed its configured maximum size, evicts
    least-recently-used entries first and never exceeds the bound (FR-27).
84. Traffic-cache adapter, given an explicit clear-cache action, removes all previously cached
    entries — a subsequent request for a previously-cached area is a cache miss (FR-30).
85. Traffic-cache adapter's configured base directory resolves to Android's no-backup files
    directory (`Context.getNoBackupFilesDir()`), verified via Robolectric against a real (test)
    `Context`, mirroring tests 53 and 80 (FR-31).

**Stage G (continued) — the third D6 failure state, and the cache-clear settings affordance**

Also appended for the same numbering-stability reason. A prior revision of this test list covered
`ProviderUnreachable` (tests 63, 67, 68) and `RelayNotChosen` (test 73) at the presentation layer
but left `RelayUnreachable` asserted only at the data layer (test 58) — meaning a build could pass
every other test and still show the user one generic error for all three of decision D6's failure
types, which the Risks section below says must not happen. Likewise, FR-30's cache-clearing
mechanism was tested at the data layer (tests 52, 79, 84) but never wired to a settings affordance.

86. A screen (destination search or map, whichever the use case that failed belongs to) renders the
    `RelayUnreachable` state distinctly from both `RelayNotChosen` and `ProviderUnreachable`, with a
    path to fix or change the relay setting — the third of decision D6's three failure types is
    visible to the user, not only asserted in a data-layer test (FR-25).
87. The settings screen exposes a control (or controls — one combined action or three independent
    ones, per FR-30) that, when activated, invokes the clearing path for each response cache
    verified at the data layer by tests 52, 79, and 84 — wiring only; durability of the clearing
    behaviour itself is those three tests, not this one.

**Stage A (continued) — domain additions for decisions D10–D16 (Waze-backed routing and traffic)**

Appended here rather than inserted into Stage A's original position, for the same
numbering-stability reason as every other appended block above: every existing cross-reference to
tests 1–87 elsewhere in this document, in `docs/testing.md`, and in the ADR proposals stays valid.
These are pure-Kotlin domain tests against hand-written fakes/fixtures, independent of every
pending ADR, exactly like tests 1–6.

88. `Incident.subtype` maps an unrecognised source value to `Unknown` rather than throwing or
    defaulting to a guessed known case.
89. `Incident`'s reliability signals bundle a confirmation count and a reporter-trust band, both
    constructible independently of each other — no field requires the other to be present.
90. `CongestedSegment` requires a non-empty geometry (at least two points).
91. `CongestedSegment.congestionLevel` maps an out-of-range or unrecognised source value to
    `Unknown`.
92. `CongestedSegment.estimatedDelay` is `null` when the source's sentinel-for-blocked value is
    mapped, never a literal negative duration.
93. `Route.trafficDelay` equals `durationWithTraffic − durationWithoutTraffic` and is never a
    stored, independently-settable field — a regression guard against the two figures drifting
    apart (decision D11).
94. `Route.trafficDelay` is non-negative for a route where traffic is at or below the free-flow
    baseline — given fixture data where the two source durations are equal, or where
    `durationWithTraffic` is (incorrectly, per a malformed fixture) reported lower than
    `durationWithoutTraffic`, the delay floors at zero rather than going negative.

**Stage D (continued) — `LoadTrafficForArea`, and `LoadIncidentsForRoute`'s widened backing**

95. `LoadTrafficForArea` requests a `TrafficSnapshot` scoped to a given viewport, not the whole
    visible map margin beyond what was asked.
96. Returns both `incidents` and `congestedSegments` from a single `TrafficIncidentProvider` call —
    asserted via a fake that records call count, guarding the one-call-per-area politeness property
    (decision D12).
97. Returns an empty `TrafficSnapshot` (not an error) for an area with no incidents and no
    congested segments.
98. Returns a distinct "unavailable" result — not a blocking error — when the port is unreachable,
    so map/route display continues without the overlay (widens the existing `IncidentsUnavailable`
    behaviour to also cover congested segments, decision D13).
99. `LoadIncidentsForRoute`, now backed by the widened port, still returns only incidents
    intersecting the route corridor, not the full `TrafficSnapshot` unfiltered — a regression guard
    that widening the port did not widen this use case's own contract.

**Stage F (continued) — traffic/jam adapter widening (decisions D10, D12, D13, D15)**

Mirrors the shape of tests 41–43 (the existing traffic adapter) and 49–53/76–85 (the response
caches), extended for the widened port and the new politeness/resilience requirements.

100. Maps a successful area response into `Incident` objects with correctly parsed subtype,
     position, and reliability fields, against a fixture built from a real observed sample shape.
101. Maps a successful area response into `CongestedSegment` objects with correctly parsed
     geometry, congestion level, and delay (including the blocked-sentinel case from test 92).
102. Given a fixture with a field the adapter does not recognise (an extra key, a renamed key, or
     an alert `subtype` casing variant), the adapter still produces a result for every field it
     *does* recognise, and maps the rest to their `Unknown` fallbacks — never throwing and never
     dropping the whole response over one unfamiliar field.
103. Given a fixture that is malformed in a way no fallback covers (e.g. missing a structurally
     required field like the array itself), the adapter maps this to a typed provider error, not
     an unhandled exception escaping to `domain`.
104. Given an empty-area fixture (valid response, zero incidents, zero congested segments), the
     adapter returns an empty `TrafficSnapshot`, not an error.
105. Given a simulated throttled response (`429`/`Retry-After` or equivalent fixture), the adapter
     backs off per the configured policy and does not reissue before the backoff interval elapses.
106. Given a simulated blocked response (sustained `403`-shaped fixture), the adapter surfaces
     `ProviderUnreachable` and does not retry in a tight loop.
107. Given a bounding box the fake region-selector reports as spanning two regions, the adapter
     resolves a single deterministic region choice or fails explicitly with a distinct
     region-mismatch error (FR-37, decision D15), rather than sending an inconsistent multi-region
     request.
108. Coarsens the requested area before it reaches the injected transport, exactly like tests
     42/45's existing pattern — extended here to confirm the widened port still coarsens for the
     viewport case, not only the route-corridor case (FR-26).
109. Routes through the currently active `RelayConfiguration`, and issues an identical payload
     whether relayed or not, mirroring tests 36/37/46/47, extended to the widened request shape.
110. Enforces a five-minute minimum interval between two requests covering the same area: a request
     issued before five minutes have elapsed since the last request for that area is served from
     the traffic cache instead, independent of that cache's own configured TTL (FR-35, decision
     D12).
111. Coalesces two requests for overlapping areas into a single outbound request rather than
     issuing them separately (FR-36, decision D12).

**Stage F (continued) — routing adapter, Waze-backed (decision D11)**

112. Maps a successful response into a `Route` carrying both `durationWithTraffic` and
     `durationWithoutTraffic` from the two source fields, not just one.
113. Given a fixture missing the without-traffic figure, the adapter surfaces a typed provider
     error rather than inventing a value or silently falling back to a single-duration `Route`.
114. Given a fixture using an alternate key-naming variant already known to exist in the wild for
     this kind of source, the adapter still parses correctly — a direct regression guard against
     response-shape drift.
115. Maps a "no path" response into the existing `NoRouteFound` result, unchanged from the current
     routing-adapter test pattern.
116. Applies the same unreachable/offline/relay/throttled/blocked mapping behaviour as the
     traffic/jam adapter (parallel suite, own provider), mirroring tests 40, 105, and 106.

**Stage G (continued) — attribution (decision D10, FR-38)**

117. A fixed, always-reachable location in the app (e.g. an about/info screen or a map-layer
     legend) renders a plain descriptive statement naming where traffic, incident, and route data
     come from, with no logo, mascot, icon, or visual pastiche of that source's identity — wiring
     only; the non-affiliation disclaimer itself is unaffected and outside this feature's test
     surface.

## Definition of Done

- [ ] FR-1 through FR-38 are each covered by at least one passing test from the list above,
      **except FR-18's negative clause** — that no persistence of destination text, search
      results, or the computed route exists *beyond* the relay setting and the three bounded
      response caches (no separate, unbounded, user-facing "recent searches" or trip-history path)
      — and **FR-20's string-concatenation and plurals clauses**: both are negative/structural
      properties (no additional code path persists these fields beyond what tests 76–80/81–85
      already bound; no code path builds a sentence by concatenation or bypasses `<plurals>`)
      verified by code review and lint/detekt during the mandatory self-review pass, not by a
      positive automated test — stated here explicitly rather than claimed as tested. Test 75
      covers only key *presence* in `en`/`fr`, not the string-shape clauses of FR-20.
- [ ] The domain module contains zero Android framework imports (verified, not assumed).
- [ ] Every outbound call this feature makes (geocoding, routing, traffic, tiles) is documented
      in [`../privacy.md`](../privacy.md), matching actual behaviour exactly.
- [ ] Tests 36, 40, 43, and 46 (relay applies to every one of the four providers — geocoding,
      routing, traffic, tile) pass — the relay setting has no exception.
- [ ] Tests 57 and 58 pass: no path exists where an unset (`NotChosen`, surfaced as
      `RelayNotChosen`) or an unreachable configured relay (surfaced as `RelayUnreachable`)
      silently permits a direct request (decisions D1/D6; regression guard against fail-open).
- [ ] Tests 49–53, 76–80, and 81–85 pass: each of the three bounded on-disk response caches
      (tile, geocoding, traffic — FR-27–FR-31, decisions D7/D9) hits without a network call
      (including while offline), refetches on TTL expiry, stays within its configured size bound
      under pressure, is fully clearable, and resolves to Android's no-backup files directory. The
      geocoding cache (76–80) meets this at the same depth as the tile cache, not more loosely
      (decision D9).
- [ ] Tests 86 and 87 pass: `RelayUnreachable` renders as a distinct UI state from
      `RelayNotChosen` and `ProviderUnreachable`, and the settings screen's cache-clear affordance
      actually invokes the clearing path — closing the presentation-layer gap a later review found
      in a prior revision of this test list.
- [ ] Test 59 passes: FR-19 (no PII in logs) is covered by an automated fake-logger assertion,
      not by manual review alone. A manual logcat spot-check during the self-review pass remains
      good practice but is not the basis of this DoD item.
- [ ] Tests 88–99 pass: the `Incident`/`CongestedSegment`/`Route` domain additions (subtype,
      absolute coarsened position, reliability signals, congestion-level and delay fallbacks, the
      computed `trafficDelay`) and the widened `LoadTrafficForArea`/`LoadIncidentsForRoute` use
      cases behave as decisions D11, D13, and D14 require, entirely against fakes.
- [ ] Tests 100–111 pass: the widened traffic/jam adapter parses defensively (an unrecognised or
      differently-cased field never drops or crashes the whole response), fails explicitly on a
      structurally malformed response or a region-spanning area (FR-37), and enforces the
      five-minute minimum request interval and area-coalescing (FR-35, FR-36, decision D12) —
      distinct from, and in addition to, the existing traffic-cache TTL tests (81–85).
- [ ] Tests 112–116 pass: the Waze-backed routing adapter (decision D11) carries both durations,
      fails explicitly rather than guessing on a missing figure or a "no path" response, and applies
      the same resilience behaviour as the traffic/jam adapter.
- [ ] Test 117 passes: the attribution statement (FR-38, decision D10) is rendered, without any
      third-party branding, and the non-affiliation disclaimer is unchanged.
- [ ] No network call, location read, or map render occurs while the app is backgrounded or the
      screen is off (verified, not assumed).
- [ ] No data is persisted beyond process lifetime except the relay setting (FR-24, via
      `RelaySettingsStore`/DataStore Preferences, decision D2) and the three bounded on-disk
      response caches — tile, geocoding, traffic (FR-27–FR-31, decisions D7/D9); destination text,
      search results, and the computed route are never exposed as a persistent history and never
      persist outside those caches' short-TTL bounds (FR-18).
- [ ] Every new user-facing string exists in `res/values/strings.xml` (`en`) and
      `res/values-fr/strings.xml` (`fr`); no hardcoded text; no string concatenation for
      sentences; plurals via `<plurals>` where applicable.
- [ ] Accessibility: content descriptions present on all map markers/icons and the
      tile-unreachable placeholder; all error/empty states reachable by screen reader;
      route/incident distinction is never colour-only.
- [ ] [`../architecture/README.md`](../architecture/README.md) updated to reflect the modules
      actually created.
- [ ] [`../privacy.md`](../privacy.md) updated and re-verified against the actual outbound calls
      and permissions requested by the merged code, including all three caches' retention/
      deletion/backup-exclusion entries.
- [ ] Every ADR this spec cites under Prerequisites exists and is approved before the
      corresponding adapter/screen is implemented.
- [ ] The tile flow (`TileProvider`, tests 44–48, 72, plus its cache at tests 49–53) has the same
      test depth as the geocoding, routing, and traffic flows, and the geocoding and traffic
      response caches (tests 76–85) have the same test depth as the tile cache — none of the
      three is a lighter-weight exception.
- [ ] Lint/detekt/ktlint clean; no rule disabled to reach green.
- [ ] Full test-suite output pasted in the PR, per `CLAUDE.md` §8 — no claim of "tests pass"
      without it.
- [ ] Mandatory self-review pass completed before the PR is opened.

## Prerequisites

**Must be resolved (ADR approved) before the corresponding code is written:**

| ADR | Blocks |
|---|---|
| [`001-ui-toolkit`](../adr/proposals/001-ui-toolkit.md) — `proposed` | All of Stage G (presentation), tests 60–75, plus the appended presentation tests 86–87 and 117 |
| [`002-map-rendering-and-tiles`](../adr/proposals/002-map-rendering-and-tiles.md) — `proposed` | The map screen (tests 65–72) and the tile-provider outbound flow (tests 44–48) |
| [`003-routing-engine`](../adr/003-routing-engine.md) — **`accepted`** (decision D11) | The routing adapter (tests 38–40, 65–69, and the appended tests 112–116); OQ3 is resolved by this decision and D13, see [Open questions](#open-questions) |
| [`004-geocoding-provider`](../adr/proposals/004-geocoding-provider.md) — `proposed` | The geocoding adapter and search screen wiring (tests 30–34, 36–37, 60–64) — test 35 (`RelaySettingsStore`) sits between 34 and 36 but does not need this ADR, see below |
| [`005-traffic-source-integration`](../adr/005-traffic-source-integration.md) — **`accepted`** (decisions D10, D12–D15) | The traffic/jam adapter (tests 41–43, 71, and the appended tests 100–111) |
| [`006-http-and-serialisation`](../adr/proposals/006-http-and-serialisation.md) — `proposed` | Concrete request/response mapping in all network adapters (tests 30–34, 36–48, 100–116) — again excluding test 35, which needs no HTTP client |
| [`007-relay-and-proxy`](../adr/proposals/007-relay-and-proxy.md) — `proposed` | The concrete relay-aware client (tests 36, 37, 40, 43, 46, 47, 57, 58, 73, 74, 86, 109, 116) — the domain port itself (test 5) and the `RelaySettingsStore` persistence mechanism (test 35, settled independently by decision D2/ADR 014) do not need it |
| [`010-module-layout`](../adr/proposals/010-module-layout.md) — `proposed` | Not a hard blocker for writing domain tests in a temporary single module, but rework is expected if skipped before the first commit |

**003 and 005 are now `accepted`** (decisions D10–D16) — they no longer block writing the code that
depends on them, only on the remaining `proposed` ADRs in this table (001, 002, 004, 006, 007, 010)
still gating the concrete adapters. This narrows, but does not close, the overall
`NEEDS_HUMAN_DECISION` verdict at the top of this spec.

**Not a blocker for v0.1**, contrary to what an earlier revision of this spec implied:
[`008-local-persistence`](../adr/proposals/008-local-persistence.md) is scoped to the v0.4
database question only. The relay setting's persistence mechanism (Jetpack DataStore
Preferences) is settled directly by decision D2 and recorded as
[`014-settings-persistence`](../adr/014-settings-persistence.md); it needs no proposal ADR of its
own, and test 35 (`RelaySettingsStore` persistence) does not depend on either 007 or 008 — it is
gated by neither ADR 004 nor ADR 006 either, which is why it is excluded from both of their ranges
above rather than swept into them. Neither does any of the three response caches (decisions D7,
D9): each wraps its own provider port behind a fake in tests 49–53 (tile), 76–80 (geocoding), and
81–85 (traffic), and each is a file-based store independent of any structured-persistence engine,
so none is gated by 008, and none is gated by 002/003/004/005/006 (no cache test needs a concrete
provider, tile source, or HTTP client — only a fake of the port it wraps).

**Can start before any ADR is resolved:**

- Stage A (domain value objects, tests 1–6, plus the appended domain tests 88–94) — pure Kotlin, no
  external dependency.
- Stages B–E (use-case tests, tests 7–29, plus the appended `LoadTrafficForArea`/
  `LoadIncidentsForRoute` tests 95–99) against hand-written fakes of the domain ports — this is
  also how the ports themselves get frozen before `data` implements them.
- Test 35 (`RelaySettingsStore` persistence), against a fake DataStore Preferences instance —
  gated by no ADR, per the paragraph above.
- The tile-cache adapter tests (49–52), the geocoding-cache adapter tests (76–79), and the
  traffic-cache adapter tests (81–84), each against a fake of the port it wraps and file-based
  storage — independent of every pending map/tile/HTTP/persistence ADR (see above). Tests 53, 80,
  and 85 (the backup-exclusion test for each cache) can also start now in the sense that they need
  no pending ADR either, but they do need Robolectric and a real or Robolectric-provided `Context`
  to resolve `getNoBackupFilesDir()` — they are not fake-only the way the rest of this cluster is.
- The domain sealed result/error types shared across use cases.
- This spec and the architecture description of the layering it assumes.

## Risks

- **Resolved — split traffic providers (was: "Split traffic providers").** OQ3 asked whether
  routing (FR-4) and traffic-incident listing (FR-6) would turn out to be one call or two. Decisions
  D11/D13 answer this: they are **two independent port calls** (`RouteProvider` for the route and
  its two durations; the widened `TrafficIncidentProvider` for incidents and congested segments),
  even though both are now backed by the same operator (Waze, decision D10). This recon found no
  evidence the two wire requests can be merged into one. FR-11 and FR-12 already modelled two
  independent error states for exactly this shape, so no re-baselining of this spec was needed —
  see [Open questions](#open-questions) for the formal resolution of OQ3.
- **Two of the app's most sensitive capabilities are now concentrated behind one operator (decision
  D11).** Extending the traffic source (D10) to routing means the single most sensitive pair of
  coordinates this app ever handles — origin and destination, together — also reaches Waze/Google,
  under the same terms D10 already accepted. This is a known, accepted cost recorded in
  `docs/adr/003-routing-engine.md` and `docs/roadmap.md`, not an oversight.
- **No offline routing (decision D11).** Every route computation and every v0.2 reroute is a live
  remote call to the same operator as the traffic flow; there is no fallback once connectivity is
  lost. `RouteProvider` stays abstract so an on-device engine remains addable later as an offline
  mode, but nothing in this milestone builds one.
- **Destination text is the highest-sensitivity payload in this milestone**, and decision D3
  makes it worse than a single-shot search would: because suggestions fire on every debounced
  keystroke burst (FR-21), partial strings — including text the user typed and then deleted —
  reach `PlaceSearchProvider` repeatedly per trip, not once. FR-9 (relay coverage), FR-17/FR-21
  (minimisation) and FR-22/FR-23 (cancellation, whitespace exclusion) are the controls carrying
  that weight, and all must be verified by the Stage B/F tests, not by code review alone.
- **Relay friction.** The first-run gate (FR-8, surfaced as `RelayNotChosen`) and fail-closed
  behaviour (FR-25, surfaced as `RelayUnreachable`) mean a user who has not yet chosen a relay, or
  whose configured relay goes down, cannot use any feature in this spec at all — this is the
  correct privacy trade-off per decisions D1/D6, but the UI copy for both states, and for the
  unrelated `ProviderUnreachable` state, must make the cause and the remedy obvious and distinct
  (pick a mode; fix or change the relay; retry later — three different remedies, not one generic
  "error"), per decision D6.
- **None of the three response caches is encrypted at rest (decisions D7, D9).** Stated honestly
  rather than implied protected: the on-disk tile, geocoding, and traffic caches introduced by
  FR-27–FR-31 are each a legible record — viewed map areas, and, for the geocoding cache
  specifically, recent destination search text and its candidate results — to anyone with access
  to the device's storage. The geocoding cache is the most sensitive of the three for exactly this
  reason (decision D9), which is why it is specified and tested (76–80) to the same standard as
  the tile cache, not a looser one. `docs/threat-model.md` records this as a physical-access
  exposure; the mitigation in this milestone is each cache's size/TTL bound and its clear action
  (FR-30), not encryption. This is a deliberate application of decision D9's doctrine — the privacy
  bar governs what leaves the device, and this data never does — not an oversight; the doctrine
  does not relax the three things D9 itself keeps strict: no PII in logs, backup/data-extraction
  exclusion, and this honest description.
- **User expectation mismatch.** This milestone stops all activity the moment the app is
  backgrounded, by design (no background operation). A user expecting the app to "keep working"
  while driving with the screen off will be surprised; this is correct per the brief for v0.1,
  but UI copy should make the limitation visible rather than silent. Not a functional
  requirement of this spec — flagged for UX consideration.
- **Third-party politeness.** v0.1's traffic/jam flow is user-triggered, never on a timer, but is
  now additionally throttled to a five-minute minimum interval per area, with overlapping areas
  coalesced (FR-35, FR-36, decision D12) — a self-imposed floor, since no rate limit is published
  for this endpoint (`docs/adr/005-traffic-source-integration.md`). This is stricter than
  FR-17/FR-21's debounce alone required before this decision; it changes further at v0.3 once
  traffic queries become tied to an ongoing trip.

## Open questions

- **OQ1 — human decision required.** Is a manually entered start point in scope for v0.1, or is
  the origin always the device's live position? The brief only mentions "own position on the
  route," implying live position as origin. This spec treats a manual start point as out of
  scope by default.
- **OQ2 — human decision required.** What location accuracy/update interval is "coarse enough"
  to place the user on the route (tests 29, 55)? No figure is given in the brief.
- **OQ3 — resolved by decisions D10/D11/D13, not reopened here.** Are traffic-for-routing-cost
  (FR-4) and traffic-incidents-for-display (FR-6) the same provider call, or two independent ones?
  **Two independent port calls**: `RouteProvider` (route + both durations) and the widened
  `TrafficIncidentProvider` (incidents + congested segments), even though both are now backed by the
  same operator (Waze). The recon behind D10–D16 found no evidence the two wire requests can be
  merged into one. FR-11 and FR-12's existing two-error-state design already matches this outcome,
  so no re-baselining was needed.
- **OQ4 — human decision required.** Does v0.1 show more than one route alternative, or exactly
  one? The brief says "a route," singular; this spec assumes exactly one.
- **OQ5 — human decision required.** Must the relay setting expose the full mode set (direct/no
  relay / Tor / proxy / self-hosted) in v0.1's UI, or only a binary on/off with the concrete mode
  fixed elsewhere? This spec assumes the full set, per the brief's description of the relay as a
  first-class requirement, deferring only the concrete transport (007).
- **OQ6 — human decision required.** Confirm that the only persistence beyond process lifetime is
  the relay setting and the three bounded, short-TTL response caches (FR-18) — with no user-facing
  "recent destination"/history convenience of any kind — is the intended scope, per decision D9,
  rather than a broader convenience feature the brief does not mention (which would in any case be
  a v0.4+-style concern needing its own privacy analysis).
- **OQ7 — human decision required (decisions D7/D9 introduce this, deliberately left open).** The
  concrete maximum-size and time-to-live figures for each of the three response caches — tile,
  geocoding, traffic (FR-27). D7/D9 fix that these are named constants per cache and that eviction
  is LRU; they deliberately do not fix the numbers themselves — tests 49–53, 76–80, and 81–85 are
  written against each cache's bound, not a specific value, so this can be set later without
  touching the test list. Nothing requires the three caches share the same figures.

**Resolved since the previous revision, not reopened here:** the relay default/egress-blocking
behaviour and its persistence mechanism (formerly open, now D1/D2), the destination-search
debounce/minimum-length figures (resolved by D3 into FR-17/FR-21, no longer tracked under any open
question here), whether local response caching for geocoding/traffic is permitted at all (resolved
by D9 — it is; only the figures in OQ7 remain open), the traffic/incident source and its integration
architecture (resolved by D10/D12/D13/D14/D15, recorded as `docs/adr/005-traffic-source-
integration.md`), the routing engine (resolved by D11, recorded as `docs/adr/003-routing-engine.md`
— the costs accepted are recorded there and in `docs/roadmap.md`, not hidden), and OQ3 above. See
the maintainer's decision record for the full reasoning; D5 and the round-2/round-3 decision records
list what is still genuinely open beyond OQ1, OQ2, OQ4–OQ7 above.

**Known gap, not a decision to make — tracked so it is not mistaken for an oversight:** no
accessibility or string-resource inventory exists yet anywhere in this project. This spec's
Accessibility and Internationalisation requirements above are the first written for LibreWays,
with no prior baseline audit to reconcile against. Noted per the maintainer's decision record; no
inventory is invented here.
