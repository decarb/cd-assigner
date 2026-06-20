# The Suggestion Engine — Design

_The crux of the project. Roster ingestion and timer extraction are solved-shaped; this is where the
value is._

## 0. What existing tools do — and the gap we fill

Viserio Cooldowns and the "thick Excel sheet" are **manual placement tools**: a human drags each
cooldown onto a boss timeline and tracks recharge/coverage in their head. They show the boss's cast
track and let you assign per-cast, but **none of them auto-solve the assignment.** The supply/demand
reasoning — "I have 8 spikes, my 3-min CDs each fire twice, so I need 4 providers rotating" — lives
in the raid leader's head. **Automating that reasoning is the entire point of this project.**

So we are not rebuilding Viserio's UI. We are building the *solver* that produces a draft Viserio
would otherwise build by hand.

## 1. The problem, formalized

This is a **typed interval-coverage / scheduling-with-cooldowns** problem.

**Inputs**
- **Roster** `R` — players, each with a spec → a set of available abilities (from the cooldown KB).
- **Cooldown KB** — per ability: `{recharge, duration, type, target, magnitude}` (see `cooldown-kb.md`).
- **Encounter model** for the boss — a list of **damage events** `E`. Each event:
  `{id, phase, time-or-%HP, school (magic/physical), shape (spike/sustained), coverage_required}`
  where `coverage_required` is typed demand, e.g. `{dr_magic: 1, aoe_heal: 1}`.
- **Fight duration** `T` (kill time) — the variable that ties everything together.

**Output**
- An assignment `x[player, ability, event]` such that every event's `coverage_required` is met by
  available cooldowns of the matching type.

**Hard constraints**
1. **Recharge spacing** — for a given `(player, ability)`, consecutive assigned uses must be ≥ `recharge`
   seconds apart. (The defining constraint.)
2. **Type match** — an event's `dr_magic` demand can only be filled by `dr_magic`/`dr_all` abilities, etc.
3. **One ability per player per instant** — a player can't channel Tranq and Barrier simultaneously.
4. **Existence** — a player only provides abilities their spec has.

**Objective (soft, weighted)**
- Maximize covered demand on high-severity events first.
- Balance load across players (don't burn one healer's every CD while another idles).
- Respect raid-leader preferences (e.g. "save Barrier for P3").
- Minimize wasted overlap (two raid CDs on a trivial event).

## 2. The central insight: SUPPLY vs DEMAND

This is the heart of the engine and is worth computing **before** any placement, because it's both the
most useful output and the thing humans get wrong. It directly fuses the user's three variables —
**timers** (how many events), **kill time** (how long the window is), **recharge** (how often a CD returns).

For each coverage **type** (aoe_heal, dr_magic, dr_physical, dr_all, ...):

```
DEMAND(type) = Σ over events e of  occurrences(e, T) × coverage_required(e, type)

   occurrences(e, T) = how many times event e fires given kill time T
                       (from BigWigs cadence + phase model, clamped by T)

SUPPLY(type) = Σ over (player, ability) providing type of  charges(ability, T)

   charges(ability, T) = floor(T / recharge) + 1      # uses available in the fight
```

- If `SUPPLY(type) < DEMAND(type)` → **the fight is under-covered for that type.** Surface it loudly:
  *"Magic damage reduction: 6 needed, 4 available — you are short 2. Options: bring a 2nd Disc priest,
  or accept uncovered Empowered Whirling Corruption #5 and #7."* This alone is hugely valuable and
  needs no assignment solving.
- If `SUPPLY ≥ DEMAND` → a valid assignment likely exists; proceed to placement.

`charges()` is exactly where **recharge + kill time** interact, and `occurrences()` is where **timers +
kill time** interact. The supply/demand sheet is the engine's "show your work."

## 3. Data model (3 schemas)

```yaml
# ability (static KB — see cooldown-kb.md)
- id: power_word_barrier
  class: priest; spec: discipline
  recharge: 180; duration: 10
  type: dr_all; target: ground; magnitude: 0.25

# encounter event (per boss, authored/mined)
- id: empowered_whirling_corruption
  phase: 1
  recurs_every: 45        # seconds (from BigWigs cadence); or %HP trigger
  school: magic; shape: spike
  coverage_required: { dr_magic: 1, aoe_heal: 1 }
  severity: high

# roster entry (from raidplan API + normalization)
- name: Termes; class: priest; spec: discipline   # provides PW:Barrier, Pain Supp, ...
```

The **encounter model is the high-effort artifact** (the rest is cheap). It is also where AI can
genuinely help — see §6.

## 4. Algorithm — layered, deterministic core

### Layer 0 — Feasibility / supply-demand (instant)
Compute §2. Output the coverage ledger and any shortfalls. **Ship this first**; it's useful standalone
and validates the data model before any solver exists.

### Layer 1 — Greedy assigner (fast, explainable baseline)
1. Expand events into concrete occurrences along `T` (respecting phases/%HP).
2. Sort occurrences by severity, then time.
3. For each, fill each typed demand with the *best available* provider: matching type, off recharge at
   that time, least-loaded, cheapest (don't spend a strong CD on a weak event).
4. Mark that `(player, ability)` on recharge until `t + recharge`.

Produces a sensible rotation instantly and explains every pick ("Barrier here because it's up and
nothing stronger is needed"). Good enough for v1 and a great seed for Layer 2.

### Layer 2 — CP-SAT / ILP refiner (optimal, optional)
Model as constraint program (Google OR-Tools CP-SAT fits perfectly):
- Vars: `x[p,a,e] ∈ {0,1}`.
- Recharge: for each `(p,a)`, any two chosen events within `recharge` seconds are mutually exclusive.
- Coverage: `Σ_p,a matching x[p,a,e] ≥ coverage_required[e][type]` per type.
- Objective: maximize `Σ severity[e]·covered[e]` − `λ·imbalance` − `μ·waste`.
Gives provably-balanced, near-optimal plans. Warm-start from Layer 1. Only build if Layer 1's quality
proves insufficient.

### Robustness to kill-time variance
Because phases are %HP-based, prefer **per-phase rotation order** ("in P1, rotate Barrier → Devo →
Spirit Link → repeat") over absolute timestamps. Then a faster/slower kill just truncates the rotation
instead of invalidating it. Take `T` as an input (raid-leader estimate or WCL median of similar groups)
and optionally show plans for fast/median/slow kills.

## 5. A worked micro-example
Garrosh-style: P1 has `Empowered Whirling Corruption` (magic spike) every ~45s. Estimated P1 length 4
min → ~5 occurrences, each needs `{dr_magic:1}`.
- SUPPLY of dr_magic from a roster with Disc Priest (Barrier 180s), Holy Pala (Devo 180s), DK (AMZ
  120s): charges = (4/3+1)+(4/3+1)+(4/2+1) = 2+2+3 = **7 ≥ 5**. Feasible.
- Greedy rotation: t0 Barrier, t45 Devo, t90 AMZ, t135 AMZ(no—AMZ recharge 120, next ok at 90? it was
  used? no)... → Barrier, Devo, AMZ, Barrier(up at 180), Devo(up at 225) → all 5 covered, load spread.

(This tiny example is the whole engine in miniature: timers set occurrences, kill time bounds them,
recharge controls reuse, type gates eligibility.)

## 6. Where AI fits — bounded, not the solver

- **Bootstrapping the encounter model (best use):** feed BigWigs module + Dungeon Journal / guide text
  to an LLM to draft `coverage_required` and severity per event ("which mechanics actually need a raid
  CD?"). This attacks the highest-effort artifact. Human/raid-leader reviews the draft.
- **NL preferences → constraints:** "we're healer-light, lean on personals in P1" → weights/constraints.
- **Explanations:** narrate why the solver made each call.

The **assignment itself stays deterministic** (greedy/CP-SAT). An LLM must not invent abilities,
mis-time recharges, or silently drop coverage — a solver won't.

## 7. Genuinely hard parts / open questions
1. **Authoring `coverage_required`** — "how much mitigation does this mechanic need?" is judgment.
   Mitigate: **derive it from logs** (see `logs-pipeline.md`) — magnitude/breadth/school/recurrence are
   facts in the combat log; AI labels avoidability + severity; raid-leader reviews. Also cross-check
   against the raid-leader's existing Excel, which already encodes this.
2. **occurrences() under %HP phasing** — needs a phase model + kill-time estimate; BigWigs gives cadence
   but not exact phase lengths. WCL can calibrate.
3. **Externals are a different sub-problem** — single-target, recipient = tank, tied to tankbuster
   timers; model separately from raid coverage.
4. **Duration vs instantaneous** — a 10s Barrier covering a 2s spike vs a sustained channel: the model
   should know whether one cast covers the window.
5. **Magnitude/throughput modeling (stretch)** — v1 treats coverage as integer counts ("1 raid CD");
   a later version could compare damage magnitude (from logs) to healing/absorb throughput for true
   sufficiency rather than count-based heuristics.

## 8. Recommended build order
1. **Cooldown KB** (done, draft) + **roster ingest** (done, verified).
2. **Encounter model schema + one boss authored by hand.**
3. **Layer 0 supply/demand ledger** — first real, useful output.
4. **Layer 1 greedy assigner** → human-readable rotation.
5. Validate with raid leader on 1–2 bosses; iterate the encounter model.
6. **Layer 2 CP-SAT** only if greedy quality demands it.
7. AI bootstrapping + NL layer last.

## Sources
- [Viserio Cooldowns — planning guide](https://wowutils.com/viserio-cooldowns/guide/cooldown-planning)
- [Viserio's spreadsheets (Wowhead spotlight)](https://www.wowhead.com/news/raid-tool-spotlight-viserios-raid-healing-assignments-spreadsheets-organize-your-337152)
- [healiocentric — MoP Raid Cooldowns Overview](https://healiocentric.wordpress.com/2013/06/24/raid-cooldowns-overview/)
- [Warcraft Logs API v2 — ReportFight (kill time, phaseTransitions)](https://www.warcraftlogs.com/v2-api-docs/warcraft/reportfight.doc.html)
