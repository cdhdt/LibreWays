# 007. Relay transport

## Status

`accepted`

Date: `2026-07-26` — Decided by: the maintainer, **by explicit delegation to the orchestrator**
(decision D18), the same delegation pattern already used for
[`012-build-and-test-tooling.md`](012-build-and-test-tooling.md) and
[`006-http-and-serialisation.md`](006-http-and-serialisation.md). The maintainer did not evaluate
each transport option personally; they authorised the orchestrator to decide this specific, bounded
transport-mechanism question so the relay chokepoint `CLAUDE.md` §5.1 requires could be implemented
without waiting on every other open decision. This ADR is otherwise binding exactly like any other
accepted ADR and **the maintainer may amend it at any time**, the same as any other accepted ADR —
delegation is not a lesser form of acceptance, and it does not make this record provisional.

## Context

[`proposals/007-relay-and-proxy.md`](proposals/007-relay-and-proxy.md) is the analysis brief this
ADR resolves — read it first for the full options analysis (Option A generic proxy, Option B + Orbot
integration, Option C embedded Tor) and the leak risks it identifies. Two sub-questions the brief
itself left open were already decided directly by the maintainer before this ADR existed —
fail-closed behaviour when a configured relay is unreachable, and that "direct, no relay" is an
explicit, first-class choice rather than the absence of one — and are **restated, not reopened,
below**. This ADR resolves the one sub-question the brief left genuinely open: **which transport
mechanism(s) the relay setting supports.**

This decision is only enforceable because of the single-networking-chokepoint invariant
(`docs/architecture/README.md` §4, `docs/adr/006-http-and-serialisation.md`): whatever is decided
here is implemented once, inside the chokepoint's client-construction code, never per-provider.

## Decision drivers

Unchanged from the proposal brief: privacy (does the mechanism actually decouple the user's IP from
the destination, without introducing a new leak); works fully without Google Play Services;
resource cost (battery, memory, APK size); GPL-3.0-compatible licence; F-Droid compatibility
(reproducible build, no proprietary blob); maintenance and community health; development speed;
testability; reversibility.

## Options

The brief's three options stand.

| Option | Verdict here |
|---|---|
| A — Generic configurable proxy (HTTP + SOCKS5) | **Chosen, mandatory baseline for v0.1** |
| B — Option A plus dedicated Orbot integration | Not chosen for v0.1 — deferred, not rejected |
| C — Embedded Tor client (Arti, JNI, no companion app) | Not chosen — excluded, not merely deferred |

## Decision

**A single generic proxy transport: user-configured HTTP or SOCKS5.** One mechanism, exposed by the
networking chokepoint through the platform's own proxy types (e.g. `java.net.Proxy` with
`Proxy.Type.HTTP`/`Proxy.Type.SOCKS`, configured on the single `OkHttpClient` instance per
`docs/adr/006-http-and-serialisation.md`), serves every case `CLAUDE.md` §5.1 names: Tor via Orbot
(which listens as a local SOCKS proxy this setting can point at), any HTTP or SOCKS proxy the user
runs or trusts, and a self-hosted endpoint the user operates. The last of these is not a fourth
transport — it is Option A pointed at a user-controlled address, given its own label in the settings
UI so the user understands the different trust model (an operator they chose and presumably
control, versus an anonymity network or a third-party proxy operator), exactly as the brief's
Context section describes.

**Embedded Tor (Option C) is excluded**, not deferred: no stable Kotlin/JNI bindings exist for Arti
today, and vendoring a native Tor binary is a materially harder F-Droid reproducible-build target
than pure-Kotlin proxy configuration code — against `CLAUDE.md` §0.1's F-Droid-compatibility and
no-heavy-unauditable-native-blob posture. Revisit only if Arti's Android integration matures with
official bindings and a concrete, verified need for companion-app-free Tor emerges; that would be a
fresh decision, not a default revival of this option.

**Dedicated Orbot integration (Option B) is deferred, not rejected.** It is additive UX on top of
Option A's transport — auto-detecting, prompting to install, and launching Orbot, rather than
requiring the user to type its SOCKS port manually — not a different privacy mechanism, since both
paths ultimately hand traffic to the same Orbot SOCKS listener. It depends on a third-party helper
library (historically NetCipher) whose current maintenance status the brief could not verify with
confidence. A technically capable user can already route through Orbot manually today under Option
A, by pointing the generic SOCKS5 setting at Orbot's documented local port. Revisit once the helper
library's maintenance is checked directly against its actual commit history and licence at the
version to be pinned — a pre-adoption task for whoever picks this up, not settled here.

**Restated, not reopened, from the maintainer's prior direct decisions** (both already recorded in
`docs/specs/001-navigation-mvp.md` and `docs/privacy.md`):

- **Fail-closed.** A configured relay that becomes unreachable fails the outbound request outright,
  with an explicit error to the user; the app never silently falls back to a direct, unrelayed
  connection (FR-25).
- **Explicit choice, always.** `RelayConfiguration` models an unset state (`NotChosen`), distinct
  from every selectable mode including "direct, no relay" itself, and that unset state blocks all
  egress. No outbound request is made before the user has made an explicit choice (FR-8, decision
  D1).

**Proxy authentication — out of scope for v0.1 (decision D19).** The proposal brief's leak-risk
item 4 named a real, unresolved question this ADR does not silently drop: Option A's own
description allows an optional username/password on a generic proxy, and if a credential field were
added without a decision, it would land in the already-decided, **unencrypted** DataStore
Preferences store (`docs/adr/014-settings-persistence.md`) with no storage-at-rest analysis and no
test covering it — exactly the kind of gap the brief flagged as a dependency on
`docs/adr/proposals/008-local-persistence.md`, never resolved there either. **The maintainer decided:
v0.1 does not support authenticated proxies.** `RelayConfiguration`'s proxy and self-hosted modes
carry host and port only, no credential field, for this milestone. Reasoning: a proxy credential
would need its own storage-at-rest decision (ADR 014's unencrypted store is not an authorisation to
persist one) and adds a leak surface — never logged, storage posture unresolved — for marginal
benefit, since a user who needs an authenticated upstream proxy can run a local, unauthenticated
listener in front of it and point this setting at that instead. This closes the brief's leak-risk
item 4 as **moot for v0.1**, not dropped: there is no credential field to leak or store until this
decision is revisited, which — like any accepted decision — the maintainer may do at any time, at
which point the storage-at-rest question becomes live again and needs its own ADR-008-scoped answer.

### The blocking verification this decision carries

**DNS resolution must not happen outside the configured proxy.** With SOCKS proxying, Java/Android
HTTP clients can resolve the destination hostname locally by default — handing the hostname to the
local resolver, and transitively to whoever the ISP or local network operator is, even though the
connection itself is subsequently proxied. If that happens, the relay *appears* to work — the
connection travels through the proxy — while the one thing the user configured it to hide, which
host they are contacting, has already leaked before the proxy hop begins. The proposal brief marked
this behaviour as unverified for this app's exact HTTP client and Android version. **This ADR does
not settle it. It is not a footnote to this decision — it is a blocking requirement on the
implementation that follows it:**

- **The relay must not be described as working — in `docs/privacy.md`, in any other document, or in
  the app's UI — until an instrumented test demonstrates that no DNS query for a request's host
  leaves the device outside the configured proxy.** Until that test exists and passes, any claim
  that the relay hides the user's destination from their local network is unverified, not delivered.
- **This property does not reduce to one in-process test.** OkHttp's own client-level behaviour
  (does its own `Dns` seam ever get asked for the destination host; is the SOCKS route's socket
  address left unresolved) is real and testable in-process — tracked as
  `docs/specs/001-navigation-mvp.md` test 125 — but it is **necessary, not sufficient**: the actual
  name-resolution risk lives in the platform's own socket/SOCKS implementation, below any seam
  OkHttp exposes, which no in-process fake can observe or substitute. **The instrumented test that
  is the prerequisite of the relay implementation, not a follow-up task**, is
  `docs/specs/001-navigation-mvp.md` test 127 — it must exist and pass, on a real or emulated
  device, before the relay transport is considered done. Test 125 passing must never be read as
  satisfying this requirement on its own.
- **Also required, with the same standing**: a test that no pooled/keep-alive connection established
  under a prior relay setting is reused after the setting changes (test 126, since OkHttp's
  connection pool is keyed by address including the proxy, but this must be verified for the actual
  client, not assumed from general knowledge of the library — via an app-owned collaborator the
  chokepoint injects, since OkHttp's own `ConnectionPool` is a `final` type this project's
  no-mocking-library policy cannot substitute). The fail-closed and unset-blocks-egress behaviours
  restated above already have tests (57, 58) and are not reopened by this caveat.
- **If verification shows local DNS resolution cannot be prevented with this app's chosen stack
  (OkHttp on Android), that is an escalation to the maintainer, not something to route around — and
  it reopens this decision.** A relay transport that cannot be made to avoid a DNS leak is not the
  mechanism this ADR describes.

**Confidence: medium-high on Option A as the v0.1 baseline** (a well-understood platform mechanism,
low implementation risk), **medium on deferring Option B** (depends on the NetCipher-or-equivalent
maintenance check this ADR does not resolve), **high on excluding Option C** (the blocking facts —
no stable Arti bindings, no official Kotlin support — are directly verified, not inferred) —
matching the brief's own stated confidence levels for each. **Not yet confident, and explicitly not
claimed above**: whether the DNS-leak risk can actually be prevented with this stack: that is
exactly what the blocking verification exists to establish, not something this ADR's confidence
level covers.

## Consequences

- **Easy**: adding a fourth relay mode later, or Option B's Orbot UX, is additive to the same
  settings surface and the same chokepoint configuration point — no `domain`/`presentation` change,
  because the whole relay setting is expressed as a `domain`-owned `RelayConfiguration` type.
- **Hard**: the DNS-leak verification above must be completed before the relay may be described as
  working at all; if the stack cannot be made to avoid the leak, this decision itself is reopened —
  a materially larger cost than "revisit later," stated plainly rather than minimised.
- **Must be abstracted now to stay reversible**: `RelayConfiguration` stays a `domain`-owned type
  with a mode enum and mode-specific fields, so adding Option B's Orbot-detection logic, or
  reconsidering Option C, later is a `data`-layer-only change behind the same interface.

## What was needed from the human

The maintainer's explicit delegation of this decision to the orchestrator (D18), on top of their own
prior direct decisions on fail-closed behaviour and explicit-choice-before-first-request, which this
ADR restates but does not reopen. No hosting, keys, or infrastructure was needed — this is a
client-side transport decision only.

## Reversibility

High, conditional on the chokepoint invariant holding
(`docs/architecture/README.md` §4, `docs/adr/006-http-and-serialisation.md`): because every option
sits entirely behind the single networking chokepoint and is expressed as a `domain`-owned
`RelayConfiguration`, moving from Option A to Option B, or later reconsidering Option C, changes only
the chokepoint's internal client-construction code — no provider implementation, no use case, and no
presentation code references the relay mechanism directly. The one cost that is **not** reversible
in this cheap sense is the outcome of the DNS-leak verification: if it forces a reopening of this
decision, the replacement mechanism's own leak-risk profile must be re-verified from scratch, not
assumed equivalent to this one's.
