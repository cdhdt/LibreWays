# 006. HTTP client and serialisation

## Status

`accepted`

Date: `2026-07-26` — Decided by: the maintainer, **by explicit delegation to the orchestrator**
(decision D17), the same delegation pattern already used for
[`012-build-and-test-tooling.md`](012-build-and-test-tooling.md). The maintainer did not evaluate
each candidate library personally; they authorised the orchestrator to decide this specific,
bounded pair of library choices so implementation is not blocked waiting on every other open
`CLAUDE.md` §0.2 decision. This ADR is otherwise binding exactly like any other accepted ADR and
**the maintainer may amend it at any time**, the same as any other accepted ADR — delegation is not
a lesser form of acceptance, and it does not make this record provisional.

## Context

[`proposals/006-http-and-serialisation.md`](proposals/006-http-and-serialisation.md) is the analysis
brief this ADR resolves — read it first for the full options analysis (OkHttp, Ktor Client, plain
`HttpURLConnection`; kotlinx.serialization, Moshi, Gson) and their trade-offs. This document does
not repeat that analysis; it records the decision, the version/licence facts verified directly for
this ADR, and the consequences.

This decision is what makes `CLAUDE.md` §5.1's single-networking-chokepoint invariant
(`docs/architecture/README.md` §4) enforceable rather than aspirational: every remote data source in
this app — traffic (`docs/adr/005-traffic-source-integration.md`), routing
(`docs/adr/003-routing-engine.md`), and whatever geocoding/tiles ADRs 002/004 eventually settle — is
a client of one HTTP stack, injected once, not free to open its own connection.

## Decision drivers

Unchanged from the proposal brief: ability to enforce the relay/proxy chokepoint (the dominant
driver for this specific decision); no Google Play Services or transitive GMS dependency; no
runtime reflection; binary size; maintenance and community health (verified, not assumed);
GPL-3.0-compatible licence.

## Options

The brief's options stand; the recommendation is confirmed as the choice.

| Layer | Option | Verdict here |
|---|---|---|
| HTTP client | **OkHttp (direct)** | **Chosen** |
| HTTP client | Ktor Client (OkHttp/CIO engine) | Not chosen — wraps OkHttp on Android for no benefit at this app's scale, and its multiplatform value does not apply while `CLAUDE.md` §0.1 fixes one platform |
| HTTP client | Plain `java.net.HttpURLConnection` | Not chosen — re-implements proxy/TLS/timeout handling OkHttp already provides, tested |
| Serialisation | **kotlinx.serialization** | **Chosen** |
| Serialisation | Moshi | Not chosen — viable only in its codegen mode, an ongoing discipline to enforce rather than a property of the library itself |
| Serialisation | Gson | Not chosen — reflection-based by construction, with a known history of bypassing Kotlin data-class invariants |

## Decision

**OkHttp for the HTTP client, kotlinx.serialization for JSON serialisation.** A single,
constructor-injected `OkHttpClient` instance behind a thin `HttpGateway` abstraction in `data`,
configured once with the relay-capable proxy settled by decision D18
(`docs/adr/007-relay-and-proxy.md`); `@Serializable` domain/data models decoded by a shared JSON
instance configured with `ignoreUnknownKeys = true`.

**Versions and licences, verified directly against upstream sources for this ADR — neither is
carried forward from the proposal brief, which left both open pending confirmation:**

| Library | Verified current stable version | Verified licence |
|---|---|---|
| OkHttp (`com.squareup.okhttp3:okhttp`) | **5.4.0**, released 2026-06-08 — confirmed against `square/okhttp`'s own `CHANGELOG.md` and `README.md` | **Apache License, Version 2.0** — confirmed against `square/okhttp`'s own `LICENSE.txt`; the brief had left this specific fact unverified |
| kotlinx.serialization (`org.jetbrains.kotlinx:kotlinx-serialization-json`) | **1.11.0**, released 2026-04-10 — confirmed against `Kotlin/kotlinx.serialization`'s own `CHANGELOG.md` | **Apache License, Version 2.0** — confirmed against `Kotlin/kotlinx.serialization`'s own `LICENSE.txt`, matching the brief's own already-verified claim |

Concrete version pinning happens in `libs.versions.toml` once the build exists
(`docs/adr/012-build-and-test-tooling.md`); the table above is what to pin at that time, not a
substitute for the version catalog, and it will need re-checking if time passes before the build is
actually created.

**Rejected, as the brief already analysed and this ADR does not re-litigate**: Ktor Client, plain
`HttpURLConnection`, Moshi, Gson — see Options above and the brief's own per-option detail for the
full reasoning.

## Requirements this implies, to be specified and tested

- **Exactly one `OkHttpClient` instance for the whole app**, constructor-injected into every
  `data`-layer provider adapter; no adapter constructs its own client. A test must fail if any
  adapter bypasses the injected instance (`docs/specs/001-navigation-mvp.md` test 123).
- **Unknown JSON fields are ignored, not fatal** (`ignoreUnknownKeys = true`), consistent with
  `docs/adr/005-traffic-source-integration.md`'s finding that Waze's own documentation disagrees
  with itself on field names and casing; a malformed or structurally unexpected payload still fails
  cleanly and visibly as a typed provider error, never corrupting domain state or crashing
  (`docs/specs/001-navigation-mvp.md` test 124).
- `domain` depends on neither `okhttp3.*` nor `kotlinx.serialization.*` directly — only on the
  project's own `HttpGateway`/repository interfaces, unchanged from the brief's own reversibility
  requirement.

## Consequences

- **Easy**: enforcing the chokepoint (one `OkHttpClient`, one place to wire the relay); adding new
  remote data sources without adding new HTTP-stack decisions; keeping serialisation
  reflection-free by construction, not by discipline.
- **Hard**: nothing specific identified — this remains, as the brief already assessed, the
  lowest-risk decision of this batch: both verified facts driving the choice (reflection-free, no
  forced GMS dependency) directly satisfy the stated drivers with no material trade-off against the
  alternatives evaluated.
- **Must be abstracted now to stay reversible**: `domain` must depend only on the project's own
  `HttpGateway`/repository interfaces, never on `okhttp3.*` or `kotlinx.serialization.*` types
  directly, so a later stack change would be a `data`-layer-only change.

## What was needed from the human

The maintainer's explicit delegation of this decision to the orchestrator (D17), the same
delegation pattern already used for ADR 012. No library evaluation, hosting, keys, or policy call
was needed from the maintainer directly.

## Reversibility

Highest reversibility of the decisions in this batch, unchanged from the brief's own assessment:
since neither library appears in `domain` or shapes data beyond what `@Serializable` annotations
require, replacing either later is a `data`-layer change with no user-visible behaviour change and
no privacy re-review needed — provided the `HttpGateway` abstraction is respected from the first
line of code that makes a network call.
