# Log-Driven Coverage Model (Warcraft Logs)

_The insight: don't **guess** `coverage_required` per mechanic — **measure** it from real kills. This
attacks the single highest-effort, highest-judgment artifact in the whole project (§7.1 of
`engine-design.md`), and it's where AI becomes genuinely useful rather than optional polish._

## 1. Why logs change the design

In `engine-design.md` the hard part was authoring, per mechanic, "how much mitigation does this need?"
That's judgment. But a combat log already contains the ground truth:

- **Which abilities actually hit the raid** (by ability ID).
- **How hard** (damage amount, vs player HP pools).
- **How often** (timestamps → recurrence interval = our `occurrences`).
- **How broadly** (# of distinct targets hit in the same instant → raid-wide vs tank-only vs targeted).
- **What school** (magic vs physical → which `dr_*` type can mitigate it).

So logs turn `coverage_required` from a guess into a **derived, evidence-backed** quantity, and give us
real **kill times** and **phase timings** for free.

## 2. Warcraft Logs API v2 — essentials (✅ confirmed)

- **Type:** GraphQL. **Endpoint:** `https://www.warcraftlogs.com/api/v2/client`.
- **Auth:** OAuth2 **client credentials** (free). Create a client in WCL account → get
  `client_id`/`client_secret` → POST to `https://www.warcraftlogs.com/oauth/token` with
  `grant_type=client_credentials` (HTTP basic auth) → `Bearer` token. Reads public reports.
- **Rate limit:** points-based (~hourly point budget; complex/raw-event queries cost more). Implication:
  **cache aggressively**, prefer aggregate `table` queries over raw `events` where possible, and mine a
  handful of representative reports rather than thousands.
- ⚠️ **To confirm:** MoP **Classic** SoO zone/encounter IDs (MoP Classic is its own WCL game version);
  plenty of SoO Classic kills are logged, but we need the correct `encounterID`s for each boss.

## 3. The queries we need

1. **Find representative kills** — rankings / reports for a SoO `encounterID`, filtered to clears by
   comps similar to ours (ideally same difficulty + similar healer makeup). 1–5 good logs per boss.
2. **Per fight: timing + structure**
   `reportData.report.fights { id, encounterID, kill, difficulty, startTime, endTime,
   phaseTransitions { id, startTime } }` → kill time `T` and phase boundaries (calibrates the %HP
   phase model BigWigs can't give us).
3. **Per fight: damage magnitude (aggregate, cheap)**
   `report.table(dataType: DamageTaken, fightIDs, startTime, endTime)` → total damage taken per ability
   → ranks the abilities that matter.
4. **Per fight: timing + breadth (raw events, heavier)**
   `report.events(dataType: DamageTaken, abilityID, ...)` → per-hit timestamps, `targetID`, `amount`.
   Group hits by timestamp window → **breadth** (how many players hit at once) and **recurrence**.

## 4. Deterministic damage-profiling (the quantitative backbone)

This stage is **pure data processing — no AI.** For each ability appearing in DamageTaken:

| Metric | Computed from | Tells us |
|---|---|---|
| total / max hit | table + events `amount` | severity vs raid HP pool |
| breadth | distinct `targetID` per time-window | raid-wide vs tank-only vs targeted |
| recurrence interval | gaps between hit-clusters | `occurrences(e, T)` |
| school | event metadata | `dr_magic` vs `dr_physical` eligibility |
| phase | hit timestamp vs `phaseTransitions` | which phase the event belongs to |

Output: a ranked list of **candidate damage events**, each already quantified. This alone gets us ~80%
of the encounter model mechanically — magnitude, frequency, breadth, school, and phase are all *facts*
in the data, not judgment.

## 5. Where AI genuinely earns its place (the last ~20%)

The log knows *what* hit and *how hard*, but not the **semantics**. Turning the quantitative profile
into a coverage model needs judgment, and that's the AI-shaped part:

- **Avoidable vs unavoidable** — a big magic hit could be an unavoidable raid mechanic *or* people
  standing in fire. The number alone can't tell you; an LLM with the ability name + boss-guide context
  + breadth pattern can classify it (and we only mitigate the unavoidable, raid-wide ones).
- **Ability ID → human mechanic name** — cross-reference BigWigs spell names so output is readable.
- **Severity / priority** — "does this *warrant* a major raid CD, or is it healable normally?" —
  calibrated against raid HP and healer throughput, articulated in words.
- **Proposing `coverage_required`** — synthesize the above into `{dr_magic: 1, aoe_heal: 1}` per event,
  **as a draft for the raid leader to review** — not gospel.
- **Explanation** — "Empowered Whirling Corruption: 1.2M raid-wide magic burst every ~45s,
  unavoidable → recommend a magic-DR each cast."

Honest division of labor: **deterministic pipeline produces the numbers; AI labels and explains them.**
The AI does *not* do the assignment (still the deterministic solver) and its coverage drafts are
human-reviewed. This keeps it trustworthy while using AI for what it's actually good at — semantic
judgment over messy, context-dependent data.

## 6. How this plugs into the engine

The log pipeline **auto-generates the encounter model** (`events[]` with `coverage_required`,
`recurs_every`, `school`, `severity`, `phase`) that `engine-design.md` §3 previously assumed was
hand-authored. Everything downstream — supply/demand ledger (Layer 0), greedy/CP-SAT assignment — is
unchanged; it just gets fed evidence-based input instead of guesses. Kill time `T` and phase boundaries
also come straight from the logs, sharpening `occurrences()` and `charges()`.

## 7. Risks / open questions
- **Sample selection bias** — a parse-padding speed-kill comp mitigates differently than ours. Mitigate:
  pick logs with similar comp/difficulty; let the raid leader pick the reference log.
- **Avoidable-damage noise** — the classifier must not flag "stood in bad" as required coverage; breadth
  + consistency-across-pulls + guide context help separate mechanics from mistakes.
- **Rate limits / points** — cache per-report results; pull raw events only for the top candidate
  abilities, not the whole fight.
- **MoP Classic IDs** — confirm zone/encounter IDs for SoO Classic before building queries.
- **Difficulty scaling** — pull from the difficulty we actually raid (LFR/Flex/Normal/Heroic differ).

## 8. Suggested build order (revised)
1. WCL client + auth + cache layer.
2. Fights query → kill time + phases for one boss (cheap, proves auth + parsing).
3. Deterministic damage-profiler → candidate events table for that boss.
4. AI classification → draft `coverage_required` (review with raid leader).
5. Feed into the existing Layer 0 supply/demand ledger → full loop on one boss.

## Sources
- [Warcraft Logs API docs (auth, GraphQL)](https://www.warcraftlogs.com/api/docs)
- [Warcraft Logs v2 GraphQL schema — Query root](https://www.warcraftlogs.com/v2-api-docs/warcraft/query.doc.html)
- [Warcraft Logs v2 — ReportFight (kill time, phaseTransitions)](https://www.warcraftlogs.com/v2-api-docs/warcraft/reportfight.doc.html)
- [Example OAuth2 client-credentials apps (Node/Python)](https://github.com/Farah404/Raiddon-wclog-data)
