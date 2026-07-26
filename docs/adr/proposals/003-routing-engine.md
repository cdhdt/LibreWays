# 003. Routing engine

## Status

`superseded by 003-routing-engine.md` (accepted, decision D11) — this brief's analysis led directly
to that decision; it is kept here as the record of the analysis, per `docs/adr/README.md`'s
lifecycle, and is no longer itself open. Do not treat anything below as inviting a re-open.

**This was the highest-stakes open decision in the product**: it determined whether the single
most sensitive fact this app ever handles — a user's origin and destination, together, in one
place, at one time — would ever leave the device. It has been decided: see
[`../003-routing-engine.md`](../003-routing-engine.md) for the decision and its accepted costs.

## Context

v0.1 ("A to B") must compute a route from the user's position to a destination, accounting for
traffic. Where that computation happens is architecturally distinct from where the traffic data
comes from (proposal 005) and from map rendering (proposal 002); those decisions do not decide this
one. v0.2 ("active guidance") adds off-route detection and automatic rerouting, which changes the
economics of this decision materially — see "v0.2 rerouting" below — so this brief must be decided
with v0.2 in view even though only v0.1 needs it to ship.

## Decision drivers

- **Privacy — dominant driver for this decision.** An on-device engine leaks nothing: the route
  computation never crosses the device boundary. Any remote engine (self-hosted or third-party)
  necessarily receives the origin **and** destination together in a single request — the exact
  combination that reveals a trip's full intent, sent to infrastructure outside the device.
- Works fully without Play Services.
- Resource cost: on-device shifts CPU/battery/storage cost onto the phone; remote shifts it onto a
  server and onto the radio (network round-trip per query).
- GPL-3.0-compatible licence for anything linked into the app (on-device engine only — a remote
  engine's own licence does not propagate into the app, since the app only speaks HTTP to it).
- F-Droid-compatible: reproducible build, no proprietary blob, no account-gated dependency.
- Maintenance and community health (verified per option below).
- Development speed.
- Testability (an on-device engine is testable offline and deterministically; a remote one requires
  network mocking or a live test server).
- Reversibility.
- Where traffic data enters route cost (explicit product-relevant driver).
- Device CPU/battery cost of route computation itself.
- Data size and update cadence, for on-device options only.
- Quality of results.
- What each option means for v0.2 rerouting specifically.

## Options

| Option | Summary | Verdict |
|---|---|---|
| On-device engine, map/routing data on device | Route computed locally against a bundled or downloaded regional data extract | Real candidate, highest engineering cost, best privacy |
| Self-hosted routing service (project-operated) | Project runs its own OSS routing engine as a server; app calls it like any other backend | Real candidate |
| Third-party routing API | A commercial or public SaaS routing endpoint | Real candidate, weakest privacy fit |

### On-device engine

Two concrete engines were evaluated as the underlying routing library; both would be embedded and
run inside the app process, not as a separate service:

- **GraphHopper (core library)** — pure Java, JVM-native, no JNI/NDK bridging required to run on
  Android. **Verified**: GraphHopper's own documentation states official Android support ended
  after v1.0 ("Offline routing is no longer officially supported but may still function on Android
  as it supports most of Java"), i.e. there is no currently-maintained, officially-supported
  on-device Android integration — this must be re-validated with a feasibility spike against the
  current GraphHopper version before being relied on. **Verified** licence: Apache-2.0.
- **Valhalla** — C++ engine, no official Android/mobile bindings found (only Python bindings are
  documented); embedding it on Android would mean the project builds and owns a bespoke JNI/NDK
  wrapper from scratch. **Verified** licence: MIT. **Verified** feature: "dynamic edge/vertex
  costing via plugins" — architecturally, Valhalla is designed to accept custom cost overlays
  (relevant to where traffic data would enter route cost, see below), but this project would need
  to build the entire Android embedding layer itself.
- OSRM was considered and is ruled weak for this option specifically: it is architected as a C++
  server process with no documented embeddable/mobile path; treating it as an on-device engine
  would mean a from-scratch NDK port with no upstream precedent — not a realistic v0.1 candidate.

Common to both real on-device sub-options:

- Pros: **zero network transmission of the route request** — origin and destination never leave
  the device, for either the initial route or any v0.2 reroute; works with no connectivity once
  data is loaded (relevant to tunnels, rural coverage, airplane mode); no dependency on a third
  party's uptime.
- Cons: requires a **new data pipeline** — generating routing graph extracts from OSM data for
  whatever region(s) are supported, hosting them somewhere for the app to download, and refreshing
  them on a cadence the project must define (weekly/monthly is typical for OSM-derived extracts;
  unverified exact cadence — this is itself a product decision, not just an engineering one).
  Extract size is region-dependent — plausibly tens to low hundreds of MB per country-sized region
  (unverified precise figures for either engine on current OSM data) — which is a real APK/storage
  and first-run download cost, and that download is itself a new outbound flow requiring its own
  `docs/privacy.md` entry (what is fetched, how identifying the request is, how often).
- Privacy impact: the route computation itself is fully private; the extract *download* is a bulk,
  infrequent, low-granularity flow (which region, not which trip) and is far easier to make
  privacy-honest than a per-trip query.
- Resource impact: CPU burst per route computation, scaling with graph size and query complexity;
  this is deferred, on-demand work, not a background/polling cost, so it does not conflict with
  CLAUDE.md §4's resource-discipline rules — but it is real, user-visible latency/battery cost at
  the moment of route computation, and must be benchmarked once implemented.
- Reversibility: **switching away from on-device later is expensive** in a way pure code
  abstraction cannot fully absorb — users will have downloaded region data, and removing that
  changes the app's own storage/permission story, not just its `data`-layer implementation.

### Self-hosted routing service (project-operated)

The project runs its own instance of an OSS routing engine (GraphHopper server, Valhalla server, or
OSRM server — all three are real, mature, self-hostable server options; Valhalla and GraphHopper's
licences are verified above, OSRM's licence was not queried this session and should be confirmed
before use) and the app calls it over HTTP, exactly like any other backend.

- Pros: the project fully controls logging policy and can commit to zero request logging; can be
  placed behind the same relay/proxy chokepoint as every other outbound call (proposal 007); avoids
  the on-device data pipeline entirely; lower engineering effort than building/validating an
  on-device Android embedding; any of the three mature OSS engines can be swapped behind the same
  HTTP interface without changing the app.
- Cons: **this is still a remote option under the dominant privacy driver** — origin and
  destination leave the device together on every request, to infrastructure the project operates.
  It is a real privacy improvement over a stranger's server (the project can promise no logs, run
  it behind Tor-friendly infrastructure, and be legally accountable to its own users) but it is not
  "nothing leaves the device," and it introduces a **standing infrastructure and operational
  commitment** for the project (hosting cost, uptime, OSM data refresh cadence for the routing
  graph) that on-device does not.
- Privacy impact: origin+destination transmitted per request to project-operated infrastructure;
  materially better than a third-party API (project can enforce a real no-logs policy) but strictly
  worse than on-device.
- Resource impact: minimal on-device (a network request and a JSON parse); all routing compute cost
  is borne by the project's server, an ongoing real cost.
- Licence: not applicable to the app itself — the app only makes HTTP calls to a server process it
  does not link into its binary; the server's own licence only matters to whoever builds/redis-
  tributes that server, which may be the same human but is a separate concern from the app's
  GPL-3.0 compliance.
- Google Play Services dependency: none — this is a plain HTTP call through the app's own client
  (proposal 006), same as every other server call in the app.
- Maintenance status: GraphHopper and Valhalla are both actively maintained per verification above;
  OSRM's current maintenance status was not checked this session.
- What it forecloses: nothing architecturally if the domain layer speaks to the `domain`-owned
  `RouteProvider` interface (`docs/architecture/README.md` §2.1 — the canonical name; earlier
  revisions of this brief called it `RoutingEngine`, a name this document no longer uses) — the
  server behind that interface could later be replaced by an on-device engine.

### Third-party routing API

A commercial or public SaaS routing endpoint the project does not operate — e.g. a keyed commercial
directions API, or a public community-run instance not operated by the project.

- Pros: fastest to integrate — no infrastructure to build or operate, no data pipeline; typically
  the highest-polish result quality out of the box, since commercial providers tune their own
  costing extensively and usually already have their own live-traffic signal baked in.
- Cons: **worst fit for the dominant privacy driver, and the worst option specifically under v0.2
  rerouting** (see below) — origin and destination leave the device together, to a third party the
  project has no operational or logging-policy control over, on every single query. Commercial
  routing APIs commonly require account registration and an API key, which sits in tension with the
  "no account-gated SDK" F-Droid target named in CLAUDE.md §0 (this is a judgement call for the
  human, not a settled disqualification — flagged, not decided). A third party's own baked-in
  traffic signal is very likely **not** the same traffic source the project chooses in proposal
  005, creating an architectural mismatch: the app would either ignore its own chosen traffic
  source for routing purposes (making proposal 005 partially redundant for route costing, still
  useful for on-map incident display) or attempt to layer its own traffic-aware cost adjustment on
  top of a black-box remote route, which is imprecise and doubles outbound exposure per route
  (one call to the routing API, one to the traffic source).
- Privacy impact: highest exposure of any option — worst case is a stranger's server, a stranger's
  logging policy, and (see below) it compounds badly under repeated rerouting.
- Resource impact: minimal on-device, same shape as the self-hosted option.
- Licence: not applicable to the app itself (HTTP client only), but the API's own terms of service
  govern what the app may do with responses (caching, redistribution) — a legal question, not
  engineering; noted, not litigated, per CLAUDE.md §5.2/§9.
- Google Play Services dependency: none inherent to "a routing API," though some commercial SDKs
  bundle a client library that could pull it — would need per-vendor verification if this path is
  chosen.
- Maintenance status: vendor-dependent, not evaluated generically here.
- What it forecloses: consistency between the routing engine's traffic model and the project's own
  traffic-source decision (proposal 005), as explained above.

## Where traffic data enters route cost

- **On-device**: traffic must be fed into the local graph's edge costing as a client-side overlay —
  Valhalla's documented "dynamic edge/vertex costing via plugins" is architecturally suited to this
  (verified feature, not a verified working integration); GraphHopper exposes a pluggable weighting
  abstraction in its routing core by design, but this was not independently verified via context7
  this session — **unverified**, confirm before relying on it.
- **Self-hosted**: the project's own server ingests the same third-party traffic feed (proposal
  005) and folds it into the engine's edge costing server-side — architecturally the cleanest fit,
  since the project already controls both the traffic ingestion and the routing server.
- **Third-party API**: route costing is a black box using the vendor's own traffic signal, not the
  project's chosen source (see above) — this is a structural mismatch specific to this option.

## Device CPU/battery cost of route computation

On-device: a real, bursty CPU cost per route request, proportional to graph size and route
distance/complexity; deferred/on-demand, not background polling, so it does not itself violate
CLAUDE.md §4's resource rules, but it must be benchmarked once built. Self-hosted/third-party:
effectively zero on-device compute cost beyond the network request and response parse; the cost is
entirely a network-radio and latency cost instead.

## Quality of results

Third-party commercial APIs are generally the most polished out of the box (heavily tuned costing,
their own live traffic already integrated). Self-hosted and on-device using the same underlying OSS
engines (Valhalla, GraphHopper) produce comparable route quality to each other, since it is the same
engine either way — the difference is only where it runs, not how good its results are; both are
well-regarded in the FOSS navigation space generally (unverified specific benchmarks this session).

## v0.2 rerouting

This is the sharpest distinguishing factor. v0.2 introduces automatic off-route detection and
rerouting — meaning the routing call is no longer a single one-shot event per trip but a **repeated
event, potentially many times per trip**, each carrying the user's current position and destination
together again. For a remote option (self-hosted or third-party), this **multiplies** the exposure
compared to a single lookup: every reroute is a fresh origin+destination disclosure, compounding
over the length of a drive. For third-party APIs specifically, this compounding happens on
infrastructure with no logging guarantee from the project — the worst combination in the whole
option set. For on-device, rerouting is a local recomputation with **no additional network
exposure and no additional latency risk from connectivity loss** (relevant in tunnels, rural areas)
— the v0.2 milestone is architecturally the strongest argument for on-device, independent of the
v0.1 privacy argument alone.

## Recommendation

On-device engine — specifically GraphHopper-core, contingent on a feasibility spike (build it into
a current Android target and confirm it still runs acceptably, since official Android support
lapsed years ago) — as the option that actually satisfies "privacy is the dominant driver" without
qualification: nothing leaves the device, for the initial route or any v0.2 reroute. Cost, stated
honestly: a new data pipeline (extract generation, hosting, update cadence, first-run/region
download UX and its own privacy documentation), a real APK/storage footprint, unverified current
build feasibility, and a real risk to the v0.1 timeline if the spike takes longer than expected.

If that cost is not acceptable for v0.1, the documented fallback consistent with the privacy driver
is a **project-operated self-hosted service** (not a third-party API, for either v0.1 or v0.2) —
reachable only through the mandatory relay/proxy chokepoint (proposal 007), with an explicit no-logs
policy documented in `docs/privacy.md`. A third-party routing API is not recommended for either
milestone: it is the weakest privacy fit, it compounds specifically at v0.2, and it structurally
conflicts with the project's own traffic-source decision.

Confidence: **medium**. The privacy reasoning is solid and the GraphHopper-Java-no-JNI advantage
over Valhalla is a real, verified engineering simplification, but there is no verified confirmation
in this session that GraphHopper's on-device path builds and performs acceptably on a current
Android target today — that must be spiked before this recommendation is accepted as feasible, not
just as privacy-optimal.

## Consequences

- Easy (either on-device or self-hosted, if the interface below is respected): swapping the
  underlying OSS engine later, since GraphHopper/Valhalla/OSRM all fit behind the same
  `RouteProvider` domain interface.
- Hard: reversing on-device once users have downloaded region extracts, without a deliberate
  migration story; validating a currently-unofficial embedding path (on-device); standing up and
  operating routing infrastructure indefinitely (self-hosted).
- Must be abstracted now to stay reversible: a `RouteProvider` interface in `domain` (pure Kotlin,
  no engine-specific types), implemented in `data` by whichever engine/transport is chosen — this
  is what makes "start self-hosted, move to on-device once the data pipeline is funded" a real,
  incremental path rather than a rewrite.

## What is needed from the human

1. Accept or reject the on-device recommendation given its stated cost and unverified feasibility —
   or authorise a time-boxed feasibility spike (embed current GraphHopper-core in a throwaway
   Android project, confirm it builds and computes a route) before committing.
2. If on-device is rejected for v0.1: confirm the self-hosted fallback and who operates that
   infrastructure (the human, or a to-be-decided community host) — this requires the human to
   provide or approve hosting.
3. Explicitly rule third-party routing APIs in or out — if the human wants one considered further
   despite the analysis above, name the specific vendor so its account/API-key terms and any GMS
   exposure can be verified.
4. If on-device is chosen at any point: the human must decide the extract update cadence and who
   hosts the extract files — this is a policy call, not something recon can set.

## Reversibility

Moving from self-hosted to on-device later (the natural "start simple, earn the privacy upgrade"
path) is a bounded `data`-layer change if the `RouteProvider` interface above exists from the start,
plus the one-time cost of building the extract pipeline whenever that move happens. Moving from
on-device to remote later is cheaper in code but has a real user-facing cost: users who already
downloaded region data would need that storage reclaimed or repurposed. Moving from third-party API
to either alternative later is the most disruptive: it likely means renegotiating or dropping a
vendor relationship, rebuilding the traffic-cost integration from scratch (since the vendor's
black-box costing is replaced by the project's own), and re-auditing the entire privacy story for
`docs/privacy.md` — this is the strongest argument for not choosing third-party even as a stopgap.
