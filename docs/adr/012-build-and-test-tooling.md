# 012. Build and test tooling

## Status

`accepted`

Date: `2026-07-25` — Decided by: the maintainer, **by explicit delegation to the orchestrator**.
The maintainer did not personally pick each row below; they explicitly authorised the orchestrator
to decide this specific, bounded set of tooling questions on their behalf so that TDD work could
start without waiting on every other open `CLAUDE.md` §0.2 decision. This ADR is otherwise binding
exactly like any other accepted ADR (`CLAUDE.md` §0.2) and **the maintainer may amend it at any
time**, the same as any other accepted ADR — delegation is not a lesser form of acceptance, and it
does not make this record provisional.

## Context

Nothing could be built or tested test-first without deciding, at minimum, a build system, a test
runner, an assertion style, how coroutines/`Flow` are tested, how Android-framework-dependent code
is tested, whether a mocking library is used, and what runs blocking in CI. `docs/testing.md`
already described the test pyramid and what a good test looks like in tooling-agnostic terms, but
stated plainly that "no build tool, test runner, or CI is set up yet" and that framework/runner
names were pending. This ADR resolves the build-and-test half of that gap so the first failing test
in this project can actually be written, run, and watched fail for the right reason. It does not
resolve module layout (`docs/adr/proposals/010-module-layout.md`), CI/reproducible-build pipeline
specifics (`docs/adr/proposals/011-ci-reproducible-build-fdroid.md`), or the UI toolkit
(`docs/adr/proposals/001-ui-toolkit.md`) — those remain open proposals; this ADR only fixes what is
needed to write and run a test at all.

## Decision

| Concern | Decision |
|---|---|
| Build system | Gradle with the Kotlin DSL, dependency versions centralised in a Gradle version catalog (`libs.versions.toml`) |
| Test runner | JUnit4 |
| Assertions | `kotlin.test` |
| Coroutines / `Flow` testing | `kotlinx-coroutines-test`, plus Turbine for `Flow` assertions |
| Android-dependent unit tests | Robolectric, only where a test genuinely cannot avoid the Android framework |
| Instrumented tests | androidx.test, reserved for critical paths |
| Mocking | None. No Mockito, no MockK. Hand-written fakes of the domain provider interfaces |
| Static analysis | ktlint and detekt, both blocking in CI |
| Versions | Latest stable at project setup, recorded in the version catalog. Specific version numbers are not fixed by this ADR — they live in `libs.versions.toml`, not in prose that would go stale |

Confidence: high for a delegated, bounded tooling decision — every row above is a well-understood,
widely used choice for Android/Kotlin projects, not a novel or contested combination.

## Rationale

- **JUnit4, not JUnit5**: JUnit5 on Android requires a third-party Gradle plugin, and both
  Robolectric and androidx instrumentation are natively JUnit4. JUnit4 unblocks the first test
  without depending on the undecided module-layout ADR (`010`) and without pulling in a
  non-official plugin. Revisit JUnit5 if and when a JVM-only `domain` module exists (which module
  layout, not this ADR, would decide).
- **No mocking library**: the project's own architecture rule — every external source reached
  through a `domain`-owned interface, per `docs/testing.md` §4 — makes every provider fakeable by
  hand. A mocking framework would add a dependency and a different testing style for no capability
  the hand-written-fake approach lacks, and hand-written fakes keep tests behavioural rather than
  call-sequence-coupled, which `docs/testing.md` §3 already requires independent of tooling.
- **Robolectric only where unavoidable, androidx.test for instrumented critical paths only**:
  matches the test pyramid already described in `docs/testing.md` §2 — the bulk of tests are pure
  JVM `domain` tests with no Android dependency at all; Robolectric and real instrumentation are
  reserved for the minority of cases that genuinely need an Android runtime or a real device/
  emulator, kept deliberately small since both are slower and more failure-prone than a plain JVM
  test.
- **ktlint and detekt blocking in CI**: operationalises `CLAUDE.md` §4's "lint/detekt/ktlint clean
  ... never disable a rule to pass" as an enforced gate rather than a review-time hope.
- **Gradle Kotlin DSL + version catalog**: centralises dependency versions in one file
  (`libs.versions.toml`) rather than scattered across build scripts, which is both the current
  Android-ecosystem convention and what keeps future dependency-vetting (licence, GMS-exclusion
  checks per `docs/adr/proposals/011-ci-reproducible-build-fdroid.md`) auditable from one place.

## Consequences

- **Becomes easy**: writing the first domain-layer failing test immediately, without waiting on
  module layout, UI toolkit, or CI pipeline ADRs to land first.
- **Becomes hard / needs attention**: revisiting JUnit5 later means re-checking whether Robolectric
  and androidx.test have since gained first-class JUnit5 support, since that was the reason JUnit4
  was chosen here, not a permanent preference.
- **What must be abstracted to keep it reversible**: nothing structural — this ADR fixes tooling,
  not architecture. Domain code depends on nothing test-framework-specific; only test source files
  reference JUnit4/`kotlin.test`/Turbine/Robolectric/androidx.test, so changing any one of them
  later touches test files, not production code.

## What was needed from the human

The maintainer's explicit delegation of this specific, bounded decision to the orchestrator, so
that TDD work could begin without every `CLAUDE.md` §0.2 open decision being resolved first. No
hosting, keys, or external policy call was needed.

## Reversibility

High. Every row in the Decision table is a test-scope or build-scope choice; none of it is
referenced from `domain`, `data`, or `presentation` production code. Swapping the test runner,
adding a mocking library later, or changing the coroutine-testing library is a test-source and
build-script change only. The one meaningfully costly reversal is dropping JUnit4 for JUnit5 after
a large test suite already exists in JUnit4 style, which is why the rationale above states the
concrete condition (a JVM-only `domain` module) under which that revisit is worth making.
