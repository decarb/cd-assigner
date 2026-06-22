# Handover — CD Assigner (WoW MoP Cooldown Assignment Tool)

_Last updated: 2026-06-22. Status: **`pull` + SUPPLY ledger (`supply`) built, tested, committed &
pushed (origin `d9e68cb`). DEMAND model spec'd + planned (Thok 25H Heroic); implementation not yet
started.**_

> **Product goal (clarified 2026-06-22):** the output is a **recommended cooldown schedule** — which
> player presses which CD at which moment — driven by the engine's internal supply-vs-demand
> knowledge. Supply/demand is **not** the output, and the tool gives **no** roster-composition advice.
>
> **Current phase (2026-06-22):** building the **DEMAND** side for one boss (Thok the Bloodthirsty,
> **Heroic 25**). WCL is now **in-design** (being un-stashed) — BigWigs spellIds ⋈ WCL `DamageTaken`
> categorise **raidwide** damage events (externals out of scope). Spec: `research/demand-model-spec.md`;
> 9-task plan: `research/demand-model-plan.md`. The schedule/assigner (L1) is the **next** session.
>
> **Earlier rescope (2026-06-20):** phase 1 was roster `pull` only; the (working, tested) WCL auth
> code + `Encounter` enums were stashed at `/home/claude/cd-assigner-deferred/` (being un-stashed now
> per the plan). Raid-Helper note: raidplan URLs use the 19-digit **event** id (e.g.
> `raid-helper.xyz/raidplan/<id>`); a 404 `"unknown comp"` means no finalized comp for that id.

## What this project is

A tool to help a WoW **Mists of Pandaria (5.4.8) Siege of Orgrimmar** raid leader by **auto-suggesting
raid cooldown assignments**. Input = a raid roster (from Raid-Helper or manual); output = suggested
cooldown assignments for a given boss. Designed to extend to other instances later.

**The point of the project:** existing tools (Viserio, the raid leader's "thick Excel sheet") are
**manual placement** tools — a human drags each CD onto a timeline and tracks coverage in their head.
**Nobody auto-solves the assignment.** Automating that reasoning is the entire value proposition.

## Scope decisions (important)
- **WeakAuras export is OUT of scope for v1.** Only roster-in → suggestions-out.
- The Excel sheet is deferred (but is the cheapest future source for the raid leader's coverage
  judgments — it already encodes them).

## The core idea: the engine is a SUPPLY vs DEMAND problem

The suggestion engine is a **typed interval-coverage / scheduling-with-cooldowns** problem, NOT an AI
problem at its core. The central abstraction fuses the three key variables:

```
DEMAND(type) = Σ events  occurrences(e, T) × coverage_required   # timers × kill time
SUPPLY(type) = Σ abilities  charges(ability, T)                  # recharge × kill time
   charges(ability, T) = floor(T / recharge) + 1
```
where `T` = kill time, `type` = coverage type (aoe_heal, dr_magic, dr_physical, dr_all,
effective_health, external). If SUPPLY < DEMAND for a type, the fight is under-covered — surface it.
Computing this ledger **before** any placement is the most valuable single output.

Build layers: **L0** supply/demand ledger (ship first) → **L1** greedy assigner → **L2** optional
CP-SAT solver (Google OR-Tools). The solver stays **deterministic**.

## Confirmed facts (verified live — don't re-research)

### Roster ingestion — Raid-Helper API (no auth) — BUILT & VERIFIED
- **PRIMARY:** `GET https://raid-helper.dev/api/raidplan/{id}` → finalized comp as `slots[]`
  (`name, className, specName, isConfirmed, groupNumber`). This is the actual roster going into the raid.
  (`raid-helper.xyz` serves the same `/api/` backend; the public site is `.xyz`.)
- **FALLBACK (not yet wired):** `GET https://raid-helper.dev/api/event/{id}` (singular `event`, **no**
  `/v2/`) → `signups[]` (`name, class, spec, cClass, cSpec, role, status`). Use when an event has no
  finalized raidplan.
- ⚠️ **Normalization (built):** values are template-specific. Tanks come as `className="Tank"` with the
  real class in spec (`Protection1`=Prot Warrior, `Guardian`=Guardian Druid); specs carry digits
  (`Holy1`=Holy **Paladin**, `Restoration1`=Resto **Shaman**). See `roster/RosterNormalizer.scala`.
- Verified live against a real 25-person roster (raidplan id `1515463310269747423`): all 25 slots
  normalized with zero errors.
- Samples: `research/raidhelper-raidplan-sample.json`, `research/raidhelper-event-sample.json`.

### Encounter timers — BigWigs (machine-readable)
- Repo: `BigWigsMods/BigWigs_MistsOfPandaria`, one Lua file per SoO boss (all 14 present).
- `self:Bar(spellId, seconds)` calls = minable cadence. **Event-driven state machine, not flat
  timestamps** — reconstruct from recurrence + pull timers, or get exact timing from logs.
- Samples: `research/bigwigs/GarroshHellscream.lua`, `research/bigwigs/IronJuggernaut.lua`.

### Encounter model = BigWigs structure + WCL calibration (decided 2026-06-20)
The demand side is built from **both** sources, which are complementary:
- **BigWigs = skeleton** — spell IDs, ability cadence, phase triggers. *What* mechanics exist and
  *roughly how often*, deterministically, with no kill data.
- **WCL = calibration** — real kill time `T`, actual phase lengths, damage **magnitude** per mechanic,
  **breadth** (how many players hit), school, real occurrence counts. Turns "every ~45s" into "fired 6×
  over a 6:30 kill, hit 18 players for X".
- Then **AI labels** avoidability/severity and drafts `coverage_required`; **raid leader reviews**.
  AI does NOT do the assignment.
- **WCL API v2:** GraphQL at `https://www.warcraftlogs.com/api/v2/client`; OAuth2 **client credentials**
  (free) via `https://www.warcraftlogs.com/oauth/token` (`grant_type=client_credentials`, HTTP basic
  with client_id/secret); points-based rate limit → cache, prefer aggregate `table` over raw `events`.
  Queries: `fights` (kill time + phaseTransitions), `table(dataType:DamageTaken)` (magnitude),
  `events(dataType:DamageTaken)` (timing + breadth).

## Environment / secrets — validated, now deferred
- WCL credentials live in `/home/claude/cd-assigner/.env` (gitignored; template in `.env.example`):
  `WCL_CLIENT_ID`, `WCL_CLIENT_SECRET`. **Populated and verified working** (limitPerHour=3600).
- ⚠️ Must be **WCL API v2** OAuth2 client (Client ID **+** Secret from
  https://www.warcraftlogs.com/api/clients/) — a legacy v1 "API key" does NOT work here.
- The auth test passed (token + trivial GraphQL query, secret never printed), then was **stashed out of
  the repo** during the rescope. Code: `/home/claude/cd-assigner-deferred/{wcl/WclClient.scala,
  config/Env.scala}`. Restore when the demand/logs stage begins.

## Recommended next steps (build order)
1. ~~WCL auth test → confirms credentials + connection.~~ **DONE (2026-06-20).**
2. ~~Roster ingest + normalization (Raid-Helper → canonical class/spec).~~ **DONE (2026-06-20),
   committed `91e57b8`, pushed.**
3. ~~**SUPPLY side.** Cooldown KB → typed Scala + `supply` CLI command.~~ **DONE (2026-06-20).**
   `domain/CoverageType.scala` (6-type taxonomy enum), `domain/Cooldown.scala` (`Cooldown` +
   `CooldownSource` = `AnySpec(class)` | `Specs(class, specs)`; `charges(T)=T/recharge+1`),
   `supply/CooldownKb.scala` (full KB table; Tranquility split into Resto 180 / off-spec 480 via
   disjoint spec sets), `supply/SupplyLedger.scala` (compute + `chargesByType`), `SupplyReport.scala`
   (text report), `supply/KillTime.scala` (parses `390` or `6:30`). CLI: `supply --roster <f>
   --kill-time <s|m:ss>`. Deterministic, no WCL. 6 tests in `SupplyTest`. **KB audited spell-by-spell
   against wowhead `mop-classic` (2026-06-22)** — see the Verification table in `research/cooldown-kb.md`.
   Corrections: Devotion Aura is **Holy `dr_all` + Ret/Prot `dr_magic`** (was Holy-only `dr_magic`),
   Hand of Sacrifice 150→120s, Light's Hammer 16→14s, + minor duration/magnitude fixes. Verified on
   the live 25-person roster (16 bring a CD): 43 aoe_heal / 8 dr_magic / 24 dr_all / 22 external
   charges over a 6:30 kill. **Committed `1402dda`, pushed.**
4. **DEMAND side (IN PROGRESS).** Spec'd + planned: **Thok the Bloodthirsty, Heroic 25**, encounter
   model = BigWigs skeleton ⋈ WCL calibration, raidwide events only. See `research/demand-model-spec.md`
   + `research/demand-model-plan.md` (9 tasks, T1–T9). Verified WCL Classic IDs: SoO zone `1054`,
   Thok 25H encounter `51599`. Un-stash WCL during T3.
5. **L0 supply/demand ledger.** Combine 3 + 4 → coverage gap analysis ("magic DR: need 6, have 4,
   short 2"). First genuinely useful end-to-end output; validates the model before any placement.
6. **L1 greedy assigner.** The actual assignments — prefer per-phase rotation order (robust to
   kill-time variance) over absolute timestamps.
7. **L2 CP-SAT** only if L1 quality demands; **AI bootstrapping** (encounter drafts) + NL preferences last.

## Open questions / to-confirm
- ~~MoP Classic SoO zone/encounter IDs on WCL.~~ **RESOLVED (2026-06-22):** SoO zone `1054`, Thok
  25H encounter `51599` (BigWigs `engageId` + Classic `5` prefix).
- ~~Which difficulty the guild raids.~~ **RESOLVED:** **Heroic** (25-player).
- ~~A reference SoO log URL for calibration.~~ **RESOLVED:** self-served via the API —
  `encounter(51599).characterRankings` yields a top public 25H report + damage tables.
- **Raidwide breadth threshold** (proposed ≥ 60% = 15/25) — tune against the real log in T5.
- ~~Verify cooldown KB values for 5.4.8 against wowhead mop-classic (§3).~~ **DONE (2026-06-22)** —
  full spell-by-spell audit, see Verification table in `research/cooldown-kb.md`.
- **Talent/glyph-conditional supply (deferred):** the roster has no talent data (Raid-Helper =
  class/spec only), so talent-dependent CDs aren't modelled yet — the KB lists base values only.
  Examples: Clemency (+1 charge on the Hands), Unbreakable Spirit (halves Hand of Sac to 60s). WCL
  logs expose per-player talents/glyphs, so the demand stage (§4) is the natural place to resolve them.
- **Normalization VERIFY:** `Priest "Smite"` → assumed Discipline; `Tank "Protection2"` → assumed Paladin
  (only `Protection1`=Warrior seen in samples). The live 25-person roster had neither — still unconfirmed.
- ~~Which boss to author first.~~ **RESOLVED:** **Thok the Bloodthirsty** (current guild progression).
- **Blood Frenzy (Thok P2) typing:** `dr_physical` demand vs pure `aoe_heal` throughput — resolve in T5.

## Research artifacts (all in `research/`)
- `engine-design.md` — the suggestion engine (the crux). **Read this first.**
- `demand-model-spec.md` — DEMAND side + feasibility spec for Thok 25H (current phase). **Read second.**
- `demand-model-plan.md` — 9-task implementation plan (T1–T9) for the current phase.
- `feasibility-assessment.md` — overall feasibility, all components.
- `logs-pipeline.md` — Warcraft Logs-driven coverage model.
- `cooldown-kb.md` — MoP raid cooldown KB (SUPPLY source; audited 2026-06-22).
- `raidhelper-raidplan-sample.json`, `raidhelper-event-sample.json` — real API responses.
- `bigwigs/*.lua` — sample boss modules (`GarroshHellscream`, `IronJuggernaut`, **`Thok`** = current target).

## Tech notes
- **Stack: Scala 3 / scala-cli** (script-first), conventions mirror `~/scala-cli/ranking-table`:
  cats-effect, opaque types, `-Wunused:all`, `io.github.decarb.cdassigner.*`, munit tests.
  HTTP+JSON = sttp client4 (cats backend) + circe; decline-effect for the CLI. See `project.scala`.
- **Repo: git initialized, pushed to `git@github.com:decarb/cd-assigner.git`** (branch `master`,
  initial commit `91e57b8`). Local identity set repo-locally to `Dev <dvnjoubert0@gmail.com>` —
  placeholder name, change with `git config user.name`.
- Code in repo (Scala 3.3, JVM 17, 5 tests green):
  - `domain/` — `PlayerClass`, `Spec` (+ legal pairings), `Roster`/`RaidMember`/`PlayerName` (circe
    codecs; saved JSON carries `raidPlanId` + `capturedAt` provenance).
  - `raidhelper/` — `RawSlot` (DTO), `RaidPlanRef` (URL-or-id parsing), `RaidHelperClient` (fetch).
  - `roster/` — `RosterNormalizer` (table + error collection), `RosterStore` (JSON file IO).
  - `supply/` — `CooldownKb` (KB table + `forMember`), `SupplyLedger` (`compute` + `chargesByType` /
    `contributors`), `SupplyReport` (text render), `KillTime` (parse seconds or m:ss).
  - `Main.scala` — decline-effect CLI: **`pull`** and **`supply`**.
  - Tests: `RosterNormalizerTest`, `RaidPlanRefTest`, `SupplyTest` (KB spec-dependency + charges math).
- **Stashed (out of repo, gitignored sibling dir `/home/claude/cd-assigner-deferred/`):**
  `wcl/WclClient.scala`, `config/Env.scala`, `domain/Encounter.scala` (`Raid`/`Boss` enums, 14 SoO bosses).
- `.gitignore` covers `.env`, `roster-*.json` (generated), scala-cli build dirs, editor/tool local settings.
- Memory: stack decision saved as `cd-assigner-stack` (Scala, not Python).
