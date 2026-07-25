# 007 — Relay and proxy implementation

## Status

`proposed`

Nothing in this document is authority until the human accepts it and it is recorded as an accepted
ADR per `CLAUDE.md` §0.2. **Two sub-questions this document originally left open — fail-closed vs.
fail-open, and whether an explicit relay choice is required before the first outbound call — have
since been decided by the maintainer** (see [Fail-closed vs. fail-open](#fail-closed-vs-fail-open-when-the-relay-is-unreachable--decided)
and leak-risk item 5 below). **The transport mechanism itself (Option A/B/C) remains open.**

## Context

`CLAUDE.md` §5.1 states plainly: *"A user-selectable relay is a first-class requirement, not an
add-on ... A networking design that makes this retrofit-only is not acceptable."* This is not a
v0.2+ nice-to-have — the v0.1 "A to B" milestone already has three outbound flows that need it on
day one (`docs/architecture/data-flow.md` §1.2 steps 1, 3b, 5: geocoding, traffic incidents, map
tiles), and a fourth conditionally (routing, step 3, if the routing-engine ADR settles on a remote
shape). `docs/specs/001-navigation-mvp.md` FR-8/FR-9 already assume this setting exists and applies
uniformly; this document decides only **what the relay setting is built from**, not whether it
exists.

This decision is scoped narrowly to **the transport mechanism(s) the networking chokepoint's relay
setting supports** — the fail-closed default and the explicit-choice-before-first-request rule are
already decided (see below) and are recorded here, not re-opened. It is not the HTTP client choice
itself
(that is a separate ADR proposal, referenced but not decided here) — this document assumes
whichever HTTP client is chosen exposes a way to route through an upstream proxy, which every
mainstream JVM/Android HTTP client does.

**What "self-hosted endpoint" actually means here, stated once to avoid a false fourth option.**
A "user-supplied self-hosted instance" is not a distinct transport mechanism — from the app's
point of view it is an HTTP or SOCKS proxy (or, for routing/traffic, potentially a full alternate
backend URL) that happens to be operated by the user instead of a public relay operator. The
underlying code path is identical to "generic configurable proxy" below. It is called out
separately in `CLAUDE.md` because of its different **trust model** (an operator the user chose and
presumably controls, vs. an anonymity network or a third-party proxy operator) — a UX/labelling
distinction, not an architecture one. This document treats it as always covered by whichever proxy
option is chosen, and flags where it needs its own UI affordance.

**Dependency on the networking chokepoint.** This decision only becomes enforceable because of the
single-networking-chokepoint invariant in `docs/architecture/README.md` §4: *"Every outbound network
request the app makes passes through exactly one path... this is what makes 'the relay applies to
everything' a structural fact instead of a per-provider promise."* Whatever is decided here is
implemented **once**, inside that chokepoint component, not once per provider. Section
[Enforceability](#enforceability-via-the-networking-chokepoint) below explains exactly how that
invariant is what makes this reviewable at all.

## Decision drivers

Scored qualitatively against the locked constraints (`CLAUDE.md` §0.1, §5.1); detailed per-option
scoring is in the options table below.

| Driver | Why it matters here specifically |
|---|---|
| Privacy | The entire point of this feature — does the mechanism actually decouple the user's IP from the destination provider, and does it avoid introducing a new leak (DNS, fingerprint, correlation) while doing so? |
| Works fully without Play Services | None of the candidates below depend on GMS, but a companion-app dependency (Orbot) introduces a *different* external dependency the app must degrade gracefully without |
| Resource cost (battery, memory, APK size) | Embedding a Tor client changes the APK's size and CPU/battery profile qualitatively differently from delegating to a proxy setting |
| GPL-3.0-compatible licence | Every candidate library touched must be checked, not assumed |
| F-Droid-compatible (reproducible, no blob) | An embedded native (Rust/C) Tor binary is a materially harder reproducible-build target than pure-Kotlin proxy configuration code |
| Maintenance and community health | A relay feature that silently rots because its helper library is abandoned is worse than no dedicated helper at all |
| Development speed | v0.1 needs this from day one; an option that takes materially longer delays the whole milestone |
| Testability | Can the leak risks in [Leak risks](#leak-risks-to-check) be verified by an automated test, not just code review? |
| Reversibility | Can the transport be swapped later without touching `domain`/`presentation` at all? (Should be yes for all options, given the chokepoint invariant — verified per option below.) |

## Options

| Option | Mechanism | Companion app needed | Licence (verified) | GMS dependency | Maintenance (verified) | Dev effort |
|---|---|---|---|---|---|---|
| A — Generic configurable proxy (HTTP + SOCKS5) | User enters host, port, optional credentials; `java.net.Proxy`/OkHttp `proxy()` | No | N/A (platform API + chosen HTTP client's own licence) | None | N/A (platform API) | Low |
| B — Option A + dedicated Orbot integration | Same transport as A (SOCKS5 to Orbot's local port), plus detect/prompt-install/launch UX via Guardian Project tooling | Yes (Orbot) | Orbot: 3-clause BSD (verified). Helper library: BSD-family, historically NetCipher (verify current maintenance before adopting, see below) | None | Orbot: active, F-Droid-distributed via Guardian Project's own repo, v17.8.0-RC-3 seen Jan 2026 (verified). Helper library: **unverified**, evidence of recent activity was inconclusive in research for this brief | Medium |
| C — Embedded Tor client (no companion app) | Tor compiled/linked into the app itself (Arti, Rust) via JNI, used as an in-process SOCKS proxy | No | Arti: BSD/MIT-family per Tor Project's usual licensing (**unverified for the exact Arti crates in use** — check per-crate before adopting) | None | Arti reached 1.0.0 (verified); **no stable Rust API and no official Kotlin/JNI bindings exist yet** (verified) — integration means writing and maintaining our own bindings | High |

Self-hosted endpoint is not a fourth row: it is Option A pointed at a user-controlled address, with
a distinct label in the settings UI (see [Setting exposure](#setting-exposure-and-validation)).

### Option A — Generic configurable proxy (HTTP and/or SOCKS5)

The networking chokepoint exposes a setting: proxy type (none / HTTP / SOCKS5), host, port,
optional username/password. The chokepoint's HTTP client is configured via its native proxy
support — e.g. OkHttp's `OkHttpClient.Builder.proxy(Proxy)` (verified via context7: this API exists
in the current OkHttp docs and takes precedence over `proxySelector`, with `Proxy.NO_PROXY`
available to disable it explicitly) — using the standard `java.net.Proxy` with `Proxy.Type.HTTP` or
`Proxy.Type.SOCKS`.

- **Pros**: one mechanism covers every case the product brief names — a generic HTTP/SOCKS proxy,
  Orbot used as a plain SOCKS5 proxy at its default local port, a self-hosted relay the user
  deploys, a corporate/VPN proxy. Smallest code surface: no dependency beyond the HTTP client
  already chosen. Fully reversible — swapping to Option B or C later only changes what populates
  this same setting internally, or adds a second mode alongside it.
- **Cons**: no dedicated UX for detecting/launching Orbot — the user must know Orbot's local port
  and enter it manually (or the app documents the default and pre-fills it, which is a cheap
  half-measure, not full integration). No install-Orbot-if-missing prompt. Slightly worse
  first-run experience for a non-technical Tor user.
- **Privacy impact**: correct, if implemented without the leak risks in the next section. Identical
  privacy ceiling to Option B for the Tor case, since both ultimately hand traffic to the same
  Orbot SOCKS port — the difference is UX polish, not privacy strength.
- **Resource impact**: negligible — no new process, no new native code, uses whatever HTTP client
  is already loaded.
- **Licence**: whatever the chosen HTTP client's licence already is (out of scope of this
  decision).
- **GMS dependency**: none (verified — `java.net.Proxy` is a core JDK/Android platform type).
- **Maintenance status**: not applicable — no third-party relay-specific dependency at all.
- **What it forecloses**: nothing. It is the substrate every other option is built on top of.

### Option B — Option A plus dedicated Orbot integration

Adds a Guardian Project helper (historically **NetCipher**, which ships an `OrbotHelper` utility to
detect whether Orbot is installed, prompt installation, and start it, plus `StrongBuilder`-style
factories for OkHttp/HttpURLConnection/Volley/Apache — verified via web research) so "Tor" appears
as its own first-class relay mode in the UI, not a manually-configured generic proxy that happens to
point at Orbot.

- **Pros**: materially better UX for the security-conscious, non-expert user this app targets —
  "Tor" as a labelled toggle, automatic detection of whether Orbot is running, an install prompt if
  it is not, and (per the researched NetCipher README) automatic SOCKS-vs-HTTP-port selection.
  Since Android 7.0 (API 24), Android's OpenJDK base has working native SOCKS support, so the
  proxying itself needs no special native code even with this integration (verified).
- **Cons**: adds a compile-time dependency whose current maintenance state this research could
  **not verify with confidence** — web search returned the NetCipher repository and its documented
  feature set but no clear signal on recent commit activity or release cadence; several
  community forks exist (`scalio/NetCipher`, `acomminos/OnionKit`), which is itself a signal the
  original may be under-maintained and forks have diverged to fill the gap. **This must be checked
  directly against the repository's commit history before adoption** — do not take this brief's
  research as sufficient verification on its own. If the helper library is effectively unmaintained,
  Option A plus a thin, in-house `OrbotHelper`-equivalent (checking for the Orbot package, sending
  its documented start `Intent`, and defaulting to its documented SOCKS port) is a low-effort
  fallback that keeps the UX win without taking on an unmaintained dependency.
- **Privacy impact**: identical ceiling to Option A for the Tor path (same SOCKS hop to the same
  Orbot process). The improvement is in default correctness (a user is less likely to mistype
  Orbot's port, or to think Tor is active when Orbot is not actually running) — a usability
  mitigation against user error, not a new technical privacy property.
- **Resource impact**: negligible beyond Option A — the helper does not run Tor itself, it talks to
  the already-running Orbot process.
- **Licence**: BSD-family per Guardian Project's usual licensing (verified for Orbot itself; the
  helper library's exact current licence should be re-confirmed at the specific version pinned,
  since forks may have changed it).
- **GMS dependency**: none (verified — Orbot and NetCipher are Guardian Project FOSS projects with
  no GMS involvement).
- **Maintenance status**: Orbot itself is actively maintained and F-Droid-distributed (verified,
  version 17.8.0-RC-3 seen as of January 2026, via Guardian Project's own F-Droid repository, which
  a user must add separately from the main F-Droid repo — a first-run UX detail worth documenting).
  The helper library's maintenance is **unverified** — flagged above as a required pre-adoption
  check, not a blocker to the option itself, since a thin in-house replacement is available if the
  library proves stale.
- **What it forecloses**: nothing structurally — it is additive UX on top of Option A's transport
  and remains fully behind the chokepoint. If the helper dependency turns out to be a liability, it
  can be dropped back to Option A without touching `domain` or `presentation`.

### Option C — Embedded Tor client (Arti), no companion app

Link Tor directly into the app (the Rust reimplementation, Arti, rather than the legacy C `tor`
daemon) via JNI, running an in-process SOCKS listener the chokepoint connects to, with no external
app required.

- **Pros**: removes the Orbot install dependency entirely — Tor "just works" without asking the
  user to install and run a second app. Best floor-experience for a user who wants Tor without
  knowing what Orbot is.
- **Cons**: substantial engineering cost. Verified: Arti reached v1.0.0 and is considered
  production-suitable by the Tor Project, **but has no stable Rust API and no official
  Kotlin/JNI bindings** — "you'll need to write these bindings yourself using the Java Native
  Interface" (Guardian Project's own 2023 writeup, corroborated by the Arti Android build docs).
  This is a standing maintenance burden for the LibreWays team specifically, not a one-time cost:
  every Arti upgrade risks re-breaking a hand-rolled binding layer with no upstream API stability
  guarantee yet. F-Droid reproducibility is materially harder: a native Rust `.so` per ABI must be
  built reproducibly by F-Droid's build farm from source, which is a heavier lift than pure-Kotlin
  build output and something F-Droid's own documentation flags as a known hard case for apps
  bundling native/cross-compiled code. APK size grows by the embedded binary's footprint across
  each supported ABI.
- **Privacy impact**: same destination-facing privacy ceiling as routing through Orbot (both are
  Tor), with one difference worth naming: Orbot is a separate, independently-auditable,
  widely-deployed process; an embedded, hand-bound Arti integration is new, LibreWays-specific
  code with no comparable track record, and any binding bug (e.g. mishandled DNS, a stream that
  bypasses the SOCKS listener) is a leak this project alone is responsible for catching, without
  the benefit of Orbot's own broader user base finding such bugs first.
- **Resource impact**: highest of the three options — a running Tor client's own CPU/memory/battery
  cost is now attributed to LibreWays' own process and background-execution budget, not an
  independent app the user separately chose to run (and can separately stop).
  the app must now also manage Tor's own lifecycle (bootstrap time, circuit building, keeping it
  alive appropriately without becoming an unjustified background service).
- **Licence**: Tor Project code is typically BSD/MIT-family; the exact licence of whichever Arti
  crates are vendored must be checked crate-by-crate before adoption — **not fully verified in this
  brief**, since research could not confirm the current licence text for every dependency the
  Android integration would pull in.
- **GMS dependency**: none.
- **Maintenance status**: Arti itself is active and has reached 1.0.0 (verified). Its Android
  integration path is explicitly described by its own maintainers as requiring custom work, which
  is itself the risk being scored here, independent of Arti's own health.
- **What it forecloses**: effectively locks the team into maintaining a native-binding layer
  indefinitely; reversing this decision later (moving to Option A/B) is cheap architecturally (the
  chokepoint interface does not change) but throws away whatever binding-maintenance investment was
  sunk into this option.

## Fail-closed vs. fail-open when the relay is unreachable — decided

**This sub-question is now decided by the maintainer: fail-closed.** When a configured relay is
unreachable, the outbound request fails and the user is told explicitly; the app never silently
falls back to a direct, unrelayed connection. The table below is kept as the record of the
reasoning that led to this decision, not as an open comparison.

| | Fail-closed (block the request) — **decided behaviour** | Fail-open (fall back to direct connection) — **not adopted** |
|---|---|---|
| What happens when the configured relay is unreachable | The outbound call does not happen; the user sees an explicit `RelayUnreachable` error, matching the fail-closed requirement now specified in `docs/specs/001-navigation-mvp.md` FR-25 (decisions D1/D6) and the general per-provider degradation pattern in `docs/architecture/README.md` §6 | The request proceeds directly to the provider, unrelayed, silently or with a warning |
| Privacy consequence | None beyond "the feature does not work right now" — no request the user didn't expect ever leaves unrelayed | The exact leak the user configured a relay to prevent happens anyway, at the worst possible moment (when the relay has failed, which is also when the user is least likely to be watching for it) |
| Consistency with existing design | Matches the app's existing pattern: every other provider failure in this app already degrades to an explicit error state, never a silent lower-privacy substitute (`docs/privacy.md` §"What this app cannot promise" item 5 already sets this precedent for location: "must not silently substitute a lower-quality location proxy") | Breaks that precedent specifically for the one setting whose entire purpose is a privacy guarantee |
| User experience cost | The feature stops working until the relay is fixed or disabled — worse availability | The feature keeps working, unrelayed, without necessarily being obvious that it just happened |

**Decided: fail-closed.** A relay a user configured specifically to hide their IP that silently
stops doing so at the exact moment it fails would be a worse outcome than an explicit, temporary
loss of functionality — it would convert a configuration choice into a false sense of protection.
This matches the app's own existing degradation philosophy (explicit failure states everywhere
else) rather than a special-cased silent fallback for the one setting whose entire purpose is a
privacy guarantee. Fail-open is not adopted; a future change away from fail-closed would itself
need a fresh decision and a `docs/privacy.md` update, not a quiet implementation change.

## Leak risks to check

Independent of which option above is chosen, these are the concrete ways a relay setting can look
correct in code review and still leak, in rough order of how easy each is to miss:

1. **DNS resolution happening outside the proxy.** This is a well-documented, industry-wide gotcha
   for Java/Android SOCKS clients, not specific to any option here: if the destination hostname is
   resolved locally (e.g. by constructing a resolved `InetSocketAddress(host, port)` instead of an
   unresolved one before handing it to the SOCKS layer, or by any DNS-performing code running ahead
   of the proxy hop), the destination hostname — and therefore *what* the user is about to
   contact — is exposed to the local network/ISP even though the connection itself is subsequently
   proxied. **This is not independently verified against this project's exact HTTP client and
   Android version in this brief** — it must be confirmed with a live instrumented test (see
   [How it is tested](#how-it-is-tested)) before this is trusted, for whichever HTTP client and
   proxy-configuration API the chokepoint ADR settles on.
2. **A library performing its own HTTP request outside the chokepoint.** Already named as a
   standing invariant in `docs/architecture/README.md` §4: "A map/tile library that manages its own
   internal networking... must be evaluated against this invariant before adoption." The same check
   applies to any other library considered for geocoding, traffic, or routing — a library with a
   built-in HTTP client that cannot be redirected through the chokepoint's proxy configuration is
   unsuitable regardless of its other merits, full stop.
3. **Connection reuse across a relay change.** A pooled/keep-alive HTTP connection established
   before the user changed or disabled the relay setting could still be alive and reused for a
   subsequent request that should now go through the new setting (or through no relay at all) —
   this is a real risk with connection-pooling HTTP clients (e.g. OkHttp's connection pool is keyed
   by `Address`, which includes the proxy, so a genuine setting change should naturally produce a
   new pool key and not reuse an old connection — but this must be verified for the actual client
   chosen, not assumed from general knowledge of the library). The chokepoint's implementation must
   either rebuild its HTTP client instance or explicitly evict its connection pool whenever the
   relay setting changes, and a test must assert no request after a relay-setting change reuses a
   pre-change connection.
4. **Credential handling for an authenticated proxy.** If a proxy requires a username/password,
   that credential must never appear in a log statement (`CLAUDE.md` §5) and its storage-at-rest
   posture is a `docs/adr/proposals/008-local-persistence.md` concern — flagged here as a
   dependency, not resolved in this document. This is distinct from the `RelayConfiguration`
   *mode* itself (which relay is active, including "direct, no relay"), whose persistence
   mechanism is already decided as Jetpack DataStore Preferences, recorded in
   [`docs/adr/014-settings-persistence.md`](../014-settings-persistence.md) — a credential is one
   field a chosen mode might carry, not the mode selection this ADR governs.
5. **First-request-before-configuration — decided.** The maintainer has decided: no outbound
   request may be made before the user has made an explicit relay choice. "Direct, no relay" is a
   first-class, deliberately selected option, not the absence of configuration — `RelayConfiguration`
   must model "unset" as a distinct state from "direct" and that unset state must block egress. A
   first run with no choice yet made performs zero network requests. This closes the question
   `docs/privacy.md` previously logged as open; both documents now agree.

## How it is tested

Per `docs/testing.md` §5, "every outbound request goes through the relay path" is already named as
"the single highest-value test in the suite." Concretely, for whichever option is chosen:

- **Data-layer tests** (fakes/fixtures, no real network, per `docs/testing.md` §2.2 and §4): assert
  that the chokepoint component builds its HTTP client with the currently configured
  `Proxy`/relay setting applied — not that a request "succeeds," but that the client object
  actually carries the expected proxy configuration for each of no-relay / HTTP-proxy / SOCKS-proxy
  / self-hosted-endpoint modes.
- **A dedicated leak-risk instrumented test**, run against a local test harness rather than a real
  relay or a real destination: stand up two local listeners — one acting as "the configured proxy"
  and one acting as "the destination provider would be, if reached directly." With a relay
  configured, assert the proxy listener receives the connection and the direct listener receives
  **nothing**, for every provider call site the chokepoint serves. This is the concrete,
  automatable form of the DNS-leak and bypass risks in the previous section — a static code read
  cannot rule these out with confidence, an executed test can.
- **A fail-closed test**: with the configured relay deliberately made unreachable (point it at a
  closed port), assert the direct listener never receives the request and an explicit error
  propagates to the user — this test must fail if fail-open were shipped by accident, since
  fail-closed is now the decided behaviour, not one of two options to keep open.
- **A first-request-before-configuration test**: with no relay choice ever made, assert zero
  network requests are attempted against either listener — covering the decided "unset blocks
  egress, direct-no-relay is a deliberate choice" behaviour above.
- **A connection-reuse test**: issue a request, change the relay setting, issue a second request,
  and assert (via the test proxy listeners above) that the second request's connection is distinct
  from the first's — catching the pooling risk in item 3 above directly rather than by inspection.
- **Manual verification** (per `docs/privacy.md` "How to verify these claims yourself"): a
  packet-capture tool run against the real Orbot integration on a real device remains the final
  check that Option B's actual runtime behaviour matches what the unit/instrumented tests assert in
  isolation — automated tests reduce, but do not eliminate, the value of this manual pass for the
  Tor-specific option, given Orbot's own process lifecycle is outside this app's direct control.

## Setting exposure and validation

- **UI**: a settings screen exposing, at minimum: no relay (direct) — must be an explicit,
  distinguishable choice per the open question in `docs/privacy.md`, not merely the absence of
  configuration; a generic proxy mode (type: HTTP or SOCKS5, host, port, optional
  username/password); if Option B or C is chosen, a distinct "Tor" mode; and a "custom relay"
  labelling for the self-hosted case, even though it shares Option A's underlying fields, so the
  user understands the trust distinction named in [Context](#context).
- **Validation before saving**: host/port format validation; a lightweight reachability probe
  (attempt a connection through the configured proxy before accepting the setting) so a
  misconfiguration is caught at settings-save time rather than surfacing as a confusing failure the
  next time the user tries to search or route; a validation failure must distinguish
  "unreachable"/"connection refused" from "misconfigured" from, for the Orbot mode specifically,
  "Orbot not installed"/"Orbot not running" — three different states requiring three different user
  actions, per the same "distinct, explicit error state" principle already established for every
  other provider failure in `docs/specs/001-navigation-mvp.md`.
- **No credential logging**: whatever validation/probe logic is added must be covered by the same
  logging-policy tests in `docs/testing.md` §5 ("nothing sensitive is logged") — a proxy credential
  is exactly the kind of value that must never appear in a diagnostic log line, including on
  failure paths, which are precisely where developers are tempted to log "what was attempted."

## Enforceability via the networking chokepoint

This decision is only as good as its enforcement, and the enforcement mechanism already exists by
design, not as something this document adds: `docs/architecture/README.md` §4 fixes that **every**
outbound call passes through one component. Concretely, this means:

- The relay setting is read and applied in exactly one place in the codebase — the chokepoint's
  HTTP-client-construction code. There is no per-provider "does this one also check the relay
  setting?" question to re-ask every time a new provider (geocoding, traffic, tiles, routing) is
  added, because no provider constructs its own client.
- Review enforcement (per `docs/architecture/README.md` §4's stated blocker-finding rule) reduces to
  a single, mechanical check: does any production code construct an HTTP client, socket, or
  connection outside the chokepoint component? If yes, blocker finding, independent of whether that
  code path happens to also apply the relay — the invariant is "there is exactly one path," not
  "every path remembers to apply the relay."
- This is what makes the recommendation in this document **reversible in the cheap sense**: whichever
  option is chosen, swapping it for another later touches only the chokepoint's internals, never
  `domain`, `presentation`, or any individual provider implementation — see
  [Reversibility](#reversibility).

## Recommendation

**Adopt Option A (generic configurable HTTP/SOCKS5 proxy) as the mandatory baseline for v0.1**, since
it is the smallest change that satisfies `CLAUDE.md` §5.1 for every flow in the v0.1 milestone, adds
no new dependency to verify, and already covers the self-hosted-endpoint case. **Layer Option B
(dedicated Orbot integration) on top once the NetCipher-or-equivalent maintenance question is
resolved** — it is additive UX, not a different privacy mechanism, so it need not block v0.1 if the
maintenance check takes longer than the milestone allows; Option A alone already lets a
technically-capable user route through Orbot manually. **Do not pursue Option C (embedded Tor) for
v0.1** given its verified lack of stable bindings and its open F-Droid-reproducibility and licence
questions — revisit only if Arti's Android story matures and a concrete, verified need for
companion-app-free Tor emerges.

**Confidence: medium-high on Option A as the baseline (well-understood platform mechanism, low
risk); medium on deferring Option B (depends on an unverified maintenance fact this brief flags but
does not resolve); high on excluding Option C for now (the blocking facts — no stable bindings, no
official Kotlin support — are directly verified, not inferred).**

**Honest cost**: Option A alone gives a less polished first-run experience for a Tor-preferring user
than Option B would; that gap is the price of not blocking v0.1 on an unverified dependency's
maintenance status.

## Consequences of the recommendation

- **Becomes easy**: adding a fourth or fifth relay mode later (e.g. a different anonymity network,
  or a future protocol) is additive to the same settings surface and the same chokepoint
  configuration point — no `domain`/`presentation` change.
- **Becomes hard / needs attention**: if the human wants day-one Orbot auto-detection UX (Option B)
  rather than a documented manual SOCKS entry, that UX work must be explicitly scheduled rather than
  assumed to come for free with Option A.
- **What must be abstracted to keep it reversible**: the chokepoint's relay configuration must be
  expressed as a `domain`-owned type (`docs/architecture/README.md` already sketches
  `RelayConfiguration` as such in `docs/specs/001-navigation-mvp.md`'s layered decomposition) with a
  mode enum and mode-specific fields, so that adding Option B's Orbot-detection logic or Option C's
  embedded client later is purely a `data`-layer addition behind the same interface, never a
  `domain`/`presentation` change.

## What is needed from the human

Fail-closed and the explicit-choice-before-first-request rule are **already decided** (see the two
sections above) and do not need to be asked again. What remains open:

1. **Confirm or override the option recommendation**: Option A now, Option B deferred pending the
   maintenance check, Option C excluded for now — or a different sequencing.
2. **If Option B is pursued**: someone must actually check NetCipher's (or its most-maintained fork's)
   current commit history and licence file at the exact version to be pinned — this brief could not
   verify that with confidence and explicitly flags it as a pre-adoption task, not a decided fact.

Nothing here requires the human to provide hosting, keys, or infrastructure — this is a
client-side transport decision only.

## Reversibility

**High**, conditional on the chokepoint invariant holding. Because every option here sits entirely
behind the single networking chokepoint (`docs/architecture/README.md` §4) and is expressed as a
`domain`-owned `RelayConfiguration`-shaped interface, switching from Option A to B, or later adding
C, changes only the chokepoint's internal client-construction code — no provider implementation, no
use case, and no presentation code references the relay mechanism directly. The cost of reversing
is therefore bounded to: reworking the chokepoint's proxy-construction logic, updating the settings
UI's mode list, and re-running the leak-risk test suite in [How it is tested](#how-it-is-tested)
against the new mechanism. The one non-reversible cost is Option C's sunk engineering effort in a
hand-rolled JNI binding layer, called out explicitly in that option's "what it forecloses."
