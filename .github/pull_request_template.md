<!--
Read CONTRIBUTING.md before opening this PR. This PR must target `develop` and stay in draft
until the reviewer's approval is recorded (comment + label). Fill in every section honestly —
paste real command output, don't assert without evidence.
-->

## What and why

<!-- The problem being solved and why this is the right change. Not just a diff summary. -->

## Linked issue and spec

- Issue: #
- Spec: `docs/specs/` (link the file, or state "none — trivial change" and justify why no spec
  was needed)

## Definition of Done

<!-- Copy the checklist from the recon spec / issue. Check off only what is actually done. -->

- [ ] 
- [ ] 
- [ ] 

## Test evidence

<!-- Paste the real output of the commands you ran. "Tests pass" without output is not evidence. -->

```
(paste command and output here)
```

- [ ] Tests were written before the implementation (TDD, `CLAUDE.md` §4)
- [ ] No test performs a real network call (`docs/testing.md` §4)
- [ ] No test is disabled, ignored, or sleep-based

## Docs updated

<!-- List every doc file touched in this PR. If none, state why none applied. -->

- [ ] `docs/privacy.md` (if any data flow, permission, or logging changed)
- [ ] `docs/testing.md` (if the test strategy or how-to-run changed)
- [ ] `docs/architecture/` (if module boundaries or data flow changed)
- [ ] `docs/adr/` (if a §0.2 open decision was resolved — link the ADR)
- [ ] Other: 

## Privacy impact

- New outbound network request? Y/N — if yes, documented in `docs/privacy.md`? Y/N
- New permission? Y/N — if yes, justified in `docs/privacy.md`? Y/N
- New stored field (disk, `SharedPreferences`, backup)? Y/N — if yes, what and why kept minimal?
- New log statement touching location, destination, search text, or any identifier? Y/N — if
  yes, what was done to keep it PII-free?

## Resource impact

- Main-thread work added or removed:
- Background work added (e.g. foreground service, coroutine scope, or another deferred/batched
  mechanism — the background-scheduling approach itself is an open ADR, see `docs/adr/README.md`;
  do not assume WorkManager or any other specific mechanism is the expected answer) and its
  cancellation path:
- Battery/memory impact expected: none / negligible / measured (attach evidence)

## i18n

- [ ] All new user-facing strings are in `res/values/strings.xml` with descriptive keys
- [ ] `fr` (French) resources updated in the same PR
- [ ] No string concatenation used to build a sentence; placeholders/plurals used where needed

## Google Play Services

- [ ] No dependency added requires or pulls in Google Play Services / Firebase / GMS, including
      transitively — verified with: <!-- e.g. `./gradlew app:dependencies`, or "no dependency
      added" -->

## Reviewer attention points

<!-- Anything you want the reviewer to look at specifically: a tricky edge case, an assumption
you made, a risk you see. -->

## Confirmation

- [ ] This PR targets `develop` (not `main`)
- [ ] This PR is opened as **draft** and stays draft until the reviewer's approval is recorded
- [ ] I have not merged, approved, or marked this PR ready myself
- [ ] This PR's title follows the commit convention (gitmoji, Conventional Commits, subject
      ≤ 72 characters) — merges into `develop` are squash merges, so this title becomes the
      commit message
