# 006. HTTP client and serialisation

## Status

`superseded by 006-http-and-serialisation.md` (accepted, decision D17) — this brief's analysis led
directly to that decision; it is kept here as the record of the analysis, per
`docs/adr/README.md`'s lifecycle, and is no longer itself open. Do not treat anything below as
inviting a re-open.

## Context

Every outbound flow in the app — traffic (005), tiles (002), routing (003), geocoding (004), and
whatever proposal 007 designs for the user-selectable relay — ultimately goes through one HTTP
client and one serialisation approach. This decision is what makes CLAUDE.md §5.1's single-
networking-chokepoint invariant enforceable in practice: if every remote data source is constructed
with the same injected client, the relay/proxy can be wired in one place; if libraries are left to
make their own independent HTTP calls (as flagged as an open risk for MapLibre Native in proposal
002), the chokepoint is only as strong as its weakest, unaudited exception.

## Decision drivers

- **Ability to enforce the relay/proxy chokepoint** — the dominant driver for this specific
  decision, since that is precisely this component's job.
- No Google Play Services or transitive GMS dependency.
- No reflection at runtime — CLAUDE.md's general resource-discipline stance and the desire to keep
  the serialisation path predictable, testable, and free of the class-invariant-bypassing failure
  mode reflection-based deserialization is known to have with Kotlin data classes (constructors can
  be bypassed, defaults and non-null invariants silently skipped).
- Binary size.
- Maintenance and community health (verified where checked).
- GPL-3.0-compatible licence.

## Options

| Layer | Option | Verdict |
|---|---|---|
| HTTP client | OkHttp (direct) | Real candidate |
| HTTP client | Ktor Client (OkHttp or CIO engine) | Real candidate |
| HTTP client | Plain `java.net.HttpURLConnection` (no library) | Real candidate, weak fit |
| Serialisation | kotlinx.serialization | Real candidate |
| Serialisation | Moshi | Real candidate |
| Serialisation | Gson | Real candidate, weak fit |

### OkHttp (direct)

- Pros: a single `OkHttpClient` instance, constructor-injected into every `data`-layer remote source
  (SOLID dependency inversion), is the simplest possible chokepoint: the proxy/relay is configured
  once (via `Proxy`/`Dns`/interceptors) on that one instance and every caller automatically inherits
  it; mature, widely used across the FOSS Android ecosystem; connection pooling, HTTP/2, transparent
  compression, response caching all built in rather than hand-rolled.
- Cons: none material found against the other two chokepoint-relevant candidates other than lacking
  Ktor's higher-level DSL (not a cost for this project, since the domain layer should not depend on
  either library's types directly regardless).
- Privacy impact: neutral — it is exactly as private as however it is configured; the whole point of
  this decision is that configuration happens once.
- Resource impact: **verified** — requires Android 5.0+ (API 21+); uses AndroidX Startup for
  initialization (not a GMS component); an optional integration with Google Play Services'
  `ProviderInstaller` is documented only as a *recommendation* for improving TLS connectivity on
  very old devices — it is opt-in, not a forced dependency, so it does not violate the zero-GMS
  constraint by default.
- Licence: commonly documented as Apache-2.0 (Square); **not returned verbatim by context7 this
  session** — treat as unverified pending direct confirmation, though residual risk is low given
  its long-standing, widely-relied-upon status across the Android ecosystem including other
  F-Droid-distributed apps.
- Google Play Services dependency: **verified absent by default** (see above).
- Maintenance status: actively maintained (current version referenced directly in its own docs).
- What it forecloses: nothing — a thin `HttpGateway` abstraction in `data` keeps OkHttp's own types
  out of `domain`, so this is not a one-way door.

### Ktor Client (OkHttp or CIO engine)

- Pros: **verified** Apache-2.0 licence (JetBrains); Kotlin-first API with coroutine-native
  suspending calls; **verified** engine table shows an `OkHttp` engine (Android 5.0+) and a `CIO`
  engine (Android 7.0+, needs Java 8 API desugaring on older versions) — meaning choosing Ktor does
  not actually avoid depending on OkHttp if that engine is used, it wraps it; content-negotiation
  and logging plugins are convenient if the app grows many endpoints with varied needs.
  Achieves the same chokepoint property as OkHttp direct, since the underlying engine's client can
  still be configured once and shared.
- Cons: adds a second abstraction layer (Ktor's own client DSL, plugin pipeline) on top of whichever
  engine actually performs the request — for an app with a small, fixed number of remote sources
  (four to five, per the confirmed data flows), this is engineering surface without a
  corresponding benefit; the logging plugin specifically must be configured carefully to avoid
  violating CLAUDE.md §5's "no PII in logs, ever" if enabled at all, an extra place to get privacy
  configuration wrong that a bare OkHttp setup does not introduce.
- Privacy impact: neutral, same reasoning as OkHttp — entirely a function of configuration.
- Resource impact: marginally larger dependency surface (Ktor core + chosen engine + content-
  negotiation + serialization integration modules) than OkHttp alone; exact APK size delta not
  measured this session.
- Google Play Services dependency: none inherent; inherits whatever its underlying engine's status
  is (verified none for the OkHttp engine, as above).
- Maintenance status: **verified** actively maintained by JetBrains.
- What it forecloses: nothing architecturally, same reasoning as OkHttp.

### Plain `java.net.HttpURLConnection`

- Pros: zero added dependency.
- Cons: proxy/relay support, connection pooling, timeouts, retries, and TLS configuration must all
  be hand-rolled and kept correct over time — every one of those is a place a chokepoint or a
  privacy guarantee could silently regress with no library maintainer catching it; strictly more
  code to write and review than either library option above for the same guarantees.
- Verdict: weak fit — the "no added dependency" benefit does not outweigh re-implementing
  security- and privacy-relevant behaviour (proxy handling, TLS, timeouts) that OkHttp already
  provides, tested, by default.

### kotlinx.serialization

- Pros: **verified** — "Kotlin multiplatform / multi-format reflectionless serialization... operates
  without reflection... utilizes a compiler plugin to generate visitor code," and explicitly:
  "Kotlin Serialization does not use reflection, so you cannot accidentally deserialize a class
  which was not supposed to be serializable" — directly satisfies the "no reflection at runtime"
  driver by design, not as a configuration option; **verified** Apache-2.0 licence; first-party
  JetBrains/Kotlin-foundation project, natural fit alongside Kotlin-first code.
- Cons: requires the `@Serializable` annotation and the Kotlin serialization compiler plugin in the
  build — a small, one-time build-configuration cost, not a runtime one.
- Licence: **verified** Apache-2.0.
- Google Play Services dependency: none.
- Maintenance status: **verified** actively maintained, current Kotlin-version-aligned releases.
- What it forecloses: nothing found.

### Moshi

- Pros: reflection-free when used with its codegen annotation processor (`moshi-kotlin-codegen`);
  mature, from the same maintainers as OkHttp, integrates cleanly with it.
- Cons: Moshi's other mode (`moshi-kotlin`, reflection-based adapter) does use `kotlin-reflect`,
  which adds both size and exactly the reflection-based risk this decision is trying to avoid — the
  codegen mode must be deliberately chosen and enforced (e.g. by lint/detekt or build config) so a
  future contributor does not accidentally pull in the reflective variant.
- Licence: commonly documented Apache-2.0 (Square); **not independently confirmed via context7 this
  session** — unverified.
- Google Play Services dependency: none.
- Maintenance status: actively maintained, not independently re-verified this session.
- What it forecloses: nothing, provided the codegen-only mode is enforced.

### Gson

- Pros: extremely widely used, simple API, no compiler plugin or annotation-processor setup needed.
- Cons: **reflection-based by design, with no codegen alternative** — directly fails the "no
  reflection at runtime" driver, not as a configurable option but as its fundamental architecture;
  additionally well known for constructing objects via reflection in a way that can bypass Kotlin
  data class constructors, silently skipping non-null/default-value invariants the class was
  written to guarantee — a correctness risk on top of the reflection cost itself.
- Verdict: weak fit given this project's explicit "no reflection at runtime" driver; listed to show
  it was considered and ruled out on its merits, not omitted.

## Recommendation

OkHttp (direct) for the HTTP client, kotlinx.serialization for serialisation: a single injected
`OkHttpClient` (configured with the relay-capable proxy from proposal 007) behind a thin
`HttpGateway` abstraction, constructor-injected into every `data`-layer remote source, paired with
`@Serializable` domain/data models. Cost: none material relative to Ktor for an app with this small
and fixed a set of remote endpoints — the honest trade-off is forgoing Ktor's higher-level DSL and
coroutine-native ergonomics, which OkHttp's own coroutine-friendly call adapters cover adequately for
this project's scale. Confidence: high — both licence facts (Apache-2.0 for Ktor and
kotlinx.serialization) and the reflection-free property of kotlinx.serialization are directly
verified via context7 this session; OkHttp's licence specifically is treated as unverified pending
direct confirmation, though the risk of it being anything other than Apache-2.0 is low given its
long, uncontested status in the Android ecosystem.

## Consequences

- Easy: enforcing the chokepoint (one `OkHttpClient`, one place to wire the relay); adding new
  remote data sources without adding new HTTP-stack decisions; keeping serialisation
  reflection-free by construction rather than by discipline.
- Hard: nothing specific identified — this is the lowest-risk decision of the six in this batch,
  since both verified facts (reflection-free, no forced GMS) directly satisfy the stated drivers
  with no material trade-off against the alternatives evaluated.
- Must be abstracted now to stay reversible: `domain` must depend only on the project's own
  `HttpGateway`/repository interfaces, never on `okhttp3.*` or `kotlinx.serialization.*` types
  directly, so a later move to Ktor (e.g. if multiplatform ever became relevant — explicitly not a
  driver here, since CLAUDE.md §0.1 commits to a single platform) would be a `data`-layer-only
  change.

## What is needed from the human

Confirm OkHttp + kotlinx.serialization, or request that OkHttp's licence be independently confirmed
before sign-off (a five-minute check against the project's own `LICENSE` file, not a blocker to
proceeding in practice). No hosting, keys, or policy call is needed for this decision.

## Reversibility

Highest reversibility of the six proposals in this batch: since neither library appears in `domain`
or influences data shape beyond what `@Serializable` annotations already require, replacing either
later is a `data`-layer change with no user-visible behaviour change and no privacy re-review needed
— provided the `HttpGateway` abstraction above is respected from the first line of code that makes
a network call.
