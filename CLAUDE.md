# CD Assigner

A tool that auto-suggests **raid cooldown assignments** for WoW **Mists of Pandaria (5.4.8) Siege of
Orgrimmar**. Input: a raid roster (Raid-Helper API or manual). Output: suggested cooldown assignments
for a boss. Extensible to other instances.

**Core insight:** existing tools (Viserio, the raid leader's Excel sheet) are *manual* placement.
Nobody auto-solves the assignment — automating that is the whole point. The engine is a deterministic
**supply-vs-demand / interval-scheduling** problem; AI is bounded to labeling log data and explaining,
**not** doing the assignment.

## Read first
- `HANDOVER.md` — full project state, decisions, verified facts, next steps. **Start here.**
- `research/engine-design.md` — the suggestion engine (the crux).
- `research/logs-pipeline.md` — Warcraft Logs-driven coverage model.
- `research/cooldown-kb.md` — MoP raid cooldown data.

## Scope
- v1 is **roster-in → suggestions-out only**. WeakAuras export is **out of scope**.

## Secrets — strict
- WCL API credentials live in `.env` (gitignored): `WCL_CLIENT_ID`, `WCL_CLIENT_SECRET`.
- **Never** `cat`/Read/print `.env` or its values. Load credentials via environment at runtime only.
- **Never** ask the user to paste secrets into chat — they edit `.env` directly in their editor.

## Verified data sources (see HANDOVER.md for detail)
- Roster: `GET https://raid-helper.dev/api/raidplan/{id}` (primary) / `.../api/event/{id}` (fallback).
  Needs a `(className, specName) → canonical {class, spec}` normalization table.
- Timers: `BigWigsMods/BigWigs_MistsOfPandaria` repo (one Lua file per boss).
- Coverage/kill-time/phases: Warcraft Logs API v2 (GraphQL, OAuth2 client credentials).

## Stack
- **Scala 3 via scala-cli**, script-first. Conventions mirror `~/scala-cli/ranking-table`:
  cats-effect, opaque types for domain modelling, `-Wunused:all`, packages under
  `io.github.decarb.cdassigner`, munit + munit-cats-effect for tests.
- HTTP + JSON: **sttp client4 (cats-effect backend) + circe**.
- L2 solver (optional, later): TBD — OR-Tools has no first-class Scala binding, so likely a
  hand-rolled greedy L1, with L2 revisited if needed.

## Current scope — roster `pull` only
The initial phase is **only** the pull stage: fetch a Raid-Helper roster (by URL or id), normalize
to the canonical domain model, save as JSON. Everything else (WCL logs, the assign engine, the
boss/encounter model) is deferred. Deferred code is stashed at `/home/claude/cd-assigner-deferred/`
(WCL client, `.env` loader, `Encounter` enums) — restore when those phases begin.

## Build / run
- Compile: `scala-cli compile .`  · Test: `scala-cli test .`
- `scala-cli run . -- pull <raidplan-url-or-id> [-o out.json]` — fetch + normalize + save.
  Accepts a full `raid-helper.xyz/raidplan/<id>` URL or a bare id; defaults output to `roster-<id>.json`.

## Roster normalization
- Canonical model: `domain/{PlayerClass,Spec,Roster}.scala`. Fetch (`raidhelper/`) +
  `(className,specName)→canonical` table in `roster/RosterNormalizer.scala`; unknown pairs are
  collected as explicit errors. `className="Tank"` collapses class into spec; Raid-Helper digit
  suffixes (`Holy1`, `Protection1`) are stripped. `"Smite"`→discipline and Paladin-Protection are
  unconfirmed (marked VERIFY). Verified live against a real 25-person roster.

## Conventions
- Keep deterministic logic and AI/LLM calls cleanly separated — the solver must stay deterministic.
