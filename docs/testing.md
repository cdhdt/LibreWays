# LibreWays — Test strategy

The test **tooling** (build system, test runner, assertions, coroutine/`Flow` testing, mocking
policy, static analysis) is now decided — see [`adr/012-build-and-test-tooling.md`](adr/012-build-and-test-tooling.md)
and [§8](#8-how-to-run-the-tests) below. What is still pending is everything that ADR does not
cover: module layout, the UI toolkit, and the CI/reproducible-build pipeline — see
[Pending tooling decisions](#pending-tooling-decisions). What was always fixed regardless of
tooling remains fixed: the TDD discipline, the shape of the pyramid, what a test must and must not
do, and the privacy/resource checks every relevant feature carries.

Related: [`../CLAUDE.md`](../CLAUDE.md) §4 and §5 (binding rules this document operationalises),
[`privacy.md`](privacy.md), [`architecture/`](architecture/), [`adr/012-build-and-test-tooling.md`](adr/012-build-and-test-tooling.md),
[`adr/proposals/`](adr/proposals/).

---

## 1. The TDD rule, operationally

`CLAUDE.md` §4: TDD is the default, not a preference. Concretely, for every behaviour change:

1. **Write the failing test first.** It names the behaviour, not the implementation
   (`returns coarsened coordinates when precision is reduced`, not `test1` or
   `calls roundTo3Decimals`).
2. **Observe it fail**, and fail for the expected reason (missing class, wrong assertion) —
   not a compile error unrelated to the behaviour, not a fail from a leftover bug in the test
   itself. A test nobody has watched fail is not trusted to catch a regression.
3. **Write the minimal production code to pass it.** No extra behaviour, no speculative
   generalisation ahead of the next test.
4. **Refactor** with the test green as a safety net: remove duplication, clarify names, extract
   collaborators. Tests are refactored too, to the same standard as production code.
5. Repeat for the next test in the recon's TDD test list (`docs/specs/<feature>.md`).

A PR whose tests were written after the implementation, or added only to satisfy a coverage
number, is a review finding, not a formality — see `CLAUDE.md` §10.9.

---

## 2. The test pyramid for this app

The architecture (`domain` pure Kotlin / `data` implements domain interfaces / `presentation`
holds no business logic — `CLAUDE.md` §4) is what makes this pyramid enforceable: the layering
itself decides where a behaviour can be tested without an Android runtime.

```
                    ▲  few
        instrumented / UI  — critical paths only, real or emulated device
                    │
        data-layer tests   — fake providers, recorded/fixture responses
                    │
        domain tests       — pure Kotlin, no Android imports, no runtime
                    ▼  many
```

### 2.1 Domain tests — the bulk

- Pure Kotlin, run on the JVM, zero Android framework imports, zero instrumentation.
- Cover routing decisions, coordinate coarsening, relay selection logic, incident/traffic
  merging, geocoding query shaping, error-type mapping — anything expressible as a function of
  inputs to outputs without a device.
- Fast enough to run on every commit locally; this is the layer TDD red-green-refactor happens
  in almost all of the time.

### 2.2 Data-layer tests

- Exercise `data` implementations of `domain` interfaces (repositories, HTTP clients, local
  storage) against **fakes and recorded/fixture responses**, never a live endpoint.
- Verify: request shaping (what is actually sent), response parsing, error mapping, retry/cache
  behaviour, and — for every outbound integration — that the relay path is actually exercised
  (§5).
- A recorded response is a static fixture checked into the test sources, built from synthetic
  data (see `CLAUDE.md` §5, "in the repository" rules): no real coordinates, no real address, no
  real trace.

### 2.3 UI / instrumented tests — critical paths only

- Reserved for what cannot be verified any other way: does the map actually render a route, does
  the permission-denied path actually show the fallback UI, does the foreground-service
  notification actually appear (from v0.2).
- Kept deliberately small in number — slow, flaky-prone, and expensive to maintain. Each one
  earns its place by covering a path where a domain or data test cannot reach the real failure
  mode (rendering, system permission dialogs, lifecycle transitions driven by the OS).

---

## 3. What a good test looks like here

A test that is accepted:

- **Behavioural** — asserts an observable outcome (returned value, emitted state, request sent),
  not an internal call sequence or private field.
- **Independent** — does not depend on execution order, shared mutable state, or another test's
  side effect. Can run alone and get the same result.
- **Not a tautology** — does not assert something the production code trivially guarantees by
  construction (e.g. asserting a constant equals itself, or mirroring the implementation's
  arithmetic instead of an independently-derived expected value).
- **Deterministic** — same result every run: no wall-clock reads, no unseeded randomness, no
  reliance on real-world timing.
- **No network access** — every external source is reached through a `domain` interface with a
  fake or fixture implementation in tests. See §4.
- **No real coordinates** — fixtures use obviously synthetic values (e.g. `0.0, 0.0` or clearly
  fake test coordinates), never a real address or a real GPS trace, per `CLAUDE.md` §5.

A test that is rejected in review:

- **Assertion-free** — runs code and asserts nothing, or only asserts "no exception was thrown"
  where an exception was never the risk being tested.
- **Implementation-coupled** — breaks when a private method is renamed or refactored without any
  change in observable behaviour (mock-heavy tests that assert call order/count on internals with
  no behavioural claim).
- **Ignored or disabled** — `@Ignore`/`@Disabled`/commented-out to reach a green build. Forbidden
  outright by `CLAUDE.md` §10.8; a failing test is fixed or the change is not merged.
- **Sleep-based** — `Thread.sleep`/fixed delays to "wait" for async work instead of an explicit
  synchronisation point (test dispatcher, fake clock, deterministic scheduler, awaiting a
  well-defined signal). Sleep-based tests are slow and still flaky.

---

## 4. Fakeability: provider interfaces and the no-real-network rule

Every external source the app touches — the Waze-backed traffic/congestion endpoint (decision D10),
the OSM tile provider, geocoding/search, device GPS, and the Waze-backed routing engine (decision
D11) — is reached exclusively through a `domain`-owned interface. `data` supplies the real
implementation; tests supply a fake or a fixture-backed stub. This is what makes the rule
enforceable rather than aspirational. Tile source and geocoding provider remain open ADRs; traffic
and routing are decided and are named here rather than left as a placeholder for an eventual
choice.

**No test may perform a real network call.** Not to the traffic endpoint, not to the tile
provider, not to a geocoding provider, not to any relay/proxy, not to a routing service, not to
`localhost` standing in for one of these. A test that needs "real" data uses a recorded fixture
checked into version control, built from synthetic input.

---

## 5. Privacy-specific tests

These are not optional extras; they are the tests that make the privacy guarantees in
`CLAUDE.md` §5 and §5.1 and in `docs/privacy.md` checkable rather than asserted in prose. Every
feature that touches an outbound flow (traffic, tiles, geocoding, routing — traffic and routing
decided as Waze, decisions D10/D11; tiles and geocoding remain open ADRs) must have tests
demonstrating:

- **Every outbound request goes through the relay path.** A request built for one of these flows
  is routed through the user-selected relay/proxy abstraction — never a code path that can reach
  the network directly, bypassing the relay selection. This is the single highest-value test in
  the suite per `CLAUDE.md` §5.1.
- **Fail-closed relay behaviour is a regression-guarded property, not an assumption (decision
  D1).** A dedicated test asserts that no outbound call proceeds while `RelayConfiguration` is
  `NotChosen` (surfacing `RelayNotChosen`), and that a configured-but-unreachable relay fails the
  call rather than silently falling back to a direct connection (surfacing `RelayUnreachable`,
  decision D6) — a test that must fail if fail-open behaviour were ever shipped by accident.
  `docs/privacy.md` names this a required part of the networking test suite, not an optional
  nicety, and it is distinct from the relay-path test above: that one checks relay is *used*, this
  one checks the app never proceeds *without* one being resolved one way or the other.
- **Coordinates are coarsened for the flows where that is possible** — the traffic and tile flows
  — to the precision the feature actually needs before they leave the `domain`/`data` boundary
  outbound, verified by asserting the precision of what a fake transport actually receives, not by
  inspecting an intermediate value that is later discarded. **Geocoding and routing are explicitly
  exempt from this test**, mirroring `docs/specs/001-navigation-mvp.md` FR-26: a geocoding query
  must be sent at full text precision to work at all, and routing (Waze-backed, decision D11) needs
  precise origin/destination coordinates to route correctly, since it is a remote call by
  construction, not a hypothetical one. A test asserting coarsening on either of those two flows
  would be asserting behaviour the product does not, and must not, have.
- **No identifier is attached** to an outbound request — no session token, no install UUID, no
  device fingerprint, no persistent correlation value across requests. Verified by asserting the
  fake transport receives no such field/header, across repeated calls.
- **Nothing sensitive is logged.** Log calls in code paths touching location, destination text,
  or request contents are exercised in tests with a fake logger, and the test asserts the logged
  content contains no coordinate, address, or search string — not merely that logging "was
  called".
- **The app functions with location denied.** Domain/data logic that depends on device position
  has an explicit denied/unavailable path tested to a defined fallback behaviour, not a crash or
  a silent no-op that leaves the user without feedback.
- **On-disk response caches are bounded, evictable, clearable, and backup-excluded (decisions D7,
  D9).** `docs/privacy.md`'s doctrine that the privacy bar governs what leaves the device does not
  mean local caches are untested — the opposite: because they persist on disk, they carry their
  own testable claims. For each of the app's response caches (tile, geocoding, traffic), a test
  suite demonstrates:
  - A cache hit for a non-expired entry is served without a network call to the corresponding
    provider — including while the fake connectivity check reports offline, since a hit must not
    depend on connectivity.
  - An entry whose TTL has elapsed is treated as a miss and re-fetched, not served stale.
  - Writes that would exceed the cache's configured maximum size evict least-recently-used entries
    first, verified against the configured bound itself, not a hardcoded figure.
  - An explicit clear-cache action removes all of a cache's entries; a subsequent request for a
    previously-cached item is a miss afterward.
  - The cache's configured base directory resolves to Android's no-backup files directory
    (`Context.getNoBackupFilesDir()`), verified against a real or Robolectric-provided `Context` —
    not merely that the adapter writes to whatever directory it was constructed with, which would
    be tautological and would not verify the backup-exclusion claim at all.
  The geocoding cache is held to this same standard, not a looser one, because it is the most
  sensitive local artefact in the app (decision D9) — see
  `docs/specs/001-navigation-mvp.md` tests 49–53 (tile), 76–80 (geocoding), and 81–85 (traffic).

---

## 6. Resource-related checks

Per `CLAUDE.md` §4's resource discipline:

- **No main-thread work** for anything that touches I/O, parsing, or computation beyond trivial —
  verified by structuring the code so blocking work is expressed in a `domain`/`data` function
  callable from a test on a background dispatcher, and by review of the dispatcher used in
  `presentation`. Where the chosen concurrency primitive supports it (pending the relevant ADR),
  a test asserts the call does not run on the main dispatcher.
- **Cancellation on lifecycle end.** Work started for a screen or a location subscription is
  provably cancelled when its owning scope ends — tested by starting the work against a fake
  scope/dispatcher, ending the scope, and asserting no further side effect (no further emission,
  no further network call recorded against the fake transport) occurs afterward.

---

## 7. What cannot be unit-tested, and how it is verified instead

Some things do not reduce to a deterministic function call:

| Cannot be unit-tested | Verified instead by |
|---|---|
| Real map rendering, tile compositing on screen | Instrumented/UI test on the critical path only (§2.3), manual verification during review |
| Actual OS permission dialogs and their exact wording | Instrumented test driving the real permission flow, or manual verification |
| Real foreground-service notification behaviour under Doze (v0.2+) | Manual verification on a device/emulator; documented in the PR's test evidence |
| Actual battery/memory cost in the field | Manual profiling (Android Studio profiler or equivalent) attached as PR evidence when a change plausibly affects it, per `CLAUDE.md` §4 |
| Reproducible-build/F-Droid pipeline correctness | CI job once the ADR in `adr/proposals/011-ci-reproducible-build-fdroid.md` is decided and implemented — not a unit test |

None of these substitute for the tests in §1–§6 where those apply; they cover only what is
structurally outside a deterministic test's reach.

---

## 8. How to run the tests

**The tooling is decided (`docs/adr/012-build-and-test-tooling.md`), but no build exists yet.**
Concretely, per that ADR: Gradle with the Kotlin DSL and a version catalog
(`libs.versions.toml`); JUnit4 as the test runner; `kotlin.test` for assertions;
`kotlinx-coroutines-test` plus Turbine for coroutine/`Flow` tests; Robolectric only where a test
genuinely cannot avoid the Android framework; androidx.test for instrumented critical paths; no
mocking library (hand-written fakes of the domain provider interfaces instead); ktlint and detekt,
both blocking in CI.

**This does not mean a command is runnable today.** The Gradle project itself has not been
scaffolded yet — no `build.gradle.kts`, no version catalog, no module has been created. Until that
scaffolding exists (which depends on `adr/proposals/010-module-layout.md` for where source sets
live, and `adr/proposals/001-ui-toolkit.md` for what an instrumented/UI test targets), there is no
real `./gradlew test` or equivalent command to run. Do not assume one works or paste a fabricated
command as evidence — verify against the actual project state before citing any command as
evidence in a PR (`CLAUDE.md` §8: evidence before assertions). CI's own execution of "all tests" is
additionally pending `adr/proposals/011-ci-reproducible-build-fdroid.md`.

---

## Pending tooling decisions

The build system and test-runner/assertion/mocking tooling are decided
(`docs/adr/012-build-and-test-tooling.md`, see §8). What remains open, tracked as ADR proposals,
not decided here:

- `docs/adr/proposals/001-ui-toolkit.md` — affects what an instrumented/UI test targets.
- `docs/adr/proposals/010-module-layout.md` — affects where domain/data/presentation test source
  sets live.
- `docs/adr/proposals/011-ci-reproducible-build-fdroid.md` — affects how "all tests" are run in
  CI and what reproducibility verification looks like.

An agent or contributor needing one of these before its ADR is approved stops and asks, per
`CLAUDE.md` §0.2 — it does not pick silently.
