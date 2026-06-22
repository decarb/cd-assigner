# Handover — CD Assigner (WoW MoP Cooldown Assignment Tool)

_Last updated: 2026-06-22. Status: **intent confirmed + top-level scheduler spec APPROVED. Next
session: PLANNING (Phase 2).** Shipped & pushed code unchanged: `pull` + `supply` ledger (origin
`d9e68cb`)._

> **How we work now (adopted 2026-06-22).** This project is driven through a **skill-driven
> workflow**, not ad-hoc prompting — that is itself an explicit goal (see `docs/intent/`). The chain:
> `interview-me` → `spec-driven-development` → `planning-and-task-breakdown` →
> `incremental-implementation`/`test-driven-development` → `code-review-and-quality`. Each phase is a
> **gate**: don't advance until the human approves. Persist artifacts (spec, intent, memory) so each
> session boundary is cheap and a fresh session can pick up cold.

## North star — read these first
- `docs/intent/cd-assigner.md` — **the confirmed intent** (north star). Everything answers to it.
- `docs/spec/scheduler.md` — **the approved top-level design spec** (the scheduler is the product).

## The goal (confirmed via interview, 2026-06-22)
Given a **roster + encounter**, auto-suggest an **optimal raidwide cooldown schedule** — which player
presses which raid CD, and when — that the RL runs *instead of* their Excel sheet.
- **The auto-solve IS the goal.** The supply/demand coverage ledger is a *stepping stone*, never the
  deliverable.
- **"Optimal" =** maximize **damage mitigated assuming perfect play** (measurable objective, not a
  style-match). **Logs =** the **warm-start prior**, never the objective.
- **Hard constraint — explicability:** every assignment must be defensible from code + logic; no black
  box. Plus a high code-quality bar (readability, maintainability, idiomatic Scala 3, performance).
- **Scope:** raidwide CDs first; externals/single-target later. WeakAuras export out of v1. No
  roster-composition advice, ever.

## Doc layers — strict separation (adopted 2026-06-22)
- `research/` = **knowledge base**: facts only (game/API/log/BigWigs/cooldown values). **Never drives
  design.**
- `docs/spec/` = **design specs**: what we're building & why; iterated when design changes.
- `docs/intent/` = **the north star** the specs answer to.
- ⚠️ `research/demand-model-spec.md` is **misfiled** — it is design content, not research. Its spec
  content should migrate into `docs/spec/` (e.g. a `demand-model.md`), leaving only facts in
  `research/`. Not done yet.

## The pipeline (scheduler-headlined)
```
 Supply ledger (BUILT) ─┐
 Demand model (WIP) ────┼──▶  SCHEDULER (goal)  ──▶  schedule: (player × cooldown × timestamp)
 Logs prior (warm start)┘     max damage mitigated      + rotation overlay + per-assignment rationale
```
- **Output representation (decided):** **absolute timestamp is canonical** (derived from demand-model
  cast times → explicable by construction), computed against a reference kill-time `T`. **Rotation
  overlay is derived** where assignments group into a repeating per-phase order. Every assignment
  carries a one-line, code-grounded rationale.
- **Engine layers (deterministic throughout):** **L0** ledger/feasibility (stepping stone) → **L1**
  greedy assigner (first real schedule) → **L2** optional CP-SAT (only if L1 quality demands). AI is
  bounded to **labeling** data + **explaining** output; AI never does the assignment.

## Where we are (build status)
- ✅ **Roster ingest + normalization** (`pull`) — Raid-Helper → canonical class/spec. Live-verified on
  a real 25-person roster.
- ✅ **SUPPLY ledger** (`supply`) — typed cooldown KB → charges over `T`. KB audited spell-by-spell vs
  wowhead `mop-classic`. Committed `1402dda`.
- 🟡 **DEMAND model** — spec'd + planned for Thok 25H (BigWigs skeleton ⋈ WCL calibration, raidwide
  only). Implementation not started. (Spec content lives in `research/` and should migrate to
  `docs/spec/` per the doc-layer rule.)
- 🟢 **SCHEDULER spec** — approved (`docs/spec/scheduler.md`). **Planning is the immediate next step.**

## Immediate next step — PLANNING (Phase 2), in a fresh session
Open a clean session and run:
```
/plan  (planning-and-task-breakdown) — break docs/spec/scheduler.md into ordered, verifiable tasks.
```
**Start with the objective function (spec Open Question #1)** — it gates everything downstream.
Candidate: `Σ over covered casts of magnitude × breadth × mitigation%`, coverage-type match gating
eligibility. Then: L0 ledger → L1 greedy assigner → golden test on Thok 25H.

## Open questions (from the spec)
1. **Objective function** — exact "damage mitigated" scoring; precise enough for greedy (L1) + solver
   (L2). **Phase 2 resolves this first.**
2. **Coverage-type matching** — scoring partial/over-coverage when multiple CD types fit one cast.
3. **Logs-prior shape** — concrete artifact log-mining produces for the scheduler to consume.
4. **Reference kill-time `T`** — point estimate vs a band for robustness.
5. **Blood Frenzy (Thok P2) typing** — `dr_physical` vs `aoe_heal`; resolve in the demand model.
6. **Talent/glyph-conditional supply (deferred)** — roster has no talent data; WCL logs do, so resolve
   at the demand stage (Clemency +1 Hand charge; Unbreakable Spirit halves Hand of Sac).
7. **Normalization VERIFY** — `Priest "Smite"`→Disc, `Tank "Protection2"`→Paladin still unconfirmed.

## Verified facts (live-checked — don't re-research; full detail in `research/`)
- **Raid-Helper API (no auth):** `GET raid-helper.dev/api/raidplan/{id}` → finalized comp `slots[]`.
  Fallback `…/api/event/{id}` → `signups[]`. URLs use the 19-digit **event** id. Values are
  template-specific (Tanks come as `className="Tank"`; specs carry digits) — see `RosterNormalizer`.
- **BigWigs timers:** `BigWigsMods/BigWigs_MistsOfPandaria`, one Lua/boss (all 14 SoO present).
  `self:Bar(spellId, seconds)` = minable cadence; event-driven state machine, not flat timestamps.
- **WCL API v2:** GraphQL `warcraftlogs.com/api/v2/client`; OAuth2 client-credentials (free). MoP
  Classic: SoO zone `1054`, Thok 25H encounter `51599`. Prefer aggregate `table` over raw `events`.
- **Cooldown KB audited 2026-06-22** vs wowhead `mop-classic` — see `research/cooldown-kb.md`
  verification table (Devotion Aura = Holy `dr_all` + Ret/Prot `dr_magic`; Hand of Sac 120s; etc.).

## Environment / secrets — strict
- WCL creds in `.env` (gitignored; template `.env.example`): `WCL_CLIENT_ID`, `WCL_CLIENT_SECRET`.
  Populated & verified (limitPerHour=3600). **Never** cat/print `.env`. Must be **WCL API v2** OAuth2
  client (id+secret), not a legacy v1 key.
- **Stashed out of repo** (gitignored sibling `/home/claude/cd-assigner-deferred/`):
  `wcl/WclClient.scala`, `config/Env.scala`, `domain/Encounter.scala` (Raid/Boss enums, 14 SoO bosses).
  Un-stash WCL when the demand/logs stage begins.

## Tech notes
- **Stack: Scala 3 / scala-cli** (script-first), conventions mirror `~/scala-cli/ranking-table`:
  cats-effect, opaque types, `-Wunused:all`, `io.github.decarb.cdassigner.*`, munit. HTTP+JSON = sttp
  client4 (cats backend) + circe; decline-effect CLI. See `project.scala`.
- **Repo:** `git@github.com:decarb/cd-assigner.git` (branch `master`). Commit/push only when asked;
  conventional commit + bulleted body; no `Co-Authored-By`. Local identity `Dev <dvnjoubert0@…>`.
- **In repo (Scala 3.3, JVM 17, tests green):** `domain/`, `raidhelper/`, `roster/`, `supply/`,
  `Main.scala` (`pull` + `supply`). Tests: `RosterNormalizerTest`, `RaidPlanRefTest`, `SupplyTest`.
- **Build/run:** `scala-cli compile|test|fmt .`; `scala-cli fix --power .`. CLI: `pull`, `supply`
  (target: `schedule`).

## Uncommitted in this session (not yet committed — commit when ready)
- `docs/intent/cd-assigner.md` (new) — confirmed intent.
- `docs/spec/scheduler.md` (new) — approved top-level scheduler spec.
- `HANDOVER.md` (this overhaul).
