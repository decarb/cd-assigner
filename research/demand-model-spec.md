# Spec: Demand Model + L0 Gap Ledger (Thok the Bloodthirsty, 25H)

_Status: **DRAFT — awaiting review** (Phase 1 of spec-driven workflow). Created 2026-06-22._
_Supersedes the "hand-authored only" assumption: WCL is now part of the design._

## Objective

**Product end goal (not this session):** given a roster + a per-encounter demand model, output a
**recommended cooldown schedule** — *which player presses which cooldown at which moment*.
Supply-vs-demand is the engine's **internal knowledge** that drives that placement; it is **not**
the output, and the tool does **not** give roster-composition advice ("bring another Disc priest").
The assignment/schedule (L1) is the **next** session.

**This session:** build the **DEMAND** side of the engine for **one boss authored end-to-end**
(Thok the Bloodthirsty, **Heroic / 25-player**) — the typed, timed model of incoming damage the
assigner will place cooldowns against. Demonstrate it by combining with the existing **SUPPLY**
ledger into a **feasibility view** (per-type `need / have / short`), which validates the demand
model end-to-end before placement is built on top of it.

**User:** the raid leader, currently progressing Heroic Thok. **Success looks like:** a correct,
typed, time-aware demand model for Thok 25H, and a feasibility command that proves DEMAND and
SUPPLY are comparable — the foundation the schedule assigner consumes next.

**Scope this delivery:**
- IN: encounter model schema; Thok 25H authored; BigWigs⋈WCL damage-event categorisation;
  raidwide DEMAND computation; L0 gap report CLI.
- OUT (explicit): externals / single-target tank cooldowns; L1 assignment/placement;
  magnitude-vs-throughput sufficiency; bosses other than Thok; live per-run WCL fetching.

### Why BigWigs **and** WCL (the core design decision)

Neither source is sufficient alone, and Thok proves it:

| Source | Provides | Thok example |
|---|---|---|
| **BigWigs** (skeleton) | spellIds, ability existence, **cadence shape** | Deafening Screech `Acceleration` ramps the interval `14 → {10.9, 7.2, 4.8, 3.6}`s |
| **WCL** (calibration) | school, magnitude, hit count, **breadth** (players hit), real **occurrence count**, kill time, phase lengths | "Screech fired 7× in P1, magic, hit 24/25 players for ~X each" |

The **join key is spellId** — every BigWigs `self:Log("SPELL_*", …, spellId)` registration
maps 1:1 to a WCL `DamageTaken` ability entry. This makes categorisation **deterministic**;
AI is not involved in this delivery.

**Categorisation pipeline:**
```
BigWigs spellIds (+ cadence shape, phase triggers)
   ⋈ join on spellId
WCL DamageTaken (school, magnitude, hitCount, uniqueTargets=breadth) per ability
   ↓ filter: breadth ≥ RAIDWIDE_THRESHOLD   → drops tank/external events automatically
   ↓ classify school → dr_magic | dr_physical ;  shape (hitCount vs duration) → spike | sustained
   ↓ occurrences(event, T) from cadence shape, calibrated/clamped by WCL count
   = typed DEMAND events  →  diff against SUPPLY ledger  →  L0 gap report
```

### Thok raidwide events (authored target)

| Event | spellId | Stage | School | Shape | coverage_required (draft) |
|---|---|---|---|---|---|
| Deafening Screech | `-7963` group (cast `143426`?) | 1 | magic | spike, **accelerating** | `{dr_magic:1, aoe_heal:1}` |
| Blood Frenzy | `143440` / `143442` (dose) | 2 | physical | sustained ramp | `{aoe_heal:1, dr_physical:?}` |

Dropped by breadth filter (tank/target, out of scope): Fearsome Roar `143426`*, Acid Breath
`143780`, Freezing Breath `143773`, Scorching Breath `143767`, Fixate `143445`, Frozen Solid
`143777`. *(\*Screech vs Roar spellId overlap is an open question — see below.)*

## Tech Stack

Unchanged from project (`CLAUDE.md`): Scala 3 / scala-cli, cats-effect, circe, sttp client4,
decline-effect, munit. WCL access reuses the **stashed** `wcl/WclClient.scala` + `config/Env.scala`
(OAuth2 client-credentials, GraphQL v2) — un-stashed in this delivery.

## Commands

```
Build:  scala-cli compile .
Test:   scala-cli test .
Format: scala-cli fmt .
Lint:   scala-cli fix --power .
Run (new):  scala-cli run . -- gap --roster <roster.json> --boss thok --kill-time <s|m:ss>
WCL author (dev-only, reads .env): scala-cli run . -- calibrate --boss thok --log <wcl-report-id>
```

## Project Structure (additions)

```
src/io/github/decarb/cdassigner/
  domain/Encounter.scala      → un-stash: Raid/Boss enums (14 SoO bosses)
  encounter/
    DamageEvent.scala         → typed event {id, spellId, phase, school, shape, cadence, coverage_required, severity}
    EncounterModel.scala      → per-boss list of DamageEvents + phase model; ThokHeroic authored here
    Occurrences.scala         → occurrences(event, T): handles flat AND accelerating cadence
    DemandLedger.scala        → DEMAND(type) = Σ occurrences × coverage_required
  wcl/                        → un-stash WclClient; add DamageTakenQuery (table+events), FightsQuery
  calibrate/Calibration.scala → join BigWigs spellIds ⋈ WCL DamageTaken → breadth/school/magnitude
  ledger/GapLedger.scala      → DEMAND ⋈ SUPPLY → per-type {need, have, short}
  ledger/GapReport.scala      → text render (mirrors SupplyReport)
research/
  thok-heroic-calibration.json → cached WCL-derived facts (committed; avoids re-querying)
```

## Code Style

Mirror existing SUPPLY code (`supply/CooldownKb.scala`, `domain/CoverageType.scala`):

```scala
enum Cadence:
  case Flat(seconds: Double)
  case Accelerating(initial: Double, ramp: List[Double]) // BigWigs accTimes, e.g. List(10.9, 7.2, 4.8, 3.6)

final case class DamageEvent(
    id: String,
    spellId: Int,
    phase: Int,
    school: School,            // Magic | Physical
    shape: Shape,              // Spike | Sustained
    cadence: Cadence,
    coverageRequired: Map[CoverageType, Int],
    severity: Severity,
)

// occurrences: deterministic, pure, unit-tested. Accelerating cadence walks the ramp until T.
def occurrences(e: DamageEvent, phaseWindow: FiniteDuration): Int = ...
```

## Testing Strategy

munit, tests under `test/…` mirroring source packages. Pure functions get exhaustive cases:
- `OccurrencesTest` — flat cadence `floor(T/r)+1`; **accelerating** cadence walks `{10.9,7.2,4.8,3.6}`
  and stops at the phase window; off-by-one at boundaries.
- `DemandLedgerTest` — DEMAND sums occurrences × coverage_required per type.
- `GapLedgerTest` — DEMAND vs SUPPLY arithmetic incl. shortfall, surplus, exact-match.
- Calibration join tested against the **committed** `thok-heroic-calibration.json` fixture (no
  live WCL in tests — tests stay offline/deterministic).

## Boundaries

- **Always:** keep solver/ledger logic deterministic and offline-testable; run `scala-cli test .`
  before commit; conventional commit + bulleted body, no `Co-Authored-By`.
- **Ask first:** un-stashing WCL code into the repo; adding OR-Tools or any new dependency;
  changing the SUPPLY-side public types; picking the raidwide breadth threshold value.
- **Never:** read/print `.env` or WCL secrets; commit a real log's raw player data beyond the
  derived calibration facts; let AI author coverage_required in this delivery.

## Success Criteria

1. `scala-cli run . -- gap --roster roster-<id>.json --boss thok --kill-time 6:30` prints a
   per-type table: `aoe_heal / dr_magic / dr_physical / dr_all` with `need / have / short`.
2. Deafening Screech occurrences use the **accelerating** cadence, not flat — verified by a test
   that flat-math would fail.
3. The breadth filter drops all tank/external events from Thok's module (0 externals in DEMAND).
4. DEMAND for at least one type, on the live 25-person roster, produces a **non-trivial shortfall
   or surplus** the raid leader can act on (validates the model end-to-end).
5. `scala-cli test .` green; new tests cover occurrences (incl. accelerating), demand, gap math.
6. WCL secret never printed; calibration facts cached to a committed JSON, queries not re-run in tests.

## Open Questions (need human / research before/within Plan)

1. ~~**Reference Heroic Thok log URL**~~ **RESOLVED** — self-served via the API: query
   `worldData.encounter(51599).characterRankings` to pull a top public 25H report code +
   damage tables. No user-provided URL required.
2. ~~**WCL MoP-Classic encounter/zone IDs**~~ **RESOLVED (verified 2026-06-22 via WCL Classic
   rankings)** — SoO Classic **zone = `1054`**, Thok 25H **encounter = `51599`** (BigWigs
   `engageId=1599` + Classic `5` prefix). Source: classic.warcraftlogs.com/zone/rankings/1054?boss=51599
3. **Deafening Screech spellId** — module bars it via section id `-7963`; the actual damage spellId
   (vs Fearsome Roar `143426`) must be resolved from the log's DamageTaken table.
4. **Raidwide breadth threshold** — propose ≥ 60% of raid (≥15/25). Tune against the real log.
5. **Blood Frenzy coverage typing** — physical raid ramp; confirm whether it demands `dr_physical`
   or is purely an `aoe_heal` throughput problem (likely the latter at count-granularity).
```
