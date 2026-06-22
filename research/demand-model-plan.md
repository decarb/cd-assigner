# Implementation Plan: Demand Model + L0 Gap Ledger (Thok 25H)

_Phase 2 of the spec-driven workflow. Spec: `demand-model-spec.md`. Status: **DRAFT — awaiting approval**._

## Overview

Build the DEMAND side for Thok 25H — the typed, timed model of incoming damage that the **next**
session's schedule assigner (L1) will place cooldowns against. This session demonstrates the model
by combining it with the existing SUPPLY ledger into a **feasibility view** (`need/have/short`),
validating that DEMAND and SUPPLY are comparable before placement is built on it. The feasibility
view is a byproduct, **not** the product (which is the cooldown schedule) and **not** roster advice.
The categorisation of damage events is **derived from WCL** (joined to BigWigs spellIds), not
hand-authored — honoring the "WCL in the design" decision.

## Architecture Decisions

1. **The Thok encounter model is *generated from* WCL calibration, not hand-authored.** The
   categorisation pipeline (BigWigs spellIds ⋈ WCL DamageTaken → school/breadth/shape) runs
   once and writes `research/thok-heroic-calibration.json`, a **committed fixture**. The
   `EncounterModel` reads that fixture. This is what "involve WCL in the design" means
   concretely: hand-authoring is replaced by a deterministic join over log facts.
2. **The deterministic ledger is independently testable, offline.** `occurrences()`,
   `DemandLedger`, `GapLedger` are pure and tested against the committed fixture — no network in
   tests. WCL touches the build only at `calibrate` time (a dev step), never in the `gap` runtime
   path or the test suite.
3. **High-risk WCL work goes early (Phase 2), before the ledger depends on it.** If Classic's
   DamageTaken shape surprises us, we find out before building on it. The risk-free domain types
   (Phase 1) come first only because everything references them.
4. **Reuse, don't fork, the SUPPLY side.** `GapLedger` diffs against the existing
   `SupplyLedger.chargesByType`; no changes to SUPPLY public types (Boundary: ask first if needed).

## Dependency graph
```
domain types (T1) ─┬─ occurrences (T2) ───────────────┐
                   └─ EncounterModel (T6) ─ DemandLedger (T7) ┐
WCL un-stash (T3) ─ queries (T4) ─ calibration JSON (T5) ─┘    ├─ GapLedger (T8) ─ gap CLI (T9)
                                                  SupplyLedger ─┘   (existing)
```

## Task List

### Phase 1 — Deterministic foundation (offline, TDD)

**Task 1 — Encounter domain types.** `encounter/DamageEvent.scala`: `School(Magic|Physical)`,
`Shape(Spike|Sustained)`, `Cadence(Flat(s) | Accelerating(initial, ramp:List[Double]))`,
`Severity`, `DamageEvent{id,spellId,phase,school,shape,cadence,coverageRequired:Map[CoverageType,Int],severity}`.
Reuses existing `CoverageType`. *Acceptance:* compiles; can express Thok's Deafening Screech
(magic/spike/accelerating) + Blood Frenzy (physical/sustained). *Verify:* `scala-cli compile .`.
*Deps:* none. *Files:* 1. *Scope:* S.

**Task 2 — `occurrences()` + tests (TDD).** `encounter/Occurrences.scala`,
`test/.../OccurrencesTest.scala`. Flat → `floor(T/r)+1`; Accelerating → walk the ramp
(`{10.9,7.2,4.8,3.6}`) accumulating until the phase window closes. *Acceptance:* accelerating
case returns a count that flat math would get wrong; boundary/off-by-one covered. *Verify:*
`scala-cli test . ` (OccurrencesTest green). *Deps:* T1. *Files:* 2. *Scope:* S.

> **Checkpoint A:** `scala-cli test .` green; types model both Thok events.

### Phase 2 — WCL categorisation pipeline (design crux; highest risk)

**Task 3 — Un-stash WCL + Encounter enums.** Move `wcl/WclClient.scala`, `config/Env.scala`,
`domain/Encounter.scala` from `/home/claude/cd-assigner-deferred/` into the repo. *Acceptance:*
compiles; auth smoke fetches a token with the secret never printed. *Verify:* `scala-cli compile .`;
manual token smoke. *Deps:* none. *Files:* 3. *Scope:* S. **(Boundary: un-stashing WCL — flagged, approved via this plan.)**

**Task 4 — WCL queries (kill time + damage taken).** `wcl/FightsQuery.scala` (encounter 51599:
kill time + phaseTransitions), `wcl/DamageTakenQuery.scala` (`table(dataType:DamageTaken)` per
ability → school, total, hitCount; breadth = unique players hit). *Acceptance:* against a public
25H Thok report, returns per-spellId rows incl. school + breadth. *Verify:* manual run prints the
Deafening Screech row. *Deps:* T3. *Files:* 2. *Scope:* M. **(Highest risk: Classic data shape.)**

**Task 5 — Calibration join → committed fixture.** `calibrate/Calibration.scala` + a `calibrate`
CLI subcommand: pull a top 25H Thok report via `encounter(51599).characterRankings`, join the
BigWigs Thok spellIds (`research/bigwigs/Thok.lua`) ⋈ DamageTaken, filter `breadth ≥ threshold`,
classify school→DR-type and shape, write `research/thok-heroic-calibration.json`. *Acceptance:*
JSON lists Deafening Screech + Blood Frenzy as raidwide (with school/breadth/magnitude/occurrence
count); all tank breaths (`143780/143773/143767`, Fearsome Roar) excluded by the breadth filter.
*Verify:* inspect committed JSON; assert externals absent. *Deps:* T4. *Files:* 2 + fixture.
*Scope:* M.

> **Checkpoint B:** calibration JSON committed; categorisation correct (Screech=magic raidwide;
> breaths dropped). **Review with human** — this is the artifact embodying the WCL-design decision.

### Phase 3 — Ledger end-to-end

**Task 6 — ThokHeroic model from calibration.** `encounter/EncounterModel.scala`,
`encounter/ThokHeroic.scala`: load the calibration JSON → `DamageEvent`s, taking cadence *shape*
from BigWigs `accTimes` and counts/magnitude from WCL. *Acceptance:* model exposes the 2 raidwide
events, typed, no externals. *Verify:* `scala-cli test .` (model load test). *Deps:* T1, T5.
*Files:* 2. *Scope:* S.

**Task 7 — DemandLedger + tests (TDD).** `encounter/DemandLedger.scala`, test. `DEMAND(type) =
Σ occurrences(e,T) × coverageRequired(e,type)`. *Acceptance:* per-type demand computed for Thok
over a given T; tested against the fixture. *Verify:* `scala-cli test .`. *Deps:* T2, T6. *Files:* 2. *Scope:* S.

**Task 8 — GapLedger + GapReport + tests.** `ledger/GapLedger.scala` (diff DEMAND vs existing
`SupplyLedger.chargesByType`), `ledger/GapReport.scala` (text render mirroring `SupplyReport`),
tests for shortfall/surplus/exact-match. *Acceptance:* `{need,have,short}` per type. *Verify:*
`scala-cli test .`. *Deps:* T7 + existing SupplyLedger. *Files:* 3. *Scope:* M.

**Task 9 — `gap` CLI command.** Wire `gap --roster <f> --boss thok --kill-time <s|m:ss>` into
`Main.scala`. *Acceptance:* success-criteria #1 — prints the per-type need/have/short table on the
live 25-person roster, with a non-trivial shortfall/surplus. *Verify:* run on
`roster-1515463310269747423.json`. *Deps:* T8. *Files:* 1. *Scope:* S.

> **Checkpoint C (Complete):** spec success criteria 1–6 met; `scala-cli test .` green; `fmt` +
> `fix --power` clean → code-review-and-quality pass → conventional commit. WCL secret never printed.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Classic DamageTaken shape / breadth field differs from retail v2 | High | Phase 2 first (fail fast); spike with one query in T4 before building T5 |
| Deafening Screech damage spellId ambiguous vs Fearsome Roar `143426` | Med | Resolve empirically from the log's DamageTaken rows in T4/T5 |
| Accelerating cadence ↔ WCL occurrence count disagree | Med | WCL count is source of truth for occurrences; BigWigs ramp only seeds shape when no log |
| Public log comp differs from guild's (magnitude off) | Low | Counts/breadth/school transfer; magnitude only orders severity in v1 |
| WCL points-based rate limit during calibrate | Low | Prefer aggregate `table` over raw `events`; cache to committed JSON, query once |

## Open Questions
- Raidwide breadth threshold value (proposed ≥ 60% = 15/25) — confirm or tune against the real log (Boundary: ask-first).
- Blood Frenzy: `dr_physical` demand or pure `aoe_heal` throughput? (resolve in T5 from magnitude/shape).
