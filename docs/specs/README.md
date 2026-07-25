# Developer specs

A dev spec is the executable, verifiable plan for one feature, written **before any production
code for it exists**. It is produced by the recon/product-owner agent (`po-recon`) per
[`CLAUDE.md`](../../CLAUDE.md) §2, step 1, and it gates whether a developer agent is dispatched
at all: no feature, fix, or refactor reaches implementation without one.

## When it is written

Before a single line of production code. The recon agent judges feasibility, designs the
approach, and writes the test plan against the code and constraints that exist *today* — it
never assumes an undecided library, and it never invents product requirements beyond what the
human or [`../roadmap.md`](../roadmap.md) states. Anything missing from the brief becomes an
explicit open question in the spec, not a guess.

## What it must contain

| Section | Content |
|---|---|
| Feasibility verdict | `FEASIBLE` / `FEASIBLE_WITH_CHANGES` / `NOT_FEASIBLE` / `NEEDS_HUMAN_DECISION` |
| Approach | The design, in prose, plus the SOLID decomposition |
| In scope / out of scope | Exhaustive, unambiguous |
| Functional requirements | Numbered, testable statements, including error paths |
| Non-functional requirements | Privacy per flow, resource budget, accessibility, i18n |
| Layered decomposition | Domain (pure Kotlin) / data / presentation responsibilities and interfaces, in prose — never a concrete library choice |
| TDD test list | Ordered, named, one behaviour per test, red → green sequence |
| Definition of Done | A checklist, including docs and i18n obligations |
| Privacy & resource impact | Explicit; `none` only if verified |
| Risks | What could break around the change: callers, resources, manifest, build, existing tests |
| Open questions | Marked **"Open question — human decision required"**; empty only if genuinely none |

A spec that needs one of the open decisions in `CLAUDE.md` §0.2 (UI toolkit, map/tiles, geocoding,
HTTP stack, persistence, scheduling, module layout, CI) references the relevant
[`../adr/proposals/`](../adr/proposals/) file by name instead of picking a library, and states
plainly which parts of the work can start before that decision and which cannot. The routing
engine and the traffic/incident source are no longer among these open decisions — both are decided
(Waze, decisions D10/D11, `docs/adr/003-routing-engine.md` and
`docs/adr/005-traffic-source-integration.md`).

## Naming convention

`NNN-<slug>.md`, zero-padded, sequential across the whole project (not per milestone), lower
kebab-case slug. Numbers are never reused or renumbered after the fact.

## Existing specs

| Spec | Milestone | Status |
|---|---|---|
| [`001-navigation-mvp.md`](001-navigation-mvp.md) | v0.1 — "A to B" | Drafted; blocked on the ADR proposals it lists under Prerequisites |
