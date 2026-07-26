# LibreWays — Data flow

Status: living document. Cross-references: [`architecture/README.md`](README.md) for the layer/provider
structure these flows run through, [`../privacy.md`](../privacy.md) for the privacy
characterisation (what is sent, to whom, how coarse, what is retained) of every network step named
below, and [`../specs/001-navigation-mvp.md`](../specs/001-navigation-mvp.md) for the requirements
this flow implements. No technology named here is decided — see
[`../adr/proposals/`](../adr/proposals/) for the open choices and their status.

## 1. v0.1 — "A to B" flow

Scope: user enters a destination, gets a traffic-aware route, sees the route and incidents on a
map, sees their own position on the route. No guidance, no rerouting, no background operation
(`architecture/README.md` §5).

### 1.1 Diagram

```
 ┌──────────────┐
 │ presentation │  user types destination text
 └──────┬───────┘
        │ (1) destination text
        ▼
 ┌──────────────────────────┐   PlaceSearchProvider    ┌──────────────────┐
 │ domain: ResolveDestination├────────────────────────▶│ data: geocoding  │
 │ use case                 │◀────candidate Place(s)───┤ source          │
 └──────┬────────────────────┘                          └────────┬─────────┘
        │ (2) user picks one Place                                │ HTTP via chokepoint (§2.2)
        ▼                                                          ▼
 ┌──────────────────────────┐   RouteProvider          ┌──────────────────┐
 │ domain: RequestRoute      ├────────────────────────▶│ data: routing    │
 │ use case                 │◀──Route(+both durations)──┤ source (Waze,   │
 └──────┬────────────────────┘                          │ decision D11)   │
        │ (3) Route                                      └────────┬─────────┘
        │                                                          │ HTTP via chokepoint,
        │                                                          │ ALWAYS (decision D11: remote, certain)
        ▼
 ┌──────────────────────────┐  TrafficIncidentProvider ┌──────────────────┐
 │ domain: LoadIncidentsFor  ├──(RouteCorridor/Viewport)▶│ data: traffic   │
 │ Route / LoadTrafficForArea│◀─TrafficSnapshot(Incidents,│ source (Waze,  │
 │ use cases                 │  CongestedSegments)──────┤ decision D10)   │
 └──────┬────────────────────┘                          └────────┬─────────┘
        │ (4) Route + Incidents + CongestedSegments                │ HTTP via chokepoint,
        │                                                           │ ≥5 min/area, coalesced (D12)
        ▼                                                          ▼
 ┌──────────────────────────┐   TileProvider           ┌──────────────────┐
 │ presentation: map render  ├────────────────────────▶│ data: tile       │
 │                           │◀───────tiles─────────────┤ source          │
 └──────┬────────────────────┘                          └────────┬─────────┘
        │ (5) map with route + incidents drawn                    │ HTTP via chokepoint
        ▼
 ┌──────────────────────────┐   OwnPositionSource      ┌──────────────────┐
 │ domain: TrackOwnPosition  ├────────────────────────▶│ data: location   │
 │ use case                 │◀──────Position stream────┤ source (device   │
 └──────┬────────────────────┘                          │ GPS, on-device) │
        │ (6) Position projected onto Route              └──────────────────┘
        ▼                                             (no network — on-device only)
 ┌──────────────────┐
 │ presentation: map │  own-position marker on the route
 └──────────────────┘

                    ┌────────────────────────────────────────────────┐
                    │  data/net: networking chokepoint (README §4)     │
                    │  every arrow above labelled "HTTP via             │
                    │  chokepoint" passes through here: relay           │
                    │  selection, rate limiting and response caching    │
                    │  apply to all of them. BLOCKS EVERY CALL while    │
                    │  RelayConfiguration is NotChosen (RelayNotChosen) │
                    │  and FAILS CLOSED — never falls back direct —    │
                    │  when a configured relay is unreachable           │
                    │  (RelayUnreachable), per decisions D1/D6.         │
                    │  Coordinate coarsening applies WHERE APPLICABLE   │
                    │  only — not to geocoding (query text) or to       │
                    │  routing (Waze, decision D11 — needs precise      │
                    │  coordinates); see ../privacy.md and §1.3 below   │
                    └────────────────────────────────────────────────┘
```

### 1.2 Step by step

**Step 0a — first-run relay choice (gate).** Layer: `presentation`, enforced by `data/net`'s
chokepoint. Before any of steps 1, 3, 3b, or 5 below can run even once, the user must make an
explicit relay choice — direct/no relay, Tor, HTTP/SOCKS proxy, or self-hosted instance.
`RelayConfiguration` starts `NotChosen`, a state distinct from every selectable mode including
"direct, no relay" itself, and the chokepoint blocks every outbound call while it holds
(surfacing `RelayNotChosen`, decisions D1/D6). `presentation` reflects this by routing the user to
a first-run relay-choice screen instead of destination search or the map until a choice is
recorded (FR-8). This is a precondition of every network-crossing arrow in this flow, not an
optional first screen — see `architecture/README.md` §4's invariant and §1.3 below.

**Step 0b — destination entry.** Layer: `presentation`. The user types free-text into a
destination field. Nothing crosses the network yet; this is local UI state only.

**Step 1 — geocoding.** Layer: `domain` (`ResolveDestination` use case) calling the
`PlaceSearchProvider` interface, implemented in `data` by the geocoding source.
- Crosses the network: **a separate query per debounced keystroke burst, not once per submitted
  search** (decision D3) — minimum input length of 3 characters, 600 ms debounce after the last
  keystroke, the in-flight request cancelled when the input changes again, no request for
  whitespace-only input. This includes partial strings the user typed and then deleted, if they
  survived past the minimum length and the debounce window before being replaced. Each such query
  goes out through the networking chokepoint to a place-search backend. This is the most sensitive
  outbound call in the app, and decision D3 makes it more so than a single-shot search would — the
  query text directly encodes user intent, disclosed repeatedly per search rather than once (see
  [`../privacy.md`](../privacy.md), geocoding flow).
  Passes the chokepoint: yes — relay, rate limiting, response caching apply.
- Cached: a bounded, size/TTL-evicted on-disk response cache from v0.1 (decision D9) avoids
  re-querying an identical string, whether from a minor UI re-render or the user retyping the same
  text; a cache hit produces no chokepoint traffic at all. Held to the same size/TTL/LRU/clearable/
  backup-excluded standard as the tile cache, not a looser one, since this is the most sensitive of
  the three cached artefacts.
- Persisted: the geocoding response cache above, on-disk, bounded, not encrypted at rest, excluded
  from Android backup/Data Extraction Rules, user-clearable — this is not a "saved places" or
  "recent searches" user-facing feature (that remains v0.4+, undesigned, out of scope here); see
  [`../privacy.md`](../privacy.md), geocoding flow, for the full retention/deletion account.
- On failure (no network, provider error, no results): `domain` returns an explicit "no result" /
  "provider unreachable" error type (`architecture/README.md` §6); `presentation` shows an explicit
  empty/error state, never a guessed or stale destination.
- User sees meanwhile: a loading indication while candidates are fetched, then a list of
  candidate places to disambiguate ("Main St" could be several).

**Step 2 — user selects a `Place`.** Layer: `presentation` forwards the chosen candidate to
`domain`. No network; this is a local selection among already-fetched candidates.

**Step 3 — traffic-aware route request.** Layer: `domain` (`RequestRoute` use case) calling the
`RouteProvider` interface, implemented in `data` by the routing source (Waze, decision D11,
`docs/adr/003-routing-engine.md`).
- Crosses the network: **always** — this is now settled (decision D11), not conditional on an
  undecided shape (`architecture/README.md` §3). Origin and destination coordinates cross the
  network together through the chokepoint — the single most sensitive coordinate pair the app
  produces, since together they describe a specific trip. The same request also returns a
  traffic-aware and a traffic-free duration, from which `domain` computes the displayed delay as
  their difference. An on-device engine remains a real, addable-later alternative (`RouteProvider`'s
  signature does not depend on which one is used), but nothing in this milestone builds one.
  Passes the chokepoint: yes, unconditionally — no routing call bypasses it.
- Cached: a computed route for a given origin/destination/traffic-state may be cached for the
  duration of the trip context to avoid recomputation; not persisted beyond the session in v0.1.
- Persisted: nothing in v0.1.
- On failure (no route found, provider unreachable, rate-limited): explicit domain error type;
  `presentation` shows a clear "no route available" state rather than a blank or stale map.
- User sees meanwhile: a loading state on the map/route panel between destination confirmation and
  route display.

**Step 3b — incident and congestion overlay.** Layer: `domain` (`LoadIncidentsForRoute` for the
route-scoped case, `LoadTrafficForArea` for the viewport-scoped case — distinct use cases from
`RequestRoute`, not a sub-step folded into it: this data can fail, be retried, or be refreshed
independently of the route itself) calling the widened `TrafficIncidentProvider`, implemented in
`data` by the traffic source (Waze, decision D10, `docs/adr/005-traffic-source-integration.md`).
- Crosses the network: the route's geographic area or the viewport goes out through the chokepoint
  to Waze (see [`../privacy.md`](../privacy.md), traffic flow). Not the exact route polyline unless
  the provider's query model requires it — minimise to what the feature needs (`CLAUDE.md` §5.1).
  One request returns both `Incident`s and `CongestedSegment`s (decision D13). No more than one
  request per five minutes for a given area, with overlapping areas coalesced into one (decision
  D12).
  Passes the chokepoint: yes.
- Cached: incident/jam data is short-lived by nature (traffic conditions change); held in a bounded,
  size/TTL-evicted on-disk response cache from v0.1 (decision D9) so an unchanged viewport/corridor
  does not refire — a cache hit produces no chokepoint traffic at all. This same cache is also where
  decision D12's five-minute floor is enforced, as a distinct property from the cache's own TTL.
- Persisted: the traffic response cache above, on-disk, bounded, not encrypted at rest, excluded
  from Android backup/Data Extraction Rules, user-clearable; see [`../privacy.md`](../privacy.md),
  traffic flow, for the full retention/deletion account.
- On failure (traffic source unavailable, or a region-mismatch error per decision D15): degrades to
  route-without-incidents per the degradation strategy in `architecture/README.md` §6 — not a hard
  failure of the whole flow.
- User sees meanwhile: the route can display before incidents/jams finish loading, or both can be
  shown together, depending on how the use case sequences the two calls — sequencing is an
  implementation detail, not fixed here.

**Step 4 — route displayed with incidents and congestion.** Layer: `presentation` renders the
`Route` (with its traffic-aware and traffic-free durations, decision D11), the `Incident` list, and
the `CongestedSegment` list it received. No new network activity at this step — rendering consumes
what domain already fetched.

**Step 5 — map tiles.** Layer: `presentation` (map rendering) calling `TileProvider`, implemented
in `data` by the tile source.
- Crosses the network: tile requests for the visible viewport go out through the chokepoint to
  the OSM tile provider or an alternative (see [`../privacy.md`](../privacy.md), tile flow) — a
  second, distinct third party from the traffic source, with its own IP/viewport exposure.
  Passes the chokepoint: yes, including if the eventual map library ships its own built-in tile
  fetcher — see the chokepoint invariant in `architecture/README.md` §4. This is an unresolved risk,
  not a settled fact: a map library that manages its own internal networking may not expose a hook
  to redirect it through the chokepoint, in which case relay routing, coarsening-where-applicable,
  and rate limiting cannot be applied to its traffic at all. Whether the eventually-chosen library
  can be redirected, or must be disabled in favour of feeding it tiles fetched by our own
  chokepoint-routed code, is decided at the point `docs/adr/proposals/002-map-rendering-and-tiles.md`
  is accepted — a library that fails this test is unsuitable regardless of its rendering quality,
  full stop.
- Cached: tiles are cached on-device (standard tile-client behaviour) to avoid re-fetching the
  same viewport; bounded by a configured size cap and TTL with LRU eviction from v0.1 (decision D7,
  `docs/specs/001-navigation-mvp.md` FR-27–FR-31); a cache hit produces no chokepoint traffic at
  all for that tile.
- Persisted: cached tiles may persist across sessions as an on-device cache (not offline maps —
  that is v0.4+); nothing about this cache is transmitted anywhere. Not encrypted at rest in v0.1
  (`docs/threat-model.md`); user-clearable and excluded from Android backup/Data Extraction Rules.
- On failure (no network, tile source unavailable): map fails to render tiles; the rest of the
  flow (route, incidents, own position) is still computable and, if the UI offers a non-map
  fallback, displayable — otherwise a clear "map unavailable" state.
- User sees meanwhile: a placeholder/background while tiles load, populated as they arrive.

**Step 6 — own position tracked on the route.** Layer: `domain` (`TrackOwnPosition` use case)
calling `OwnPositionSource`, implemented in `data` by the location source (wrapping the platform
GPS API).
- Crosses the network: **nothing.** Position acquisition is on-device only. The device's raw
  location never leaves the device at this step. It only becomes an input to Steps 1/3/3b/5 when
  those features need it (e.g. "route from my current position" reuses the position as the origin
  coordinate, coarsened as needed for whichever of those calls it feeds) — see
  [`../privacy.md`](../privacy.md) for exactly which flows consume position and at what precision.
- Cached: the current position is held in memory for the duration of the screen/trip context;
  not persisted to disk in v0.1.
- Persisted: nothing.
- On permission denial (`ACCESS_FINE_LOCATION` refused or revoked): the use case degrades to "no
  own position available" — the route and incidents still display; presentation shows an explicit
  "location unavailable" indicator rather than silently omitting the marker or crashing. No
  background location is requested at any point (`CLAUDE.md` §5, product brief).
- User sees meanwhile: a marker on the route once a position fix is available; nothing (or an
  explicit prompt/indicator) before permission is granted or before the first fix arrives.

### 1.3 Cross-cutting notes for this flow

- Every network-crossing step above (1, 3, 3b, 5) passes through the single networking
  chokepoint (`architecture/README.md` §4). What the chokepoint applies is **not identical across
  all four**: relay selection, rate limiting and response caching apply to every one of them
  without exception. Coordinate coarsening is applied **only where the flow's correctness allows
  it** — it is a real mitigation for the traffic corridor/viewport (step 3b) and for tiles (step 5),
  but it cannot be applied to geocoding (step 1: the query text must be sent as typed, or the
  feature breaks) or to routing (step 3: origin/destination must be precise enough to route
  correctly — certain per decision D11, not conditional on a shape). The chokepoint is the single
  place *where* coarsening happens when it happens, not a claim that it happens everywhere — see
  [`../privacy.md`](../privacy.md) §"What this
  app cannot promise" (point 4) and its per-flow mitigation rows for exactly which policy applies to
  which flow.
- **Every one of steps 1, 3, 3b, and 5 is additionally blocked while `RelayConfiguration`
  is `NotChosen`, and fails closed rather than falling back to direct when a configured relay is
  unreachable** (step 0a above; decisions D1/D6). This is not a fifth, separate policy alongside
  relay selection/rate limiting/caching — it is what "relay selection" means at the moment no
  relay has been chosen yet, or the chosen one has failed: block, then fail closed, never fall
  through direct. See `architecture/README.md` §4's invariant.
- Failure in one step never blocks the others from degrading gracefully and independently — see
  `architecture/README.md` §6 for the per-provider degradation table.
- No step in this flow runs unless the user is actively on this screen; nothing polls or continues
  once the user navigates away (`architecture/README.md` §5).

## 2. v0.2 — active guidance flow (forward-looking, NOT YET SPECIFIED)

This section previews the next milestone's shape only to show where v0.1's boundaries anticipate
it; it is **not** a specification. Full detail belongs in a future `docs/specs/` entry when v0.2 is
planned.

```
 (v0.1 flow above, continuing)
        │
        ▼
 ┌────────────────────────────┐
 │ domain: guidance use case   │  turn-by-turn instruction generation from Route + Position
 │ (not yet specified)         │
 └──────┬─────────────────────┘
        │ instruction events
        ▼
 ┌────────────────────────────┐        ┌───────────────────────────┐
 │ data: on-device TTS         │        │ presentation: foreground   │
 │ (platform TTS, no network)  │        │ guidance notification      │
 └────────────────────────────┘        └───────────────────────────┘
        ▲
        │ off-route detection triggers a new call through
        │ RouteProvider (Step 3 above), same chokepoint rules apply
 ┌────────────────────────────┐
 │ domain: off-route detection │
 │ + rerouting (not yet        │
 │ specified)                  │
 └────────────────────────────┘
```

Known constraints already fixed by `CLAUDE.md` and the product brief, even though the flow itself
is unspecified:
- Runs inside a **foreground, user-visible service** — never a background service, never
  `ACCESS_BACKGROUND_LOCATION`.
- TTS is on-device; no network call for speech.
- Off-route detection reuses the same `RouteProvider`/chokepoint path as the v0.1 route request —
  it does not introduce a second, parallel networking path.
- Guidance notification and wake handling are UI/lifecycle concerns in `presentation` and the v0.2
  service component, not `domain` logic.

Open questions (v0.2, deferred): exact off-route detection thresholds, rerouting trigger
frequency/backoff, instruction generation source (derived from the same `Route` object or a
separate guidance-specific provider) — all **open questions for the human / a future recon pass**,
not decided by this document.

## 3. Explicitly out of scope of this document

- Any flow for community-contributed incident reporting (distant roadmap item; not designed, per
  the product brief).
- v0.3 (incident alerts along the active route, traffic-aware ETA) and v0.4+ (saved places, route
  caching for offline use) flows — not traced here; each gets its own data-flow update when
  planned.
