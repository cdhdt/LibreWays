# LibreWays — Privacy and data flows

> **Status: pre-alpha — nothing described below is implemented yet.** There is no working build, no
> APK, and no released code (see the root [`README.md`](../README.md)). Every claim in this document
> is a binding commitment for what the shipped app **will** do once each milestone is built, verified
> the same way this document says to verify it, against the actual running app and source — not a
> description of an app that exists today. Treat the design as decided and non-negotiable; treat the
> runtime behaviour as not yet built.

This document is the authoritative, honest description of what LibreWays is designed to send off
the device, store locally, and log once built. It is written for a hostile, technically competent
reader who intends to verify every claim against the running app and the source code as each
milestone ships. If any sentence here overstates the protection the app will actually provide,
that is a defect, exactly as serious as a data leak (`CLAUDE.md` §5.1, §10.17).

Scope: milestones v0.1–v0.4 as defined in the product brief. The distant "community incident
reporting" roadmap item is explicitly out of scope for this document until it is designed; see
[Open questions](#open-questions) for its status.

## Principles

- **Where the privacy line sits (decision D9, the maintainer's doctrine).** This document's bar
  governs what **leaves the device**. Data the app keeps locally for its own function — caches,
  settings, saved state — is acceptable, and caching is actively encouraged where it reduces
  outbound requests, because fewer requests means less third-party exposure. A reader of this
  document should not treat local storage as a defect in itself; it is a mitigation, not a leak.
  Three things stay strict regardless, because they leave the device despite appearing local:
  logs (logcat is readable by other tools and processes), backups and data-extraction rules (a
  backup leaves the device), and honesty (local data is described accurately, including that it is
  unencrypted and legible to anyone with access to the device — never implied protected). See
  `CLAUDE.md` §5 for the binding statement of this doctrine.
- Data minimisation: collect and transmit only what the active feature strictly needs, at the
  coarsest precision that still works, for the shortest useful time.
- Every outbound network call is a consequence of a specific user action. Nothing fires on launch,
  on a timer, or speculatively.
- **No outbound request is made before the user has made an explicit relay choice** (decision D1).
  "Direct, no relay" is itself a deliberate, first-class selection — it is never the silent default
  that results from not having configured anything. A first run in which no choice has been made
  yet performs zero network requests. Once a choice is made, every outbound request passes through
  the networking chokepoint that enforces it: relayed if a relay was chosen, direct only if "direct"
  was explicitly chosen. If a configured relay becomes unreachable, the request **fails closed** —
  it is reported to the user as failed, never silently retried over a direct connection.
- No identifier, no fingerprint, no telemetry, ever, without an explicit opt-in that does not exist
  in this product today.
- This document is updated in the same pull request as any change to an outbound call, a
  permission, a stored field, or a log statement (`CLAUDE.md` §7).

## What this app cannot promise

Before anything reassuring: LibreWays is a **client of third-party services it does not control**.
Using its core features necessarily sends network requests to those services. Concretely:

1. **Requests leave the device.** Rendering a map, searching for a destination, reading traffic and
   congestion data, and computing a route — the last two now both to Waze/Google, decisions D10/D11
   — all require contacting an external server. That server sees, at minimum, the requester's IP
   address (or the relay's, if one is configured) and the data the feature needs to send.
2. **We cannot audit third-party servers.** Once a well-formed request reaches the tile provider,
   the geocoding provider, or Waze (traffic, congestion, and routing), we have no visibility into
   what that operator logs, retains, correlates, or shares. We can only control what we send, not
   what happens to it afterwards.
3. **A relay hides the requester's IP, not the request content.** Routing traffic through Tor, a
   proxy, or a self-hosted instance stops the destination provider from learning the user's network
   origin. It does not stop the provider from seeing the query itself (a search string, a viewport,
   or — worst case — a routing origin/destination pair). It also does not defeat a sufficiently
   resourced adversary doing traffic-timing correlation against the relay network itself; see
   `docs/threat-model.md`.
4. **Some data cannot be coarsened without breaking the feature.** A destination search string must
   be sent as typed for geocoding to work. The routing origin and destination coordinates must be
   precise enough to route correctly — this is now certain, not conditional (decision D11): every
   route computation sends them to Waze/Google together, at full precision — coarsening them
   materially degrades or breaks the result. Precision reduction is a real mitigation for viewport
   and traffic queries; it is not a free option everywhere.
5. **We cannot protect an unlocked, physically accessed, or compromised device.** Local storage
   minimisation and deletion controls reduce what there is to find; they do not defend against a
   device that is unlocked in someone else's hands, rooted, or running a compromised OS. See
   `docs/threat-model.md` for the explicit non-goals.
6. **We cannot stop a modified fork from lying about this document.** GPL-3.0 permits redistribution
   of modified builds. Nothing in this document, nor in the genuine app, prevents a third party from
   shipping a build that behaves differently while claiming to be LibreWays. The mitigation is
   reproducibility and source auditability, not a technical guarantee inside the app itself.

Everything that follows describes what the app does; it does not change the limits above.

## Flow summary

| Flow | Third party | What is sent | Precision | Trigger | Local storage | Relay-eligible | Sensitivity |
|---|---|---|---|---|---|---|---|
| (a) Traffic and congestion endpoint | **Waze/Google** (decision D10; `docs/adr/005-traffic-source-integration.md`) | Viewport or route-corridor area, IP — one request returns both community incident reports and congested-segment (jam) data (decision D13) | Coarsened bounding box/corridor | Map/traffic layer visible, active trip; no more than once per five minutes for a given area, overlapping areas coalesced (decision D12) | On-disk response cache (size/TTL-bounded, LRU), from v0.1 (decision D9) | Yes | Highest — see re-stated ranking below |
| (b) OSM tile provider | Tile provider (ADR pending) | Tile coordinates (z/x/y) for visible area, IP | Tile-grid quantised | Map on screen | On-disk tile cache (size/TTL-bounded) | Yes | Lowest of the four network flows |
| (c) Geocoding / place search | Geocoding provider (ADR pending) | Free-text search string per debounced keystroke burst (including deleted partial strings), optional viewport bias, IP | Full text precision — not coarsenable | Repeatedly per trip: each debounced keystroke burst past 3 characters (600 ms debounce, decision D3) — not once per search | On-disk response cache (size/TTL-bounded, LRU), from v0.1 (decision D9) — the most sensitive local artefact in the app, bound to the same standard as the tile cache, not a looser one | Yes | High — certain, but no longer the single worst flow, see re-stated ranking below |
| (d) Routing | **Waze/Google** — the same operator as flow (a) (decision D11; `docs/adr/003-routing-engine.md`) | Origin + destination together, at full precision, in one request; that same request also returns a traffic-aware and a traffic-free duration (decision D11) | Full precision — not coarsenable | User requests a route; reroute | Active-route geometry, session-only | Yes | **Highest — now certain, not conditional; the single worst flow, see re-stated ranking below** |
| (e) Device GPS | None — on-device only | Nothing directly | N/A | Foreground use, active guidance | In-memory only | N/A | Feeds into (a)–(d) |

## Sensitivity ranking

**Re-stated again after decision D11** (Waze-backed routing accepted, `docs/adr/003-routing-engine.md`):
the previous version of this ranking (itself re-stated after decision D3) placed geocoding first and
routing second, on the reasoning that geocoding's elevated risk was certain to exist while routing's
was conditional on an ADR that had not been accepted. That conditionality is now resolved: ADR-003 is
`accepted`, so a routing request stating origin and destination together, at full precision, is now
**certain** to occur on every route request and every reroute — not a hypothetical worst case. The
previous ranking already recognised that, per event, a routing request is a more precise and complete
disclosure than any individual geocoding query (a specific trip's exact start and endpoint together,
versus a single search string). With both flows now certain to exist, per-event severity becomes the
deciding factor, and routing moves to the top of this ranking. This is stated explicitly, not left as
the previous ordering would imply:

From worst to least sensitive, and why:

1. **Routing — now the single worst flow, and certain to exist (decision D11).** A request states
   the precise origin and destination together, in one place, at the full precision routing
   correctness requires — an unambiguous, complete statement of a specific trip in progress, repeated
   on every reroute (v0.2 onward). This is worse per-event than any individual geocoding query, and,
   unlike the previous revision of this document, it is no longer conditional on an undecided ADR: it
   is what v0.1 ships. It is additionally concentrated behind the same operator as flow (a) below
   (decision D10), which is a further, distinct cost recorded in `docs/adr/003-routing-engine.md` and
   `docs/roadmap.md` — not a privacy mitigant, and not counted twice in this ranking, but worth
   naming here since it is why this flow's exposure cannot be treated as isolated from flow (a)'s.
2. **Geocoding / place search — still the worst flow by frequency, now second by this ranking's
   per-event/certain-existence logic.** Every build ships autocomplete-as-you-type (decision D3).
   Each debounced keystroke burst is a query, at full text precision, that cannot be coarsened
   without breaking the feature. Repeated firing during a single destination search means this is a
   **sequence** of disclosures, not one — including abandoned, deleted candidate strings that never
   became the final search. Its v0.1 on-disk response cache (decision D9) adds a second, local
   dimension to this risk: unlike the network disclosure, which only the geocoding provider (and a
   relay, if not used) can see, the cache is a physical-access risk — a device examined while the
   cache still holds an entry discloses the same intent without any network capture at all.
3. **Traffic and congestion endpoint — Waze/Google (decision D10).** A single request is
   comparatively low-information (a viewport or corridor), but a *sequence* of requests during a trip
   approximates the route travelled. Decisions D13/D14 add detail about *what* is disclosed per area
   (which reliability-rated incidents and jam levels were present) without changing *that* a sequence
   of areas is disclosed — inference risk remains cumulative, not per-request. The five-minute
   minimum interval and area-coalescing (decision D12) reduce request *volume*, not the sensitivity
   of any individual request that does fire.
4. **OSM tile provider.** Same trajectory-revealing risk class as the traffic endpoint through
   request sequences, generally less tied to a specific active route than the traffic layer, and
   partially mitigated by caching (fewer repeat requests). Its main residual risk is the on-disk tile
   cache itself, which is a local-storage risk (physical-access adversary), not only a network one.
5. **Device GPS.** Zero direct network exposure — it never leaves the device as a raw fix. Ranked
   last only because it is on-device; it is the *source* that feeds the coarsened inputs to flows
   1–4, so its handling still matters (see below).

**The inference risk, stated plainly:** a sequence of viewport and routing requests made while a
trip is underway reveals that trip — where it went, roughly when, and for how long — even if no
single request is very informative. A geocoding search now carries the same kind of sequence risk,
on top of its already-high single-event severity: it reveals intent *before* the trip starts, and,
under decision D3, does so across a sequence of debounced queries rather than in one event. Both are
real risks that no architecture in this document eliminates; they are reduced, not removed, by the
mitigations below.

## Detailed flows

### (a) Traffic and congestion endpoint — Waze/Google

**Recipient, named per decision D10** (`docs/adr/005-traffic-source-integration.md`): this flow's
requests go to infrastructure operated by **Waze/Google**. "A third party" is no longer accurate
enough for an auditor now that the source is known; naming it here is required by the honesty rule
this document is built on, not branding — see `README.md`'s non-affiliation disclaimer, unchanged.

| | |
|---|---|
| Sent | Current map viewport bounding box, or the corridor around an active route; IP address; a generic app identifier (name + version) in the request's User-Agent. One request returns both community incident reports and congested-segment (jam) data for the area (decision D13) — this adds detail to an existing flow, not a new recipient or a new request |
| Precision | Coarsened to the visible map area or route-corridor buffer — never a raw GPS point |
| Frequency | On viewport change past a debounce threshold, while the traffic/incidents layer is shown; periodically along the corridor ahead during an active trip (v0.3 traffic-aware ETA); no more than once every five minutes for a given area, served from cache in between, with overlapping areas coalesced into one request (decision D12) |
| Trigger | User has the traffic layer visible, or is navigating |
| Local storage | On-disk response cache, size- and TTL-bounded (LRU eviction), **from v0.1** (decision D9 — this is decided, not a v0.4 candidate). Not encrypted at rest. Excluded from Android Auto Backup and Data Extraction Rules; user-clearable (`docs/specs/001-navigation-mvp.md` FR-27–FR-31) |
| Observer inference | A single request is low-information; a sequence over the duration of a trip approximates the route travelled — now also including which reliability-rated incidents (subtype, confirmation count, reporter-trust band, confidence, age — decision D14) and congestion levels (decision D13) were present in each area. This is additional detail about *what* is disclosed per area, not a new *recipient* or a new *kind* of disclosure. The cache itself, examined on the device, reveals recently-viewed traffic areas without needing to capture network traffic — a physical-access risk, not only a network one |
| Mitigation | Relay-eligible by construction; viewport coarsening; no session identifier; debounce on pan/zoom; a self-imposed five-minute minimum request interval per area and coalescing of overlapping-area requests (decision D12), since Waze publishes no rate limit for this endpoint; the bounded, user-clearable, backup-excluded response cache reduces repeat requests (decision D9) |
| Data source's own risk exposure | Waze's terms grant a personal, non-commercial, revocable, non-transferable, non-sub-licensable licence; its data is a protected database under EU law; the endpoints used are undocumented and may change or be withdrawn without notice. The maintainer has accepted this exposure as a known risk — see `docs/adr/005-traffic-source-integration.md` and `docs/roadmap.md` — it is not hidden and not an open question |

### (b) OSM tile provider

| | |
|---|---|
| Sent | Tile coordinates (zoom/x/y) for the visible map area; IP address; generic User-Agent |
| Precision | Inherently quantised to the tile grid; the zoom level in use is an indirect signal of how "zoomed in" the user is |
| Frequency | On every pan/zoom that reveals tiles not already cached |
| Trigger | The base map is on screen. Because a map view is not optional in a navigation app, this flow's first request typically fires as soon as the user opens the app to use it — that is still a direct consequence of a user action (opening the app *to navigate*), not the passive launch-time beacon the Principles section above rules out; the distinction is "the user did something" (open the app to use it) versus "nothing the user did" (a timer, an update check, a beacon independent of any screen being shown) |
| Local storage | On-disk tile cache, size- and TTL-bounded (LRU eviction), from v0.1 (decision D7). Persists until evicted or manually cleared. Not encrypted at rest in v0.1. This is a repeat-view/privacy mitigation, not offline map support — a cache miss still requires a network request; true offline map/route support remains a separate, undesigned v0.4+ roadmap item |
| Observer inference | Same trajectory-revealing risk as (a) via request sequences. Additionally: the cache itself, examined on the device, reveals historical viewed areas without needing to capture network traffic — this is a physical-access risk, not only a network one |
| Mitigation | Relay-eligible by construction; cache-first fetching so repeat views cost no request; no prefetch beyond the currently visible tile set plus a small, fixed adjacent margin — no wide speculative prefetch; cache size cap, eviction policy, and a manual "clear map cache" action |

### (c) Geocoding / place search — autocomplete-as-you-type (decision D3)

The maintainer chose autocomplete with debounce over an explicit-submit-only design, with full
knowledge of the cost stated plainly below — this is not softened.

| | |
|---|---|
| Sent | The destination search text as typed, sent repeatedly as a query per debounced keystroke burst — not just once per submitted search; optionally a viewport-bias bounding box to improve relevance; IP address; generic User-Agent |
| Precision | Full text precision. This cannot be coarsened without breaking the feature — a partial or generalised query does not find the place the user is looking for |
| Frequency | **Repeatedly per trip, not once.** Each debounced keystroke burst produces a geocoding query. This includes **partial strings the user typed and then deleted** — a string that never becomes a submitted search still reaches the geocoding provider if it survived past the minimum length and the debounce window. Mitigations, now spec requirements with tests, not open questions: minimum input length of **3 characters** before any request fires; **600 ms** debounce after the last keystroke; the in-flight request is cancelled when the input changes; no request fires for whitespace-only input. These figures are orchestrator-set defaults, revisable by the maintainer |
| Trigger | User types (past the 3-character minimum, debounced) or submits a destination search |
| Local storage | On-disk response cache, size- and TTL-bounded (LRU eviction), **from v0.1** (decision D9) — the query text and its candidate results are held only long enough, and to only the bound, needed to avoid re-issuing an identical request; not encrypted at rest; excluded from Android Auto Backup and Data Extraction Rules; user-clearable (`docs/specs/001-navigation-mvp.md` FR-27–FR-31). This is deliberately **not** the same thing as a "recent searches" user-facing convenience feature, which remains a plausible future addition (v0.4+) and is explicitly **not** decided — the v0.1 cache is never surfaced to the user as a history, holds no more than its bound allows, and evicts automatically; a future "recent searches" feature, if built, would still need its own privacy analysis and must be treated as highly sensitive, opt-in, and independently deletable regardless of this cache's existence |
| Observer inference | The single strongest per-event intent signal in the app, now disclosed **multiple times per search** rather than once: it reveals a specific place the user wants to go, whether or not they ever travel there, and it reveals intermediate, possibly-abandoned candidate strings along the way. The on-disk cache is, for the same reason, the single most sensitive local artefact in the app: examined on a physically-accessed device, it can reveal recent destination text and candidates without any network capture at all |
| Mitigation | Relay-eligible by construction; no query text or candidate ever reaches **logcat** (distinct from the on-disk cache, which is a bounded, evictable, backup-excluded store, not a log — decision D9); the 3-character/600 ms/cancel-in-flight/no-whitespace mitigations above; no side-channel (e.g. no separate analytics call carrying the same string); the cache is bound to the same size/TTL/LRU/clearable/backup-excluded standard as the tile cache, not a looser one, precisely because this is the most sensitive artefact of the three. These mitigations reduce the *number* of partial-intent strings sent, and cut short abandoned typing early — they do not reduce the sensitivity of whichever string is actually sent, nor prevent a genuinely typed-then-deleted 3+ character string from reaching the provider once the debounce window has elapsed |
| Consequence for ADR 004 | A public geocoding instance whose usage policy forbids autocomplete-style querying is effectively excluded by this product decision, which narrows the viable backends toward a self-hosted geocoder — see `docs/adr/proposals/004-geocoding-provider.md`. This document does not pick the backend; that remains the maintainer's decision |

### (d) Routing — Waze/Google (decision D11)

**Settled, `docs/adr/003-routing-engine.md`.** v0.1's `RouteProvider` is backed by Waze's own
routing endpoint — the same operator named for the traffic/congestion flow, (a) above (decision
D10). This was the highest-stakes open decision in this document; it is now resolved, and the costs
are recorded here plainly, not softened:

| | |
|---|---|
| Sent | Origin and destination coordinates together, at full precision, in one request; IP address; generic User-Agent |
| Recipient | Waze/Google — the same operator as flow (a). This is a deliberate, accepted concentration of two of the app's most sensitive capabilities behind one third party's terms, logging policy, and licence (D10's own terms: personal, non-commercial, revocable, non-transferable, non-sub-licensable), not a coincidence of implementation |
| Precision | Full precision. **Cannot be coarsened** without breaking the feature — routing correctness requires exact coordinates, unlike the traffic and tile flows |
| Frequency | On each route request, and on each reroute (off-route recalculation, v0.2) |
| Trigger | User requests a route, or the app detects the user left the planned route |
| What this same request also returns | The route geometry, a traffic-aware duration (`durationWithTraffic`), and a traffic-free duration (`durationWithoutTraffic`); the app computes the displayed traffic delay client-side as their difference — never received from Waze or estimated separately (decision D11) |
| Local storage | The computed route geometry is held for the duration of the active trip (needed for turn-by-turn display) and should not outlive the trip unless the user explicitly saves it (v0.4 saved trips, opt-in) |
| Mitigation | Relay-eligible by construction is the only mitigation available for IP exposure. **No coarsening mitigation exists here**, exactly as before this decision: routing correctness requires precise coordinates, so precision cannot be traded away without breaking the feature |
| What this decision does not provide | No offline routing exists in this milestone or the next — every route computation and reroute is a live remote call, with no fallback once connectivity is lost. `RouteProvider` stays abstract so an on-device engine remains addable later as an offline mode, but nothing in this design builds one now |
| Data source's own risk exposure | The same terms named for flow (a) — personal, non-commercial, revocable, non-transferable, non-sub-licensable licence; protected database under EU law; undocumented, may-change-without-notice endpoints — now apply to this flow too, as its own, separately accepted commitment (core routing functionality, not a supplementary overlay). The maintainer accepted this knowingly — see `docs/adr/003-routing-engine.md` and `docs/roadmap.md` — it is not hidden and not an open question |

**Settled (decision D11), previously the single open question in this section:** which shape is
adopted. It is Waze's own routing endpoint — the "third-party remote routing API" shape this section
previously described as one of three candidates. The other two candidates (on-device engine,
project-operated self-hosted service) were not chosen for v0.1; `docs/adr/003-routing-engine.md`
records the full trade-off analysis and the reasoning, including why an on-device engine remains
addable later without touching `domain` or `presentation`.

### (e) Device GPS — on-device only

| | |
|---|---|
| Sent over network | Nothing directly. Raw GPS fixes never leave the device |
| On-device handling | Read from the OS location API, foreground only. v0.1: a low-frequency fix to place a "you are here" marker. v0.2: continuous fixes during active guidance, via a foreground service (see permissions below) |
| Derived exposure | The current position, coarsened, becomes: the origin parameter of a routing request (flow d — at full precision if a remote shape is chosen, since routing cannot use a coarsened origin without degrading results); a factor in the viewport sent to the tile/traffic providers when the map is centred on the user; a bias parameter for geocoding autocomplete (flow c) |
| Precision requested from the OS | Should be the coarsest accuracy that satisfies the active feature (e.g. not requesting the finest available fix when a coarser one suffices) — this is an implementation choice for the developer spec, flagged here as a review item, not yet a fixed value |
| Local storage | Not persisted beyond the current session/trip. No location history is built in v0.1–v0.3. A "trip history" feature is out of scope until v0.4+ and, if ever proposed, is opt-in, explicit, and independently deletable |
| Mitigation | Foreground-only permission, never background; fixes held in memory, not written to disk; coarsened before being handed to any outbound flow that can tolerate coarsening |

## Mitigations — mandatory by construction

Each of the following must be true of the shipped networking layer and must be verifiable by
reading the code, not by trusting a claim:

1. **User-selectable relay applied to every outbound request by construction, with an explicit
   choice required and a fail-closed failure mode (decision D1).** The networking layer has a
   single egress point (or a small, enumerable set of them) through which all HTTP traffic to tile,
   traffic, geocoding, and — if a remote shape is chosen — routing providers is routed. There must
   be no code path that constructs a network client bypassing that egress point. A code reviewer
   should be able to grep for HTTP client construction and find exactly the sanctioned
   factory/factories, nowhere else. The domain's `RelayConfiguration` has no "unset means direct"
   state: unset is modelled distinctly from "direct, no relay" and blocks egress until the user
   chooses one or the other; a configured relay that is unreachable fails the request rather than
   silently falling back to direct. A test asserting this — i.e. one that fails if fail-open
   behaviour were shipped by accident — is a required part of the networking layer's test suite, not
   an optional nicety. Candidate relay targets (Tor, an HTTP/SOCKS proxy, a self-hosted instance) are
   evaluated in `docs/adr/proposals/007-relay-and-proxy.md`.
2. **Coordinate coarsening to the precision the feature needs.** Viewport and traffic-corridor
   requests round or quantise coordinates rather than sending a raw GPS fix. Exact numeric
   parameters (decimal precision, tile-grid snapping) are a developer-spec decision, not fixed here
   — see open questions.
3. **No persistent session identifier.** No client-generated ID, cookie, or token is attached to
   outbound requests across sessions.
4. **No device or client fingerprint.** No Android ID, no advertising ID, no hardware identifiers,
   no unique User-Agent beyond a generic app-name/version string shared by every install.
5. **No request the user did not trigger by using a feature.** No polling, no background refresh of
   any of flows (a)–(d), no request fired by app launch alone.
6. **No speculative prefetch.** Tile fetches are bounded to the visible set plus a small fixed
   margin; no prefetching of areas, routes, or search results the user has not asked to see.
7. **Caching to reduce repeat requests.** Three bounded, short-TTL, on-disk response caches — tile,
   traffic, and geocoding, all decided for v0.1 (decisions D7, D9), not candidates — so an
   unchanged view or an already-answered query does not refire. Each is size- and TTL-bounded with
   LRU eviction, user-clearable, and excluded from Android Auto Backup and Data Extraction Rules;
   none is encrypted at rest, stated plainly rather than implied protected. The geocoding cache is
   held to the same standard as the other two, not a looser one, because it is the most sensitive
   of the three artefacts.
8. **Polite rate limiting.** Requests respect the target provider's published usage policy;
   failures back off rather than retry in a tight loop.

## Permissions

| Permission | Introduced at | Why unavoidable | If the user denies it | Notes |
|---|---|---|---|---|
| `INTERNET` | v0.1 | Required by every documented network flow (tiles, traffic, geocoding, and routing — decision D11 makes routing certain, not conditional) | Not user-revocable at runtime on stock Android — it is granted at install and has no runtime prompt. A user can still block it via a device-level firewall or flight mode | The app must degrade to an offline/no-network state without crashing or busy-retrying when connectivity is unavailable |
| `ACCESS_FINE_LOCATION` | v0.1 | Needed to place the user's own position on the map/route, and to supply an accurate origin to the routing flow (decision D11: certain, not conditional) | Search and route preview remain usable without a live position marker; the app must not crash and must not silently substitute a lower-quality location proxy (e.g. IP-based geolocation) as a workaround | Requested at first use of the position feature, not at app launch |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | v0.2 | Turn-by-turn guidance needs continuous location while the screen may be off or the app is not the foreground activity; Android requires this service sub-type for that | Guidance/turn-by-turn is unavailable; the app can fall back to a static route the user must keep the app open and foregrounded to follow | The mandatory ongoing notification for a foreground service is the transparency mechanism that substitutes for requesting background location |
| `POST_NOTIFICATIONS` | v0.2 | Android 13+ requires this permission for the app to display the guidance foreground-service notification | **Open question — human decision required**: exact platform behaviour when denied (whether the OS still forces a foreground-service notification without this permission) depends on Android version and must be confirmed in the v0.2 spec | Requested only when guidance is first started |

**`ACCESS_BACKGROUND_LOCATION` is never requested, at any milestone.** Guidance always runs as a
user-visible foreground service with an ongoing notification; there is no planned feature that
needs location while the app is fully backgrounded or closed outside an active, visible guidance
session. If a future feature seemed to need it, that would itself be an escalation under
`CLAUDE.md` §9, not a default assumption.

## Local storage

| Data | Introduced at | Sensitive? | Retention | Deletion path |
|---|---|---|---|---|
| `RelayConfiguration` (relay/proxy choice, including the explicit "direct, no relay" choice), units, locale preferences | v0.1 — persisted via **Jetpack DataStore Preferences** (decision D2, recorded as `docs/adr/014-settings-persistence.md`, not in-memory only) | Low — reveals privacy posture (e.g. relay chosen), not location | Until changed, or until app data is cleared | Settings screen lets the user re-choose at any time (including switching back to "direct, no relay"); clearing app data or uninstalling deletes the DataStore Preferences file entirely, which reverts the app to the unset state — no relay choice made — so the fail-closed behaviour in decision D1 applies again and no outbound request is made until a new explicit choice is recorded |
| On-disk tile cache | **v0.1** (decision D7 — the v0.1 spec previously excluded this and was wrong; corrected to match this document, not the reverse) | Yes — indirectly reveals areas viewed, if examined on the device; **not encrypted at rest in v0.1**, stated honestly rather than implied protected (see `docs/threat-model.md` §"Someone with physical access to an unlocked device") | Size/TTL-bounded (LRU); concrete size cap and TTL are configured constants whose values remain an open question for the maintainer | Manual "clear map cache" action (`docs/specs/001-navigation-mvp.md` FR-30); excluded from Android Auto Backup and Data Extraction Rules (FR-31); app uninstall |
| On-disk geocoding response cache | **v0.1** (decision D9 — local caching for its own function is acceptable under the privacy doctrine; this is not a "recent searches" feature, see flow (c) above) | Yes — the most sensitive local artefact in the app: recent destination search text and its candidate results, legible to anyone with device access; **not encrypted at rest**, stated honestly | Size/TTL-bounded (LRU), to the same standard as the tile cache, not looser; concrete size cap and TTL remain an open question for the maintainer | Manual clear-cache action (`docs/specs/001-navigation-mvp.md` FR-30); excluded from Android Auto Backup and Data Extraction Rules (FR-31); app uninstall |
| On-disk traffic response cache | **v0.1** (decision D9) | Yes — indirectly reveals recently-viewed traffic areas/corridors, if examined on the device; **not encrypted at rest** | Size/TTL-bounded (LRU); concrete size cap and TTL remain an open question for the maintainer | Manual clear-cache action (`docs/specs/001-navigation-mvp.md` FR-30); excluded from Android Auto Backup and Data Extraction Rules (FR-31); app uninstall |
| Active-route geometry | v0.1 (in-memory), v0.2 (foreground service holds it during guidance) | Yes — describes a specific planned trip | Session-only; cleared when the trip ends or is cancelled | Ending/cancelling the trip; app close |
| Saved places / saved trips | v0.4+ (not yet designed) | Yes — explicitly sensitive; can reveal home, work, and habitual destinations | User-controlled, opt-in | Per-item and bulk deletion required by design once this feature exists |

**Backup and data-extraction posture:** `allowBackup` and Android's Auto Backup / Data Extraction
Rules are explicit choices, not left at their defaults (`CLAUDE.md` §5). Any locally stored field
above that is sensitive (the three response caches, active-route geometry, saved places/trips)
must be excluded from cloud device backups so that an OS-level backup channel does not become an
unaccounted-for outbound path for this data — this is the one place decision D9's doctrine does
**not** relax anything: a backup leaves the device, so it is governed exactly like an outbound
network call. For all three v0.1 response caches this is a **decided requirement, not open** (FR-31,
decisions D7/D9): their storage must be excluded from the start, via Android's no-backup files
directory. **Open question — human decision required**: the exact backup-rules XML / manifest
configuration covering the later, structured-database features (v0.4 saved-places, cached routes)
is not yet written; it must be authored and reviewed alongside those features, before they ship —
this open question does **not** apply to the three v0.1 response caches above, which are already
decided.

**Deletion:** uninstalling the app removes its private storage under normal Android behaviour. All
three v0.1 response caches already require an in-app clear-cache action (FR-30) — this is not
deferred to v0.4. Once saved places or further structured caching exist (v0.4+), an equivalent
"clear my data" control is required for that data too, in addition to uninstall.

## Logging policy

Nothing sensitive reaches logcat, in any build variant, including debug. Specifically never
logged: coordinates (raw or coarsened), addresses, geocoding search text, route
origin/destination, saved-place contents, IP addresses, or any user-entered string.

What a developer may log instead: HTTP response status codes and generic error categories (e.g.
"geocoding request failed: timeout"), request timing/duration, retry counts, cache hit/miss
outcomes, and qualitative feature-lifecycle events (e.g. "guidance started", "guidance ended")
that carry no coordinates or identifying content. A log statement that would only make sense with
a coordinate or a search string embedded in it should not exist; log the fact that something
happened, not the data it happened to.

## No telemetry

LibreWays is designed to ship with **zero telemetry**: no analytics SDK, no crash reporting, no
remote logging, no update-check ping, no launch-time beacon of any kind, in any milestone currently
planned. This is not "anonymised telemetry" or "aggregated statistics" — no such code path exists
in any spec, and none is planned. Default off means zero outbound traffic beyond what a specific,
user-triggered feature (map view, search, traffic layer, routing) sends, exactly as documented
above, once that feature is built.

Should telemetry ever be proposed for a future version, `CLAUDE.md` §5 requires it to be
explicit, informed, revocable opt-in, off by default, reviewed as a privacy change, and documented
here before merge. No such feature is planned in any milestone in the current product brief.

## How to verify these claims yourself

- **Network capture:** use a FOSS on-device capture tool (e.g. PCAPdroid) or a MITM proxy to
  observe every connection the app makes while exercising each feature. Confirm: only the endpoints
  documented above are contacted; nothing fires without a corresponding action you just took; when a
  relay is configured, the first-hop destination is the relay, not the underlying provider.
- **Firewall/logging:** a FOSS per-app firewall can confirm the app makes no connection attempts
  while idle or backgrounded outside an active foreground guidance session.
- **Logs:** run `adb logcat` while using every feature and search the output for coordinate-like
  patterns, address fragments, or the exact search strings you typed. None should appear.
- **Static inspection:** decompile the APK (e.g. with a FOSS Android reverse-engineering tool) and
  confirm the permission list matches the table above, that no Firebase/Analytics/Crashlytics
  classes are present, and that no hard-coded account-tied API key exists.
- **Storage inspection:** on a debuggable build or a device you control, inspect the app's private
  storage directory and confirm its contents match this document — no unexpected fields, and the
  three bounded response caches (tile, geocoding, traffic) present but never exceeding their
  documented size/TTL bound, never presented back to you as a "recent searches" or history UI, and
  removable via the clear-cache action described above. Expect to find recent destination query
  text and candidates in the geocoding cache until it evicts or is cleared — this is the decided
  v0.1 behaviour (decision D9), not a bug.
- **Build reproducibility:** once a reproducible-build pipeline exists (`CLAUDE.md` §0.2), compare
  the hash of an F-Droid-distributed build against a build you reproduce yourself from the tagged
  source, to confirm the binary you run matches the source this document describes.

## Maintenance rule

Any pull request that adds or changes an outbound network call, a requested permission, a persisted
field, or a log statement updates this document **in the same pull request**. A PR that changes any
of these without a corresponding update here is incomplete, not merely undocumented.

## Open questions

- **Settled (decision D11) — routing shape.** Routing is Waze-backed — the same operator as the
  traffic/congestion flow (decision D10) — recorded as `docs/adr/003-routing-engine.md`. This was
  the single highest-stakes privacy decision left in the product; it is resolved, with its costs (no
  offline routing, two sensitive capabilities behind one operator, origin+destination leaving
  together at full precision) accepted and recorded there, not hidden. See the re-stated Sensitivity
  ranking above, where this flow now ranks first.
- **Open question — human decision required:** concrete tile source and rendering choice —
  `docs/adr/proposals/002-map-rendering-and-tiles.md`.
- **Open question — human decision required:** concrete geocoding provider —
  `docs/adr/proposals/004-geocoding-provider.md`.
- **Settled (decision D10) — traffic/congestion source; partially settled (decision D12) —
  politeness.** The source is Waze — recorded as `docs/adr/005-traffic-source-integration.md` — and
  the minimum interval between two requests covering the same area is fixed at **five minutes**,
  with overlapping areas coalesced into one request. **Still open:** the traffic response cache's own
  size cap and time-to-live (OQ7 in `docs/specs/001-navigation-mvp.md`, decisions D7/D9) — a distinct
  parameter from the five-minute politeness floor, not settled by D12.
- **Open question — human decision required:** concrete relay/proxy implementation, and whether it
  is offered as an equally-weighted user choice among Tor/HTTP/SOCKS/self-hosted, or one is a
  suggested default — `docs/adr/proposals/007-relay-and-proxy.md`.
- **Settled (decision D1) — first-run and fail-closed behaviour.** First run performs **zero**
  network requests before the user has made an explicit relay choice; "direct, no relay" is itself
  that choice, never the silent result of leaving the setting untouched. A configured relay that
  becomes unreachable **fails the request and tells the user** rather than silently falling back to
  a direct connection. This is no longer open; see Principles and Mitigations (item 1) above.
- **Open question — human decision required:** exact coarsening parameters (decimal-degree rounding,
  tile-grid snapping) for viewport and traffic-corridor requests.
- **Settled (decision D3) — geocoding debounce and minimum length.** Minimum input length of **3
  characters** before any request fires; **600 ms** debounce after the last keystroke; the in-flight
  request is cancelled when the input changes; no request fires for whitespace-only input. These
  figures are orchestrator-set defaults, revisable by the maintainer, but they are no longer an open
  question — see the geocoding flow section above.
- **Open question — human decision required:** whether a "recent searches" or "trip history"
  **user-facing feature** is built at all (v0.4+); if so, it needs its own privacy analysis before
  design. This is distinct from, and not resolved by, the v0.1 short-TTL geocoding/traffic response
  caches (decision D9) — those are bounded, invisible-to-the-user, and evict automatically; a
  "recent searches" feature would be a new, separate, user-facing surface.
- **Open question — human decision required:** exact backup / Data Extraction Rules configuration
  excluding sensitive local storage from Android Auto Backup — this applies only to the later,
  v0.4 structured-database features (saved places, cached routes); it does **not** apply to the
  three v0.1 response caches (tile, geocoding, traffic), whose backup exclusion is already decided
  (FR-31, decisions D7/D9) and does not wait on this question.
- **Open question — human decision required:** platform behaviour of the guidance foreground-service
  notification when `POST_NOTIFICATIONS` is denied on Android 13+.
- **Open question — human decision required:** size cap and TTL for each of the three v0.1
  response caches — tile, geocoding, traffic (decisions D7, D9) — eviction policy itself is
  settled as LRU for all three, and that they exist at all is settled; only the concrete figures
  are open, per cache, tracked as OQ7 in `docs/specs/001-navigation-mvp.md`.
- The distant "community incident reporting" roadmap item is deliberately undesigned. It implies
  third-party account credentials, a write path that is inherently identifying, and terms-of-service
  exposure. It is out of scope for this document until it has its own recon and its own privacy
  analysis; it must not be assumed compatible with the principles above until that happens.
