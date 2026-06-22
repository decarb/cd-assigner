# Spec: Cooldown Scheduler (top-level product)

_Design doc. Answers to `docs/intent/cd-assigner.md` (the north star). Draws on `research/` as a
knowledge base only — research never drives this design. Living document: when the design changes,
this file changes first, then the code. Status: **APPROVED 2026-06-22** (Phase 1 of
spec-driven-development complete; Phase 2 Plan is next)._

## Objective

Given a **roster** and an **encounter**, auto-suggest an **optimal raidwide cooldown schedule** —
which player presses which raid cooldown, and when — that the raid leader runs *instead of* hand-
placing CDs in a spreadsheet.

- **User:** the raid leader, weekly, on raid night.
- **"Optimal" =** mathematically **maximize damage mitigated assuming perfect play**. A measurable
  objective, not a style-match to what a pro would draw.
- **Logs =** the **warm-start prior**, never the objective. Patterns mined from logs with similar
  kill-times (and, in progression, good guilds) seed the scheduler with a solid starting basis.
- **Hard constraint — explicability:** every suggested assignment must be defensible from code and
  logical reasoning ("X covers this cast because it is the only `dr_magic` charge available in this
  window"). No black-box output. Inexplicable scheduling fails the product.

### The pipeline (scheduler-headlined)

The scheduler is the product; the rest are typed **inputs** to it:

```
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │    Supply    │   │    Demand    │   │  Logs prior  │
  │    ledger    │   │ (per         │   │ (warm start) │
  │   (BUILT)    │   │  encounter)  │   │              │
  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
         │                  │                  │
         └──────────────────┼──────────────────┘
                            ▼
                  ┌───────────────────┐
                  │     SCHEDULER     │   maximize damage mitigated, explicable
                  │      (goal)       │
                  └─────────┬─────────┘
                            ▼
   Schedule: (player × cooldown × timestamp)
             + rotation overlay + per-assignment rationale
```

- **Supply ledger** — per roster: available cooldowns and their charge count over kill-time `T`,
  typed by `CoverageType`. **Built & tested** (`supply/`).
- **Demand model** — per encounter: the dangerous raidwide damage events, each with timing
  (BigWigs skeleton ⋈ WCL calibration), magnitude, school, breadth, and a `coverage_required`
  typing. **In progress** (Thok 25H). Raidwide only; externals out of scope.
- **Logs prior** — patterns from comparable logs used to warm-start placement, never to define the
  objective.

### Engine layers (build order, deterministic throughout)

- **L0 — ledger / feasibility:** supply vs demand per coverage type. A *stepping stone* that
  validates the model and surfaces "this cast is uncovered." **Not the deliverable.**
- **L1 — greedy assigner:** the first real schedule. Produces assignments + rationale.
- **L2 — CP-SAT solver (optional):** only if L1 schedule quality demands it. Stays deterministic.

AI is bounded to **labeling** log/encounter data (avoidability, severity, draft `coverage_required`)
and **explaining** output. AI never performs the assignment.

### Output representation (decided)

- **Absolute timestamp is canonical** — every assignment is grounded in a time (seconds from pull),
  derived from the demand model's mechanic-cast times, so it is explicable by construction. Computed
  against a stated **reference kill-time `T`**.
- **Rotation overlay is derived** — wherever timestamped assignments group into a repeating order
  (e.g. per phase), the schedule also presents that rotation, because it is robust to kill-time
  variance and matches how RLs think.
- **Per-assignment rationale** — each assignment carries a one-line, code-grounded reason.

## Tech Stack

Unchanged from `CLAUDE.md`: **Scala 3 / scala-cli**, script-first, cats-effect, opaque types,
`-Wunused:all`, package root `io.github.decarb.cdassigner`, decline-effect CLI, sttp client4
(cats backend) + circe, munit tests. The scheduler is **pure deterministic logic** (cats-effect only
at the IO edges).

## Commands

```
Compile: scala-cli compile .
Test:    scala-cli test .
Format:  scala-cli fmt .
Lint:    scala-cli fix --power .
Run (existing):
  scala-cli run . -- pull <raidplan-url-or-id> [-o out.json]
  scala-cli run . -- supply --roster <roster.json> --kill-time <s|m:ss>
Run (target — this spec):
  scala-cli run . -- schedule --roster <roster.json> --encounter <id> --kill-time <s|m:ss>
```

## Project Structure

```
src/io/github/decarb/cdassigner/
  domain/      → core types (PlayerClass, Spec, Roster, Cooldown, CoverageType, Encounter)
  raidhelper/  → roster ingestion (built)
  roster/      → normalization + store (built)
  supply/      → supply ledger (built)
  demand/      → per-encounter demand model (in progress)      ← input to scheduler
  scheduler/   → the assigner: ledger (L0), greedy (L1), [solver (L2)]  ← this spec
  Main.scala   → decline-effect CLI
test/io/github/decarb/cdassigner/...  → munit tests mirroring src
docs/intent/   → confirmed intent (north star)
docs/spec/     → design specs (this file); iterated when design changes
research/       → knowledge base (facts only; never drives design)
```

## Code Style

Match the existing codebase. Representative snippet (from `supply/`): opaque/enum domain types,
deterministic pure functions, explicit typing, no shortcuts that cost readability.

```scala
enum CoverageType:
  case AoeHeal, DrMagic, DrPhysical, DrAll, EffectiveHealth, External

final case class Cooldown(name: String, source: CooldownSource, recharge: FiniteDuration, ...):
  /** Charges available over a kill of length T: floor(T / recharge) + 1. */
  def charges(t: FiniteDuration): Int = (t.toSeconds / recharge.toSeconds).toInt + 1
```

Priorities, in order: **readability, maintainability, idiomatic Scala 3, performance.** No shortcut
that reduces readability is acceptable.

## Testing Strategy

- **Framework:** munit (mirrors existing tests).
- **Location:** `test/...` mirroring `src/...`.
- **Levels:**
  - *Unit* — scheduler scoring, charge math, coverage typing, rotation grouping (pure, exhaustive).
  - *Property/scenario* — given a synthetic demand model + roster, assert invariants (no double-
    booking a CD before it recharges; no dangerous cast left uncovered when supply ≥ demand).
  - *Golden* — a real encounter (Thok 25H) + real roster → a checked-in expected schedule, so
    regressions in the objective surface as diffs.
- **Explicability is testable:** every emitted assignment must carry a non-empty rationale; assert it.

## Boundaries

- **Always:** keep the scheduler deterministic; run `scala-cli test .` before commits; attach a
  rationale to every assignment; ground timestamps in demand-model cast times.
- **Ask first:** adding dependencies (e.g. an OR-Tools binding for L2); schema changes to saved
  roster/encounter JSON; introducing any AI/LLM call into the path.
- **Never:** let AI perform the assignment; emit an assignment without a code-grounded reason; let
  `research/` dictate design; commit secrets or print `.env`.

## Success Criteria

1. `schedule --roster <r> --encounter <id> --kill-time <T>` emits a schedule of `(player, cooldown,
   timestamp)` assignments for the raidwide damage events of that encounter.
2. Every assignment carries a one-line, code-grounded rationale.
3. No cooldown is scheduled again before it has recharged; no dangerous raidwide cast is left
   uncovered when supply for its coverage type is sufficient (and undercoverage is reported, not
   hidden).
4. Where assignments group into a repeating per-phase order, a rotation overlay is also emitted.
5. The schedule for Thok 25H is sound enough that the RL would run it (human judgment gate) — and
   matches a checked-in golden within tolerance.
6. The objective is explicit and computable: a documented function scoring "damage mitigated" that
   the assigner provably maximizes (greedily at L1, optimally at L2).

## Open Questions

1. **Objective function (central — Phase 2 tackles first):** how exactly is "damage mitigated"
   scored from a set of assignments? Candidate: Σ over covered casts of `magnitude × breadth ×
   mitigation%`, with the coverage-type match gating eligibility. Needs to be precise enough for a
   greedy assigner to maximize and a solver to optimize.
2. **Coverage-type matching:** when multiple CD types could mitigate a cast (e.g. `dr_all` vs
   `dr_magic` on a magic hit), how is partial/over-coverage scored?
3. **Logs-prior shape:** what concrete artifact does log-mining produce that the scheduler consumes
   — a per-cast "this is usually covered by type X" hint, or a full reference assignment to bias
   toward? (Deferrable past L1.)
4. **Reference kill-time `T`:** single point estimate, or schedule against a band to stay robust?
5. **Blood Frenzy (Thok P2) typing** — `dr_physical` demand vs pure `aoe_heal` throughput (from
   `research/`; resolve in the demand model, surfaces here).
