# Roadmap

Every milestone below is a **scope statement, not a date**. None is committed to a release
timeline; each becomes buildable only once its listed prerequisites (an approved ADR per open
decision, see [`adr/proposals/`](adr/proposals/)) are resolved by the human, and only after
its own [`specs/`](specs/README.md) dev spec exists. The order is fixed; the vision is full
turn-by-turn navigation, reached incrementally so every step ships something a user can use on
its own.

For each milestone: a one-sentence goal, what the user sees, what is deliberately excluded from
that milestone (it may appear later), the open technical decisions that gate it, and the new
privacy surface it introduces relative to the milestone before it.

## v0.1 — "A to B"

**Goal.** Let a user go from their current position to a destination they typed, seeing a
traffic-aware route and the incidents on it, on a map.

| | |
|---|---|
| User-visible scope | A first-run relay choice (direct/no relay, Tor, proxy, or self-hosted instance) that must be made before any outbound request occurs · destination text entry with search-as-you-type suggestions · a computed route from current position to destination that accounts for current traffic · the route and traffic incidents on it drawn on a map · the user's own live position on the route · a relay setting for outbound requests |
| Explicitly out of this milestone | Voice guidance, turn-by-turn instructions, automatic or manual rerouting, any background operation (no foreground service, no background location), saved places/trips, offline maps and route caching for offline use, proactive incident alerts during a trip, community contribution. (v0.1 does ship three bounded, size/TTL-evicted on-disk response caches — tile, geocoding, traffic — as repeat-request optimisations, decisions D7/D9 — these are not offline support, and a cache miss still requires connectivity exactly as before.) |
| Prerequisites (ADR proposals) | [`001-ui-toolkit`](adr/proposals/001-ui-toolkit.md) · [`002-map-rendering-and-tiles`](adr/proposals/002-map-rendering-and-tiles.md) · [`003-routing-engine`](adr/proposals/003-routing-engine.md) · [`004-geocoding-provider`](adr/proposals/004-geocoding-provider.md) · [`005-traffic-source-integration`](adr/proposals/005-traffic-source-integration.md) · [`006-http-and-serialisation`](adr/proposals/006-http-and-serialisation.md) · [`007-relay-and-proxy`](adr/proposals/007-relay-and-proxy.md). Module layout, previously listed here as recommended before the first commit, is decided — see [`adr/010-module-layout.md`](adr/010-module-layout.md) — and the first commit has been made. |
| New privacy surface | First four outbound flows of the app, all new: destination text to a geocoding/search provider — the **highest-cost flow in this milestone**, because destination search is search-as-you-type: every debounced keystroke burst sends a query, so partial strings (including text typed and then deleted) reach that provider repeatedly over the course of choosing a destination, not once; origin+destination to a routing engine; viewport/route area to a traffic-incident source; viewport tiles to a map-tile provider; foreground-only device GPS read to place the user on the route. No outbound request of any kind occurs until the user has made an explicit first-run relay choice, and all four flows must be relay-capable from this milestone (see [`privacy.md`](privacy.md)). **Also the app's first persistent local storage**: three bounded, size/TTL-evicted on-disk response caches — tile, geocoding, traffic — each user-clearable and excluded from backup/Data Extraction Rules, but none encrypted at rest (decisions D7, D9; see [`privacy.md`](privacy.md), [`threat-model.md`](threat-model.md)). The geocoding cache is the most sensitive of the three, since it holds recent destination search text and results — the maintainer's doctrine (D9) is that this local-only trade-off is acceptable because it reduces outbound requests, provided logs, backup exclusion, and honest description remain uncompromised |

Dev spec: [`specs/001-navigation-mvp.md`](specs/001-navigation-mvp.md).

## v0.2 — Active guidance

**Goal.** Guide the user turn-by-turn along the v0.1 route while they drive, without any of
that guidance leaving the device as speech data.

| | |
|---|---|
| User-visible scope | Turn-by-turn instructions · on-device text-to-speech · off-route detection and rerouting · a guidance notification · keep-awake/screen handling while guiding |
| Explicitly out of this milestone | Incident alerts pushed during the trip and traffic-aware ETA updates (v0.3), saved trips (v0.4+), community contribution |
| Prerequisites (ADR proposals) | [`009-location-and-foreground-service`](adr/proposals/009-location-and-foreground-service.md) · relies on `003-routing-engine` (v0.1) already being resolved, since rerouting reuses the same routing capability |
| New privacy surface | A foreground service keeps location active and issues repeated routing/traffic calls while the trip is in progress (rerouting), which is new outbound-call *frequency*, not a new destination; new permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (still **no** `ACCESS_BACKGROUND_LOCATION` — guidance only runs in a user-visible foreground service); on-device TTS introduces no new outbound flow |

## v0.3 — Trip awareness

**Goal.** Keep the user informed of incidents and delay on their active route without them
having to ask.

| | |
|---|---|
| User-visible scope | Incident alerts along the active route · traffic-aware ETA updates during the trip |
| Explicitly out of this milestone | Any rerouting-strategy change beyond v0.2's mechanism, saved data, community contribution |
| Prerequisites (ADR proposals) | Extends `005-traffic-source-integration`; needs an explicit refresh/polling cadence added to that ADR or a follow-up decision, since this milestone raises call frequency on someone else's infrastructure (CLAUDE.md §5.1 — no aggressive polling) |
| New privacy surface | Traffic-source queries become correlated with an active, real-time trip rather than a one-off route request — higher frequency, same destination-revealing risk as v0.1's traffic flow, now sustained over the trip's duration |

## v0.4+ — Convenience

**Goal.** Let the user save places and trips and use previously fetched data without a network
connection.

| | |
|---|---|
| User-visible scope | Saved places · saved trips · route/tile caching for offline use |
| Explicitly out of this milestone | Any account or cloud-sync system (see open question below), community contribution |
| Prerequisites (ADR proposals) | [`008-local-persistence`](adr/proposals/008-local-persistence.md) |
| New privacy surface | The app's first persistent data at rest that is **identifying in the specific sense of naming places the user chose** — home, work, habitual destinations — rather than merely recording map areas viewed (v0.1's on-disk tile cache, decision D7, already persists that). A history of places and trips stored on the device. Requires an explicit deletion path, exclusion from `allowBackup`/data-extraction rules unless the user opts in, and no exposure of this data in exported files or crash traces |

**Open question — human decision required:** does "saved places/trips" imply any sync
mechanism across devices? The brief does not describe one. Absent a decision, this milestone is
scoped as on-device-only storage; introducing sync (even self-hosted) would need its own ADR and
recon spec, and must not silently reintroduce an account or identifying network flow.

## Distant roadmap — community contribution (not designed)

The vision includes letting users report incidents upstream, the way mainstream crowdsourced
navigation apps do. This is **deliberately not designed**: no architecture, no API shape, no
data model exists for it, and none should be started before the open questions below are
resolved by the human. Listed here only so the possibility is not lost, and so no agent
mistakes its absence for an oversight.

**Open questions — human decision required, all unresolved:**

- **Third-party credentials.** Reporting an incident upstream would likely require
  authenticating against a service LibreWays does not control — this conflicts directly with
  CLAUDE.md §5.1's prohibition on touching third-party account credentials unless and until a
  human explicitly decides otherwise for this specific case.
- **Terms-of-service exposure.** Whether writing data to that third party is even permitted
  under its terms is a legal question, not an engineering one.
- **Privacy cost of an identifying write path.** A contribution is, by nature, attributable to
  a place and moment a specific device was at — the opposite of everything else in this app's
  design. No mitigation for this has been designed or proposed.

No further planning work should occur on this item until a human resolves these.
