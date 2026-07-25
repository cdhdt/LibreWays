# 005. Traffic source integration

## Status

`proposed`

## Context

v0.1 ("A to B") requires live traffic/incident data on the route; the product brief confirms this
as one of the four known outbound data flows ("Third-party traffic endpoint — incidents/traffic.
Outbound: viewport or route area, user IP"). The identity of the specific third-party traffic
source is not named or decided anywhere available to this brief — the brief deliberately keeps it
generic ("the third-party traffic data source"). This proposal is therefore not a "pick a named
library" decision like 001–004 and 006; it is a decision about the **integration architecture**:
how the app fetches from whatever source is eventually chosen, at what granularity, how it caches,
how it rate-limits itself politely, how it survives the source changing shape or disappearing, and
what abstraction keeps the source swappable without touching the rest of the app. It depends on
proposal 006 (the shared HTTP client) and interacts with proposal 007 (the mandatory relay), which
this brief assumes will apply to this flow like every other.

## Decision drivers

- Privacy: request granularity (what area, how coarse, how often) and whether requests are
  triggered only by user action versus a standing background schedule.
- Works fully without Play Services: not directly implicated by this decision, listed for
  completeness.
- Resource cost: **CLAUDE.md §4 explicitly forbids polling loops** — any fetch strategy that polls
  the third party on a fixed background timer regardless of whether the user is looking at the map
  is a direct violation, not a trade-off.
- GPL-3.0-compatible licence: not applicable — this is an integration pattern, not a library choice;
  whatever HTTP client is used is proposal 006's decision.
- F-Droid-compatible: not directly implicated.
- Maintenance and community health: not applicable in the library sense; relevant instead as
  "resilience to the source changing or disappearing," addressed below.
- Development speed.
- Testability: the abstraction boundary must let the traffic source be faked/mocked in tests
  without a live network dependency.
- Reversibility: the source is not yet named, so swappability is close to the central point of this
  decision, not a secondary property of it.

## Options

| Option | Summary | Verdict |
|---|---|---|
| Direct on-demand client fetch | App queries the third party directly, scoped to what the user is currently looking at | Real candidate |
| Client-side fetch with local caching only | Same as above, plus a client-side cache to avoid redundant requests on small pans/zooms | Real candidate — refinement of the first, not a separate architecture |
| Project-operated caching/aggregation proxy in front of the third party | Project runs a server that fetches from the third party once per area/TTL and serves many app instances from its own cache | Real candidate, different cost profile |

### Direct on-demand client fetch (with client-side caching)

- Pros: no new infrastructure for the project to operate; the request the third party sees is
  exactly what one user is looking at, at the time they are looking at it — no aggregation, no
  project-side visibility into other users' viewports either; simplest to reason about and to keep
  behind the relay/proxy chokepoint (proposal 007), since it is just another call through the same
  shared HTTP client as everything else.
  Adding a short-lived client-side cache (keyed by area, TTL matched to how fast traffic conditions
  actually go stale — likely low minutes, unverified exact figure since the source is unnamed)
  avoids redundant re-fetches from small pans/zooms without changing the architecture.
- Cons: the app's combined user base makes individual, uncoordinated requests to the third party;
  without a shared cache, popular areas are fetched redundantly across users unless the third party
  does its own edge caching; the project has no way to shield the third party from the app's
  aggregate request volume beyond what each device does independently — good rate-limiting must be
  implemented per-device (backoff, concurrency caps), not centrally.
- Privacy impact: best of the three options — no project-operated server ever sees any user's
  viewport; the only party that sees anything is the third party itself (and the relay, if the user
  has selected one).
- Resilience: the `domain`-owned `TrafficIncidentProvider` interface (`docs/architecture/README.md`
  §2.1 — the canonical name; earlier revisions of this brief called it `TrafficDataSource`, a name
  this document no longer uses; see below) isolates the app from the source's exact
  response shape; if the source changes format or goes down, only the `data`-layer implementation
  needs updating, and the app must degrade gracefully (no traffic overlay, not a crash).

### Project-operated caching/aggregation proxy in front of the third party

- Pros: reduces load on the third party (one fetch per area per TTL serves every app user, not one
  per user) — a genuine politeness improvement in aggregate; insulates the app from the source
  changing shape, since only the proxy needs updating, not every installed app instance; can
  normalize the third party's format into the app's own stable schema.
- Cons: **reintroduces a centralized project-operated server that sees every user's viewport
  query**, the same category of trade-off already flagged for the self-hosted options in proposals
  003 and 004 — a second (or third, if those are also self-hosted) standing infrastructure and
  operational commitment for the project, and a second flow needing its own `docs/privacy.md`
  entry (device → project proxy → third party). It also does not eliminate the politeness question,
  it relocates it: the proxy itself must still poll or refresh its cache from the third party
  somehow, and doing so for every area any user might look at risks the exact "aggressive polling of
  someone else's infrastructure" CLAUDE.md §5.1 prohibits, unless refresh is itself demand-driven
  (only refresh an area's cache entry when a real user request arrives for it, not speculatively).
- Privacy impact: worse than direct fetch — a project-operated server now sees every user's
  viewport, even though it does not see origin+destination pairs (proposal 003's concern) or search
  strings (proposal 004's concern); still a real, new piece of user-behavior visibility to the
  project that direct fetch does not create.
- Resilience: strongest of the three — a single point of adaptation if the source changes.

## Request shape and granularity

Whichever fetch strategy is chosen, the request should be scoped to the visible map viewport or the
active route's corridor (a bounding box or a buffered polyline around the route), at the zoom-
appropriate precision — never the user's raw fine-grained GPS coordinate as a standalone "center
point" beyond what the visible extent already requires. This mirrors CLAUDE.md §5.1's general
instruction to "coarsen coordinates to the precision the feature actually needs."

## Caching

Client-side (or proxy-side, if that option is chosen): cache responses keyed by area, with a TTL
matched to the real freshness window of traffic data — long enough to avoid re-fetching on trivial
pans/zooms, short enough that stale traffic is not shown as current. The exact TTL depends on the
eventual source's own update cadence, which is unverified since no source is named yet.

## Polite rate limiting

Per-device: respect whatever documented limits the eventual source publishes (unverified — no
source is named), implement exponential backoff and honor `Retry-After` if present, cap concurrent
in-flight requests, and never retry aggressively on failure. This is a hard requirement regardless
of which fetch strategy is chosen, per CLAUDE.md §5.1's "rate-limit and cache politely."

## Resilience to the source changing or becoming unavailable

A `TrafficIncidentProvider` interface in `domain` (pure Kotlin, no knowledge of the source's actual wire
format), implemented in `data`. If the source changes its response shape, only that implementation
changes. If the source becomes unavailable, the app must fail closed — no traffic overlay, not a
crash, not stale data silently presented as current — and must not retry in a tight loop (a polling
storm on an already-failing endpoint is exactly the resource-discipline violation CLAUDE.md §4
prohibits).

## Terms of service and legal exposure

The third-party traffic source's terms of service may restrict automated access, caching, or
redistribution of its data, and the specific source is not yet named in this brief. This is a legal
and business-policy question for the human once a source is chosen, not an engineering one, per
CLAUDE.md §5.2/§9.

## Recommendation

Direct on-demand client fetch, scoped to viewport/route-corridor, with short client-side caching,
through the same shared HTTP client as every other outbound call (proposal 006) and therefore
automatically behind the mandatory relay/proxy chokepoint (proposal 007) — not a project-operated
caching proxy, for v0.1. Reasoning: proposals 003 and 004 may already introduce one or two
project-operated servers (routing, geocoding) depending on how the human decides them; adding a
third standing service specifically for traffic caching multiplies the project's operational burden
and centralizes visibility into user viewports for a politeness gain that is real but secondary to
privacy. Cost: without a shared cache, the project cannot shield the third party from the app's
aggregate request volume beyond per-device rate limiting and caching — if the eventual source's
usage policy requires stricter aggregate control than that, a caching proxy becomes necessary and
this recommendation should be revisited. Confidence: medium — the architecture reasoning is sound,
but it is contingent on a source not yet named, so its actual rate limits and caching-friendliness
are unverified.

## Consequences

- Easy: keeping traffic fetches behind the same relay chokepoint as everything else; testing the
  `TrafficIncidentProvider` implementation in isolation with a fake.
- Hard: shielding the third party from aggregate request volume across the whole user base without
  a shared cache — if this becomes a real problem post-launch, it forces a revisit toward the
  caching-proxy option, which is itself a nontrivial infrastructure addition at that point.
- Must be abstracted now to stay reversible: `TrafficIncidentProvider` in `domain`, so introducing a
  caching proxy later (if needed) is a `data`-layer change, not a rearchitecture.

## What is needed from the human

1. Name the actual third-party traffic source (or confirm it remains undecided for this brief) so
   its real rate limits, response shape, and terms of service can be verified before implementation.
2. Confirm direct on-demand fetch (recommended) versus standing up a project-operated caching proxy
   from the start.
3. Read and accept the terms-of-service exposure of whichever source is eventually chosen — a
   policy call only the human can make.

## Reversibility

Moving from direct fetch to a project-operated caching proxy later is a bounded `data`-layer and
infrastructure addition if the `TrafficIncidentProvider` interface is respected from the start — existing
domain and presentation code does not change. Moving the other direction (dropping a proxy once
built) is cheaper in code but wastes the infrastructure investment already made. Changing the
underlying third-party source itself, independent of fetch strategy, is the reason this interface
exists at all and should always be a `data`-layer swap regardless of which fetch strategy is chosen.
