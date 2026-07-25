# 009 — Location and foreground service

## Status

`proposed`

Nothing in this document is authority until the human accepts it and it is recorded as an accepted
ADR per `CLAUDE.md` §0.2.

## Context

Two milestones need this decision, at different scopes:

- **v0.1** needs a one-shot/low-frequency, foreground-only position fix to place the "you are here"
  marker on the route (`docs/specs/001-navigation-mvp.md` FR-7, Stage E of its TDD test list). No
  foreground service exists yet in v0.1 — location is read only while the map screen is visible and
  the app is foregrounded, and every location callback unregisters the moment that stops being true
  (`docs/specs/001-navigation-mvp.md` non-functional "Resource budget" section; FR-7 — the tests
  asserting position updates emit only while foregrounded and stop once that signal ends, tests
  27/28).
- **v0.2** ("Active guidance", per `docs/roadmap.md`) needs continuous location during active
  turn-by-turn guidance, which is where a foreground service becomes necessary: `docs/roadmap.md`
  v0.2 row names `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS` as
  new permissions, and states explicitly: *"still no `ACCESS_BACKGROUND_LOCATION` — guidance only
  runs in a user-visible foreground service."* `docs/privacy.md`'s permissions table repeats this:
  *"`ACCESS_BACKGROUND_LOCATION` is never requested, at any milestone."*

This document decides the **location-acquisition API** (which must work with zero Google Play
Services, per `CLAUDE.md` §0.1) and the **v0.2 foreground-service shape** together, since the choice
of location API directly constrains what the service can safely do, and the service's lifecycle is
what the position `domain` interface (`OwnPositionSource`, per
`docs/architecture/README.md` §2.1) must be implemented against.

**GMS exclusion is not optional here — it is the reason this ADR exists at all.** The default,
easiest Android location API — `FusedLocationProviderClient` — is a Google Play Services API and is
excluded outright by `CLAUDE.md` §0.1 ("Zero Google Play Services... This includes transitive
pulls"). This document therefore evaluates only platform (AOSP) location APIs; there is no realistic
GMS-based alternative to weigh against them, so the "options" below are variations on the platform
API and its usage pattern, not a genuine multi-vendor comparison.

## Decision drivers

| Driver | Application here |
|---|---|
| Privacy | Location is "the crown jewel" of this app per `CLAUDE.md` §5; the API choice must support coarse-over-fine, foreground-over-background, and never persisting a raw fix, by construction rather than by convention |
| Works fully without Play Services | Non-negotiable — this is the entire reason `FusedLocationProviderClient` is off the table |
| Resource cost (battery) | Location acquisition is one of the most battery-sensitive subsystems on Android; update cadence and requested accuracy directly trade off against battery life, and this is explicitly a `CLAUDE.md` §4 resource-discipline concern, not an afterthought |
| GPL-3.0-compatible licence | The platform API itself carries no separate licence to evaluate; not a meaningful differentiator between the options below |
| F-Droid-compatible | Not affected by this choice — no proprietary blob is introduced either way |
| Maintenance and community health | `LocationManager` is a stable, long-standing platform API; the open question is which of its *methods* (legacy callback vs. modern single-shot) is appropriate, not whether the API itself is maintained |
| Development speed | The legacy `LocationListener`-callback pattern is more boilerplate than the modern `getCurrentLocation()`/executor-based pattern; both are viable |
| Testability | The `OwnPositionSource` `domain` interface must be fakeable with no real location fix in any domain test (`docs/testing.md` §4) — true regardless of which platform API implements it in `data` |
| Reversibility | Swapping acquisition strategy later (e.g. adopting a FOSS fused-location reimplementation such as microG's, if the human ever authorises interoperating with it) must be a `data`-layer change only |

## Location acquisition options

All options below use `android.location.LocationManager` (verified as the platform, non-GMS
location API); they differ in **which of its methods** are used and how.

| Option | Mechanism | Freshness/accuracy trade-off | API level floor | GMS dependency |
|---|---|---|---|---|
| A — Legacy continuous `requestLocationUpdates` + `LocationListener` callback | Register a listener with a minimum time/distance interval; receive continuous callbacks | Continuous stream; app controls cadence via the requested interval | Available since API 1 (Executor-based overload since API 31) | None |
| B — Modern single-shot `getCurrentLocation()` | Request one fresh fix via a callback/`CancellationSignal`, no continuous registration | One fix, requested only when needed (e.g. v0.1's one-shot marker placement) | API 30+ | None |
| C — `LocationManager.FUSED_PROVIDER` (AOSP fused provider, distinct from Google's `FusedLocationProviderClient`) | Same `LocationManager` API surface, but requesting the platform's own on-device sensor-fusion provider name if the ROM ships one | Potentially better accuracy/battery trade-off than requesting `GPS_PROVIDER` alone, if available | API 31+, **and only if the ROM ships it** | None (it is an AOSP API name, not a Google Play Services client) |

### Option A — Continuous updates via `LocationListener`

- **Pros**: works down to the oldest API levels realistically supported; well-understood pattern
  with the largest body of existing Android documentation and community troubleshooting; natural
  fit for v0.2's continuous-guidance need.
- **Cons**: more boilerplate than the modern callback style (manual listener registration/removal,
  manual thread/looper handling on older API levels); easy to get cancellation wrong if not
  carefully scoped to the screen/service lifecycle — exactly the resource-discipline risk
  `docs/specs/001-navigation-mvp.md`'s foreground-gated-emission, stop-on-background, and
  callback-unregistration tests (tests 27, 28, 54) are designed to catch.
- **Privacy/resource impact**: identical ceiling to Option B/C — the risk is in how cadence and
  accuracy are configured, not in this method choice itself. A continuous registration left running
  after the owning scope ends is the single most likely resource-discipline bug this option
  invites, which is exactly why the test list below insists on an explicit unregistration test.
- **Fit**: better suited to v0.2's continuous-guidance need than to v0.1's one-shot marker.

### Option B — Modern one-shot `getCurrentLocation()`

- **Pros**: matches v0.1's actual need precisely — "a low-frequency fix to place a 'you are here'
  marker" (`docs/privacy.md` flow (e)) is a one-shot request, not a continuous stream; the
  API is explicitly a request-response pattern with a `CancellationSignal`, which maps cleanly onto
  the `domain` use case's cancellation semantics (`docs/architecture/README.md` §5: "cancellation
  tied to screen/trip lifecycle").
  Per current Android developer guidance research, `getCurrentLocation()` "gets a fresher, more
  accurate location more consistently" than reading a possibly-stale last-known value, when only a
  single fix is needed.
- **Cons**: not designed for continuous updates — using it in a loop to simulate continuous
  guidance would be worse than Option A's purpose-built continuous API, both in code clarity and in
  battery behaviour (repeated fresh-fix requests cost more than one sustained registration at a
  controlled interval).
- **Privacy/resource impact**: naturally bounded — a single request cannot "run forever" the way a
  forgotten continuous registration can, which is a meaningful resource-discipline advantage for
  v0.1's one-shot need specifically.
- **Fit**: recommended for v0.1's `OwnPositionSource` one-shot case; not a substitute for Option A
  in v0.2.

### Option C — `LocationManager.FUSED_PROVIDER` (AOSP), where present

- **Pros**: if the ROM provides it, this is the closest non-GMS equivalent to the accuracy/battery
  trade-off `FusedLocationProviderClient` offers on a GMS device, without any Play Services
  dependency — it is requested through the same `LocationManager` API as `GPS_PROVIDER`, just by a
  different provider name.
- **Cons**: **its presence is not guaranteed and is unverified for this project's actual target
  devices.** This is the single most important open technical fact in this document: whether
  GrapheneOS and other de-Googled ROMs this app explicitly targets (`CLAUDE.md` §0: "Audience...
  including GrapheneOS / de-Googled devices without Play Services") ship an AOSP fused provider
  under this name is **not verified by this research** and must be checked directly against a real
  de-Googled device/ROM before any code depends on it being present. If absent, the app must fall
  back to `GPS_PROVIDER` (and, where still meaningfully available without Google's network location
  service backing it, `NETWORK_PROVIDER`) without crashing or silently degrading — this fallback
  requirement holds regardless of which option is finally chosen.
- **Privacy/resource impact**: potentially the best of the three if genuinely present, since
  on-device sensor fusion generally trades battery for accuracy better than raw GPS polling alone —
  but this is a claim about a provider this brief could not confirm exists on the actual target
  devices, so it must not be relied upon as a baseline.
- **Fit**: worth probing for and preferring **opportunistically** (query
  `LocationManager.getAllProviders()` at runtime and use `FUSED_PROVIDER` if present, otherwise fall
  back), never assumed present.

## Foreground service design (v0.2)

`CLAUDE.md` §4 requires that "no wakelocks or foreground services [exist] without documented
justification" — this section is that justification and the concrete design constraints it implies.

### Service type and manifest declaration

- **Foreground service type: `location`.** Verified platform behaviour: apps targeting Android 14
  (API 34) or higher must declare the specific foreground service type in the manifest and the
  operating system checks, at the moment the service is created, that the app holds the matching
  permission for that type — for `location`, that is `FOREGROUND_SERVICE_LOCATION` (a normal
  permission, granted automatically at install, not user-revocable at runtime) **plus** the runtime
  location permission (`ACCESS_FINE_LOCATION`, already requested in v0.1).
- **No `ACCESS_BACKGROUND_LOCATION`.** Verified platform constraint, directly relevant here: a
  location-type foreground service **cannot be started while the app is in the background** unless
  the app holds `ACCESS_BACKGROUND_LOCATION` — which this app deliberately never requests. This is
  not merely a policy choice this document repeats from the product brief; it is a **structural
  constraint that enforces the policy**: the service must always be started as a direct
  consequence of a foreground user action (tapping "start guidance" while the app is the visible,
  foregrounded activity), never from any background trigger. If a future feature ever seemed to
  need starting guidance from the background, that would require `ACCESS_BACKGROUND_LOCATION` and
  is therefore an automatic escalation under `CLAUDE.md` §9, not something this design permits
  silently.

### Notification

- A location-type foreground service **must** show an ongoing, user-visible notification for the
  duration it runs — this is Android's transparency mechanism substituting for background-location
  consent (`docs/privacy.md` permissions table: *"the mandatory ongoing notification... is the
  transparency mechanism that substitutes for requesting background location"*). The notification
  content must itself respect the logging policy in `docs/privacy.md` — no raw coordinates,
  addresses, or destination text rendered into notification text; a generic "Guidance active" style
  message with, at most, the next turn instruction (which is guidance content the user is already
  seeing on-screen, not a new disclosure) is the appropriate content shape — exact copy is a v0.2
  spec detail, not decided here.
- `POST_NOTIFICATIONS` (Android 13+) is required to display it at all. `docs/privacy.md` already
  flags an **open question — human decision required**: exact platform behaviour when the user
  denies `POST_NOTIFICATIONS` — whether the OS still forces some form of foreground-service
  notification without it, or whether the service cannot start. This must be confirmed against a
  real device/emulator before the v0.2 spec finalises its degradation behaviour for that denial
  path; this document does not resolve it, only restates it as directly relevant to the service
  design.

### Keep-awake policy

- No dedicated wakelock beyond what the foreground service itself and the location subscription
  require by platform design — `CLAUDE.md` §4 explicitly forbids wakelocks "without documented
  justification," and a foreground service with an active location subscription already has the
  platform-provided execution guarantee it needs; an additional, separately-acquired
  `PowerManager.WakeLock` should not be added unless a specific, documented gap in that guarantee is
  found (e.g. keeping the CPU awake between location callbacks on a device that aggressively
  suspends) — and if one is found, it must be scoped as tightly as the service itself and released
  the moment guidance ends, never held indefinitely.
- Screen-on/keep-awake behaviour for the guidance UI itself (e.g. `FLAG_KEEP_SCREEN_ON` on the
  guidance activity) is a `presentation`-layer, UI-lifecycle concern, not a location or service
  design decision — noted here only so it is not confused with the service's own wakelock policy.

### What stops when the trip stops

Per `docs/architecture/README.md` §5 ("nothing runs when the user is not navigating") and
`docs/roadmap.md` v0.2's explicit no-background-operation framing, ending or cancelling a trip must,
atomically from the user's perspective:

1. Stop the foreground service (removing its process-priority guarantee and its notification).
2. Unregister the location subscription — not merely stop consuming its output, actually
   unregister the platform callback, mirroring the same discipline
   `docs/specs/001-navigation-mvp.md`'s position-provider callback-unregistration test (test 54)
   already requires for v0.1's simpler case.
3. Cancel any in-flight rerouting/traffic call tied to the trip's scope, per the existing
   cancellation-on-lifecycle-end rule in `docs/architecture/README.md` §5.
4. Release any wakelock acquired under the narrow justification above, if one exists.

A service that keeps running, or a location callback that keeps firing, after the user has ended
the trip is both a resource-discipline defect (`CLAUDE.md` §4) and a privacy defect (location
continuing to be read with no active, visible reason for it) — the two concerns converge on the
same test requirement.

### Why no background-location permission is requested

Restated plainly because it is the single most load-bearing constraint in this document: the
product's entire guidance experience is designed to need location **only while a foreground
service, with its mandatory visible notification, is running as a direct consequence of a
foreground user action.** There is no feature in any milestone (`docs/roadmap.md` v0.1 through
v0.4+) that needs location while the app is fully backgrounded or closed outside an active,
visible guidance session. This is not a workaround forced by refusing the permission — it is a
description of what the product actually does. If a future feature genuinely seemed to require
background location, `docs/privacy.md` already states that would itself be an escalation, not a
default assumption; this document does not open that question, it closes it for everything
currently in scope.

### Resource budget the implementation must respect

- **Accuracy**: request the coarsest accuracy sufficient for the active need — a one-shot marker
  fix (v0.1) does not need the same precision as active turn-by-turn guidance (v0.2); the exact
  figures are an open question already logged in `docs/specs/001-navigation-mvp.md` (OQ2), restated
  here as applying identically to the v0.2 continuous case, likely at a different (tighter) setting
  than v0.1's one-shot use, given guidance needs enough precision to detect off-route deviation.
- **Cadence**: v0.2's continuous updates must use a deliberately bounded interval (not the
  platform's most aggressive default), matched to what turn-by-turn guidance and off-route
  detection actually need — not "as fast as possible."
- **Android 15 foreground-service timeout behaviour (verified)**: the 6-hour rolling
  `dataSync`/`mediaProcessing` foreground-service timeout introduced in Android 15 does **not**
  apply to the `location` service type — verified research indicates location-type foreground
  services have no equivalent automatic timeout, though they still require the ongoing
  notification and appropriate consent throughout. This is a relevant fact for a long road trip
  (a guidance session plausibly running for several hours) and should be re-verified against the
  final Android target API level at implementation time, since platform behaviour in this area has
  changed across recent releases and may change again.
- **No polling beyond the location subscription itself**: rerouting/traffic-refresh cadence during
  active guidance is governed by `docs/roadmap.md` v0.3's explicit "no aggressive polling" note, not
  by this document — flagged here only as a boundary, not decided.

## How it is tested

- **Domain tests** (`docs/testing.md` §2.1): `OwnPositionSource` is a `domain`-owned interface
  (`docs/architecture/README.md` §2.1); the `TrackOwnPosition` use case is tested entirely against
  a hand-written fake emitting synthetic positions, per `docs/specs/001-navigation-mvp.md` Stage E,
  the own-position use-case tests (tests 26–29) — no real `LocationManager` call in any domain
  test, for either the v0.1 one-shot case or the v0.2 continuous case.
- **Data-layer tests** (`docs/testing.md` §2.2): the concrete `LocationManager`-backed
  implementation is exercised for its mapping and lifecycle behaviour — request-once vs.
  continuous-registration code paths, accuracy/cadence parameters actually passed to the platform
  API, and, critically, that `removeUpdates`/callback-unregistration is actually invoked (not merely
  that the app "stops caring about" the output) when told the owning scope has ended — this is
  the same pattern as `docs/specs/001-navigation-mvp.md`'s position-provider
  callback-unregistration test (test 54), extended to the v0.2 service's start/stop lifecycle.
- **Foreground-service lifecycle tests**: instrumented tests (per `docs/testing.md` §2.3, "critical
  paths only") verifying the service actually stops, the notification is actually removed, and the
  location callback is actually unregistered when a trip ends — this is exactly the kind of
  OS-lifecycle-driven behaviour `docs/testing.md` §7 names as "cannot be unit-tested," verified
  instead by instrumentation or manual profiling.
- **Manual verification**: real-device battery/foreground-service-notification behaviour under Doze
  is explicitly named in `docs/testing.md` §7 as requiring manual verification attached as PR
  evidence — this applies directly to the v0.2 service and cannot be fully substituted by any
  automated test.
- **Permission-denial tests**: `docs/specs/001-navigation-mvp.md`'s permission-denied test (test
  26, v0.1) already requires the position use case to return an explicit `PermissionDenied` result
  with no platform call attempted; the equivalent v0.2 test must confirm the foreground service
  simply never starts (and no location subscription is ever attempted) if the runtime location
  permission is absent, rather than starting and failing loudly or silently.

## Recommendation

**v0.1**: Option B (`getCurrentLocation()`, one-shot, per-need requests) implementing
`OwnPositionSource`'s v0.1 usage. **v0.2**: Option A (continuous `requestLocationUpdates` with a
deliberately bounded interval/accuracy) inside the foreground service described above. **Both
milestones**: probe for `LocationManager.FUSED_PROVIDER` (Option C) at runtime and prefer it
opportunistically when present, falling back to `GPS_PROVIDER` unconditionally — never assume
Option C's presence as a baseline given it is unverified on the app's actual de-Googled target
devices.

**Confidence: high** on excluding `FusedLocationProviderClient` (directly required by `CLAUDE.md`
§0.1, not a judgment call). **High** on the v0.1/v0.2 API split (matches the documented
one-shot-vs-continuous need precisely). **Medium** on Option C's opportunistic use, specifically
because its actual availability on GrapheneOS and comparable ROMs is unverified by this research and
must be confirmed on real hardware before the implementation relies on it for anything beyond an
optional accuracy/battery improvement.

**Honest cost**: without a confirmed `FUSED_PROVIDER`, the app is likely running on `GPS_PROVIDER`
alone on many of its actual target de-Googled devices, which is more battery-hungry than a genuine
sensor-fusion provider would be — this is a real, currently-unresolved resource-cost gap this
document cannot close without device-level verification the human (or a later recon/dev pass) must
perform.

## Consequences of the recommendation

- **Becomes easy**: v0.1's one-shot and v0.2's continuous acquisition share the same `domain`
  `OwnPositionSource` interface and the same underlying `LocationManager` API family, so v0.2's
  `data`-layer implementation extends rather than replaces v0.1's.
- **Becomes hard / needs attention**: verifying `FUSED_PROVIDER` availability across the actual
  target device/ROM matrix (GrapheneOS at minimum) is manual work with no shortcut — this should be
  scheduled explicitly, not assumed to resolve itself.
- **What must be abstracted to keep it reversible**: the concrete provider name/selection logic
  (`GPS_PROVIDER` vs. `FUSED_PROVIDER` vs. a future alternative) stays entirely inside the `data`
  implementation of `OwnPositionSource`; `domain`'s `TrackOwnPosition` use case and every consumer
  of `Position` never reference a provider name, per the existing architecture rule.

## What is needed from the human

1. **Confirm the v0.1 (one-shot) / v0.2 (continuous) API split**, or state a reason to use the
   continuous API in v0.1 too (e.g. anticipated near-term reuse) even though the one-shot need is
   what v0.1 specifies today.
2. **Set the concrete accuracy/cadence figures** — already logged as open questions
   (`docs/specs/001-navigation-mvp.md` OQ2 for v0.1; this document's equivalent, currently
   unspecified, figure for v0.2's continuous case).
3. **Decide the `POST_NOTIFICATIONS`-denied fallback behaviour** for v0.2, once the platform's
   actual behaviour is confirmed on a real device — an open question this document restates but
   does not resolve.
4. **Arrange verification of `FUSED_PROVIDER` presence** on the project's actual target de-Googled
   devices/ROMs (at minimum GrapheneOS) — this is device-level manual verification work, not a
   documentation task, and no hosting/keys are needed for it.

## Reversibility

**High.** `OwnPositionSource` is a `domain`-owned interface per `docs/architecture/README.md` §2.1;
switching the acquisition method (one-shot vs. continuous, or the provider name preferred) is
confined to its `data`-layer implementation. The foreground-service shape itself (service type,
notification, lifecycle wiring) is more costly to change after the fact only in the ordinary sense
that any shipped, user-visible service behaviour (the notification's appearance, its exact
lifecycle timing) becomes something real users have already formed expectations around — reversing
*that* is a product-communication cost, not an architectural one. The one plausible non-reversible
element is if code were ever written assuming `FUSED_PROVIDER` is always present without a verified
fallback — which is precisely why this document insists the fallback path is mandatory and tested,
not optional.
