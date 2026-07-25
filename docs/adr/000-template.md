# NNN. &lt;Decision title&gt;

> Copy this file to `docs/adr/NNN-<title>.md` once the human has decided. Before that, the same
> content lives as a brief at `docs/adr/proposals/NNN-<title>.md` with status `proposed`. See
> [README.md](README.md) for the full lifecycle and the full status vocabulary. Delete this
> blockquote and every `<...>` placeholder when filling the template in.

## Status

`proposed` | `accepted` | `superseded by NNN`

Date: `<YYYY-MM-DD>` — Decided by: `<human>` (agents propose, never decide; CLAUDE.md §0.2).

## Context

<What forces this decision now — which milestone or feature depends on it, what breaks if it stays
open, what else in `docs/adr/` this touches.>

## Decision drivers

Score every option against the locked constraints (CLAUDE.md §0.1) and the project's stated
priorities. Use only the drivers that actually discriminate between the options; drop the rest.

- Privacy (what leaves the device, to whom, how identifying)
- Works fully without Google Play Services (including transitive dependencies)
- Resource cost (battery, memory, APK size, CPU)
- GPL-3.0-compatible licence
- F-Droid-compatible (reproducible build, no proprietary blob, no account-gated build dependency)
- Maintenance and community health (verified, not assumed)
- Development speed
- Testability
- Reversibility (cost of changing this decision later)

## Options

| Option | Summary | Verdict |
|---|---|---|
| `<A>` | | |
| `<B>` | | |
| `<C>` | | |

### `<Option A>`

- Pros:
- Cons:
- Privacy impact:
- Resource impact:
- Licence:
- Google Play Services dependency (verified/unverified):
- Maintenance status (verified/unverified):
- What it forecloses:

### `<Option B>`

(same structure)

## Decision

<The option chosen, the reasoning, and the honest cost — not just the upside. State the confidence
level (high / medium / low) and why.>

## Consequences

- What becomes easy:
- What becomes hard:
- What must be abstracted now to keep this reversible later:

## What was needed from the human

<The precise question that was answered, and anything the human had to provide — hosting, an API
key, a policy call, a licence judgement.>

## Reversibility

<What changing this decision later would cost — code, data migration, user-visible behaviour,
re-review.>
