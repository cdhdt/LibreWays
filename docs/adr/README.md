# Architecture Decision Records (ADR)

This index explains what an ADR is in LibreWays, how a decision gets from an open question to a
binding record, and lists the current state of every tracked decision. See also
[`CLAUDE.md`](../../CLAUDE.md) §0.2 (open decisions) and §7 (documentation map).

## What an ADR is here

An ADR records **one** architecturally significant decision that CLAUDE.md §0.2 lists as open: a
choice among real, named alternatives, evaluated against the locked constraints in CLAUDE.md §0.1
(Kotlin native, zero Google Play Services including transitive pulls, GPL-3.0-compatible,
F-Droid-compatible reproducible build, works fully de-Googled, low resource cost). An ADR is not a
design doc, not a how-to, and not a place to re-litigate a locked constraint — only the human can
reopen §0.1.

## Lifecycle

```
1. RECON writes a proposal brief         docs/adr/proposals/NNN-<title>.md
   (po-recon; options, drivers, a
   recommendation with its cost —
   never a decision)
                    │
                    ▼
2. HUMAN decides                          the human reads the brief and picks an option,
                                           amends it, or asks for more options
                    │
                    ▼
3. ADR is recorded                        docs/adr/NNN-<title>.md
   (same number, top level, using
   000-template.md; status: accepted)
                    │
                    ▼
4. Binding                                the decision is now authoritative per CLAUDE.md §0.2;
                                           an agent that contradicts it without a new ADR is in
                                           violation of CLAUDE.md §10.15
```

A proposal brief and its ADR share the same number. The brief stays in `proposals/` as the record
of the analysis that led to the decision; the ADR at the top level is the decision itself. Nothing
in a brainstorming transcript, a chat export, or a prior suggestion is ever a decision — only an
ADR recorded this way is (CLAUDE.md §0.2, §10.15).

## Numbering

Three-digit, sequential, never reused or renumbered. `000` is reserved for the template. `001`
onward are assigned in the order the human first needs the decision; the assignment below is
fixed regardless of the order decisions are actually made in.

## Statuses

| Status | Meaning |
|---|---|
| `proposed` | Brief exists in `proposals/`, no ADR yet. Not binding. Anyone may still add options or challenge the analysis before the human decides. |
| `accepted` | Recorded as `docs/adr/NNN-<title>.md`. Binding per CLAUDE.md §0.2. Code, review, and future recon must comply until superseded. |
| `superseded` | A later ADR replaces this one. The superseded ADR is kept (history is not deleted); it links forward to the ADR that replaces it, and the replacing ADR links back. |

## Index

| # | Title | Brief | Status |
|---|---|---|---|
| 001 | UI toolkit | [proposals/001-ui-toolkit.md](proposals/001-ui-toolkit.md) | `proposed` |
| 002 | Map rendering and tile source | [proposals/002-map-rendering-and-tiles.md](proposals/002-map-rendering-and-tiles.md) | `proposed` |
| 003 | Routing engine | [proposals/003-routing-engine.md](proposals/003-routing-engine.md) | `accepted` — see [003-routing-engine.md](003-routing-engine.md) |
| 004 | Geocoding provider | [proposals/004-geocoding-provider.md](proposals/004-geocoding-provider.md) | `proposed` |
| 005 | Traffic source integration | [proposals/005-traffic-source-integration.md](proposals/005-traffic-source-integration.md) | `accepted` — see [005-traffic-source-integration.md](005-traffic-source-integration.md) |
| 006 | HTTP client and serialisation | [proposals/006-http-and-serialisation.md](proposals/006-http-and-serialisation.md) | `proposed` |
| 007 | Relay and proxy implementation | [proposals/007-relay-and-proxy.md](proposals/007-relay-and-proxy.md) | `proposed` |
| 008 | Local persistence | [proposals/008-local-persistence.md](proposals/008-local-persistence.md) | `proposed` |
| 009 | Location and foreground service | [proposals/009-location-and-foreground-service.md](proposals/009-location-and-foreground-service.md) | `proposed` |
| 010 | Module layout | [proposals/010-module-layout.md](proposals/010-module-layout.md) | `proposed` |
| 011 | CI, reproducible build, F-Droid pipeline | [proposals/011-ci-reproducible-build-fdroid.md](proposals/011-ci-reproducible-build-fdroid.md) | `proposed` |
| 012 | Build and test tooling | none — decided directly, no proposal brief; see [012-build-and-test-tooling.md](012-build-and-test-tooling.md) | `accepted` |
| 013 | Background scheduling strategy | not yet written | *(no brief exists yet — not a status-vocabulary value; see note below)* |
| 014 | Settings persistence | none — decided directly, no proposal brief; see [014-settings-persistence.md](014-settings-persistence.md) | `accepted` |

This index lists every ADR proposal and decision recorded in this repository. Until an ADR is
recorded at `docs/adr/NNN-*.md`, every proposal remains a non-binding brief and an agent that needs
the decision before then must stop and ask, per CLAUDE.md §9. **012 and 014 are recorded as
accepted ADRs with no proposal brief** — the maintainer explicitly delegated the 012 decision to
the orchestrator (see `012-build-and-test-tooling.md`) and made the 014 decision directly (see
`014-settings-persistence.md`); ADR numbering therefore has two accepted decisions with no
corresponding `proposals/` entry (012, 014) while 001–011 remain proposals, and that gap is
expected, not an error. **003 and 005 follow the more typical path**: each has a `proposals/` brief
recording the analysis, and each is now additionally recorded as an accepted ADR at the top level
under the same number. The brief file itself is **not deleted** — it stays in the repo as the record
of the analysis that led to the decision, per the lifecycle above — but its own `Status` header now
reads `superseded by <NNN>-<title>.md`, pointing at the accepted ADR, precisely so nothing in the
brief's own present-tense analysis (written before the decision existed) reads as an invitation to
re-open a decision this index already lists as `accepted`. This index's own Status column for 003
and 005 is unaffected by that brief-level relabelling: it reports the decision's status, which is
`accepted`, not the brief's. **013 tracks a real gap, not a fourth status value**: CLAUDE.md §0.2 names
"background scheduling strategy" as an open §0.2 decision, but no proposal brief covers
deferrable/batched background work (proposal 009 covers only the foreground-service/location
strategy). Its Status cell deliberately does not use any of the three status words above, since
"no brief exists yet" is not a point in that vocabulary — it is tracked here so the gap is not
silently missing, gated to whichever milestone first needs deferred/batched background work
outside an active foreground session. `docs/roadmap.md` does not yet pin down that trigger
precisely (the closest candidates today are v0.1's own tile-cache maintenance, decision D7, and
v0.3's traffic refresh cadence, neither confirmed as requiring this specific decision). No brief
should be written for 013 until a task actually needs it, per CLAUDE.md §0.2.

## Writing a new ADR once the human decides

Copy [`000-template.md`](000-template.md) to `docs/adr/NNN-<title>.md` (same number and slug as the
brief it resolves), fill it in from the brief plus the human's actual decision, set status to
`accepted`, and update this index's Status column. If the decision changes later, do not edit the
accepted ADR's decision in place — write a new ADR that supersedes it and cross-link both.
