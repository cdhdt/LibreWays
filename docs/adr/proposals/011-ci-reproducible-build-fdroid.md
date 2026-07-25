# 011 — CI, reproducible build, and F-Droid pipeline

## Status

`proposed`

Nothing in this document is authority until the human accepts it and it is recorded as an accepted
ADR per `CLAUDE.md` §0.2.

## Context

**Re-quoted for this pass**: `docs/testing.md` §8 no longer says tooling is undecided — the build
system, test runner, assertions, coroutine/`Flow` testing, and mocking policy were settled by
`docs/adr/012-build-and-test-tooling.md` (an accepted ADR, decision D4). §8 now reads: "The tooling
is decided (`docs/adr/012-build-and-test-tooling.md`), but no build exists yet," and separately,
"the Gradle project itself has not been scaffolded yet ... there is no real `./gradlew test` or
equivalent command to run." What remains true, and what this ADR still exists to resolve, is
exactly that residual gap: no build has been scaffolded, and CI itself does not exist. §8 names
this ADR, alongside `adr/proposals/010-module-layout.md` and `adr/proposals/001-ui-toolkit.md`, as
a precondition for the test strategy document to state real, runnable commands and for CI to run
"all tests" at all. `docs/testing.md` §7 also names "reproducible
build/F-Droid pipeline correctness" as verifiable only "by CI job once the ADR in
`docs/adr/proposals/011-ci-reproducible-build-fdroid.md` is decided and implemented — not a unit
test," making this the one quality property in the whole test strategy that has no substitute
outside this decision. `docs/threat-model.md` §"A malicious fork redistributing a modified build"
names reproducibility as the concrete mitigation for that adversary: *"once the reproducible-build/
F-Droid pipeline exists... a build a user or F-Droid can reproduce and hash-compare against the
distributed binary."* This decision gates F-Droid distribution itself (`CLAUDE.md` §0: "Distribution
target: F-Droid-compatible"), so it is a release-readiness blocker for the project's stated
distribution goal, even though it does not block v0.1 feature development directly the way the UI
toolkit or a provider ADR does.

This document covers four related but distinct questions: (1) what runs on every pull request, (2)
what runs on release, (3) reproducible-build requirements, (4) signing-key handling, plus a
cross-cutting requirement — automated enforcement of the no-GMS rule.

## Decision drivers

| Driver | Application here |
|---|---|
| Privacy | CI is where the "no telemetry/no GMS/no PII in logs" promise stops being a code-review-time claim and starts being an automatically-checked one — this is the single cheapest point in the whole pipeline to catch a violation, per this document's own framing below |
| Works fully without Play Services | The entire reason a dedicated, automated GMS-dependency check is proposed rather than relying on review discipline alone |
| Resource cost | Not directly applicable to the shipped app, but CI minutes/compute cost is a real, if secondary, resource-discipline consideration for a small team |
| GPL-3.0-compatible licence | A licence-compliance check on every dependency is one of this document's concrete CI proposals |
| F-Droid-compatible (reproducible, no blob) | The central subject of this document — verified F-Droid requirements below, not assumed |
| Maintenance and community health | CI/CD tooling choices (GitHub Actions vs. an alternative) should themselves be actively maintained and widely used, reducing the chance of the pipeline itself becoming unmaintainable |
| Development speed | A PR-gate pipeline that is too slow or too strict becomes friction the team routes around; must be calibrated (fast checks on every PR, heavier checks — e.g. full dependency-vulnerability scan — possibly gated to release or a scheduled run) |
| Testability | Whether the reproducibility claim itself can be verified mechanically (a rebuild-and-hash-compare step) rather than asserted |
| Reversibility | Changing CI provider or reproducible-build tooling later should not require re-architecting the app itself — this is a build-tooling decision, confined to build configuration and CI workflow files |

## Verified F-Droid requirements (do not assume — checked directly)

Research against F-Droid's own documentation corrects a common assumption worth stating plainly,
since getting this wrong would misdirect engineering effort:

- **Reproducible builds are not a strict F-Droid inclusion requirement.** Verified: F-Droid's own
  documentation states reproducible builds are "not a requirement for apps being on F-Droid, though
  they are considered best practice," and F-Droid "mainly encourages their use for new apps" —
  specifically because Android does not allow updating an app with a different signing key, so a
  project that starts non-reproducible and later becomes reproducible would force existing users to
  reinstall rather than update. **This is a strong argument for getting reproducibility right from
  the very first release**, since LibreWays is exactly the "new app" case this guidance targets, and
  retrofitting it later carries a real user-facing cost the app would otherwise avoid.
- **The hard inclusion requirement is that the app, and everything it bundles, is Free/Libre/Open
  Source Software**, verifiable by F-Droid both by visual source inspection and by F-Droid's own
  build server building the app from the published source. This is what `CLAUDE.md` §0.1's
  "no proprietary or closed-source dependency" constraint must satisfy — F-Droid's own bar and this
  project's own locked constraint point the same direction here.
- **Signing**: verified — F-Droid historically signed every published APK with its own per-app key,
  built entirely on F-Droid's own infrastructure. Since F-Droid's 2023 shift (verified via F-Droid's
  own blog post, "Reproducible builds, signing keys, and binary repos"), an app whose build is
  reproducible can instead be distributed **signed with the developer's own key**, because F-Droid
  can independently verify that its own from-source build produces a byte-identical (or
  cryptographically equivalent) artifact to what the developer publishes elsewhere — meaning
  reproducibility is precisely what lets F-Droid trust a developer-signed binary instead of
  re-signing with its own key. This directly connects to the [Signing-key handling](#signing-key-handling)
  section below.
- **Two distinct F-Droid mechanisms, not one — re-checked via web research this pass, since an
  earlier draft of this document conflated them:**
  - **Inclusion Policy (the stricter, exclusionary rule)**: verified against F-Droid's own
    Inclusion Policy page — "the implementation of proprietary tracking or advertising libraries
    and analytics tools such as Google Play Services and Firebase and Crashlytics and proprietary
    ad/tracking SDKs are strictly forbidden in all applications." An app bundling these is not
    merely tagged, it is excluded from the main F-Droid repository outright. This is verified,
    quoted, and matches the strong claim.
  - **Anti-Features tagging system (a separate, more lenient mechanism)**: this is where the
    `NonFreeDep` anti-feature lives — verified as applying to apps that "require things that are
    not Free Software in order to run" **without bundling them**, e.g. an app that only works if
    Google Maps happens to already be installed on the device. `NonFreeDep` is a warning label on
    an otherwise-includable app, not an exclusion — the opposite end of F-Droid's spectrum from the
    Inclusion Policy rule above. Neither Google Play Services nor Firebase is mentioned in the
    Anti-Features document itself; they are handled by the stricter Inclusion Policy rule instead.
  - **What this means for LibreWays**: bundling GMS/Firebase-style SDKs would fail F-Droid's
    Inclusion Policy outright, independent of any anti-feature tag — the earlier "strictly
    forbidden, not merely flaggable" framing was directionally correct for *that* specific case but
    wrongly presented it as part of the Anti-Features system. LibreWays' own position — **zero
    Google Play Services at all, including transitive pulls** — is the project's own choice per
    `CLAUDE.md` §0.1, stricter than either F-Droid mechanism requires on its own; it is not derived
    from or dictated by F-Droid's rules, it merely happens to satisfy F-Droid's Inclusion Policy as
    a consequence.

## What runs on every pull request

| Check | Purpose | Failure severity |
|---|---|---|
| Build (compile all modules/variants) | Baseline correctness | Blocks merge |
| Unit tests (`domain`, `data` per `docs/testing.md` §2.1–2.2) | TDD discipline enforcement, per `CLAUDE.md` §4 | Blocks merge |
| Lint / static analysis (ktlint/detekt, per `CLAUDE.md` §4: "never disable a rule to pass") | Code-quality gate; also where the module-layout ADR's structural-lint test (`docs/adr/proposals/010-module-layout.md` Option B, if chosen) would run | Blocks merge |
| **Automated GMS/Firebase dependency check** (see below) | The cheapest point to catch a `CLAUDE.md` §0.1 violation — before a human reviewer has to notice it in a dependency-tree diff | Blocks merge |
| Licence compliance check across all resolved dependencies | Enforces `CLAUDE.md` §0.1's "no proprietary or GPL-3.0-incompatible dependency" automatically, not only at the point a new dependency is proposed | Blocks merge |
| Dependency vulnerability scan (e.g. OWASP dependency-check-gradle, verified as an actively maintained Gradle plugin with configurable per-task CVSS thresholds) | Security hygiene; can run at a lower, PR-appropriate severity threshold (e.g. fail only on the highest-severity CVEs on every PR) with a stricter threshold reserved for the release check below | Blocks merge above the configured PR threshold |
| Instrumented/UI tests — **critical paths only** (`docs/testing.md` §2.3) | Kept deliberately small per the test-pyramid document's own framing; not the bulk of PR-gate time | Blocks merge for the specific critical paths covered |

### Automated no-GMS enforcement, concretely

`CLAUDE.md` §5.1 asks for this document to explain *"how the no-GMS rule is enforced automatically
in CI rather than by review discipline alone, since that is the cheapest place to catch it."*
Verified: no single, purpose-built, off-the-shelf Gradle plugin dedicated specifically to "detect
and fail on any Google Play Services / Firebase dependency" was found in this research — this is
flagged explicitly as **unverified/not found**, not silently assumed absent. The recommended
mechanism instead uses Gradle's own, well-documented dependency-resolution controls, which do not
require a third-party plugin at all:

- **Dependency exclusion + resolution-failure**: declare, at the root `build.gradle.kts` (or via a
  convention plugin, if `docs/adr/proposals/010-module-layout.md` adopts one), an explicit
  `resolutionStrategy` / `exclude` rule banning the `com.google.android.gms` and
  `com.google.firebase` dependency groups (and any other group the human wants named, e.g. Google
  Maps SDK coordinates) across every configuration. Gradle's dependency exclusion is a standard,
  documented mechanism — this does not depend on an external plugin's maintenance status at all,
  which is itself a small resilience advantage over relying on a third-party checker.
- **A CI task that asserts the resolved dependency graph is clean**: a small custom Gradle task (or
  a script step) running the equivalent of `./gradlew :app:dependencies` and asserting none of the
  banned group IDs appear anywhere in the resolved graph — including transitively. This is
  explicitly named in `CLAUDE.md` §0.1 itself ("check with `./gradlew app:dependencies` when adding
  anything"), so this CI check simply automates a step the project's own constraints already name as
  the manual verification method.
- Both mechanisms are complementary, not alternatives: the exclusion rule prevents the dependency
  from resolving at all (fails the build immediately, with a clear Gradle error naming the excluded
  module), while the graph-assertion task is a second, explicit, human-readable CI check that
  produces a clear pass/fail signal in the PR rather than relying on developers correctly
  interpreting a Gradle resolution failure buried in a build log.

## What runs on release

| Step | Purpose |
|---|---|
| Full PR-gate suite (all checks above), plus the full instrumented suite if any is deferred from PR-gate for time | Release must not skip anything the PR gate does |
| Stricter dependency-vulnerability threshold (e.g. the OWASP plugin's own documented pattern of a lower/stricter CVSS failure threshold for release-tagged builds than for ordinary PRs, verified as a supported configuration pattern) | Release is where a lower risk tolerance is appropriate, since the artifact is about to be distributed |
| Reproducible-build verification step (see below) | Confirms the artifact about to be published matches what a from-source rebuild produces, before it ever reaches a user |
| Signing (see below) | Never performed with a key present in the repository |
| Changelog / release-notes generation reflecting `docs/` changes in the same release, per `CLAUDE.md` §7's same-PR documentation rule | Keeps the release artifact's documented behaviour honest, matching `docs/privacy.md`'s own standard of "reality matching the document" |

## Reproducible-build requirements

Given reproducibility is F-Droid best-practice rather than a hard gate (see above), but is
strategically valuable to get right from the very first release (avoiding the reinstall-forcing
retrofit cost), the concrete requirements are:

- **Deterministic build inputs**: pinned dependency versions (a Gradle lockfile or equivalent), no
  build step that embeds a build timestamp, machine-specific path, or random value into the
  artifact — standard reproducible-build hygiene, verified as the general shape F-Droid's own
  Reproducible Builds documentation describes (byte-for-byte or cryptographically-equivalent output
  from the same source).
- **A CI step that actually proves it**: build the release artifact twice, from the same tagged
  commit, in two independent environments (or the same environment run twice with build caches
  cleared), and diff/hash-compare the two outputs — this is the mechanical, testable form of the
  reproducibility claim `docs/threat-model.md` relies on, not merely a hygiene checklist followed by
  hope.
- **No proprietary blob anywhere in the build output** — restates `CLAUDE.md` §0.1 in
  build-artifact terms; the dependency and licence checks above are what makes this checkable
  automatically rather than only at release-review time.

## Signing-key handling

**Never in the repository, in any form, at any time — including history.** Concretely:

- The release signing key lives outside version control entirely — a CI secret store (e.g. GitHub
  Actions encrypted secrets) or a hardware/offline mechanism the human manages directly, never
  committed, never in a build script's plaintext, never in a Gradle properties file checked into
  the repo.
- CI signs the release artifact using the secret injected at build time, not a key file present in
  the checked-out source tree.
- Per the verified F-Droid signing model above: once reproducibility is demonstrated, F-Droid can
  distribute the app signed with the **developer's own key** rather than re-signing with an
  F-Droid-generated one — meaning the human's own signing key (handled per the paragraph above) is
  the same key that ends up on F-Droid-distributed builds, not a second key F-Droid manages
  independently. This is a reason to get the human's own key-handling process right once, rather
  than needing a separate F-Droid-specific signing story.
- `CLAUDE.md` §10.10 already forbids "committing secrets... or binaries" as a process violation;
  this document's contribution is naming the concrete mechanism (CI secret injection) rather than
  leaving "don't commit the key" as an unenforced norm — a pre-commit/CI secret-scanning step (e.g.
  a check for common keystore file extensions or key-material patterns) is a further recommended,
  cheap safety net, verified as a common, low-cost CI addition rather than a novel proposal.

## Options for CI provider/tooling

This document does not need to litigate every CI vendor — the realistic choice for a public GitHub
repository is narrow — but names it for completeness, since `CLAUDE.md` §0 fixes the repo host.

| Option | Fit |
|---|---|
| GitHub Actions | Native integration with the project's existing GitHub hosting (`CLAUDE.md` §0: "Repo: `cdhdt/LibreWays` — public"); no separate account/service needed; free minutes for public repositories is the typical arrangement for open-source projects, though the exact current free-tier terms should be reconfirmed at implementation time rather than assumed indefinitely stable |
| A self-hosted or third-party CI service (e.g. a self-hosted runner, or another SaaS CI provider) | Adds an operational dependency (someone must run/maintain the runner, or trust and pay for a second third-party service) with no clear privacy or F-Droid-compliance benefit over GitHub Actions for a project already hosted on GitHub — **not recommended** absent a specific reason (e.g. a resource constraint GitHub Actions cannot meet) the human has not raised |

**Recommendation on this narrow point**: GitHub Actions, given the repository is already
GitHub-hosted and no reason to introduce a second CI vendor has been raised. This is a low-stakes,
low-confidence-needed sub-decision relative to the rest of this document.

## How reproducibility and the no-GMS rule are verified, end to end

- **Unit/lint/dependency checks**: run on every PR as described above — this is where a GMS
  dependency, a licence violation, or a high-severity CVE is caught **before** a human reviewer has
  to notice it manually, directly answering the "cheapest place to catch it" framing from
  `CLAUDE.md` §5.1.
- **Reproducibility**: verified mechanically at release time via the double-build-and-compare step
  above — this is the same mechanism `docs/threat-model.md` describes as the mitigation against a
  malicious-fork adversary, and `docs/testing.md` §7 already defers this exact verification to this
  ADR's eventual CI job rather than treating it as a unit test.
- **Manual/periodic checks**: F-Droid's own build-server rebuild-from-source process (once the app
  is actually submitted and accepted) is an **independent, external** confirmation of the same
  property this project's own CI already checks internally — worth stating explicitly as a
  second, external layer of the same verification, not a duplicate effort.

## Recommendation

**GitHub Actions** as the CI provider; a **PR-gate pipeline** covering build, unit tests,
lint/detekt, an automated GMS/Firebase dependency-exclusion-and-assertion check (via Gradle's native
exclusion mechanism plus a custom assertion task, since no dedicated off-the-shelf plugin was
verified to exist), a licence-compliance check, and a PR-appropriate-threshold vulnerability scan
(OWASP dependency-check-gradle, verified as fit for purpose); a **release pipeline** adding a
stricter vulnerability threshold, a double-build reproducibility verification step, and CI-injected
signing with the key never present in the repository; and pursuit of **reproducible builds from the
very first release**, given F-Droid's own guidance that retrofitting reproducibility later forces
existing users to reinstall.

**Confidence: high** on the GitHub Actions choice and the general PR/release-gate shape (low-risk,
conventional, directly aligned with already-verified F-Droid and `CLAUDE.md` requirements).
**Medium** on the specific GMS-check mechanism: the Gradle-native exclusion approach is verified as
a real Gradle capability, but no existing, purpose-built plugin was found to compare it against, so
this recommendation is this document's own synthesis rather than an industry-standard pattern
directly observed elsewhere — the human or the implementing developer should re-check whether a
more turnkey tool has emerged before committing significant CI-script effort to a hand-rolled
version. **High** on pursuing reproducibility from the first release, given the directly-verified
F-Droid guidance that this is specifically encouraged for new apps to avoid a later forced
reinstall.

**Honest cost**: the double-build reproducibility-verification step and the licence/dependency scans
add real CI time and, for a small team, real setup effort before the very first release — this is
time spent on release-pipeline infrastructure rather than on user-facing features, justified here
because F-Droid distribution and the `CLAUDE.md` §0 non-negotiables make it a genuine release
blocker, not a nice-to-have.

## Consequences of the recommendation

- **Becomes easy**: every subsequent dependency addition is automatically checked for a GMS/Firebase
  pull and a licence problem, removing that burden from manual review on every single PR going
  forward.
- **Becomes hard / needs attention**: maintaining the hand-rolled GMS-exclusion/assertion mechanism
  as Google's own package naming or AndroidX's dependency graph evolves — this is bespoke project
  code, not a maintained third-party tool, so it is this project's own responsibility to keep
  current, unlike a plugin that would receive upstream updates on its own.
- **What must be abstracted to keep it reversible**: none of this touches application code at all —
  it is entirely CI workflow files and Gradle build configuration, so switching CI provider or
  swapping the dependency-check mechanism later never requires touching `domain`, `data`, or
  `presentation`.

## What is needed from the human

1. **Confirm GitHub Actions** (or state a reason for an alternative — none identified in this
   research).
2. **Set the PR-gate vs. release vulnerability-scan thresholds** — this document proposes "lenient
   on PR, strict on release" as a pattern but does not fix concrete CVSS numbers; that is a policy
   call.
3. **Decide who holds and operates the release signing key**, and provision it as a CI secret —
   this is the one place this document needs something concrete *from* the human: the actual
   signing key/keystore must be generated and held by the human (or a process the human designates),
   never generated inside, or committed to, the repository.
4. **Confirm the reproducible-builds-from-day-one commitment**, given the verified cost of
   retrofitting it later (forcing existing users to reinstall) — or explicitly accept that cost if
   the human prefers to defer reproducibility work past the first release for some other reason.
5. When the project is ready to submit to F-Droid, someone (human or a future recon pass) should
   re-verify F-Droid's current Inclusion Policy and submission process directly against F-Droid's
   own documentation at that time, since policy specifics can change and this document's research
   reflects a point-in-time check, not a standing guarantee.

## Reversibility

**High for CI tooling itself.** GitHub Actions workflow files and Gradle build-script configuration
are not load-bearing for the app's runtime architecture at all — switching CI providers, changing
the dependency-check mechanism, or adjusting vulnerability thresholds is confined to `.github/`
and Gradle build files, with zero impact on `domain`/`data`/`presentation` code.
**Lower for the signing-key and reproducibility commitments once real users exist.** Per F-Droid's
own verified guidance, changing from a non-reproducible to a reproducible build, or changing signing
approach, after real users have installed a build signed one way, forces those users to reinstall
rather than update — this is the one place in this document where "reversible" is the wrong frame
entirely: getting reproducibility and signing right **before** the first real release is
materially cheaper than fixing it after, which is the concrete argument, restated here, for why this
document recommends committing to reproducibility from day one rather than treating it as a
deferrable nice-to-have.
