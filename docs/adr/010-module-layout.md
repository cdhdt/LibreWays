# 010. Module layout

## Status

`accepted`

Date: `2026-07-25` — Decided by: the maintainer.

[`proposals/010-module-layout.md`](proposals/010-module-layout.md) is the analysis brief this ADR
resolves — read it first for the full options analysis (Option A: single module, review-enforced;
Option B: single module plus Konsist-style structural-lint enforcement; Option C: multiple Gradle
modules with real dependency-graph enforcement) and their trade-offs. This document does not
repeat that analysis; it records the decision and its consequences.

## Context

`docs/architecture/README.md` §2.4 already states the rule this decision must serve:
*"dependencies point inward, toward `domain`... `domain` depends on nothing else in the app"* — and
names a build-time check as "the long-term goal" while review remained the interim enforcement
mechanism. `docs/specs/001-navigation-mvp.md` lists this ADR as a soft prerequisite for Stage A
(domain value objects), warning that skipping it risks rework. This ADR resolves it before Stage A's
first test is written, so no rework is needed.

## Decision drivers

Unchanged from the proposal brief: enforcement of the dependency-direction rule (the central
question), privacy/works-without-Play-Services (indirect, via `domain`'s Android-free guarantee),
resource cost, F-Droid reproducibility, maintenance burden for a small team, development speed,
testability, and friction for a small, agent-heavy team. See the brief for the full discussion; this
ADR does not repeat it.

## Options

The same three the brief evaluated.

| Option | Domain import of `android.*` prevented by | Verdict here |
|---|---|---|
| A — Single module, review-enforced | Nothing structural — review only | Not chosen |
| B — Single module + Konsist-style structural-lint test | A test, at test-run time | Not chosen for v0.1 |
| **C — Multiple Gradle modules (`:domain`, `:app`)** | **The compiler — `:domain` has no Android Gradle Plugin applied and no Android SDK on its classpath** | **Chosen** |

## Decision

**Two Gradle modules: `:domain` as a pure Kotlin/JVM module (`kotlin("jvm")`, no Android Gradle
Plugin, no Android dependency of any kind), and `:app` as the Android application module, depending
on `:domain`.**

The point is structural, not conventional: `:domain` declares no Android dependency at all, so
`import android.*` there is a compile error — the module simply has no Android SDK on its compile
classpath — rather than a review-time finding a tired or rushed reviewer might miss. This is the
strongest available enforcement of `CLAUDE.md` §0.1's locked constraint that `domain` stay
Android-free, and it is why this is chosen over Option B's structural-lint test, which is real but
weaker: a test can, in principle, be deleted, skipped, or simply not run in a moment of haste (even
though `CLAUDE.md` §10.8 already forbids that at the process level), whereas a module with no
Android Gradle Plugin applied cannot be made to compile an `android.*` import no matter what anyone
does to the test suite.

The brief's own recommendation was to start with Option B and graduate to Option C once the team or
build-time cost justified the heavier setup. This ADR does not follow that sequencing: the
maintainer chose to pay Option C's setup cost immediately rather than defer it, because this is the
very first Gradle configuration this project will ever have — there is no existing single-module
project to migrate away from, no team habituated to Option B's structure, and no build-time cost yet
measured that a later "graduate when it hurts" trigger could be pinned to. Paying the heavier setup
cost once, now, at zero migration cost, is judged cheaper than deferring it and paying both the
original Option B setup cost and a later migration.

**Concrete layout**: `:domain` (packages `model/`, and `usecase/`/`provider`/error types as later
stages need them) and `:app` (manifest, and — once ADR 001 and the composition-root wiring exist —
the application's `data`/`presentation` code and DI wiring). This ADR does not itself split `data`
and `presentation` into further modules; that remains open for a future revision if the team outgrows
two modules, following the same "structural over conventional" reasoning stated here.

Confidence: high. Every row in the brief's options table was a well-understood, widely used choice;
this decision does not introduce a novel or contested combination, only a different point on an
already-mapped setup-cost/enforcement-strength trade-off.

## Consequences

- **Becomes easy**: no reviewer vigilance is required to catch an accidental Android import in
  `domain` — the build itself refuses to compile it. `domain`'s test suite is *guaranteed* to run as
  plain JVM tests (no emulator, no Robolectric, no instrumentation possible even by accident), not
  merely "tests that happen not to use Android APIs today."
- **Becomes hard / needs attention**: every new module (were `data` or `presentation` later split out
  too) carries its own `build.gradle.kts` boilerplate; this project's version catalog
  (`gradle/libs.versions.toml`) is exactly the mechanism `docs/architecture/README.md` and ADR 012
  already rely on to keep that boilerplate from drifting version-by-version across modules. A
  `build-logic` convention-plugin setup (the brief's Option C sub-question) is not adopted in this
  PR — with only two modules, the duplication is small enough not to justify it yet; revisit if a
  third module is added.
- **What must be abstracted to keep it reversible**: nothing beyond what
  `docs/architecture/README.md` §2 already requires. Splitting `:app` further into `:data`/
  `:presentation` modules later is a mechanical move (relocate files, add module `build.gradle.kts`,
  re-point dependencies), not a redesign, provided the package-level separation inside `:app` stays
  clean from the start.

## What was needed from the human

The maintainer's direct decision to adopt Option C immediately rather than follow the brief's
"Option B now, Option C later" sequencing, having been shown the cost (heavier upfront Gradle setup)
and the reason to accept it now rather than defer it (no existing project to migrate, no measured
build-time trigger yet, cheaper to pay once than to pay twice). No hosting, keys, or external policy
call was needed.

## Reversibility

High in the direction this ADR does not need to take (collapsing `:domain`/`:app` back into one
module is unusual and not anticipated). Splitting `:app` further into `:data`/`:presentation`
modules later is the anticipated direction of change and is cheap: the responsibility boundaries
`docs/architecture/README.md` §2 already defines do not change, only how they are compiled and
packaged, exactly as the proposal brief's own reversibility analysis for Option C already states.

## Addendum — `minSdk`, `compileSdk`/`targetSdk` (added during review of the build-skeleton PR)

Scaffolding the `:app` module required two Android SDK-level values this ADR had not itself
addressed. Recorded here, alongside the module-layout decision, rather than left as unreviewed
version-catalog entries.

- **`minSdk = 26` (Android 8.0 "Oreo") is a real product decision, not an incidental default.**
  API 26 was chosen as the floor because it covers the large majority of devices actually in active
  use while this project's stated audience (privacy-conscious users on GrapheneOS/de-Googled
  devices, CLAUDE.md §0) runs comparatively recent hardware — a floor lower than 26 would mean
  carrying compatibility shims for a shrinking, and for this audience specifically unlikely, tail of
  devices. This is not re-litigated per feature; a future feature needing a higher floor states so
  explicitly against this baseline.
- **`compileSdk`/`targetSdk` were first set to `37`, unverified, and that was a defect caught in
  review.** Checked directly against Google's SDK repository manifest
  (`https://dl.google.com/android/repository/repository2-3.xml`) at the time of this addendum: no
  plain `platforms;android-37` package exists — only fractional/preview packages
  (`android-37.0`, `android-37.1`, both stable-channel but fractional; `android-37.2-beta1`, a named
  preview with codename `CinnamonBun`). This is what was actually established, stated as such rather
  than as a claim about what AGP would do with it: no environment with a real Android SDK installed
  was available to confirm the resolution failure directly, only that the plain package `37` names
  is simply absent from the manifest that provisions it. The latest **released, stable,
  plain-integer** platform verified in that manifest is `platforms;android-36` (`channelRef`
  `channel-0`, i.e. stable; no codename). Both `compileSdk` and `targetSdk` are corrected to `36`,
  the verified value, rather than left on the unverified one.
