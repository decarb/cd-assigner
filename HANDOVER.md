# Handover — CD Assigner (WoW MoP Cooldown Assignment Tool)

_Last updated: 2026-06-20. Status: **roster `pull` built & verified live (25-person roster) — initial commit.**_

> **Rescoped (2026-06-20):** the initial phase is **only** the roster `pull` stage (Raid-Helper →
> canonical domain model → JSON on disk). WCL logs + the assign engine are deferred. The (working,
> tested) WCL auth code + `Encounter` enums are stashed at `/home/claude/cd-assigner-deferred/`.
> Raid-Helper note: raidplan URLs use the 19-digit **event** id (e.g.
> `raid-helper.xyz/raidplan/<id>`); a 404 `"unknown comp"` means no event/finalized comp for that id.

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

### Roster ingestion — Raid-Helper API (no auth)
- **PRIMARY:** `GET https://raid-helper.dev/api/raidplan/{id}` → finalized comp as `slots[]`
  (`name, className, specName, isConfirmed, groupNumber`). This is the actual roster going into the raid.
- **FALLBACK:** `GET https://raid-helper.dev/api/event/{id}` (singular `event`, **no** `/v2/`) →
  `signups[]` (`name, class, spec, cClass, cSpec, role, status`).
- ⚠️ **Normalization needed (~40-row table):** values are template-specific. Tanks come as
  `className="Tank"` with real class in spec (`Protection1`=Prot Warrior, `Guardian`=Guardian Druid);
  specs carry digits (`Holy1`=Holy **Paladin**, `Restoration1`=Resto **Shaman**).
- Samples: `research/raidhelper-raidplan-sample.json`, `research/raidhelper-event-sample.json`.

### Encounter timers — BigWigs (machine-readable)
- Repo: `BigWigsMods/BigWigs_MistsOfPandaria`, one Lua file per SoO boss (all 14 present).
- `self:Bar(spellId, seconds)` calls = minable cadence. **Event-driven state machine, not flat
  timestamps** — reconstruct from recurrence + pull timers, or get exact timing from logs.
- Samples: `research/bigwigs/GarroshHellscream.lua`, `research/bigwigs/IronJuggernaut.lua`.

### Coverage model — derive from Warcraft Logs (don't hand-author)
- Instead of guessing `coverage_required`, **measure it from real kills.** Logs give magnitude,
  breadth (raid-wide vs tank-only), recurrence, school, kill time, phase timing.
- **WCL API v2:** GraphQL at `https://www.warcraftlogs.com/api/v2/client`; OAuth2 **client credentials**
  (free) via `https://www.warcraftlogs.com/oauth/token` (`grant_type=client_credentials`, HTTP basic
  with client_id/secret); points-based rate limit → cache, prefer aggregate `table` over raw `events`.
- Queries: `fights` (kill time + phaseTransitions), `table(dataType:DamageTaken)` (magnitude),
  `events(dataType:DamageTaken)` (timing + breadth).
- **Division of labor:** deterministic profiler extracts the numbers (~80%); **AI labels the semantics**
  (avoidable vs unavoidable, mechanic naming, severity, draft `coverage_required` for raid-leader
  review). AI does NOT do the assignment.

## Environment / secrets — DONE
- WCL credentials live in `/home/claude/cd-assigner/.env` (gitignored; template in `.env.example`):
  `WCL_CLIENT_ID`, `WCL_CLIENT_SECRET`. **Populated and verified working.**
- ⚠️ Must be **WCL API v2** OAuth2 client (Client ID **+** Secret from
  https://www.warcraftlogs.com/api/clients/) — a legacy v1 "API key" does NOT work here.
- Auth test passes: `scala-cli run .` → loads creds, fetches bearer token, runs
  `{ rateLimitData { limitPerHour pointsSpentThisHour } }`, prints success only (never the secret).
  Confirmed live: limitPerHour=3600. Code: `Main.scala`, `wcl/WclClient.scala`, `config/Env.scala`.

## Open questions / to-confirm
- MoP **Classic** SoO zone/encounter IDs on WCL (Classic is its own game version).
- Which **difficulty** the guild raids (LFR/Flex/Normal/Heroic) — coverage differs.
- A reference SoO log URL from the guild's difficulty/comp for calibration.
- Verify a few cooldown KB values for 5.4.8 (e.g. Spirit Link CD, AMZ duration) against wowhead mop-classic.
- **Normalization VERIFY:** `Priest "Smite"` → assumed Discipline; `Tank "Protection2"` → assumed Paladin
  (only `Protection1`=Warrior seen in samples). Confirm against the guild's real Raid-Helper template.
- Need a real Raid-Helper event id to live-test `pull` (decode+normalize already proven offline).

## Recommended next steps (build order)
1. ~~WCL auth test → confirms credentials + connection.~~ **DONE (2026-06-20).**
2. `fights` query for one boss → real kill time + phases (proves auth + parsing, cheap).
3. Deterministic damage-profiler → candidate events table for that boss.
4. Cooldown KB → structured data file (from `research/cooldown-kb.md`).
5. ~~Roster ingest + normalization table (Raid-Helper → canonical class/spec).~~ **DONE (2026-06-20).**
6. **L0 supply/demand ledger** — first end-to-end useful output on one boss.
7. AI classification for coverage drafts; then L1 greedy assigner.

## Research artifacts (all in `research/`)
- `feasibility-assessment.md` — overall feasibility, all components.
- `engine-design.md` — the suggestion engine (the crux). **Read this first.**
- `logs-pipeline.md` — Warcraft Logs-driven coverage model.
- `cooldown-kb.md` — starter MoP raid cooldown knowledge base.
- `raidhelper-raidplan-sample.json`, `raidhelper-event-sample.json` — real API responses.
- `bigwigs/*.lua` — sample boss modules.

## Tech notes
- **Stack: Scala 3 / scala-cli** (script-first), conventions mirror `~/scala-cli/ranking-table`:
  cats-effect, opaque types, `-Wunused:all`, `io.github.decarb.cdassigner.*`, munit tests.
  HTTP+JSON = sttp client4 (cats backend) + circe. See `project.scala`.
- Code so far (Scala 3.3, JVM 17, all green):
  - `config/Env.scala` (.env loader), `wcl/WclClient.scala` (token + GraphQL).
  - `domain/` — `PlayerClass`, `Spec` (+ legal pairings), `Roster`/`RaidMember`/`PlayerName` (circe
    codecs), `Encounter` (`Raid`/`Boss` enums, 14 SoO bosses).
  - `raidhelper/` — `RawSlot` (DTO), `RaidHelperClient` (raidplan fetch).
  - `roster/` — `RosterNormalizer` (table + error collection), `RosterStore` (JSON file IO).
  - `Main.scala` — decline-effect CLI: `pull` / `assign` / `wcl-test`.
  - Test: `test/.../RosterNormalizerTest.scala` normalizes the committed sample offline (3 tests pass).
- No git repo initialized yet.
- Memory: project tracked in auto-memory as `cd-assigner-project`.
