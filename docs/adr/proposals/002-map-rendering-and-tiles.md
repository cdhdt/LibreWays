# 002. Map rendering and tile source

## Status

`proposed`

## Context

v0.1 ("A to B") requires showing the route, traffic incidents on it, and the user's own position
on a map. This is two coupled but separable decisions: (a) which **rendering library** draws the
map inside the app, and (b) which **tile source** supplies the map imagery/vector data it renders.
Both interact with CLAUDE.md §5.1's single-networking-chokepoint invariant: "a networking design
that makes [user-selectable relay] retrofit-only is not acceptable — the review checks for it." See
[`../../architecture/README.md`](../../architecture/README.md) for the
chokepoint's full architectural description; this brief states only what each candidate does or
does not respect about it. The tile source is also, per the product brief, a second distinct third
party whose viewport-plus-IP exposure must be documented in `docs/privacy.md` alongside the traffic
source (proposal 005).

## Decision drivers

- Privacy: whether tile/style/glyph fetches go through the app's own chokepoint-able HTTP client or
  bypass it via a library's private networking stack.
- Works fully without Play Services.
- Resource cost: raster vs vector CPU/GPU/battery/memory profile; APK size of the library and any
  bundled offline data.
- GPL-3.0-compatible licence.
- F-Droid-compatible: reproducible build, no proprietary blob.
- Maintenance and community health (verified).
- Development speed.
- Testability.
- Reversibility.
- Offline capability (explicit product-relevant driver: v0.4+ roadmap is "route/tile caching for
  offline use," so a library/tile-source combination that already has an offline story is worth
  more than one that needs one built from scratch later).

Google Maps SDK is not evaluated as an option: it requires Google Play Services and a Google
account-gated key, which directly violates the locked constraint in CLAUDE.md §0.1 ("No Google
Maps SDK"). Named here only to rule it out, not as a candidate.

## Part A — Rendering library

| Option | Model | Verdict |
|---|---|---|
| osmdroid | Raster tiles, `View`-based | Real candidate |
| MapLibre Native (Android SDK) | Vector tiles, GPU-accelerated, `View`-based | Real candidate |
| Mapsforge | Vector rendering from local `.map` files, `View`-based | Real candidate |

### osmdroid

- Pros: mature, direct replacement for Android's old `MapView`; supports numerous tile sources,
  online and offline, out of the box; **verified** — the tile downloader (`MapTileDownloader`) has
  had a pluggable HTTP client factory since v4.0, i.e. the app can inject its own HTTP client
  (including one wired through the mandatory relay/proxy) instead of letting the library make its
  own unmanaged requests — this is a direct, verified fit for the chokepoint invariant.
- Cons: raster-only — no GPU vector rendering, so re-styling (e.g. a dark map theme) means fetching
  differently-rendered raster tiles rather than restyling client-side; less visually modern than
  vector rendering; per-tile HTTP overhead at high zoom/pan rates unless cached well.
- Privacy impact: fully controllable — the app decides exactly which HTTP client fetches tiles.
- Resource impact: raster decoding/bitmap caching is comparatively cheap on CPU but tile bitmaps use
  more memory/storage than an equivalent vector tile; no GPU rendering pipeline needed.
- Licence: **verified** Apache-2.0 for the core distribution (context7: `osmdroid-server-jdk`
  module explicitly Apache-2.0; the core `osmdroid-android` artifact is commonly documented under
  the same licence — not independently re-confirmed for that specific artifact this session, low
  residual risk).
- Google Play Services dependency: **verified** — the `osmdroid-android` AAR (v5.0+) "has no
  external requirements"; only the optional `osmdroid-thirdparty` AAR pulls Google Play Services —
  that optional module must not be added.
- Maintenance status: **verified** actively released (Maven Central releases, an active wiki and
  changelog through recent versions); community reputation "Medium" per context7's source scoring.
- What it forecloses: GPU vector styling and the visual polish that comes with it; nothing else.

### MapLibre Native (Android SDK)

- Pros: GPU-accelerated vector tiles, client-side restyling (e.g. offline dark theme) without
  re-fetching, modern rendering quality, active fork of the pre-relicense Mapbox GL engine with a
  large ecosystem of vector styles.
- Cons: **verified** — the SDK's tile/style/glyph fetching goes through its own native `FileSource`
  (C++ core, its own on-disk cache database, its own `setApiKey`/`setResourcesCachePath` API); no
  documented hook was found in this session for redirecting that internal networking through an
  app-supplied `OkHttpClient` or proxy — the only OkHttp reference found in the SDK's docs is for
  an unrelated feature (fetching external GeoJSON for map annotations), not the tile pipeline
  itself. This is a real, unresolved risk against the chokepoint invariant and must be verified
  against the actual SDK source (not just its docs) before this option can be accepted. **Verified**
  — the SDK's manifest merges `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` into the host app
  by default (for its own `LocationComponent`), which must be reviewed even if the app supplies its
  own location engine, since an unused merged permission is exactly the kind of thing CLAUDE.md
  §5.1 style privacy audits flag.
- Privacy impact: potentially bypasses the chokepoint unless a proxy-transparent solution is
  confirmed (e.g. routing the whole device/emulator's network through a system-level proxy, or
  finding an undocumented native override) — treat as an open engineering risk, not a settled fact
  either way.
- Resource impact: GPU rendering is efficient once running but has a higher baseline
  memory/graphics-context cost than raster; vector tiles are typically smaller over the wire than
  raster tiles at equivalent visual detail.
- Licence: **verified** BSD-2-Clause.
- Google Play Services dependency: **not fully verified** — no explicit `play-services-location`
  dependency was found in the manifest/dependency excerpts inspected, and the `LocationComponent`
  API explicitly accepts a custom `LocationEngine`, meaning GMS is not architecturally forced — but
  the SDK's *default* engine implementation was not independently inspected this session.
- Maintenance status: **verified** actively released (recent versions referenced in its own docs,
  "High" source reputation, large snippet count), it is the direct, actively maintained fork
  used by multiple FOSS mapping projects after Mapbox's 2020 relicensing.
- What it forecloses: if the native-networking risk above is confirmed (no proxy hook exists), this
  option forecloses a clean chokepoint-compliant relay for the base map specifically, while still
  allowing the relay for every other outbound call (traffic, geocoding, routing) — a partial, not
  total, violation, but one CLAUDE.md §5.1 explicitly says the review checks for.

### Mapsforge

- Pros: renders vector maps from **local `.map` files** — no live tile HTTP fetch is architecturally
  required at all once an extract is on the device; this is the only candidate that makes the base
  map's privacy exposure zero by construction rather than by careful proxying; matches the v0.4+
  "offline use" roadmap item today rather than requiring it be retrofitted later; **verified**
  companion tile-server projects exist for anyone wanting to serve Mapsforge tiles live instead
  (MIT and GPL-3+ licensed tile servers are documented in the project's own applications list).
- Cons: requires a `.map` extract generation/hosting/update pipeline (shared concern with the
  on-device option in proposal 003 and 004 — see those for the general shape of that cost);
  extract-based rendering means the map is only as fresh as the last extract build, not live;
  smaller community than osmdroid or MapLibre; requires an extra native SVG dependency
  (`androidsvg`) for the Android map module.
- Privacy impact: zero live-tile network exposure once an extract is loaded; the extract
  download/update itself is a bulk, infrequent flow that is far easier to make privacy-honest and
  easy to relay than a per-viewport live-tile stream.
- Resource impact: on-device storage for extracts (region-sized, likely tens to low hundreds of MB,
  unverified exact figures); rendering is CPU-side vector drawing, not GPU-accelerated like
  MapLibre.
- Licence: **verified** LGPL-3.0, explicitly with "a simplification that allows its inclusion in
  Android applications without requiring the application itself to be open source" — compatible
  with a GPL-3.0 app regardless, since LGPL is copyleft-permissive in this direction.
- Google Play Services dependency: none found in the dependency list inspected (`mapsforge-core`,
  `mapsforge-map`, `mapsforge-map-reader`, `mapsforge-map-android`, `androidsvg`) — reasonably
  confirmed absent, not exhaustively audited.
- Maintenance status: "Medium" source reputation per context7, smaller snippet count than the other
  two — treat community health as a genuine open question, not settled.
- What it forecloses: GPU-rendering visual quality of MapLibre; requires committing to an extract
  pipeline decision at the same time as this one (see "What is needed from the human" below).

## Part B — Tile source (relevant to osmdroid and MapLibre; Mapsforge's default mode needs
extracts instead, see above)

| Option | Summary | Verdict |
|---|---|---|
| Public OSM tile server (`tile.openstreetmap.org`) | The community-run default raster tile endpoint | Real candidate, policy-constrained |
| Self-hosted tile server (project-operated) | Project runs its own raster or vector tile server (e.g. from an OpenMapTiles/Mapsforge-tile-server build) | Real candidate |
| Commercial tile provider (e.g. a Thunderforest/MapTiler/Stadia-class vendor) | Keyed, paid third-party tile API | Real candidate, weak fit |
| Pre-built offline extracts distributed by the project | No live tile fetch; ships/updates `.map` or MBTiles bundles | Real candidate, pairs naturally with Mapsforge |

- **Public OSM tile server**: has a well-known, strict usage policy governing bulk/app-embedded
  use (no heavy automated use, valid `User-Agent` required, discourages high-volume third-party app
  distribution) — **not verified via context7 this session** (not queried directly); treat the
  exact current policy text as unverified and confirm it directly before relying on it for a public
  release. Zero hosting cost to the project; every request still carries the user's IP to a third
  party OSM infrastructure operator.
- **Self-hosted tile server**: full control over caching, logging policy (can commit to zero logs),
  and puts the base-map fetch behind infrastructure the project itself operates and can route
  behind the same relay chokepoint story as everything else — at the cost of real hosting
  cost/ops burden and storage for a planet or regional tile set, ongoing.
- **Commercial tile provider**: fastest to integrate, highest visual/data quality out of the box,
  but requires an account and API key (tension with the "no account-gated SDK" F-Droid target),
  costs money, and sends every viewport to a third party the project has a commercial rather than
  community relationship with — weakest fit for a privacy-first, unaffiliated app.
- **Pre-built offline extracts**: no live per-viewport exposure at all; cost is entirely in the
  extract build/hosting/update pipeline (occasional bulk downloads, not per-viewport streaming) —
  this is the natural pairing for Mapsforge and the only option that gets the v0.4+ "offline tile
  caching" roadmap item essentially for free in v0.1.

## Recommendation

osmdroid + a self-hosted or the public OSM tile server for v0.1, because it is the only rendering
library in this brief with a **verified** hook to route its networking through the app's own HTTP
client — directly satisfying the chokepoint invariant rather than requiring an unresolved
assumption about it. Cost: raster-only visuals, less modern than MapLibre's vector rendering, and
a real per-tile HTTP volume to manage (caching, rate limiting) whichever tile source is chosen.
Confidence: medium-high on osmdroid itself (the chokepoint fact is verified); low-medium on which
tile source, since the public server's exact usage policy is unverified in this session and the
self-hosted option's cost is unestimated.

Flag for the human: MapLibre Native's vector rendering is materially better visual quality and is
not ruled out — it is ruled *unconfirmed*. If the human wants MapLibre, the next step before
accepting it is a direct source-level (not docs-level) check of whether `FileSource`'s native HTTP
layer can be redirected through a proxy/relay; if it can, MapLibre becomes at least as strong a
candidate as osmdroid with better visuals. Mapsforge deserves separate consideration on its own
merits (zero live-tile exposure) independent of which HTTP-fetching library is chosen for anything
else, since it could be adopted specifically for the base map while osmdroid/MapLibre-style
libraries are not needed at all.

## Consequences

- Easy: enforcing the relay chokepoint for the base map (osmdroid path); swapping tile sources
  later without touching rendering code, since osmdroid's tile source is a configuration object.
- Hard: achieving MapLibre's visual quality without its unresolved networking risk; if the public
  OSM tile server is chosen, staying within its usage policy at any real user scale without
  self-hosting.
- To keep reversible: the map screen must depend on the `domain`-owned `TileProvider` abstraction
  (`docs/architecture/README.md` §2.1) — the canonical name for this port, not a separately-named
  `MapTileGateway`/`TileSource` type — not directly on osmdroid/MapLibre/Mapsforge types, so
  switching rendering libraries later is a `data`+`presentation` swap, not a domain change.

## What is needed from the human

1. Confirm or reject osmdroid as the rendering library given its verified chokepoint fit versus
   MapLibre's unverified one — or authorise the source-level investigation into MapLibre's
   `FileSource` networking before deciding.
2. Pick the tile source: public OSM server (accept its usage-policy constraints, verify them
   directly), self-host (accept the hosting/ops cost), or pre-built offline extracts (accept the
   extract pipeline cost, most naturally paired with Mapsforge instead of osmdroid/MapLibre).
3. If self-hosting or building an extract pipeline is chosen, the human provides or approves the
   hosting arrangement — this is infrastructure the project would operate, not a code-only choice.

## Reversibility

Rendering library: moderate cost if the `MapTileGateway` abstraction above is respected — the
protocol/rendering code changes, domain and routing/geocoding code do not. Tile source: low cost if
the source is a configuration value behind that same abstraction, regardless of which rendering
library is chosen. Switching from live tiles to offline extracts (or the reverse) is the most
expensive direction to reverse, since it changes what is stored on-device, what is downloaded in
bulk versus per-viewport, and the offline-capability story users are told about — treat that
specific sub-choice as the least reversible part of this decision.
