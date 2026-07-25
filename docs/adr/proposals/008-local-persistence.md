# 008 — Local persistence

## Status

`proposed`

Nothing in this document is authority until the human accepts it and it is recorded as an accepted
ADR per `CLAUDE.md` §0.2.

## Context

**Settings persistence is now decided, separately from this document**, and recorded as
[`docs/adr/014-settings-persistence.md`](../014-settings-persistence.md) (decision D2). The
relay/proxy configuration and other simple v0.1 settings are persisted from v0.1 via **Jetpack
DataStore Preferences** — chosen specifically so v0.1 does not wait on this ADR. **Three on-disk
response caches are likewise decided separately, from v0.1**: the tile cache by decision D7, and
the geocoding and traffic response caches by decision D9 (the maintainer's doctrine that the
privacy bar governs what leaves the device, not what a feature keeps locally for its own function
— see `docs/privacy.md` and `docs/specs/001-navigation-mvp.md` FR-27–FR-31 for all three). All
three are file-based stores, not structured databases, and this document's concern with them is
limited to their eviction-policy shape being comparable to the caches discussed below, not to when
they ship or what mechanism stores them. This document's scope is therefore narrowed to **the
structured-database question only**: cached routes and saved places, gated to v0.4. Anything
settings-shaped (key/value preferences, not structured/relational data) and the three file-based
response caches are out of scope here regardless of milestone.

`docs/roadmap.md` names `008-local-persistence` as the sole prerequisite for v0.4+ ("Convenience":
saved places, saved trips, route/tile caching for offline use) — the first milestone with any
persistent, identifying data at rest for the structured-database question this ADR actually
covers (`docs/roadmap.md` v0.4 row: *"the app's first persistent, identifying data at rest"*, a
line that predates decisions D7/D9 and is no longer literally true of the *app* — see below —
though it remains true of the *structured-database* question this document is scoped to). v0.1
itself needs **no** database: `docs/specs/001-navigation-mvp.md` FR-18 fixes that destination
text, search results, and the computed route are never exposed to the user as a persistent history
and are retained, at most, within the bounds of the three response caches above — not that zero
bytes touch disk. This document is therefore not gating v0.1's database question; it is needed
before v0.4 work starts. **None of the three v0.1 response caches is a "may be introduced earlier"
candidate any longer** — decisions D7/D9 ship all three from v0.1, as file-based stores outside
this document's scope (see above), so none of them waits on this ADR at all.

**What must be persisted, by milestone:**

| Milestone | Data | Sensitivity (per `docs/privacy.md`) | Today's status |
|---|---|---|---|
| v0.1 | Relay/proxy configuration; unit/locale preference | Low — reveals privacy posture, not location | **Decided, outside this document's scope**: Jetpack DataStore Preferences, persisted from v0.1 (see `docs/adr/014-settings-persistence.md`) |
| v0.1 | On-disk tile cache (decision D7) | Medium — indirectly reveals viewed areas if the device is examined; not encrypted at rest in v0.1 | **Decided, outside this document's scope**: file-based, size/TTL-bounded LRU store (Option D below), size/TTL figures still open — see `docs/specs/001-navigation-mvp.md` FR-27–FR-31 |
| v0.1 | Short-TTL geocoding/traffic response caches | Same sensitivity as the flow each caches — the geocoding cache is the most sensitive local artefact in the app | **Decided, outside this document's scope** (decision D9): file-based, size/TTL-bounded LRU stores (Option D below), same standard as the tile cache, size/TTL figures still open — see `docs/specs/001-navigation-mvp.md` FR-27–FR-31 |
| v0.4 | Cached routes (session-scoped today; "saved trips" would extend this) | High — describes a specific planned trip | Not yet built — **in this document's scope** |
| v0.4 | Saved places / saved trips | High — "can reveal home, work, and habitual destinations" (`docs/privacy.md`) | Not yet built; feature itself not designed beyond the roadmap line — **in this document's scope** |

This document decides **the storage mechanism for the structured-database question (cached routes,
saved places)**, not the retention policy or feature scope for saved places/trips — those remain
open questions for the v0.4 recon spec when it is written — and not the settings-persistence
mechanism (decision D2/ADR 014) or the file-based tile cache (decision D7), both already decided
outside this document's scope, as above.

**Relationship to `docs/adr/proposals/007-relay-and-proxy.md`**: that document flags proxy
credentials (if an authenticated proxy is configured) as a value needing a storage answer from
this ADR — noted here as a concrete, cross-referenced consumer of whatever encryption-at-rest
mechanism this document recommends.

## Decision drivers

| Driver | Application to this decision |
|---|---|
| Privacy | Anything location-derived this document actually decides on (cached routes, saved places — the v0.1 response caches, including geocoding, are decided separately by D7/D9 and out of this document's scope, see Context) must have a real deletion path, a retention limit, and — per `CLAUDE.md` §5 — never leak into backups, crash traces, or exported files. Encryption at rest is a mitigation for the physical-access adversary named in `docs/threat-model.md` §7, not a substitute for minimisation |
| Works fully without Play Services | Every candidate below must be checked for a Play-Services or Firebase transitive pull — none of the candidates researched carry one, but the check is repeated per option |
| Resource cost | Query performance, memory footprint of the persistence layer itself, and — for any encryption option — the CPU cost of encrypting/decrypting on every read/write, matter directly for a background-adjacent feature like tile caching |
| GPL-3.0-compatible licence | Checked per option below |
| F-Droid-compatible | No proprietary blob; reproducible build; **build-time** dependency on Google's Maven repository (needed to fetch AndroidX artifacts) is a distinct concern from a **runtime** GMS dependency and must not be conflated — clarified below |
| Maintenance and community health | Checked per option, including a load-bearing finding about `androidx.security-crypto`'s deprecation |
| Development speed | Compile-time-verified query APIs (Room, SQLDelight) reduce a whole class of runtime SQL bugs compared to hand-written SQLite access |
| Testability without Android | Whether the persistence layer's logic can be exercised in a JVM unit test (in-memory DB) vs. requiring instrumentation |
| Migration safety | Schema evolution matters starting the moment saved places/trips exist and a user expects their data to survive an app update |
| Reversibility | Can the `domain`-owned cache-port interfaces (`docs/architecture/README.md` §2.1: "`RouteCache`/`PlaceCache` persistence ports") be re-implemented against a different storage engine without touching `domain` or `presentation`? |

## Options

| Option | Type | Compile-time query verification | Encryption at rest | Licence (verified) | GMS dependency (verified) | Maintenance (verified) |
|---|---|---|---|---|---|---|
| A — Room (AndroidX Jetpack) | Typed ORM over SQLite | Yes (KSP/annotation processor) | Not built in — needs a companion mechanism (see below) | Apache 2.0 | None | Active; current version 2.8.4 confirmed via official docs at time of writing |
| B — SQLDelight | SQL-first, compiles `.sq` files to typed Kotlin | Yes, at the SQL level (arguably stronger — you write real SQL, not annotations) | Not built in — same companion-mechanism need as A | Apache 2.0 | None | Active, high documentation coverage; Cash App/Block-maintained |
| C — Plain SQLite (`android.database.sqlite` / `androidx.sqlite`) | Framework API, hand-written SQL and mapping | No | Not built in | Platform API — N/A | None | Platform-shipped; no separate maintenance risk, but no separate improvement cadence either |
| D — File-based storage for caches (tile cache specifically) | Flat files / a simple LRU-evicting file store, independent of A/B/C | N/A | N/A (files, not rows) | N/A | None | N/A |

**Encryption-at-rest companion, evaluated once, applies to whichever of A/B/C is chosen:**

| Sub-option | Status (verified) | Licence | Fit |
|---|---|---|---|
| `androidx.security-crypto` (`EncryptedSharedPreferences`/`EncryptedFile`) | **Deprecated since v1.1.0-alpha07 (April 2025)**, citing main-thread performance issues and "keyset corruption" reliability problems (verified via multiple 2025/2026 sources) | Apache 2.0 | **Not recommended** for new use given its own maintainers' deprecation |
| SQLCipher for Android (`sqlcipher-android`, Zetetic LLC) | Actively maintained; the older `android-database-sqlcipher` artifact is itself deprecated in favour of this one — verified | BSD-style Community Edition licence, requires reproducing the copyright/licence notice in the app (verified) | Encrypts the whole SQLite database file; has an official Room integration path (verified) — fits Options A and, with more manual wiring, B |
| DataStore + Tink + Android Keystore, assembled directly | Recommended migration path per multiple 2025/2026 sources reacting to `security-crypto`'s deprecation | Apache 2.0 (AndroidX DataStore, Tink) | More assembly work than SQLCipher; suited to small key-value secrets (e.g. a proxy credential) rather than whole-database encryption |

### Option A — Room

- **Pros**: the AndroidX-standard ORM; compile-time verification of SQL via annotation
  processing/KSP catches malformed queries at build time rather than at runtime; official
  `Migration` API for schema evolution; official in-memory-database support for fast JVM-adjacent
  tests (still requires the Android SQLite driver under instrumentation — see Testability below);
  official Room + SQLCipher integration exists (verified), so encryption at rest is a supported
  path, not a bespoke hack.
- **Cons**: requires either KSP or an annotation processor in the build (adds build-time
  complexity, though this is now routine for any modern Android project); build-time dependency on
  Google's Maven repository to fetch `androidx.room:*` artifacts.
- **Privacy impact**: neutral by itself; the `RouteCache`/`PlaceCache` ports already sketched in
  `docs/architecture/README.md` §2.1 map directly onto Room DAOs, so retention/deletion logic lives
  in `data`, testable via the domain-owned interface.
- **Resource impact**: standard SQLite performance characteristics; Room adds a thin compile-time
  layer with negligible runtime overhead over raw SQLite.
- **Licence**: Apache 2.0 (verified via AndroidX Jetpack documentation).
- **GMS dependency**: **none.** This needs stating precisely because it is easy to conflate with a
  different fact: building against `androidx.room` requires the **`google()` Maven repository** to
  be declared in the Gradle build so the artifact can be downloaded — this is a **build-time
  artifact-hosting** dependency (Google hosts the public Maven coordinates for AndroidX), not a
  **runtime** dependency on Google Play Services or any Google-account-gated service. F-Droid's
  build servers routinely fetch public dependencies from Maven Central and Google's Maven
  repository during the build; this is normal and does not conflict with the "no GMS" constraint,
  which is about what the *compiled app* depends on at runtime. This distinction is worth recording
  explicitly here because it is exactly the kind of thing a rushed review could misflag.
- **Maintenance status**: actively developed by Google/AndroidX; current stable version 2.8.4 per
  the official documentation retrieved for this brief.
- **What it forecloses**: nothing structural — the `domain` cache-port interfaces stay
  Room-agnostic by construction (per the architecture doc's layering rule), so this is swappable.

### Option B — SQLDelight

- **Pros**: SQL is the source of truth (`.sq` files), with Kotlin generated from it — arguably a
  stronger compile-time guarantee than annotation-driven Room, since the actual SQL is checked
  against the schema at build time, not inferred from annotations. No annotation processor needed
  (uses a Gradle plugin instead). Historically offered Kotlin Multiplatform support, but per
  `CLAUDE.md` §0.1 ("the app targets a single platform for now; do not add multiplatform
  scaffolding for later"), that capability would go entirely unused here — worth naming as a minor
  friction point: SQLDelight's Gradle plugin and project conventions are multiplatform-shaped by
  default even when only the Android target is configured, which is a small amount of unused
  surface area to carry, not a violation of the constraint by itself since no multiplatform code is
  actually added.
- **Cons**: smaller Android-specific ecosystem of guides/StackOverflow answers than Room's, since
  much of SQLDelight's public documentation and examples lean multiplatform; no first-party SQLCipher
  integration verified — encryption-at-rest wiring would be more manual than Room's documented
  SQLCipher path.
- **Privacy impact**: neutral, same as Option A structurally.
- **Resource impact**: comparable to Room — both ultimately run on SQLite.
- **Licence**: Apache 2.0 (verified).
- **GMS dependency**: none (verified).
- **Maintenance status**: actively maintained by Block (formerly Square/Cash App); high
  documentation coverage confirmed via research.
- **What it forecloses**: nothing structural, same reasoning as Option A.

### Option C — Plain SQLite (framework API)

- **Pros**: zero additional dependency — `android.database.sqlite` (and the `androidx.sqlite`
  support types) ship with the platform. No annotation processor, no code generation step, nothing
  to verify licence-wise beyond the platform itself.
- **Cons**: no compile-time query verification — a malformed SQL string or a column-name typo is a
  runtime failure, potentially discovered by a user rather than a build. All row-to-domain-object
  mapping is hand-written, which is more code to review and more surface for a subtle mapping bug
  (e.g. a coordinate column mismapped) — a real concern given the sensitivity of the data this
  layer would hold from v0.4 onward. Manual migration handling (`SQLiteOpenHelper.onUpgrade`) is
  more error-prone than Room's declarative `Migration` objects, raising the migration-safety risk
  this document's decision drivers specifically flag.
- **Privacy impact**: neutral by itself, but the higher risk of a hand-written mapping bug is a
  privacy-adjacent risk given what this table will eventually store (routes, places).
- **Resource impact**: the leanest of the three database options — no ORM/codegen overhead at all.
- **Licence**: platform API, not a separate dependency to evaluate.
- **GMS dependency**: none.
- **Maintenance status**: platform-shipped; stable, but also not actively improved for
  developer ergonomics the way Room/SQLDelight are.
- **What it forecloses**: nothing architecturally, but in practice raises the cost of every future
  schema change and every future query, since none of that work is compile-time checked.

### Option D — File-based storage for caches (tile cache specifically)

Not a competitor to A/B/C for structured data (settings, routes, places) — a **complementary**
choice for the tile cache specifically, since map tiles are natively file-shaped (one file per
z/x/y tile) rather than relational rows.

- **Pros**: matches the natural shape of tile data; a size/TTL-bounded LRU file cache (as already
  named in `docs/privacy.md` flow (b): *"On-disk tile cache, size- and TTL-bounded (LRU
  eviction)"*) is simpler to reason about than forcing tile blobs into database rows; avoids
  database bloat from binary tile data.
- **Cons**: needs its own eviction/size-accounting logic, hand-written regardless of which option
  is chosen for structured data; no query language for anything beyond "does this tile exist,
  evict the oldest."
- **Privacy impact**: same physical-access risk as any local storage (`docs/threat-model.md` §7) —
  the cache "reveals a history of viewed areas even without any network capture," independent of
  whether it is stored as files or database rows.
- **Resource impact**: low overhead; direct filesystem I/O.
- **Licence/GMS/maintenance**: not applicable — this is a pattern, not a library.
- **What it forecloses**: nothing — can coexist with whichever of A/B/C handles structured data.

## Encryption at rest, retention, deletion, backup exclusion

- **Encryption at rest**: recommended path is **SQLCipher for Android**, paired with whichever of
  Room or SQLDelight is chosen (Room has a verified, documented integration path; SQLDelight would
  need more manual wiring of its `SqlDriver` around a SQLCipher-backed connection). Do **not** adopt
  `androidx.security-crypto` for new code — it is deprecated by its own maintainers as of April
  2025 for the reasons stated above. For the narrower case of a single secret value (e.g. a proxy
  credential from `docs/adr/proposals/007-relay-and-proxy.md`), a DataStore + Tink + Android
  Keystore assembly is lighter-weight than encrypting an entire database for one field, and is the
  currently-recommended replacement pattern per the same research.
- **Retention limits**: the on-disk tile cache (decision D7, size/TTL-bounded LRU, out of this
  document's own scope — see Context) is already named in `docs/privacy.md` and
  `docs/specs/001-navigation-mvp.md` FR-27; its exact size cap and TTL remain an
  **open question — human decision required**, tracked in the v0.1 spec (OQ7), not deferred to a
  v0.4 recon spec, since the cache itself no longer waits for v0.4. What this document's own scope
  covers: cached routes — session-scoped only until "saved trips" is designed, at which point
  retention becomes user-controlled and explicit; saved places/trips — retained until the user
  deletes them, with no implicit expiry (deletion is a user action, not a policy).
- **Deletion path**: every table this document's chosen mechanism creates, once built, must have a
  corresponding "clear my data" action reachable from settings, matching `docs/privacy.md`'s
  existing statement that this becomes mandatory once saved places exist (the tile cache's own
  clear action is FR-30, already required from v0.1, outside this document's scope). This is a
  functional requirement on whatever `data`-layer implementation is written, not a property of the
  library choice itself — flagged here so it is not lost when the v0.4 spec is drafted.
- **Backup and data-extraction exclusion**: `docs/privacy.md` states this as an **open
  question — human decision required** for the later, v0.4 structured-database features (saved
  places, cached routes) — the exact backup-rules XML / manifest configuration for those is not yet
  written. This document adds the concrete mechanism for that question: whichever storage option is
  chosen, its files (database file(s), SQLCipher key material if stored on-device) must be
  enumerated in the app's Data Extraction Rules / `allowBackup` exclusion configuration before any
  v0.4 feature ships, not left at the Android default (which would otherwise fold this data into
  Auto Backup). The three v0.1 response caches (tile, geocoding, traffic) are out of this
  document's scope (see Context) and are already excluded by construction, per FR-31: they write
  under `Context.getNoBackupFilesDir()`, which the platform excludes from Auto Backup and Data
  Extraction Rules unconditionally — no `dataExtractionRules`/`fullBackupContent` XML rule is
  written or needed for them.

## How it is tested

Consistent with `docs/testing.md`'s pyramid:

- **Domain tests** (§2.1 of `docs/testing.md`): the `RouteCache`/`PlaceCache` port interfaces are
  exercised via hand-written fakes — no real database in any domain-level test, regardless of which
  option is chosen, because the interface lives in `domain` per
  `docs/architecture/README.md` §2.1.
- **Data-layer tests** (§2.2): Room and SQLDelight both support an in-memory or fully in-process
  test database, letting migration behaviour, query correctness, and encryption wiring be tested
  without an emulator for most cases; Option C (plain SQLite) generally needs more instrumentation
  to exercise the same surface, since there is no equivalent lightweight in-memory harness bundled
  with the platform API the way there is for Room.
- **Migration tests**: whichever option is chosen, a schema change must ship with a test that
  migrates a populated database from the previous schema version and asserts data survives —
  Room's `MigrationTestHelper` is a documented, purpose-built tool for exactly this; an equivalent
  must be hand-built for Option C.
- **Privacy-specific tests** (`docs/testing.md` §5): once any cache holds location-derived data,
  a test must assert deletion actually removes the underlying storage (not just a row flag), and
  that nothing sensitive from this layer appears in a log statement, mirroring the existing rule for
  every other flow in this app.
- **Encryption verification**: if SQLCipher is adopted, a test opens the raw database file bytes
  outside the app's normal access path and asserts the content is not plaintext-readable — the
  concrete, executable form of "encryption at rest," not merely "we called an encryption API."

## Recommendation

**Room, paired with SQLCipher for the encryption-at-rest requirement, and a separate file-based LRU
cache (Option D) for map tiles specifically.** Room is the AndroidX-standard choice with the widest
Android-specific tooling and documentation base, a verified, official SQLCipher integration path,
and no GMS dependency once the build-time-vs-runtime distinction above is understood correctly.
SQLDelight is a legitimate, comparably strong alternative — its SQL-first compile-time verification
is arguably stronger than Room's — and should be reconsidered if the human weighs "SQL is the
source of truth" as more valuable than "the AndroidX-idiomatic choice with the largest
Android-specific community," but this brief's default leans Room for the encryption-integration
maturity and the marginally larger pool of Android-specific (not multiplatform-diluted)
documentation and community troubleshooting. Plain SQLite (Option C) is not recommended given the
migration-safety and mapping-bug risk it raises for exactly the kind of sensitive data (routes,
places) this document exists to protect.

**Confidence: medium-high.** The Room-vs-SQLDelight comparison is close and defensible either way —
this is a genuine two-horse race, not a case where one option is clearly weak (Option C is the one
option this brief considers clearly weaker, given the sensitivity of the data at stake). The
SQLCipher recommendation is higher-confidence, resting on the directly-verified deprecation of
`androidx.security-crypto`.

**Honest cost**: adding SQLCipher means every database open/query pays an encryption/decryption
cost, and the app must manage a database encryption key (itself a secret needing an Android
Keystore-backed storage answer) — this is a real, if usually small, resource and complexity cost
being taken on specifically because the data this layer will eventually hold (saved places, cached
routes) is high-sensitivity per `docs/privacy.md` and `docs/threat-model.md` §7's physical-access
adversary.

## Consequences of the recommendation

- **Becomes easy**: adding new cached/persisted entities (saved places, saved trips) is a new Room
  entity + DAO + migration, encrypted by the same SQLCipher-backed connection already established —
  no new per-feature encryption decision.
- **Becomes hard / needs attention**: the SQLCipher key itself needs a storage answer (Android
  Keystore-backed, not a hardcoded string) — this must be designed explicitly in the v0.4 spec, not
  assumed to be free.
- **What must be abstracted to keep it reversible**: the `RouteCache`/`PlaceCache` (and future
  saved-place) ports stay `domain`-owned interfaces per the existing architecture rule; no
  Room-specific type (`@Entity`, `@Dao`) is ever referenced outside `data`. This is already the
  stated architecture rule, not a new constraint this document adds — it is what makes swapping to
  SQLDelight, or dropping SQLCipher for a different encryption mechanism, a `data`-layer-only
  change later.

## What is needed from the human

1. **Confirm Room + SQLCipher + file-based tile cache**, or choose SQLDelight instead of Room, or
   reject encryption-at-rest entirely for a stated reason (e.g. accepting the physical-access risk
   as out of scope, which would need to be reflected honestly in `docs/threat-model.md` §7's
   residual-risk statement rather than silently assumed away).
2. **A policy call on retention**: exact tile-cache size cap/TTL (already an open question in
   `docs/privacy.md`) and whether "saved trips" implies any retention limit at all versus
   indefinite-until-deleted.
3. **A policy call on backup exclusion**: confirm the Data Extraction Rules approach described above
   is acceptable, or specify a different mechanism.
4. No hosting, keys, or external infrastructure is needed from the human for this decision — the
   SQLCipher database key is generated and stored on-device (Android Keystore-backed), not
   provisioned externally.

## Reversibility

**Medium-high.** Because the cache/persistence ports are `domain`-owned interfaces
(`docs/architecture/README.md` §2.1), switching from Room to SQLDelight (or the reverse) later is a
`data`-layer rewrite: new entity/query definitions, a migration path from the old schema (or an
accepted one-time data loss if migration is not worth building), and re-running the test suite in
[How it is tested](#how-it-is-tested) against the new implementation — no `domain` or
`presentation` code changes. Dropping SQLCipher later (e.g. if its performance cost proves
unacceptable) is a narrower, cheaper change confined to the connection-construction code. The one
genuinely costly reversal is a live-production schema migration once real user data
(saved places/trips) exists on devices — at that point, reversing the storage engine choice means
either writing a real data-migration path or accepting user-visible data loss, which is why this
decision is worth getting right before v0.4 ships, even though v0.1 does not need it.
