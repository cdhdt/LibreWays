# 004. Geocoding provider

## Status

`proposed`

Nothing in this document is authority until the human accepts it and it is recorded as an accepted
ADR per `CLAUDE.md` §0.2. **The backend is still open; the autocomplete-vs-explicit-submit UX
question this brief originally flagged as gating the backend choice is no longer open — see
"The autocomplete design driver — decided" below.**

## Context

v0.1 ("A to B") requires the user to enter a destination as free text and have it resolved to a
coordinate. This flow is not one of the four data flows the product brief originally confirmed — it
is implied by "enter a destination" and is, per the brief, arguably the single most sensitive flow
in the app: **the destination search string reveals user intent before the trip even starts**, in a
way that raw coordinates on a route do not (a search for a named place — a clinic, a shelter, an
attorney's office, a competitor's address — discloses far more than a lat/lon pair on its own ever
would).

**The maintainer has since decided the UX question this brief originally left open**: destination
search is search-as-you-type with debounce, not explicit-submit (see "The autocomplete design
driver — decided" below for the exact mitigations required). That decision **multiplies exposure**
relative to explicit-submit — every debounced keystroke burst becomes a separate outbound
partial-string request, so the provider sees a meaningful part of the user's drafting process, not
just the final choice — and it **narrows which backends remain viable**, since a backend whose
usage policy forbids autocomplete is effectively excluded regardless of its other merits. This
brief evaluates the backend under that now-decided constraint.

## Decision drivers

- **Privacy — treat as dominant, same as proposal 003**: what is sent (a raw search string, not a
  coordinated pair, but arguably more revealing of intent), to whom, how often, and whether it is
  the final query only or every keystroke.
- Works fully without Play Services.
- Resource cost: negligible on-device either way (a text field and a network call); the real cost
  is per-request volume if autocomplete is used.
- GPL-3.0-compatible licence — **only relevant to an on-device option** that links a library into
  the app; self-hosted and public/third-party instances are contacted over HTTP and their own
  server-side licence does not propagate into the app.
- F-Droid-compatible: reproducible build, no account-gated dependency.
- Maintenance and community health (verified where checked).
- Development speed.
- Testability.
- Reversibility.

## Options

| Option | Summary | Verdict |
|---|---|---|
| Public Nominatim instance (`nominatim.openstreetmap.org`) | Community-run default OSM geocoder | Real candidate, policy-constrained |
| Self-hosted Nominatim | Project runs its own instance | Real candidate |
| Self-hosted Photon | Project runs its own instance (OpenSearch-backed) | Real candidate |
| On-device geocoding data | No live network call at all | Weak — no mature ready-to-use library identified |
| Third-party keyed API (e.g. a Pelias-hosted or commercial geocoding provider) | Account-gated commercial/public API | Real candidate, weakest privacy fit |

### Public Nominatim instance — effectively excluded now that autocomplete is decided

- Pros: zero hosting cost or ops burden to the project; uses the reference OSM geocoder
  implementation; no separate account/API key.
- Cons: **verified directly against the published Nominatim Usage Policy**
  (`operations.osmfoundation.org/policies/nominatim/`) as part of this fix pass — the policy states
  in its own words: *"Auto-complete search: This is not yet supported by Nominatim and you must not
  implement such a service on the client side using the API"*, and separately caps general use at
  *"No heavy uses (an absolute maximum of 1 request per second)."* This is no longer an unverified,
  commonly-cited claim — it is confirmed, verbatim, from the policy's own current text. **Now that
  the maintainer has decided v0.1 destination search is search-as-you-type (see "The autocomplete
  design driver — decided" below), this option is effectively excluded for that flow**: using it
  would mean either violating the published policy or falling back to explicit-submit for this
  backend alone, which is not the product decision. It could, in principle, still serve some
  other explicit-submit-only lookup if one existed, but no such use is currently planned.
- Privacy impact: every search string plus the user's IP goes to OSM Foundation-operated
  infrastructure the project does not control; no ability to promise a no-logs policy on the
  project's behalf.
- Resource impact: none on-device beyond the request itself.
- Licence: not applicable to the app (HTTP call only); Nominatim's own software licence was not
  independently confirmed this session (commonly documented as GPL-2.0, **unverified** here) — moot
  for this option regardless, since nothing is linked into the app.
- Google Play Services dependency: none.
- Maintenance status: actively maintained reference implementation (context7 source reputation
  "High" across its documentation sets); this reflects the software's health, not the specific
  public instance's operational policy.
- What it forecloses: autocomplete-style UX (**verified** prohibited by the policy's own text, not
  merely suspected) and any high-volume usage without the project standing up its own instance
  regardless.

### Self-hosted Nominatim

- Pros: project fully controls logging policy (can commit to zero logs) and rate limiting toward
  its own users rather than being bound by a shared community policy; no third-party account or key;
  can be placed behind the same relay chokepoint as every other outbound call.
- Cons: heavier operational footprint than Photon in practice — a Nominatim import requires a
  PostgreSQL/PostGIS database built from an OSM extract, with a nontrivial import process and
  ongoing update/re-import cadence the project must own; this is a standing infrastructure and
  hosting-cost commitment (same shape of trade-off as the self-hosted routing option in proposal
  003).
- Privacy impact: search strings reach project-operated infrastructure only, under a policy the
  project sets and can be honest about in `docs/privacy.md`.
- Resource impact: none on-device.
- Licence: not applicable to the app.
- Google Play Services dependency: none.
- Maintenance status: same as above — the reference implementation is actively maintained; this
  option's specific operational burden (import size, RAM/disk requirements) was not benchmarked
  this session.
- What it forecloses: nothing architecturally.

### Self-hosted Photon

- Pros: **verified** — self-hosting is documented as straightforward using prebuilt release
  binaries plus weekly-updated database dumps provided by GraphHopper, which materially lowers the
  ongoing update-cadence burden compared to Nominatim's own import pipeline; **verified** — Photon
  explicitly supports search-as-you-type/autocomplete via its query builder's "suggest addresses"
  mode — now directly relevant given the maintainer has decided v0.1 destination search is
  autocomplete-style, Photon is a backend already built for it (a self-hosted instance also
  sidesteps the public Nominatim policy question entirely, since the project sets its own policy).
- Cons: backed by Elasticsearch/OpenSearch, a heavier runtime dependency than Nominatim's
  PostgreSQL, though offset by not needing to run the import pipeline oneself if using the weekly
  dumps; **verified** — the public demo instance (`photon.komoot.io`) exists but is explicitly
  documented as subject to throttling or an outright ban under heavy use, so it is not a viable
  production backend for the app without the project running its own instance.
- Privacy impact: same shape as self-hosted Nominatim — project-operated infrastructure, project's
  own logging policy.
- Resource impact: none on-device; ongoing hosting cost for the OpenSearch-backed server.
- Licence: not applicable to the app (HTTP call only); Photon's own licence is commonly documented
  as Apache-2.0 but was **not independently confirmed via a dedicated license query this session**
  — mark unverified.
- Google Play Services dependency: none.
- Maintenance status: **verified** actively maintained (context7 source reputation "High"), with
  weekly data refreshes as part of its normal operating model.
- What it forecloses: nothing architecturally; already supports the now-decided autocomplete-style
  UX natively, unlike self-hosted Nominatim's unverified equivalent capability.

### On-device geocoding data

- No mature, ready-to-use FOSS Android library equivalent to Nominatim/Photon for offline place-name
  search was identified. Building one would mean constructing and maintaining a custom offline
  address index from OSM extracts from scratch — a substantially larger, less-precedented
  engineering effort than the on-device routing option in proposal 003 (which at least has
  GraphHopper/Valhalla as existing engines to embed). **Flagged as weak**: not ruled impossible, but
  no realistic library candidate exists to recommend today; the human should not expect this to be
  a v0.1-viable option without a dedicated, separately scoped research spike.

### Third-party keyed API

- E.g. a commercially hosted Pelias instance, or a keyed public geocoding API. Typically requires
  account registration and an API key (tension with the "no account-gated SDK" F-Droid target, a
  judgement call for the human, not a settled disqualification). Sends every query — and every
  keystroke, if combined with autocomplete UX — to a third party with no logging-policy control by
  the project. Weakest privacy fit of any option, for the same reasons as the equivalent option in
  proposal 003.
- Privacy impact: worst case of the option set, and worst specifically if paired with
  autocomplete-style UX.
- Licence/GMS: vendor-dependent, not evaluated generically here — would need per-vendor
  verification if pursued.

## The autocomplete design driver — decided

**The maintainer has decided: v0.1 destination search is search-as-you-type with debounce, not
explicit-submit.** Required mitigations, now spec requirements with tests (set by the orchestrator
as defaults, revisable by the maintainer): a minimum input length of **3 characters** before any
request is sent; a **600 ms** debounce after the last keystroke; the in-flight request is cancelled
when the input changes again before it returns; no request is sent for whitespace-only input.

Documented honestly: each debounced keystroke burst still produces a geocoding query, and partial
strings — including text the user typed and then deleted — reach the backend. This is a materially
larger disclosure than a single explicit-submit query per trip would have been; the mitigations
above bound it (no per-character request, a minimum string length, no dead-air polling) but do not
eliminate it. `docs/privacy.md` records this trade-off in the outbound-flow table; this document
records its one consequence for the backend choice: **a backend whose usage policy forbids
autocomplete is effectively excluded**, narrowing the viable options toward a self-hosted geocoder.

## Recommendation — re-scored for the now-decided autocomplete requirement

Given autocomplete is decided, the public Nominatim instance is excluded for this flow (**verified**
policy prohibition, see above) and a third-party keyed API remains the weakest privacy fit
regardless of the UX question. **This narrows the viable options to a self-hosted instance —
Photon or Nominatim, either technically viable — operated by the project and reached only through
the mandatory relay/proxy chokepoint (proposal 007).** Between the two, worth re-noting now that
autocomplete is required rather than optional: Photon's query builder has a **verified**,
purpose-built "suggest addresses" autocomplete mode; self-hosted Nominatim's autocomplete-serving
capability was not independently verified in this brief (only the public instance's *prohibition*
on it was checked) and would need confirming before relying on it for this flow. This is a
technical-capability data point for whoever picks the backend, not a recommendation of one over
the other — **the backend choice itself remains the maintainer's decision, not settled here.**

Cost, stated honestly: a standing hosting/ops commitment for whichever self-hosted instance is
chosen (same shape of cost as the self-hosted routing fallback in proposal 003 — the human may want
to weigh whether both should share infrastructure), plus the per-keystroke-burst query volume
described above. Confidence: high on "self-hosted, not public, not third-party" now that
autocomplete is decided and the public instance's policy is verified; medium on Photon vs
Nominatim specifically, since neither was benchmarked for actual hosting cost, query latency, or
(for Nominatim) autocomplete-serving capability this session.

## Consequences

- Easy: swapping Photon for Nominatim or vice versa later if both sit behind the same interface.
- Hard: the exposure from autocomplete is now a shipped product decision, not a deferred question —
  revisiting it later means re-auditing the whole exposure story in `docs/privacy.md` again, not a
  routine UI change; standing up and maintaining self-hosted geocoding infrastructure indefinitely.
- Must be abstracted now to stay reversible: the `domain`-owned `PlaceSearchProvider` interface
  (`docs/architecture/README.md` §2.1 — the canonical name for this port across the project;
  earlier revisions of this brief called it `GeocodingProvider`, a name this document no longer
  uses), implemented in `data`, so switching between Photon, Nominatim, or (if it ever matures) an
  on-device index is a `data`-layer change only. The debounce/minimum-length/cancellation logic
  belongs in `domain` (a function of keystroke timing to whether a request is issued), independent
  of which backend implements `PlaceSearchProvider`.

## What is needed from the human

1. **Confirm the backend**: self-hosted Photon or self-hosted Nominatim (either viable; Photon's
   autocomplete support is verified today, Nominatim's is not yet checked) — self-hosting requires
   the human to provide or approve hosting. The public instance and third-party vendors remain not
   recommended for the reasons above.
2. If a third-party vendor is wanted despite the analysis above, name it so its account/API-key
   terms and GMS exposure can be verified.

The autocomplete-vs-explicit-submit question that previously blocked this list is already decided
(see above) and does not need to be asked again.

## Reversibility

Switching between self-hosted Photon and self-hosted Nominatim later is a bounded `data`-layer and
infrastructure change if the interface above is respected — no privacy story changes, since both are
project-operated. Switching from self-hosted to a third-party API later is more disruptive: it
changes the privacy story the app has told its users and requires a full `docs/privacy.md` update
and re-review. Switching away from the now-decided autocomplete UX to explicit-submit later (or the
reverse, if a future milestone changed it again) is the least reversible change in this whole
document, since it changes what has already been promised to users about how many queries leave the
device per search — treat any change to that decision as a one-way door requiring a fresh privacy
review, not a routine UI change.
