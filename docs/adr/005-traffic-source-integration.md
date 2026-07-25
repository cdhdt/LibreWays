# 005. Traffic source integration

## Status

`accepted`

Date: `2026-07-25` — Decided by: the maintainer (decisions D10, D12, D13, D14, D15).

## Context

[`proposals/005-traffic-source-integration.md`](proposals/005-traffic-source-integration.md) is the
analysis brief this ADR resolves — read it first for the full options analysis (direct on-demand
client fetch, client-side caching, project-operated caching/aggregation proxy) and their trade-offs.
That brief deliberately kept the source generic, since it was not yet named when it was written.
This ADR records the decision that names the source and settles the integration-architecture
questions the brief left to the human.

## Decision drivers

Unchanged from the proposal brief — privacy (request granularity, trigger), the `CLAUDE.md` §4
prohibition on polling loops, testability (fakeable without a live network dependency), and
reversibility, since the source was not yet named when the brief was written. This ADR adds no new
driver; it resolves the brief's open questions against the drivers already listed there.

## Options

The brief's three integration-architecture options stand; **direct on-demand client fetch with
client-side caching** — the brief's own recommendation — is confirmed as the choice, now against a
named source.

| Option | Verdict here |
|---|---|
| Direct on-demand client fetch, with client-side caching | **Chosen** (brief's recommendation, confirmed) |
| Project-operated caching/aggregation proxy | Not chosen — reintroduces a centralised server that sees every user's viewport, for a politeness gain the mitigations below already achieve without it |

## Decision

**D10 — the source.** The live traffic and incident source is **Waze**, consumed via its consumer
live-map/on-demand surface (the same request pattern the routing flow uses per
[`003-routing-engine.md`](003-routing-engine.md)), not the official Connected Citizens Program (CCP)
partner feed — LibreWays is not a named Waze partner, and pursuing that is a distinct, unpursued
path (see D16 below). The maintainer decided this explicitly, having weighed the alternatives and
accepted the risks stated below. **This is settled; it is not reopened here or by any future
recon.**

**Risks accepted, stated plainly.** Waze's terms grant a personal, non-commercial, revocable,
non-transferable, non-sub-licensable licence; the data is a protected database under EU law; the
endpoints used are undocumented and may change or be withdrawn without notice; a publicly
distributed consumer app is a more visible profile than a library consuming the same endpoints
privately. Recorded as a known, accepted risk with the maintainer as the decider, per
[`../roadmap.md`](../roadmap.md) — not hidden, and not an open question.

**D12 — politeness.** No automatic polling in v0.1: every request follows a user action (opening
the map, moving it, requesting a route). A minimum of **five minutes** must elapse between two
requests covering the same area; a request inside that window is served from the traffic/jam
response cache instead, regardless of that cache's own configured time-to-live
(`docs/specs/001-navigation-mvp.md` FR-27/OQ7, which remains a separate, still-open figure — this is
a distinct floor, not the same parameter under a different name). Overlapping areas are coalesced
into one request rather than issued separately. These are requirements with tests
(`docs/specs/001-navigation-mvp.md`), not guidance. The five-minute figure is the maintainer's own
deliberate value, not a limit Waze publishes — no rate limit for this endpoint was found documented
anywhere by the recon that informed this decision, so self-imposed politeness is the only lever
available.

**D13 — congestion type.** Traffic jams are modelled as `CongestedSegment`, a sibling of
`Incident`, not a variant of it: a congested road segment and a point alert differ in geometry and
in lifecycle. Congestion level is a bounded enum with an explicit `Unknown` fallback, never a raw
integer — Waze's own documentation disagrees with itself about how many congestion levels exist
(its prose states one range; its own worked JSON examples use a value outside that range), so an
unrecognised or out-of-range value maps to `Unknown` rather than to a number the domain would reason
about wrongly.

**D14 — reliability signals ship in full.** Confirmation count, reporter-trust band, confidence,
and age are all carried from the first version of this capability. They cost a few fields, and they
are what lets the app distinguish a stale, isolated report from a confirmed one — presenting both
identically would be a defect, not a simplification.

**D15 — region mismatch fails explicitly.** An area (viewport, route corridor, or a routing
origin/destination pair) spanning two of Waze's regional backends fails with a distinct, explicit
error rather than silently splitting, retrying, or returning partial results. Silent partial data in
a navigation app is worse than a visible failure the user can act on.

**Attribution.** The app states, in plain descriptive prose and without any third-party logo,
mascot, icon, or visual pastiche, that traffic conditions, incident reports, and route computation
are read from Waze (`docs/privacy.md`, `CLAUDE.md` §5.2). This applies uniformly to the traffic,
jam, and routing flows, since all three now share this one named recipient.

**Defensive parsing is mandatory, not merely prudent.** Waze's own documentation was found to
contradict itself on two separate points this design must absorb rather than propagate: the numeric
range of the jam congestion level (see D13), and the casing of the alert `subtype` field (lowercase
in one official document, `subType` in another). An adapter that hardcodes either as a closed,
trusted shape is exactly the fragility this decision is meant to prevent: an unrecognised or
differently-cased field maps to that field's own `Unknown`/typed-error fallback, never a silent drop
of the whole response or an unhandled exception.

**D16 — not pursued now.** An official Waze Connected Citizens Program (CCP) partnership is a
distinct, real path that exists — a named, formal partner relationship, not the "independent,
unaffiliated third-party client" this project is per `CLAUDE.md` §0 — and is neither assumed nor
designed for here. Turn-by-turn instructions stay out of v0.1 as already specified in
`docs/specs/001-navigation-mvp.md`; if a later milestone wants them from this source, the field
names need their own verification step, not a documentation read.

## Consequences

- **Easy**: keeping traffic/jam fetches behind the same relay chokepoint as every other outbound
  call; testing the widened `TrafficIncidentProvider` in isolation with hand-written fakes and
  fixture responses; adding jams to the existing traffic flow without a new recipient or a new
  cache, since one request already returns both.
- **Hard**: Waze publishes no rate limit for this endpoint, so the five-minute politeness figure is
  a self-imposed, unverified-against-any-published-limit value, not a documented ceiling — if this
  proves insufficient in practice, only the maintainer can revise it, and doing so does not change
  this ADR's architecture.
- **Must be abstracted now to stay reversible**: `TrafficIncidentProvider` in `domain`, widened to
  accept a route-corridor-or-viewport area and to return both incidents and congested segments, so a
  future source change is a `data`-layer swap, not a rearchitecture. The enum-with-`Unknown`-
  fallback design for `IncidentSubtype`/`CongestionLevel` is not Waze-specific — the same domain
  shapes would hold if the source were ever swapped.

## What was needed from the human

The maintainer's direct decision naming Waze (D10), the five-minute politeness figure (D12), and
confirmation that the reliability-signal fields ship in full from v1 (D14) rather than a smaller
starting set — all recorded above. Cache size and TTL figures remain open
(`docs/specs/001-navigation-mvp.md` OQ7) and are not settled by this ADR.

## Reversibility

Moving from this source to a different one later — a CCP partnership (D16, if ever pursued), an
open data feed (DATEX II or equivalent, already on the roadmap as a possible additional or fallback
source), or another commercial provider — is a `data`-layer swap behind the unchanged
`TrafficIncidentProvider` interface, provided that interface is respected. The domain-level
`IncidentSubtype`/`CongestionLevel` enums and their `Unknown` fallbacks do not name Waze and would
not need to change shape for a source swap — only their `data`-layer mapping tables would.
