# Cooldown Assigner — Initial Feasibility Assessment

_Date: 2026-06-20_
_Game: World of Warcraft, Mists of Pandaria (5.4.8) — Siege of Orgrimmar_

## 1. Mission (as scoped)

Build a tool that:

1. **Reads in a raid roster** — manually entered, or pulled from our **Raid-Helper** signup.
2. **Suggests cooldown assignments** for a given instance (start: Siege of Orgrimmar; designed to extend to others).

**Out of scope for now:** WeakAuras export / the existing Excel export pipeline. We only care about
roster-in → suggestions-out at this stage.

## 2. The core problem, framed honestly

Cooldown assignment is, underneath the WoW dressing, a **resource-scheduling / assignment problem**:

- You have a **timeline of damage events** per boss (raid-wide bursts, breaths, tower phases, etc.),
  each at a known time, each needing some kind of coverage.
- You have a **pool of cooldowns** that the present roster can provide. Each cooldown has a
  *duration*, a *recharge time*, and a *type* (raid AoE heal, raid damage reduction, single-target
  external, etc.).
- You want to **cover every event** with the right kind/number of cooldowns, **without double-booking**
  a cooldown that's still on recharge.

That is a well-understood class of problem (interval scheduling / constraint satisfaction / can be
expressed as an ILP). **It does not require AI to solve.** AI is optional polish, not the engine.

This is the key feasibility finding: the hard-sounding part ("suggest cooldowns") is actually the
*easy, deterministic* part. The real cost is **data**, not algorithms.

## 3. Component-by-component feasibility

### 3a. Roster ingestion — EASY (✅ verified against a live event)

- **Manual:** paste/enter a list of `name, class, spec`. Trivial.
- **Raid-Helper — PRIMARY source = the Raidplan / composition tool.** ✅ **CONFIRMED WORKING.**
  `GET https://raid-helper.dev/api/raidplan/{raidplanId}` (no auth). This is the *finalized assigned
  composition* — the actual roster going into the raid — which is what we want, not raw signups.
  - Returns `slots[]`, each: `slotNumber, groupNumber, name, className, specName, isConfirmed, color`.
    Also top-level `groupCount, slotCount, title`. `groupNumber` reconstructs the raid groups.
  - Verified on a real 25-person SoO comp. Sample: `research/raidhelper-raidplan-sample.json`.
- **Raid-Helper — secondary = the Event signups endpoint.**
  `GET https://raid-helper.dev/api/event/{eventId}` (singular `event`, **no** `/v2/`; `/api/v2/...`
  404s). No auth. Returns `signups[]` with `name, class, spec, cClass, cSpec, role, status, userid`.
  Use when there's no raidplan yet (raw availability). Sample: `research/raidhelper-event-sample.json`.
  - ⚠️ **Normalization wrinkle (minor but real, same in both endpoints):** values are
    *template-specific*, not canonical:
    - **Tanks/DKs are bucketed by role-ish className** — `className = "Tank"` with the real class in
      the spec (`Protection1` = Prot Warrior, `Guardian` = Guardian Druid, `Blood`/className "DK" =
      Blood DK, `Brewmaster` = Monk). Must infer class from spec for these.
    - **Spec names carry disambiguating digits**: `Holy1` = Holy **Paladin** (vs `Holy` = Holy
      Priest); `Restoration1` = Resto **Shaman** (vs `Restoration` = Resto Druid); `Protection1` =
      Prot **Warrior** (vs `Protection` = Prot Paladin).
  - Mitigation: one small static `(className, specName) → canonical {class, spec, role}` mapping
    table. Finite (~40 rows), authorable in an hour. Manual entry remains the fallback.

### 3b. Cooldown knowledge base — EASY (static data, small)

A static table: `class → spec → ability → {duration, recharge, type, target}`. For MoP 5.4.8 this is
~30–40 entries. Examples of the raid CDs that actually get assigned:

- **Disc Priest:** Power Word: Barrier, Pain Suppression (external)
- **Holy Priest:** Divine Hymn, Guardian Spirit (external)
- **Resto Druid:** Tranquility, Ironbark (external)
- **Resto Shaman:** Healing Tide Totem, Spirit Link Totem, Ancestral Guidance
- **Holy Paladin:** Devotion Aura, Aura Mastery, Hand of Sacrifice/Protection (external)
- **Mistweaver Monk:** Revival, Life Cocoon (external)
- **Warrior:** Rallying Cry; (offensive) Skull Banner
- **DK:** Anti-Magic Zone

This is authorable by hand in an afternoon. Stable across a patch.

### 3c. Encounter timelines — THE REAL WORK

To suggest *when* to use cooldowns, the tool needs each boss's **damage-event timeline**:
which mechanics hit the raid, when, and how hard. e.g. SoO highlights:

- Iron Juggernaut — Borer Drill / Cutter Laser cadence
- Spoils of Pandaria — box-opening burst
- Thok — Fearsome Roar / breath phases
- Garrosh — Whirling Corruption / Empowered Whirling Corruption / Annihilate

**✅ Major de-risk: BigWigs is a machine-readable source.** The dedicated
[`BigWigsMods/BigWigs_MistsOfPandaria`](https://github.com/BigWigsMods/BigWigs_MistsOfPandaria) repo
has **one Lua file per SoO boss** (all 14 present: Garrosh, Iron Juggernaut, Spoils, Thok, etc.).
Inspected `IronJuggernaut.lua` + `GarroshHellscream.lua` (saved under `research/bigwigs/`). The timer
data is right there and readable:

```lua
self:Bar(-8179, 19)        -- Borer Drill in 19s
self:Bar(144498, 10)       -- Explosive Tar in 10s
self:Bar("stages", 120, CL.phase:format(2), 144498)  -- phase 2 in 120s
self:Berserk(self:Mythic() and 450 or 600)           -- difficulty-aware
```

So **spell IDs + durations + names + difficulty branches are all extractable.** DBM is a second source.

Remaining complications (now Medium, not High):
- Timers are **event-driven state machines**, not a flat timestamp list — bars are (re)scheduled in
  response to combat-log casts/phase changes. For an absolute "happens at 1:30" timeline you must
  either (a) extract *recurrence intervals + pull timers* and reconstruct, or (b) pull a
  representative fight from **Warcraftlogs**.
- Timings still vary by **difficulty** and **kill speed** (%HP phase transitions).

Mitigations:
- For cooldown assignment you usually need *cadence* ("Borer Drill ~every 19s in P1"), not
  second-perfect timing — and BigWigs gives cadence directly.
- Model events as `{phase, %HP or time, recurrence}` rather than fixed timestamps.
- Start with **one or two bosses**, validated by the raid leader (human-in-the-loop tuning).

### 3d. The assignment engine — FEASIBLE, deterministic-first

Recommended approach: **deterministic solver first, AI optional later.**

- A greedy/constraint-based assigner covers events in time order, picking the cheapest available
  cooldown of the required type whose recharge is up. This alone produces sensible, explainable
  assignments and runs instantly.
- Upgrade path: express as a constraint/ILP problem if we want provably-balanced load across players.
- **Where AI genuinely helps:** (1) natural-language interface ("we're light on healers tonight,
  bias defensives"), (2) encoding fuzzy raid-leader preferences/best-practice heuristics, (3)
  explaining *why* an assignment was made. None of this is required for a working v1.

Deterministic is preferable for the core because the raid leader needs **consistent, explainable,
correctable** output — not a black box that hallucinates a cooldown a spec doesn't have.

## 4. AI: needed or not?

**Not needed for the core.** The suggestion engine is a scheduling problem solved better by a
deterministic solver than an LLM (which could invent abilities or mis-time events). Reserve AI for
the conversational/explanatory layer once the engine works. This keeps v1 cheap, fast, offline-capable,
and trustworthy.

## 5. Key risks / unknowns

| Risk | Severity | Mitigation |
|---|---|---|
| Encounter timeline data acquisition | **Medium** (main effort; de-risked) | Mine BigWigs MoP repo (source found); cadence over exact timing; %HP phase model; logs for precision |
| Raid-Helper API field names / stability | Low (✅ verified) | Endpoints confirmed working; normalization table; manual fallback |
| Difficulty/kill-speed variance breaks timings | Medium | Model phases relatively, not absolute timestamps |
| Defining "good" coverage (how many CDs per event) | Medium | Encode raid-leader's rules as explicit config, not guesswork |
| Scope creep back into WeakAuras export | Low (deferred) | Keep output as plain structured data v1 |

## 6. Suggested phased MVP

1. **Data model + cooldown KB** — schema for roster, abilities, events, assignments. (days)
2. **Manual roster + 1 boss (e.g. Garrosh), hand-authored timeline.** (days)
3. **Deterministic assigner → human-readable suggestion output (table/text).** (days)
4. **Raid-Helper import.** (days)
5. **More bosses; tuning loop with raid leader.** (ongoing)
6. _(Later / optional)_ AI explanation layer; WeakAura/Excel export bridge.

## 7. Verdict

**Feasible, and the riskiest part is data collection, not engineering.** The "suggest cooldowns"
brain is a tractable deterministic scheduling problem. Roster ingestion (manual + Raid-Helper) is
easy. The make-or-break effort is building accurate, maintainable encounter timelines — best
de-risked by starting with one boss and a tight feedback loop with the raid leader. AI is an optional
enhancement, not a dependency.

## Sources
- [Raid-Helper API documentation](https://raid-helper.dev/documentation/api)
- [Siege of Orgrimmar MoP WeakAuras (TaurIO/stormforge)](https://wa.stormforge.gg/search/weakaura/mop/siege-of-orgrimmar)
- [python-weakauras-tool (for later WA export)](https://github.com/geexmmo/python-weakauras-tool)
- [WeakAuras2 Transmission.lua (export format, for later)](https://github.com/WeakAuras/WeakAuras2/blob/main/WeakAuras/Transmission.lua)
