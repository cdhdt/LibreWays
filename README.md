# LibreWays

LibreWays is an independent Android navigation app: the plan is to let a user enter a
destination and follow a route to it that accounts for live traffic conditions, built for
people who do not want to be tracked to get directions — including users of de-Googled
devices (GrapheneOS, no Play Services) and F-Droid distribution.

## Non-affiliation

**LibreWays is an independent, unaffiliated third-party client.** It is not affiliated with,
endorsed by, sponsored by, or connected in any way to any existing navigation service, map
provider, or traffic data provider. LibreWays reads its live traffic, incident, and route data
from **Waze** — named here in plain, descriptive prose, not as branding: an audit of this app's
network traffic should find the same name this document states, not a euphemism for it. Naming
the source is what an honest privacy account and the app's own attribution
(`docs/specs/001-navigation-mvp.md` FR-38) both require — withholding it would be less honest,
not more neutral. No logo, mascot, icon, or other element implying partnership is used anywhere
in the app or its materials; LibreWays has its own visual identity and borrows no branding from
anyone.

## Privacy, in plain language

LibreWays cannot compute a route, resolve a typed destination, show traffic, or draw a map
without sending some requests to third parties it does not control. Concretely, using the app
is expected to send: your route's origin and destination, to Waze, for route computation; the
area you're looking at, to Waze, for traffic and incident data; and the map area you're viewing,
to a map-tile provider (not yet chosen) — each of these fires once per corresponding action.
**Destination search is different and more exposed, and this must be stated plainly, not
softened**: because the app searches as
you type, it sends a query to a place-search provider every few keystrokes — concretely, every
time you pause for about 600 ms after typing at least 3 characters — not once when you finish
typing. This includes partial text you typed and then deleted: if a partial string sat for 600 ms
before you kept typing or deleted it, it already reached the place-search provider. Each of these
reveals something about you — your destination search text especially, since it can reveal intent
and is sent this way repeatedly per search — to whoever operates that service, along with your IP
address.

From the start, the networking layer is being designed so that every one of those requests can
be routed through a **relay of your choosing** (Tor, an HTTP/SOCKS proxy, or a self-hosted
instance), so a third party sees the relay's traffic rather than yours directly. There will be
**no telemetry, no analytics, no crash reporting, and no remote logging** without explicit,
revocable, off-by-default opt-in — "off" will mean zero outbound traffic for that purpose, not
"anonymised" traffic. None of this is a promise about a specific release; it is the constraint
this project is built under.

Separately from what leaves the device: the app also keeps small, bounded caches **on the
device itself** — of recently viewed map tiles, recent destination searches, and recent traffic
data — purely to avoid repeating the same request. These caches are not encrypted and are legible
to anyone with access to the unlocked device or a backup of it, though they are excluded from
Android's backup mechanisms, bounded in size and lifetime, and clearable on demand. This is a
deliberate trade-off (fewer requests to third parties, at the cost of a local, unencrypted
record) stated here plainly rather than left for you to discover.

The authoritative, exhaustive account of every outbound request and every local cache —
what is sent or stored, to whom, how often, and what you can do about it — lives in
[`docs/privacy.md`](docs/privacy.md) and will be kept honest as the app is built.

## Status: pre-alpha — nothing is implemented yet

There is no working app. There is no build, no APK, no released code, and no screenshot to
show, because none of it exists yet. This repository currently contains planning documents
only: a roadmap, developer specs, and architecture-decision material. Nothing described in
these documents should be read as a feature that exists today — treat every capability
mentioned anywhere in this repo as *planned*, not *available*, until a release says otherwise.

## Planned platform requirements

- Android, native, Kotlin (no Flutter, no React Native, no embedded third-party runtime).
- Designed to run fully on a device with **no Google Play Services / Firebase / GMS** at all —
  a feature that degrades or crashes without Google's framework will be treated as a defect.
- Distribution target: F-Droid-compatible (reproducible build, no proprietary blob, no
  account-gated SDK).

There is no build to run yet, so no build instructions are given here. This section will be
replaced with real setup steps once code exists.

## License

GPL-3.0. See [`LICENSE`](LICENSE).

## Documentation

| Doc | Content |
|---|---|
| [`docs/privacy.md`](docs/privacy.md) | Every data flow and permission, and its justification |
| [`docs/threat-model.md`](docs/threat-model.md) | Adversaries, assets, and non-goals — complements `docs/privacy.md` |
| [`docs/roadmap.md`](docs/roadmap.md) | Planned milestones (scope, not dates) |
| [`docs/specs/README.md`](docs/specs/README.md) | Developer specs, written before any code |
| [`docs/architecture/README.md`](docs/architecture/README.md) | Modules, layers, data flow |
| [`docs/testing.md`](docs/testing.md) | Test strategy and how to run everything |
| [`docs/adr/README.md`](docs/adr/README.md) | Architecture Decision Records — status and lifecycle of every tracked decision |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to propose and submit changes |
| [`CLAUDE.md`](CLAUDE.md) | Binding engineering directives for this project |
