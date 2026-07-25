# 003. Routing engine

## Status

`accepted`

Date: `2026-07-25` — Decided by: the maintainer (decision D11).

## Context

[`proposals/003-routing-engine.md`](proposals/003-routing-engine.md) is the analysis brief this ADR
resolves — read it first for the full options analysis (on-device engine, project-operated
self-hosted service, third-party routing API), their trade-offs, and the two concrete on-device
engines evaluated (GraphHopper, Valhalla). This document does not repeat that analysis; it records
the decision and its consequences.

[`005-traffic-source-integration.md`](005-traffic-source-integration.md) (decision D10) already
named Waze as the live traffic and incident source for v0.1. The proposal brief's own "Third-party
routing API" section flagged, as a specific cost of that generic option, that a third party's
baked-in traffic signal is very likely not the same source chosen for the traffic flow, creating a
traffic-model mismatch and doubling outbound exposure per route (one call to routing, one to
traffic). The maintainer decided to resolve this by choosing the same source for both: v0.1's
`RouteProvider` is backed by Waze's own routing endpoint.

## Decision drivers

Unchanged from the proposal brief — privacy is the dominant driver for this decision; the brief's
full driver list (works without Play Services, resource cost, GPL-3.0-compatible licence,
F-Droid compatibility, maintenance, development speed, testability, reversibility, where traffic
enters route cost, device CPU/battery cost, v0.2 rerouting) still applies and is not repeated here.
What this ADR adds is a driver the brief could only state generically: **traffic-model consistency
between the routing engine and the traffic feed**, which becomes concrete, not hypothetical, once
both are named as the same operator.

## Options

The same three options the proposal brief evaluated (on-device engine, project-operated self-hosted
service, third-party routing API), with one addition: **Waze's own routing endpoint**, evaluated as
a named sub-option of "third-party routing API" specifically because decision D10 already accepted
this operator for the traffic flow.

| Option | Verdict here |
|---|---|
| On-device engine (GraphHopper/Valhalla) | Not chosen for v0.1 — the real, best-privacy candidate; `RouteProvider` stays abstract so this remains addable later as an offline mode |
| Project-operated self-hosted routing service | Not chosen for v0.1 |
| Generic third-party routing API | Not chosen — superseded by the named sub-option below |
| **Waze's own routing endpoint** | **Chosen** |

## Decision

**v0.1's `RouteProvider` is backed by Waze's own routing endpoint — the same provider already named
for traffic and incidents (decision D10).** One request returns the route, a traffic-aware duration
(`durationWithTraffic`), and a traffic-free duration (`durationWithoutTraffic`) together; the
traffic delay the app displays is computed client-side as their difference
(`durationWithTraffic − durationWithoutTraffic`), never received from the provider or separately
estimated by the app.

**What this removes, stated as the reasoning for the choice.** The proposal brief's own identified
cost of a generic third-party routing API — a baked-in traffic signal that very likely does not
match the project's own chosen traffic source, forcing either a redundant traffic call or an
imprecise client-side traffic overlay on a black-box route — does not apply here, because the
routing source **is** the traffic source. There is no traffic-model mismatch left to reconcile.
This does not claim the routing call and the traffic-area query merge into one wire request — that
remains a separate, unverified question — only that the two no longer disagree with each other
about what "traffic" means.

**What this does not remove — the costs accepted, stated as plainly as the brief states its
others, not softened:**

- **No offline routing.** Every route computation, and every v0.2 reroute, is a live remote call;
  there is no on-device fallback once connectivity is lost, and rerouting compounds this per-trip
  rather than reducing it, exactly as the brief's "v0.2 rerouting" section already describes for
  any remote option.
- **Two of the app's most sensitive capabilities are now concentrated behind one operator.**
  Decision D10 accepted Waze's terms-of-service and database-rights exposure specifically for the
  traffic/incident flow (viewport-level data). This decision extends the same operator to routing,
  so the single most sensitive pair of coordinates this app ever handles — origin and destination,
  together, in one request — also reaches Waze/Google, under the same personal, non-commercial,
  revocable, non-transferable, non-sub-licensable licence D10 already named. This is a larger
  commitment than the traffic flow alone, and it is accepted here explicitly, not as a side effect
  of the traffic decision.
- **Still the weakest privacy fit among this ADR's options**, exactly as the proposal brief already
  concludes for "third-party routing API" in general. Naming the specific sub-option removes one
  objection (the traffic-model mismatch) without touching the dominant privacy driver that made
  on-device the brief's own recommendation.
- **Terms of service, again, and separately from D10.** The exposure D10 accepted for the traffic
  flow is re-accepted here for the routing flow as its own, larger commitment (core functionality,
  not a supplementary overlay) — the same undocumented-endpoint, may-change-without-notice, and
  public-consumer-app-visibility risks D10 already named, now covering the app's core routing
  capability, not only its traffic overlay. This is a known, accepted risk with the maintainer as
  the decider (see [`../roadmap.md`](../roadmap.md)) — not hidden, not an open question.

Confidence: the reasoning that removes the traffic-model-mismatch cost is solid; the underlying
per-option costs above are unchanged from the brief's own assessment of third-party routing APIs
generically, and are accepted knowingly, not discovered later.

## Consequences

- **Easy**: `RouteProvider`'s port signature (`route(origin, destination) -> Route | error`) is
  unchanged by this decision — only `Route`'s own shape gains a field (`durationWithoutTraffic`, see
  [`../specs/001-navigation-mvp.md`](../specs/001-navigation-mvp.md)) — so nothing about this
  decision touches `domain` or `presentation` beyond that additive field.
- **Hard**: reversing this decision later carries the same shape of cost the proposal brief already
  describes for "third-party API" generically — no downloaded regional data to reclaim (unlike
  on-device), but a rebuilt routing adapter and a re-audit of `docs/privacy.md`'s routing entry,
  since a different operator's wire format would replace this one.
- **Must be abstracted now to stay reversible**: `RouteProvider` stays abstract in `domain`, with no
  Waze-specific type or field name reaching it. This is what keeps an on-device engine addable later
  as an offline mode, and a self-hosted or different third-party engine addable as a swap, without
  touching `domain` or `presentation` — the same invariant the proposal brief already required; this
  decision does not weaken it.

## What was needed from the human

The maintainer's direct decision (D11) to extend the traffic source (D10) to routing, having been
shown the specific cost this removes (traffic-model mismatch) and the costs it does not remove (no
offline routing, two capabilities behind one operator, a separate ToS/database-rights acceptance).
Cache/politeness figures and other open questions this touches remain tracked separately
(`docs/specs/001-navigation-mvp.md` OQ7) and are not settled by this ADR.

## Reversibility

Moving from this decision to on-device later is the same cost the proposal brief already describes
for that path generically (a new data pipeline, a feasibility spike, a real APK/storage footprint) —
this decision neither eases nor worsens that. Moving to a project-operated self-hosted service
instead is a bounded `data`-layer swap behind the unchanged `RouteProvider` interface, with the same
standing-infrastructure commitment the brief already describes for that option. What is specific to
reversing *this* decision, rather than "third-party API" generically: no downloaded regional extract
exists to reclaim from users, since nothing is held on-device under this option — reversal is a
`data`-layer and privacy-documentation change, not a data-migration one.
