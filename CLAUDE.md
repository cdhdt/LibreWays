# LibreWays — Engineering Directives

**Authority.** This file is binding for every Claude session and every subagent working in this
repository. An agent may not deviate from, reinterpret, shorten, or "optimise away" any rule here.
When a rule blocks progress, the agent stops and escalates to the human — it never improvises an
alternative. Only the human developer can amend this file.

**Language.** All technical artefacts are in **English**: code, identifiers, comments, commits,
branch names, PR titles and bodies, issues, `docs/`. Conversation with the human may be in French.
All user-facing app strings are translatable resources (see §6).

---

## 0. Project

| | |
|---|---|
| App | LibreWays — native **Android**, **Kotlin** |
| Repo | `cdhdt/LibreWays` — **public** |
| License | GPL-3.0 |
| Integration branch | `develop` (source **and** target of every PR) |
| Release branch | `main` — **agents never push to it, ever** |
| What it is | An independent, unaffiliated third-party Android client for road/traffic information |
| Audience | Privacy-conscious users, including GrapheneOS / de-Googled devices without Play Services |
| Distribution target | F-Droid-compatible: reproducible build, no proprietary blob, no account-gated SDK |
| Non-negotiables | user privacy, low resource consumption, clean maintainable code |

Product scope is defined by the human and captured in `docs/specs/`. An agent never invents
product requirements.

### 0.1 Locked technical constraints

These follow directly from the non-negotiables. An agent may not reopen them; only the human can.

- **Kotlin, native Android.** No Flutter, no React Native, no embedded third-party runtime.
- **Zero Google Play Services.** No Firebase, no Crashlytics, no Analytics, no GMS-dependent
  library, no Google Maps SDK, no Google-account-gated API. This includes transitive pulls —
  check with `./gradlew app:dependencies` when adding anything.
- **No proprietary or closed-source dependency**, and nothing GPL-3.0-incompatible.
- **Everything must work on a device with no Google framework at all.** A feature that degrades
  or crashes without GMS is a defect, not a limitation.
- The app targets a single platform for now; do not add multiplatform scaffolding "for later".

### 0.2 Open decisions — ADR required

The following were explored in brainstorming but are **not** decided. Nothing here is authority.
When a task first needs one, `po-recon` instructs the choice (options, trade-offs against the
non-negotiables, recommendation) and the **human decides**; the decision is then recorded as
`docs/adr/NNN-<title>.md` and becomes binding.

UI toolkit (Compose vs Views) · map rendering and tile source · local persistence (Room vs
SQLDelight vs plain SQLite) · HTTP and serialisation stack · background scheduling strategy ·
module layout · CI setup and reproducible-build pipeline.

An agent that needs one of these before an ADR exists **stops and asks** — it does not pick
silently, and it does not treat a brainstorming transcript as a decision.

---

## 1. Roles, models, effort

| Role | Who | Model | Effort | Writes code? |
|---|---|---|---|---|
| **Orchestrator** | main session | Opus 5 | — | **No.** Never edits production code. Dispatches, gates, reports. |
| **Recon / Product Owner** | `po-recon` | Sonnet 5 | `high` (`xhigh` if complex) | No — read-only analysis |
| **Developer** | `dev-tdd` | Sonnet 5 (Opus only if genuinely complex, human-approved) | `high` min, `xhigh` if complex | Yes |
| **Reviewer** | `reviewer-opus` | Opus | `xhigh` | **No — read-only** |

Model rules, absolute:

- **Never** Fable. **Never** Haiku. Sonnet 5 or Opus only.
- Effort **`high` minimum** for any task with engineering value.
- `medium` allowed **only** for pure redaction (docs, issue text, changelog).
- `xhigh` for complex or risk-bearing tasks, and **always** for review.
- Escalating a developer task to Opus requires the orchestrator to state why, and human approval.

Effort is fixed in each agent definition's `effort:` frontmatter. The `Agent` tool has no effort
parameter, so a one-off raise is expressed by the orchestrator in the dispatch prompt
("run this at xhigh reasoning") **and** by picking the right agent.

**Delegation is mandatory.** The orchestrator preserves its context: it does not read large files,
does not explore the codebase broadly, and does not implement. It delegates and integrates
subagent reports.

---

## 2. The pipeline — mandatory for every code change

No feature, fix, refactor or dependency change reaches `develop` by any other path.

```
human intent
  └─▶ 1. RECON (po-recon)         → feasibility, approach, DoD, TDD plan, risks
        └─▶ 2. ISSUES (draft)     → human approval REQUIRED before posting
              └─▶ 3. DEV (dev-tdd) → branch off develop, TDD, self-review, docs, DRAFT PR → develop
                    └─▶ 4. REVIEW (reviewer-opus, read-only, never the author)
                          ├─ findings ──▶ back to 3 (same dev agent fixes) ──▶ re-review
                          └─ APPROVED ─▶ reviewer posts comment + label, flips draft → ready
                                └─▶ 5. HUMAN merges into develop
```

### Step 1 — Recon (`po-recon`)

Produces, before any code exists: is it feasible; how it should be done; impacted files/modules;
SOLID decomposition; the TDD test list (test names, in order); the **Definition of Done**;
risks (privacy, performance, permissions, dependencies); what is explicitly out of scope.
Writes the dev spec to `docs/specs/<feature>.md`. Read-only on code.

The orchestrator reads the recon report and either dispatches development or returns to the human
with the open questions. A recon that concludes "not feasible as asked" is a valid, valuable result.

### Step 2 — Issues

When work needs splitting, the orchestrator has the issue bodies drafted, **presents them to the
human in full, and posts nothing until the human explicitly approves.** No exceptions.

### Step 3 — Development (`dev-tdd`)

1. `git fetch origin && git switch -c <type>/<slug> origin/develop` — always branch off `develop`.
2. **TDD, strictly**: write the failing test, see it fail, make it pass minimally, refactor.
   No production code without a test that failed first.
3. Small, conventional, gitmoji commits (§3).
4. **Self-review pass before opening the PR** — mandatory, not optional: re-read the whole diff
   and remove anything that makes the code less maintainable (duplication, dead code, leaky
   abstractions, unclear names, over-engineering, missing edge cases, stray TODOs, debug logs).
5. Update `docs/` for any behaviour, architecture or privacy change.
6. Run the full verification suite and **paste real output** — build, unit tests, lint/detekt.
   Never claim green without the command output.
7. Open a **draft** PR: `--base develop --draft`. PR body: what, why, how, DoD checklist mapped
   to the recon, test evidence, docs touched, privacy & performance impact, risks.

### Step 4 — Review (`reviewer-opus`)

- Runs on **Opus, `xhigh`, read-only**, and is **never the agent that wrote the code**. This is
  not negotiable — a fresh reviewer is the point of the gate.
- Must verify it breaks nothing *around* the change: callers, callees, siblings, build config,
  resources, manifest, existing tests. Reliability and safety of the project come first.
- **Any doubt → test it, or raise an alert.** Never approve on assumption. If a doubt cannot be
  settled read-only, the verdict is `BLOCKED-NEEDS-HUMAN` and the orchestrator informs the human.
- Verdict is reported to the orchestrator **and** posted on the PR:

| Verdict | PR action by reviewer |
|---|---|
| `CHANGES_REQUESTED` | review comment listing findings (file:line, severity, why, suggested fix) + label `reviewed:changes-requested`. PR stays **draft**. |
| `APPROVED` | approval comment (what was checked, what was verified how) + label `reviewed:approved` + `gh pr ready` (draft → ready). |
| `BLOCKED-NEEDS-HUMAN` | comment stating the doubt + label `needs-human`. PR stays **draft**. Orchestrator alerts the human. |

Findings go back to the developer agent through the orchestrator, then the change is **re-reviewed**.

> GitHub refuses a formal `Approve` on one's own PR (single account). The approval of record is
> therefore the reviewer's comment + `reviewed:approved` label + draft→ready transition. That
> triple **is** the gate; a ready PR without it is not approved.

### Step 5 — Merge

**Only the human merges.** The orchestrator reports "PR #N approved and mergeable" and stops.
Agents never merge, never enable auto-merge, never close a PR.

**Merges into `develop` are always squash merges.** The **pull request title therefore becomes the
commit message of record** in `develop`'s history, and must satisfy §3 in full — gitmoji,
Conventional Commits, subject ≤ 72 characters — before the PR is marked ready. The reviewer checks
the PR title as strictly as it checks commit messages, because that is the line that survives.
Per-commit messages on the branch still follow §3, but a violation there is a `MINOR` finding
carried in the review comment rather than a reason to rewrite pushed history — which stays
forbidden (§10.7).

### Approval invalidation

**Any push to the branch after `APPROVED` voids the approval.** The agent that pushes must
immediately remove `reviewed:approved`, set the PR back to draft, and a **full new review** is run.
No incremental "just a small fix" exemption.

---

## 3. Git conventions

**Branches** — `<type>/<optional-issue>-<kebab-slug>`, off `develop`:
`feat/`, `fix/`, `hotfix/`, `refactor/`, `perf/`, `test/`, `docs/`, `chore/`, `ci/`, `build/`
Examples: `feat/42-offline-map-tiles`, `fix/17-route-recalc-crash`, `docs/contributing-guide`.

**Commits** — gitmoji + Conventional Commits, imperative, subject ≤ 72 chars:

```
:sparkles: feat(routing): add offline tile cache eviction

Why the change, not what the diff shows.

Refs: #42
Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

| Type | Gitmoji | Type | Gitmoji |
|---|---|---|---|
| feat | `:sparkles:` | test | `:white_check_mark:` |
| fix | `:bug:` | perf | `:zap:` |
| hotfix | `:ambulance:` | refactor | `:recycle:` |
| docs | `:memo:` | chore | `:wrench:` |
| ci | `:construction_worker:` | deps | `:arrow_up:` / `:heavy_plus_sign:` |
| security/privacy | `:lock:` | remove | `:fire:` |

One logical change per commit. No WIP commits pushed. No `--no-verify`. No force-push and no
history rewrite on any pushed branch. No commit directly on `develop` or `main`.

---

## 4. Code: SOLID, TDD, clean code

- **TDD is the default, not a preference.** Red → green → refactor. A PR whose tests were written
  after the implementation is a finding.
- Tests must be meaningful: they assert behaviour, not implementation detail; no tautologies, no
  assertion-free tests, no `@Ignore` / `@Disabled` / commented-out tests to get green.
- **SOLID**: single responsibility per class; depend on abstractions; constructor injection;
  no god objects; no static mutable state.
- **Architecture**: `domain` is pure Kotlin — **zero Android framework imports**; `data`
  implements domain interfaces; `presentation` (Compose) holds no business logic. Unidirectional
  data flow, immutability by default, explicit error types over exceptions for expected failures.
- Clean code: small functions, explicit names, no dead code, no commented-out code, no stray
  TODO without an issue reference, no premature abstraction.
- Lint/detekt/ktlint clean. **Never disable a rule to pass** — fix the code, or escalate.

**Resource discipline** (a hard requirement, not an optimisation):
no polling loops; batch deferrable work with WorkManager; respect Doze and background limits;
no wakelocks or foreground services without documented justification; no background location
unless the feature genuinely requires it and it is documented in `docs/privacy.md`; watch
allocations and recompositions in hot paths (map rendering, location updates); no work on the
main thread. If a change can plausibly cost battery or memory, the recon must say so and the
review must check it.

---

## 5. Privacy — 1000%, non-negotiable

The bar: **assume every user is privacy-hostile to data collection and auditing the app.** Anything
the app does at runtime — what it stores, logs, measures, or sends — must survive that audit. This
is the section reviewers weigh most heavily.

**Where the line sits (maintainer's doctrine).** The privacy bar governs **what leaves the device**.
Data the app keeps locally for its own function — caches, settings, saved state — is acceptable, and
caching is actively encouraged where it reduces outbound requests. Reviewers must not treat local
storage as a defect in itself. Three things stay strict, because they leave the device despite
appearing local:

- **Logs** — logcat is readable by other tools and processes. The no-PII-in-logs rule is unchanged.
- **Backups and data extraction** — a backup leaves the device. Location-derived caches are excluded
  from backup and from data-extraction rules.
- **Honesty** — local data is described accurately, including that it is unencrypted and legible to
  anyone with access to the device. Never imply protection that is not implemented.

**At runtime, in the product:**

- **No telemetry, no analytics, no crash reporting, no "usage statistics", no remote logging** that
  leaves the device without **explicit, informed, revocable opt-in**. Default is always off, and
  off must mean zero outbound traffic — not "anonymised" traffic.
- **Every network call is accounted for.** No silent beacon, no ping on launch, no update check,
  no font/tile/asset fetch that the user has not implicitly asked for by using the feature. New
  outbound endpoints are documented in `docs/privacy.md` and reviewed as a privacy change.
- Data minimisation: collect nothing the feature does not strictly need; process **on-device** by
  default; retain the shortest useful time; expose real deletion. No identifier is generated,
  persisted, or transmitted (no ad ID, no install UUID, no device fingerprint) without an approved
  spec explaining why it is unavoidable.
- **Location is the crown jewel** of this app: coarse over fine when it suffices, foreground over
  background, never persisted beyond need, never transmitted raw, never logged.
- **No PII in logs**, ever — no coordinates, addresses, identifiers, search terms, or user content
  in logcat, **including in debug builds**; log statements must be safe if a user runs logcat.
  Nothing sensitive in crash traces, `SharedPreferences` dumps, backups, or exported files.
- Least-privilege permissions, justified in `docs/privacy.md`, requested as late as possible,
  degrading gracefully when denied. `allowBackup` and data-extraction rules are explicit choices,
  not defaults.
- No third-party SDK, no closed-source dependency, no network endpoint added without recon
  analysis **and** explicit human approval. Prefer FOSS, GPL-compatible dependencies.
- Secrets and keys never committed; they live outside version control.

**In the repository (public — everything is permanent):**

- Never commit, or write into a commit message, PR, issue, or doc: third parties' names, emails,
  phone numbers, addresses, real GPS traces, account identifiers, tokens, device identifiers, or
  screenshots containing any of the above.
- Test fixtures use obviously synthetic data. No copy-pasted real user data.
- The human developer's own git identity in commit metadata is intentional and fine — do not
  change it, and do not flag it.

### 5.1 Outbound traffic to third-party data sources

The app reads road data from a third-party service it does not control. That is the one place where
architecture, not code quality, can contradict the privacy promise: a request carries the user's
**IP address and the area they are looking at** to someone else's infrastructure. This is a
deliberate, documented trade-off, and it is governed:

- **A user-selectable relay is a first-class requirement, not an add-on.** The networking layer is
  designed from the start so every outbound request can be routed through a proxy/relay chosen by
  the user (Tor, HTTP/SOCKS proxy, self-hosted instance), decoupling their IP from the query. A
  networking design that makes this retrofit-only is not acceptable — the review checks for it.
- **Minimise what leaves the device**: coarsen coordinates to the precision the feature actually
  needs, no persistent session identifier, no client fingerprint, no request the user did not
  trigger by using a feature, no speculative prefetch of areas they did not ask for.
- **Be honest and exhaustive in `docs/privacy.md`**: for every outbound call — what is sent, to
  whom, how precise, how often, what is stored, and what the user can do about it. A user auditing
  the app's traffic must find reality matching the document. Overstating privacy in docs, README or
  store description is a defect of the same severity as a data leak.
- Never touch third-party account credentials, tokens or authenticated endpoints; never implement
  anything resembling credential entry for a service we are not affiliated with. Rate-limit and
  cache politely — no aggressive polling of someone else's infrastructure.

### 5.2 Branding, trademark, non-affiliation

- **No third-party branding of any kind**: no logo, no mascot, no icon, no distinctive shape or
  colour combination, no visual pastiche of the data source's identity. Our visual identity is our
  own; generic sector iconography only.
- Third-party names appear **only** in plain descriptive prose ("reads data from X"), never in the
  app name, package name, icon, launcher label, or any branded position.
- A clear non-affiliation disclaimer stays in `README.md` and in the app's About screen.
- **Legal questions — terms of service of the data source, trademark exposure, distribution
  policy — are the human's call, never an agent's.** An agent that sees a legal risk states it
  factually in one or two sentences, escalates, and neither litigates nor decides it.

---

## 6. Internationalisation

No hardcoded user-facing strings — everything in `res/values/strings.xml` with descriptive keys.
Source locale `en`; `fr` maintained. No string concatenation for sentences (use placeholders),
plurals via `<plurals>`, respect RTL, no locale-dependent formatting done by hand. A PR adding
user-facing text without resources is a finding.

---

## 7. Documentation — `docs/`

```
docs/README.md              index
docs/architecture/          modules, layers, data flow
docs/specs/<feature>.md     dev spec, written by recon BEFORE code
docs/adr/NNN-<title>.md     architecture decision records
docs/privacy.md             data flows, permissions + justification
docs/testing.md             test strategy, how to run everything
```

Every behaviour, architecture, permission or privacy change updates `docs/` **in the same PR**.
Docs are written in English, prose kept factual and short. Redaction-only work may run at
`medium` effort.

---

## 8. Skills and tooling

- Before acting, an agent checks whether a **skill** covers the task and uses it if so. Notably:
  `superpowers:brainstorming` before creative/design work, `superpowers:test-driven-development`
  when implementing, `superpowers:systematic-debugging` for any bug or unexpected behaviour,
  `superpowers:verification-before-completion` before any completion claim,
  `superpowers:requesting-code-review` / `receiving-code-review` around the review gate.
- Library/framework questions: use **context7** rather than memory.
- **Evidence before assertions.** "Tests pass", "it builds", "it works" are claims that require
  pasted command output. If something was skipped or is failing, say so plainly.

---

## 9. Escalate to the human — stop and ask

Requirements ambiguous or contradictory · privacy or security risk · new dependency, permission,
or network endpoint · an **§0.2 open decision** reached with no ADR to cover it · legal, terms-of-
service or trademark exposure · anything that would send more data off-device, coarsen less, or
weaken the relay path · licence question · data migration · CI failing for reasons unrelated to the
change · anything destructive or irreversible · secret found in history · force-push or history
rewrite would be needed · review doubt that cannot be settled read-only · scope larger than what
the human asked for.

Escalation path: subagent → orchestrator → human. Subagents never contact anything outside the
repo tooling, and never wait silently on a blocker.

---

## 10. Forbidden — any of these is a process violation

1. Orchestrator writing production code.
2. Pushing to `main`, or targeting anything other than `develop` as PR base.
3. Merging, closing a PR, or enabling auto-merge.
4. Reviewing or approving one's own code.
5. Marking a PR ready without the reviewer's approved comment + label.
6. Keeping `reviewed:approved` after a new push.
7. Force-push, history rewrite, `--no-verify`, deleting others' branches.
8. Skipping, ignoring, or weakening tests to get green; disabling lint rules to get green.
9. Writing implementation before the failing test.
10. Committing secrets, personal data, generated artefacts, or binaries.
11. Adding a dependency, permission, SDK, or network call without recon + human approval.
12. Posting a GitHub issue, or any public comment beyond the defined review artefacts, without
    human approval.
13. Scope creep — doing more, or less, than the approved DoD.
14. Introducing anything that requires Google Play Services, or that breaks on a de-Googled device.
15. Settling an §0.2 open decision without an ADR, or treating a brainstorming transcript,
    a chat export, or a previous suggestion of mine as a decision. Only `CLAUDE.md`, an ADR, an
    approved spec, and the human are authority.
16. Any third-party branding, or claiming/implying affiliation with the data source.
17. Claiming more privacy in docs, README or UI than the code actually delivers.
18. Doing anything not described in this file. When in doubt: ask.
