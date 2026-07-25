# Contributing to LibreWays

LibreWays is an independent, unaffiliated, privacy-first Android navigation app. It is not
affiliated with, and does not imitate the branding of, any existing navigation product or data
provider. Thank you for considering a contribution — please read this document before opening a
pull request; it is derived from [`CLAUDE.md`](CLAUDE.md), which is the binding engineering
reference for the AI agents that work in this repository (see [AI agents](#ai-agents-in-this-repository)
below). Human contributors are expected to follow the same engineering conventions this document
describes — branching, commits, tests, privacy, and docs — even though `CLAUDE.md` itself formally
binds only Claude sessions and subagents.

## Before you start

- Check existing issues and `docs/specs/` for a spec covering the change you have in mind. If
  none exists for a non-trivial change, open an issue describing the problem first — see
  [Pull request expectations](#pull-request-expectations).
- New dependencies, permissions, network endpoints, or anything that could touch the constraints
  in `CLAUDE.md` §0.1/§5 need discussion before code is written, not after.

## Branch model

- The integration branch is **`develop`**. Every pull request is opened **from a branch off
  `develop`, into `develop`** — never into `main`, and never committed to directly.
- **`main` is the release branch.** Contributors do not push to it, target it, or expect their
  branch to be based on it.

Branch naming: `<type>/<optional-issue>-<kebab-slug>`

| Type | Use for |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `hotfix` | Urgent fix |
| `refactor` | Internal restructuring, no behaviour change |
| `perf` | Performance improvement |
| `test` | Test-only change |
| `docs` | Documentation only |
| `chore` | Maintenance, tooling, non-production-code housekeeping |
| `ci` | CI configuration |
| `build` | Build configuration |

Examples: `feat/42-offline-map-tiles`, `fix/17-route-recalc-crash`, `docs/contributing-guide`.

## Commit convention

Gitmoji + [Conventional Commits](https://www.conventionalcommits.org/), imperative mood, subject
line ≤ 72 characters, body explains **why**, not a restatement of the diff.

| Type | Gitmoji | Type | Gitmoji |
|---|---|---|---|
| feat | `:sparkles:` | test | `:white_check_mark:` |
| fix | `:bug:` | perf | `:zap:` |
| hotfix | `:ambulance:` | refactor | `:recycle:` |
| docs | `:memo:` | chore | `:wrench:` |
| ci | `:construction_worker:` | deps | `:arrow_up:` / `:heavy_plus_sign:` |
| security/privacy | `:lock:` | remove | `:fire:` |

Example commit messages:

```
:sparkles: feat(routing): add offline tile cache eviction

Bounds cache growth so long sessions don't exhaust device storage.

Refs: #42
```

```
:bug: fix(location): stop route recalculation crash on denied permission

Recalculation assumed a non-null last-known position; it now falls back
to the documented denied-permission state instead of throwing.

Refs: #17
```

```
:memo: docs(privacy): document geocoding outbound request

Adds the destination-search flow to the data-flow table; no code change.
```

One logical change per commit. No work-in-progress commits pushed. No `--no-verify`. No
force-push and no history rewrite on any pushed branch.

Merges into `develop` are always squash merges, so the **pull request title becomes the commit
message recorded in `develop`'s history**. It must follow the same convention as a commit subject
above: gitmoji, Conventional Commits, subject line ≤ 72 characters.

## Pull request expectations

- **Small and focused.** One change, one concern. Split larger work into several PRs rather than
  one large one.
- **Tests first.** Following TDD (`CLAUDE.md` §4): the failing test exists before the
  implementation that makes it pass. A PR whose tests were clearly written after the
  implementation is a review finding, not a style nit.
- **Docs updated in the same PR.** Any behaviour, architecture, permission, or privacy change
  updates the relevant file under `docs/` in the same pull request — not a follow-up.
- **Draft until reviewed.** Open the PR as a draft. It only moves to ready-for-review after the
  reviewer's approval is recorded (comment + label — see the review gate below); do not flip it
  yourself.
- Use the PR template (`.github/pull_request_template.md`) — fill it in honestly, including
  pasted real test output. Do not claim a command passed without having run it.

### The review gate

Every change goes through a review step before merge, and **only the maintainer merges.**
Contributors do not merge their own PRs, do not enable auto-merge, and do not mark their own PR
as approved. Any push to a branch after it was approved invalidates that approval and a fresh
review is required — do not treat a small follow-up push as exempt.

### What gets a pull request rejected

- No tests, or tests that do not actually exercise the claimed behaviour.
- A disabled test (`@Ignore`/`@Disabled`/commented-out) or a disabled lint/static-analysis rule
  used to reach a green build, instead of fixing the underlying issue.
- A dependency that pulls in Google Play Services, Firebase, or any GMS-dependent library —
  including transitively. This is a locked constraint (`CLAUDE.md` §0.1), not a preference.
- A new outbound network request that is not documented in `docs/privacy.md` in the same PR:
  what is sent, to whom, how precise, how often, what is stored, and what the user can do about
  it.
- Hardcoded user-facing strings. Everything user-facing is a translatable resource with a
  descriptive key (`CLAUDE.md` §6); `en` is the source locale and `fr` is maintained.
- Personal data anywhere in the change: real coordinates, real addresses, real names, emails,
  tokens, device identifiers, or screenshots containing any of the above — in code, tests,
  fixtures, commit messages, or the PR description itself.

## Privacy expectations for contributors

LibreWays' non-negotiable is user privacy (`CLAUDE.md` §5). As a contributor:

- **Never commit real coordinates, addresses, or personal data** — not your own, not anyone
  else's, not as a "just for testing" fixture. Use obviously synthetic values (e.g. `0.0, 0.0` or
  clearly fake test coordinates/addresses).
- Assume every log line, `SharedPreferences` dump, backup, or exported file could be read by a
  privacy-hostile auditor. No PII in logs, ever, including debug builds.
- No new identifier (ad ID, install UUID, device fingerprint), no new telemetry/analytics/crash
  reporting, and no new network endpoint without it being documented and, per `CLAUDE.md` §5,
  explicitly approved beforehand — not merged first and documented later.
- If you are unsure whether something you are about to commit contains personal data, do not
  commit it — ask first.

## AI agents in this repository

This repository is also worked on by AI agents operating under the binding rules in
[`CLAUDE.md`](CLAUDE.md): a recon/product-owner step before code, strict TDD, a read-only review
gate, and human-only merge. `CLAUDE.md` formally binds only those agents — it does not, by itself,
impose the same pipeline on human contributors.

**What is expected of human contributors, stated plainly**: the same engineering conventions
(branching model, commit style, TDD discipline, the privacy bar in §5, documentation-in-the-same-PR
rule) and the same review gate before merge — none of that is relaxed for a human PR, and a human
reviewer should hold an agent-authored PR to the identical bar.

**What is still an open question, not decided by this document**: whether a human contributor must
go through the full agent pipeline itself (recon spec before code, the specific review-gate
mechanics built for agent output) as opposed to following the same conventions and passing the same
review through a more direct route. This is explicitly the maintainer's call to make, not something
this document settles or that a contributor should assume either way.

## Licence

By contributing, you agree your contribution is licensed under **GPL-3.0**, matching the
project's licence (see [`LICENSE`](LICENSE)).

## Reporting a security or privacy issue

**Open question — human decision required.** There is no confirmed contact channel for security
or privacy reports yet. Do not open a public issue for a report that itself contains sensitive
details (e.g. a reproducible privacy leak with real data) until the maintainer has provided a
private channel. If you believe you have found a privacy or security issue and no private channel
is documented here yet, contact the maintainer through the repository's GitHub profile in the
interim.
