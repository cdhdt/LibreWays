# 010 — Module layout

## Status

`superseded by 010-module-layout.md` (accepted) — this brief's analysis led directly to that
decision; it is kept here as the record of the analysis, per `docs/adr/README.md`'s lifecycle, and
is no longer itself open. Do not treat anything below as inviting a re-open.

The maintainer chose Option C (multiple Gradle modules) immediately, not the brief's own
recommended "Option B now, Option C later" sequencing — see
[`../010-module-layout.md`](../010-module-layout.md) for the decision and its reasoning.

## Context

`docs/architecture/README.md` §2.4 already states the rule this decision must serve: *"dependencies
point inward, toward `domain`... `domain` depends on nothing else in the app"* — and §8 is explicit
that its illustrative package sketch is **provisional**, pending this ADR: *"The actual module
layout (single module with package-level separation vs. multiple Gradle modules with enforced
boundaries) is an open §0.2 decision."* `docs/testing.md` §2 states the test pyramid is only
enforceable *"because"* of this layering, and §Pending tooling decisions notes module layout
"affects where domain/data/presentation test source sets live." `docs/specs/001-navigation-mvp.md`
lists this ADR as a soft prerequisite: *"Not a hard blocker for writing domain tests in a temporary
single module, but rework is expected if skipped before the first commit."* This document is
therefore not strictly gating v0.1's first tests, but the human is explicitly warned that starting
without a decision risks rework.

This decision is purely about **build/package structure** — it does not touch what belongs in
`domain`/`data`/`presentation` (already fixed by `docs/architecture/README.md` §2), only **how that
separation is enforced and packaged.**

## Decision drivers

| Driver | Application here |
|---|---|
| Enforcement of the dependency-direction rule | The central question this ADR exists to answer: can `domain` be made **structurally unable** to import Android, or does the rule rely on review discipline (or a lighter-weight automated check) instead? |
| Privacy / works fully without Play Services | Indirect — a structure that makes it easy to accidentally leak an Android/GMS import into `domain` weakens the guarantee that `domain` (and therefore most of the test suite) has zero platform dependency, which is itself part of how the "works without Play Services" claim stays verifiable rather than asserted |
| Resource cost | Build time and CI resource cost scale differently with module count — more modules generally means better incremental-build caching but higher up-front Gradle configuration overhead |
| F-Droid-compatible / reproducible | More moving parts (module count, inter-module dependency graph) is more surface for a non-reproducible build detail to hide in, though this is a second-order concern relative to the dependency-check questions in `docs/adr/proposals/011-ci-reproducible-build-fdroid.md` |
| Maintenance and community health | Not library-dependent — this decision is about project structure, not a third-party dependency, so this driver mostly reduces to "how much unfamiliar Gradle machinery does a small team need to maintain" |
| Development speed | Single-module has near-zero setup cost; multi-module needs convention plugins or repeated boilerplate per module, verified as a common friction point in current Android tooling discussion |
| Testability | Whether `domain`'s test suite can be **proven** to run with no Android runtime (a genuine JVM-only test task) or merely happens to not use Android APIs today |
| Friction for a small team | `CLAUDE.md` describes a small, agent-heavy team; excessive module-boilerplate ceremony is a real cost against that team shape, not a hypothetical |

## Options

| Option | Structure | Domain import of `android.*` prevented by | Build time impact | Setup cost |
|---|---|---|---|---|
| A — Single module, package-level separation, review-enforced | One Gradle module (`:app`), `domain`/`data`/`presentation` as packages | Nothing structural — relies entirely on code review (already the stated interim mechanism per `docs/architecture/README.md` §2.4: *"until [a build-time check] exists, review is the enforcement mechanism"*) | Lowest — no multi-module Gradle configuration overhead | Lowest |
| B — Single module + Konsist (or equivalent) structural-lint tests | Same as A, plus a test suite that fails the build if any class under the `domain` package imports `android.*` or a `data`/`presentation` type | A **test**, not the compiler — violated only at test-run time, not impossible to write, but automatically caught before merge if CI runs the test suite (which it must, per `CLAUDE.md` §2 step 3.6) | Same as A plus the marginal cost of running the structural-lint test suite (typically fast — it inspects source, not runtime behaviour) | Low — one added test dependency and a small test file |
| C — Multiple Gradle modules (`:domain`, `:data`, `:presentation` or finer), with module-level dependency declarations enforcing direction | `domain` module has **no** Android Gradle Plugin applied and **no** dependency on `data`/`presentation` declared — an accidental `import android.*` in `domain` becomes an actual **compile error**, since the module has no Android SDK on its compile classpath at all | Higher — more Gradle configuration surface, though modern Gradle's configuration cache and convention plugins (verified as an active area of tooling improvement) mitigate a meaningful fraction of this | Higher — convention-plugin boilerplate (or repeated per-module `build.gradle.kts` setup) needed to avoid each module reinventing its own build logic |

### Option A — Single module, review-enforced

- **Pros**: zero additional setup; fastest to start (relevant given
  `docs/specs/001-navigation-mvp.md` already allows domain work to start in "a temporary single
  module" before this ADR lands); simplest mental model for a small team; fastest Gradle
  configuration time, since there is exactly one module to configure.
- **Cons**: the dependency-direction rule is enforced **only** by the reviewer reading every diff —
  `docs/architecture/README.md` §2.4 already names this as the *current* state and explicitly calls
  a build-time check "the long-term goal," implying single-module-forever is not the document's own
  preferred end state. A reviewer under time pressure, or a change spanning many files, can miss a
  single stray `import android.*` inside what is meant to be a pure-Kotlin package — the cost of
  that miss is not a compile error, it is a silent architecture violation that only review vigilance
  catches.
- **Testability**: `domain`'s tests can still be run as JVM-only tests today by simply not
  exercising any Android API — but nothing stops a future contributor from adding one, since the
  package boundary is not a build boundary. The claim "domain has zero Android imports" is true only
  as long as it keeps being true, not because the module cannot compile otherwise.
- **What it forecloses**: nothing permanently — moving to Option B is nearly free (add a test
  suite); moving to Option C later means carving the single module into several, which is real but
  bounded refactoring work (mostly moving files and writing `build.gradle.kts` declarations, not
  rewriting logic, given the packages are already separated).

### Option B — Single module + structural-lint enforcement (Konsist or equivalent)

- **Pros**: closes Option A's central gap cheaply — a Kotlin structural linter such as **Konsist**
  (verified: Apache License 2.0; "a powerful static code analyzer tailored for Kotlin... guards are
  written in the form of unit tests (JUnit/Kotest)") can express exactly the rule needed — e.g. "no
  class under `domain.**` may import `android.*` or a class from `data.**`/`presentation.**`" — as
  an ordinary test that runs in CI on every PR, per `CLAUDE.md` §2 step 3.6's mandatory
  verification-suite run. This converts the rule from "a reviewer must remember to check" to "CI
  fails if violated," without paying Option C's Gradle-restructuring cost.
- **Cons**: still not a **compile-time** guarantee — the violation is caught at test-run time, which
  is materially better than review-only but not as strong as "the code literally cannot compile"
  (Option C). A test can, in principle, be temporarily disabled or deleted by a rushed change,
  though `CLAUDE.md` §10.8 already forbids disabling a test to reach green, which mitigates this
  specific risk at the process level rather than the tooling level. Requires trusting a
  moderate-adoption third-party structural-linting library rather than the Kotlin/Gradle compiler
  itself.
- **Licence/GMS/maintenance (verified)**: Konsist is Apache 2.0, has no GMS involvement (it is a
  pure Kotlin static-analysis tool, not a runtime dependency at all — it only runs at test/build
  time and is never shipped in the APK), and research surfaced it as an actively-discussed, current
  tool in the Kotlin/Android ecosystem, though this brief's verification of its exact release
  cadence is lighter than the direct licence/GMS check.
- **What it forecloses**: nothing — this is additive to Option A and does not preclude moving to
  Option C later (the structural-lint test suite would simply become redundant with, not
  contradicted by, a later module-boundary migration).

### Option C — Multiple Gradle modules with real dependency-graph enforcement

- **Pros**: the strongest guarantee available — a `:domain` module with no Android Gradle Plugin
  applied and no compile-time dependency on Android SDK classes means `import android.*` is not a
  style violation, it is a **compilation failure**, full stop, for anyone, in any editor, before any
  test even runs. This is qualitatively stronger than Option B: it cannot be skipped, disabled, or
  missed in a rushed review, because the build itself refuses to produce an artifact if violated.
  Also gives the clearest mapping onto `docs/architecture/README.md` §8's illustrative sketch (which
  already uses `domain/`, `data/`, `presentation/` as top-level names, easily promoted to module
  names). Materially improves Gradle's ability to skip unaffected modules on incremental builds —
  `domain`-only changes do not force reconfiguring/recompiling `presentation`'s Android-specific
  build graph.
- **Cons**: highest setup cost of the three — needs either hand-maintained `build.gradle.kts` per
  module (duplicating boilerplate three-plus times) or a convention-plugin setup (a `build-logic`
  included build defining shared configuration), which is itself extra machinery a small team must
  understand and maintain. Configuration-time overhead grows with module count, though current
  Gradle configuration-cache tooling mitigates a meaningful share of that on repeated/incremental
  builds — not on the very first cold build. More Gradle files to keep F-Droid's reproducible-build
  pipeline (`docs/adr/proposals/011-ci-reproducible-build-fdroid.md`) working correctly across.
- **What it forecloses**: nothing structurally — this is the option `docs/architecture/README.md` §8
  gestures toward as its illustrative default shape, so choosing it aligns the actual build with
  what the architecture document already sketches; choosing A or B instead does not block adopting
  C later, but does mean re-doing that setup work at a later, likely less convenient time (mid-project
  rather than at the start).

## Concrete proposed layout per option

**Open question — human decision required, not settled by this document**: the package/
application-id used below (`<application-id-not-yet-decided>`) is a placeholder, not a decision.
The app's Android `applicationId`/base package name is a separate, permanent-once-published choice
(F-Droid and the Play ecosystem both treat it as effectively immutable after first release) and is
out of scope for this module-layout ADR. It must be decided explicitly by the human, recorded
wherever that decision is made, and substituted for the placeholder throughout this document and
the actual project scaffolding — never invented by an agent.

**Option A (single module):**

```
app/
  src/main/kotlin/<application-id-not-yet-decided>/
    domain/
      model/        Route, Place, Incident, Position, domain errors
      usecase/       ResolveDestination, RequestRoute, TrackOwnPosition, ...
      provider/      PlaceSearchProvider, RouteProvider, TrafficIncidentProvider,
                     TileProvider, OwnPositionSource, cache ports
    data/
      traffic/ tiles/ geocoding/ routing/ location/ persistence/ net/
    presentation/
      <feature>/
  src/test/kotlin/...        (domain + data unit tests, package-mirrored)
  src/androidTest/kotlin/... (instrumented tests, presentation + data integration)
```

**Option B**: identical layout to A, plus:

```
  src/test/kotlin/<application-id-not-yet-decided>/architecture/
    LayeringRulesTest.kt   // Konsist-based: domain must not import android.*, data.*, presentation.*
```

**Option C (multiple modules):**

```
build-logic/                          convention plugins shared across modules (optional but
                                       recommended to avoid repeated boilerplate)
domain/                               pure Kotlin/JVM module, NO Android Gradle Plugin applied
  src/main/kotlin/.../model/
  src/main/kotlin/.../usecase/
  src/main/kotlin/.../provider/
  src/test/kotlin/...                 plain JVM unit tests, no instrumentation possible even by
                                       accident — there is no Android SDK on this module's classpath
data/                                 Android library module, depends on :domain
  src/main/kotlin/.../traffic/ tiles/ geocoding/ routing/ location/ persistence/ net/
  src/test/kotlin/...                 unit tests against fakes/fixtures
  src/androidTest/kotlin/...          instrumentation tests where genuinely needed
presentation/ (or feature-per-module, finer-grained if the team later wants it)
  depends on :domain (not :data directly — wired at the composition root)
app/                                  thin application module: manifest, DI wiring/composition
                                       root, depends on :domain, :data, :presentation
```

## Recommendation

**Start with Option B (single module + Konsist-style structural-lint enforcement) for v0.1, with an
explicit intent to graduate to Option C (real multi-module) once the codebase and team have grown
enough that build-time incrementality starts to matter in practice** (a concrete, checkable trigger
— e.g. once full clean builds noticeably slow the team down, or once a second contributor joins and
review-only enforcement starts feeling thin even with the lint test as backup).

This sequencing is deliberate: `docs/specs/001-navigation-mvp.md` already permits domain work to
start in a temporary single module, and Option B gets the single most valuable property (an
automated, CI-enforced dependency-direction check, not just a reviewer's memory) at a small fraction
of Option C's setup cost, while genuinely not foreclosing Option C later — the package layout for
Option B is identical to what Option C's module layout mechanically becomes. Option A alone is not
recommended on its own merits: `docs/architecture/README.md` §2.4 already flags a build-time check
as the desired long-term state, and Option B is a cheap way to get most of that value immediately
rather than deferring it indefinitely.

**Confidence: medium-high.** The single-module-first sequencing is a defensible, common choice for a
small team at this project stage, and Konsist's licence/GMS/no-runtime-footprint facts are directly
verified. What lowers confidence slightly below "high": this brief could not verify Konsist's exact
current release cadence/community size as precisely as its licence, and the "graduate to Option C
later" trigger is stated qualitatively rather than as a hard, pre-agreed number — the human may
reasonably prefer to fix a concrete trigger (e.g. a specific module count, a specific clean-build
time) rather than leave it as a judgment call.

**Honest cost**: Option B's guarantee is weaker than Option C's — a determined or careless change
can still violate the rule between the moment code is written and the moment the lint test actually
runs, whereas Option C makes the violation simply not compile. The team is knowingly accepting a
CI-enforced-but-not-compiler-enforced guarantee for as long as it stays on Option B.

## Consequences of the recommendation

- **Becomes easy**: getting started immediately, since Option B requires almost no additional setup
  beyond what a single-module Android project needs anyway; every provider/use-case addition simply
  goes in its already-designated package.
- **Becomes hard / needs attention**: watching for the "time to graduate to Option C" signal is a
  judgment call, not an automated trigger — someone (likely a future recon pass) needs to actually
  notice and propose the migration rather than it happening by default.
- **What must be abstracted to keep it reversible**: nothing beyond what `docs/architecture/README.md`
  §2 already requires — packages must genuinely respect the dependency direction from day one
  (enforced by the Konsist test), so that promoting `domain`/`data`/`presentation` packages into
  Gradle modules later is a mechanical move (relocate files, add module `build.gradle.kts`,
  re-point module dependencies) rather than a rewrite driven by discovering the packages were never
  actually clean.

## What is needed from the human

1. **Confirm Option B now, Option C later**, or choose Option C immediately if the human weighs the
   compile-time guarantee and the anticipated near-term build-time payoff as worth the upfront
   convention-plugin setup cost from day one.
2. **If Option B is chosen**: no further input needed to start — Konsist adoption is a
   development-time decision the `dev-tdd` agent can execute directly once this ADR is accepted.
3. **If Option C is chosen immediately**: someone should decide whether to invest in a
   `build-logic` convention-plugin setup up front (more initial cost, less repeated boilerplate) or
   accept per-module duplicated `build.gradle.kts` configuration initially (less initial cost, more
   long-run duplication) — a smaller, secondary decision nested inside the larger one.
4. No hosting, keys, or external infrastructure needed for this decision.
5. **Open question — human decision required, separate from this ADR**: the application
   id/package name (placeholder `<application-id-not-yet-decided>` used throughout this document)
   must be decided before the layout above is scaffolded for real — it is not this document's
   decision to make and must not be invented.

## Reversibility

**High, in the recommended direction (A/B → C).** Because the package structure under Option A/B
already mirrors what Option C's module boundaries would be, migrating later is mechanical file
relocation plus Gradle module declarations, not a redesign — the `domain`/`data`/`presentation`
responsibility boundaries themselves (`docs/architecture/README.md` §2) do not change at all,
only how they are compiled and packaged. **Lower, in the reverse direction (C → A/B).** Collapsing
several Gradle modules back into one is unusual and rarely worth doing once a team has invested in
module-level build logic and CI caching keyed on module boundaries — not because it is
architecturally hard, but because it throws away build-tooling investment (convention plugins,
per-module CI caching) that only makes sense in a multi-module world. This asymmetry is itself an
argument for the recommended sequencing: starting cheap (A/B) and moving to C only when clearly
warranted costs less, in expectation, than starting with C's full setup and later discovering it
was premature for the team's actual size.
