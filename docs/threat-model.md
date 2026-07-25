# LibreWays — Threat model

A short, structured threat model complementing [`docs/privacy.md`](./privacy.md). That document
describes what the app sends and stores; this one asks who could see or infer what, and what we
deliberately do not defend against. Read both together — neither is complete alone.

## Assets

What an adversary would want, in rough order of sensitivity (matches the ranking in
`docs/privacy.md`):

**Re-ordered after decision D11** (Waze-backed routing accepted): a specific trip's origin and
destination moves to the top of this list, matching `docs/privacy.md`'s re-stated Sensitivity
ranking — this asset is now certain to be disclosed on every route request, not conditional on an
undecided ADR.

| Asset | Description |
|---|---|
| A specific trip's origin+destination | Now certain, not conditional (decision D11): every routing request states, together, exactly where a trip starts and ends, at full precision, to Waze/Google — see adversary 5 below |
| Destination intent | A destination search string, revealing where the user wants to go, independent of whether a trip happens |
| Travel patterns | Repeated trips over time that reveal habitual destinations (home, work, places of worship, medical facilities) |
| Location history / trajectory | A sequence of viewport, tile, or traffic requests that approximates a route travelled during one trip |
| Device network identity | The IP address (or lack of one, if relayed) attached to outbound requests |
| Saved places / saved trips (v0.4+) | Persisted, named locations the user chose to keep |
| Local cache contents (tile, geocoding, and traffic response caches; active route) | What the device itself holds, independent of any network capture — the geocoding cache is the most sensitive of the three response caches |

## Adversaries

### 1. Passive network observer / ISP

- **Capability:** sees all outbound traffic from the device at the network layer — destination
  IP/SNI, timing, and size of each connection. Cannot see payload content under TLS.
- **What it can learn:** which external services the device talks to and when; without a relay,
  connection timing and destination correlate directly with app usage and, transitively, with the
  third-party providers' own knowledge of the query. With a relay, it learns only that the device is
  talking to the relay (e.g. a Tor entry node or proxy), not to which provider or with what content.
- **Current exposure:** direct, whenever the user has explicitly chosen "direct, no relay" as their
  relay configuration (decision D1 in `docs/privacy.md`). This is a deliberate selection, not an
  unconfigured default: no outbound request is possible before that choice — or a relay choice — is
  made, so there is no "relay simply wasn't set up yet" exposure window. A user who chooses direct
  has made an explicit trade-off, not fallen into one.
- **Mitigation:** user-selectable relay (Tor/HTTP/SOCKS/self-hosted), TLS on every outbound
  connection.
- **Residual risk:** traffic-timing/size correlation against a relay (including Tor) by a
  sufficiently resourced observer is a known, unsolved limitation of relay networks in general, not
  something this app's design can close. An ISP still learns "this device uses a relay/VPN/Tor" even
  when the relay hides the destination.
- **Out of scope:** defeating global traffic-analysis adversaries; that is a relay-network research
  problem, not an app-architecture one.

### 2. The traffic and congestion provider — Waze/Google (decision D10)

- **Capability:** receives whatever flow (a) in `docs/privacy.md` sends: viewport or route-corridor
  area, IP unless relayed. One request returns both community incident reports and congested-segment
  (jam) data (decision D13), so this adversary's capability did not grow with a second recipient —
  only with more detail per area it already received.
- **What it can learn:** a sequence of requests during a trip approximates the route travelled;
  without a relay, the requester's IP ties that sequence to a network origin. Now additionally which
  reliability-rated incidents (subtype, confirmation count, reporter-trust band, confidence, age —
  decision D14) and congestion levels (decision D13) were present in each area — more detail about
  *what* is disclosed, not a new *kind* of disclosure.
- **Current exposure:** every use of the traffic/incidents layer, or every active trip, per
  `docs/privacy.md` flow (a); throttled to a self-imposed five-minute minimum interval per area, with
  overlapping areas coalesced (decision D12), since no rate limit for this endpoint was found
  published anywhere by the recon behind this decision — an absence of evidence, stated as such,
  not a claim that no limit exists.
- **Mitigation:** relay-eligibility by construction, coarsened viewport/corridor, no session
  identifier, caching and rate limiting to reduce sample count, the five-minute politeness floor and
  area-coalescing above.
- **Residual risk:** the trajectory-revealing nature of the flow is inherent to the feature (the app
  must ask "what's the traffic here" for wherever "here" currently is); coarsening and relay reduce
  precision and IP linkage but do not remove the fact that a sequence of queries describes a route.
  This same operator also receives flow (d) (routing, decision D11, adversary 5 below) — the two
  are not the same request, but they are the same recipient, which this document records as a
  distinct, accepted cost, not a mitigant.
- **Out of scope:** the provider's own retention and correlation practices once a request reaches
  it — we cannot audit their servers, and D10's accepted terms-of-service/database-rights exposure
  (`docs/adr/005-traffic-source-integration.md`, `docs/roadmap.md`) does not change that.

### 3. The tile provider

- **Capability:** receives tile coordinates for the visible map area, IP unless relayed.
- **What it can learn:** same trajectory-revealing risk class as the traffic provider, generally
  with less specificity (tiles describe what's on screen, not necessarily an active route).
- **Current exposure:** essentially continuous whenever the map is open — the base map is not
  optional in a navigation app.
- **Mitigation:** relay-eligibility by construction, cache-first fetching, no wide speculative
  prefetch.
- **Residual risk:** same as the traffic provider's residual risk, generally lower specificity but
  the earliest and highest-frequency exposure of the four network flows, since the map is the
  app's primary screen — its first tile request happens as soon as the user opens the app to
  navigate. This is still a consequence of a user action (opening the app to use it), not the
  passive launch-time beacon `docs/privacy.md`'s Principles section rules out.
- **Out of scope:** provider-side retention.

### 4. The geocoding provider

- **Capability:** receives the destination search text verbatim, per debounced keystroke burst —
  including partial strings later deleted, not just a final submitted search — optionally a
  viewport bias, IP unless relayed (decision D3, `docs/privacy.md`).
- **What it can learn:** the single strongest per-event intent signal in the app, now disclosed
  **repeatedly per trip** rather than once — a specific place the user wants to go, independent of
  whether a trip ever happens, plus intermediate candidate strings the user considered and
  abandoned. Cannot be reduced by coarsening, since the text must be sent as typed.
- **Current exposure:** every destination search, as a sequence of queries (one per debounced
  keystroke burst past the 3-character minimum) rather than a single request.
- **Mitigation:** relay-eligibility by construction, no local query logging, the minimum-length
  (3 characters), debounce (600 ms), in-flight cancellation, and no-whitespace-request mitigations
  specified in `docs/privacy.md` (reduces the number of partial-intent strings sent and cuts off
  abandoned typing early; does not reduce the sensitivity of whichever string is actually sent).
- **Residual risk:** the highest residual risk in the app after the network layer's mitigations are
  applied — see `docs/privacy.md`'s Sensitivity ranking, which now places this adversary first
  because its elevated exposure is certain (ships in every build) rather than conditional on any
  pending decision, unlike the routing provider below. The query content itself — not just the
  requester's identity — is inherently disclosed to this provider, now across a sequence of queries
  per search rather than one. A relay hides who asked; it does not hide what was asked.
- **Out of scope:** provider-side retention; any future *user-facing* "recent searches" feature
  (not designed) — distinct from the v0.1 on-disk geocoding response cache (decision D9), which is
  a network-adversary-independent, physical-access concern covered under adversary 7 below, not
  this one.

### 5. The routing provider — Waze/Google, now certain (decision D11)

- **Capability:** **settled** by `docs/adr/003-routing-engine.md` — v0.1's `RouteProvider` is
  Waze-backed, the same operator as adversary 2 above (decision D10). This adversary receives origin
  and destination together, at full precision, in one request, unless relayed for IP; the on-device
  and self-hosted alternatives `docs/adr/proposals/003-routing-engine.md` also evaluated were not
  chosen for v0.1.
- **What it can learn:** an unambiguous statement of a specific trip in progress — where it starts
  and ends, together, at full precision — repeated on every reroute (v0.2 onward). This is now the
  single most sensitive disclosure in this app, and it is **certain**, not conditional: it is what
  v0.1 ships, not a worst-case scenario among several live possibilities.
- **Current exposure:** every route request, and every reroute from v0.2 onward. No longer
  undecided; see [Open question](#open-question) below for how this document's framing changed.
- **Mitigation:** relay-eligibility by construction. **No coordinate coarsening is available for
  this flow** without breaking routing correctness — unlike adversaries 2–4, precision cannot be
  traded away here.
- **Residual risk:** this adversary now learns more, in a single event, than any other adversary in
  this document — worse than the geocoding provider (adversary 4), because origin and destination
  are confirmed together rather than a single destination text. It is also the same operator as
  adversary 2 (Waze/Google), which concentrates two of the app's most sensitive capabilities behind
  one party's terms and logging policy — a distinct, accepted cost recorded in
  `docs/adr/003-routing-engine.md` and `docs/roadmap.md`, not a mitigant of either risk. No offline
  fallback exists in this milestone or the next; `RouteProvider` stays abstract so an on-device
  engine remains addable later, but nothing in this design builds one now.
- **Out of scope:** the provider's own retention and correlation practices once a request reaches
  it, for the same reason as adversary 2 — we cannot audit their servers, and the accepted
  terms-of-service/database-rights exposure does not change that.

### 6. A co-resident app on the device

- **Capability, stock Android:** sandboxed; cannot read this app's private storage without root or
  an OS exploit. Can observe OS-level signals available to any app: the location-permission usage
  indicator, and coarse timing of when this app is in the foreground (via standard Android APIs
  available to any installed app, not anything LibreWays grants).
- **What it can learn:** that a navigation app is in use and roughly when; without root, not the
  content of any query, cache, or stored preference.
- **Current exposure:** limited to what the OS exposes to any app, which we do not control and do
  not increase.
- **Mitigation:** no data written to shared/external storage; no clipboard use beyond what a
  destination-search field ordinarily needs; least-privilege permissions overall so a co-resident
  app gains nothing extra by our design choices.
- **Residual risk:** low, given no root/exploit. A co-resident app with root, or exploiting an OS
  vulnerability, is out of scope (see non-goals).
- **Out of scope:** a rooted device or a co-resident app with an OS-level exploit.

### 7. Someone with physical access to an unlocked device

- **Capability:** full access to the running app, its visible UI state, and (on a debuggable build,
  or with developer tools) its private storage — the three on-disk response caches (tile,
  geocoding, traffic), active-route geometry, saved places once that feature exists.
- **What it can learn:** saved places (home/work/habitual destinations, v0.4+), the currently active
  route, a history of recently viewed map areas and traffic queries, and — the most sensitive of
  the three caches — recent destination search text and its candidate results, by inspecting the
  on-disk geocoding cache. All three caches are a legible approximation of browsing/search/
  navigation history, obtainable even without any network capture.
- **Current exposure:** all three on-disk response caches exist **from v0.1** (tile — decision D7;
  geocoding, traffic — decision D9, the maintainer's doctrine that local caching is an acceptable
  trade-off for reducing outbound requests, provided logs, backup exclusion, and honesty are not
  relaxed). Each is size/TTL-bounded and user-clearable, but **none is encrypted at rest** in this
  milestone, so a physical-access adversary who reaches the app's private storage (a debuggable
  build, or developer tools on the actual device) can read any of them in plain form until they
  evict or are cleared. This is stated honestly rather than implied protected. Active-route
  geometry remains session-only through v0.3 (see `docs/privacy.md` — local storage); exposure
  grows further at v0.4 with saved places.
- **Mitigation:** data minimisation (nothing persisted beyond what a feature needs), each cache's
  size/TTL bound and explicit clear action (`docs/specs/001-navigation-mvp.md` FR-30), exclusion
  from Android backup/Data Extraction Rules for all three (FR-31), explicit deletion controls
  generally, no user-facing location/search-history feature built by default.
- **Residual risk:** an unlocked device handed to, or seized by, someone else is a fundamental trust
  boundary this app cannot restore — we can only limit what there is to find. The device lock screen
  is the operating system's responsibility, not this app's.
- **Out of scope:** device-level authentication, screen-lock strength, forensic recovery of deleted
  data at the filesystem level.

### 8. A malicious fork redistributing a modified build

- **Capability:** GPL-3.0 permits redistribution of modified source. A fork could keep the
  LibreWays name/appearance (subject to non-affiliation and trademark constraints, see
  `CLAUDE.md` §5.2) while disabling the relay, adding telemetry, logging PII, or pointing requests at
  attacker-controlled infrastructure — while this very document, copied verbatim, would then be
  lying about what the fork does.
- **What it can learn:** everything, for any user who installs the modified build trusting it to
  behave like the genuine one.
- **Current exposure:** exists for any user who obtains the app from an unverified source (a
  sideloaded APK not obtained via F-Droid or a verified reproducible build).
- **Mitigation:** public source, GPL-3.0, and — once the reproducible-build/F-Droid pipeline exists
  (`CLAUDE.md` §0.2) — a build a user or F-Droid can reproduce and hash-compare against the
  distributed binary, so the claim "this binary matches this audited source" is independently
  checkable rather than asserted.
- **Residual risk:** we cannot prevent forks (the licence permits them) or stop one from
  misrepresenting itself. Users who install an APK from an untrusted source, rather than F-Droid or
  a verified reproducible build, are exposed to whatever that build actually does, regardless of
  what its bundled documentation claims.
- **Out of scope:** policing forks, or asserting anything about a binary we did not build and did not
  verify.

## Non-goals — stated honestly, not implied away

LibreWays' design does **not** defend against, and does not claim to defend against:

- A compromised, rooted, or maliciously modified operating system or ROM.
- A targeted state-level adversary with device exploits, legal compulsion of upstream providers, or
  the resources to run traffic-confirmation attacks against a relay network (including Tor).
- Physical compromise of a device that is unlocked at the time of access — the lock screen is the
  operating system's trust boundary, not this app's.
- A user who explicitly chooses "direct, no relay". The relay is a first-class, user-selectable
  mitigation (`CLAUDE.md` §5.1), not a mandatory always-on requirement — but it is also not a
  silent fallback: decision D1 (`docs/privacy.md`) requires an explicit choice before any request is
  made at all, and "direct" is one deliberately selectable outcome of that choice, never the
  unconfigured default. Operating unrelayed after making that choice is the user's explicit
  trade-off, not a defect in the app.
- The honesty, security, or retention practices of any third-party provider once a well-formed
  request reaches it. We control what we send; we do not control what happens to it afterward.
- Anonymity-network-grade guarantees merely from offering Tor as one relay option. Correct
  operational use of whichever relay the user selects is the user's responsibility, not something
  the app can enforce.
- The distant "community incident reporting" roadmap item. It is unscoped, undesigned, and
  explicitly excluded from this threat model; it will need its own threat analysis — including
  third-party credential handling and the identifying nature of any write path — before design
  begins.

## Open question

**Settled (decision D11), previously open here.** The routing shape
(`docs/adr/003-routing-engine.md`) determined whether adversary 5 (the routing provider) would be a
non-issue (on-device engine), a user-controlled trust relationship (self-hosted instance), or the
single worst-case disclosure in this entire threat model (third-party remote API, specifically
Waze's own routing endpoint). It is the last of these. Adversary 5 above and the re-ordered Assets
table at the top of this document reflect that resolution; this threat model is finalised for that
adversary as of this revision, with the costs accepted and recorded in
`docs/adr/003-routing-engine.md` and `docs/roadmap.md`.
