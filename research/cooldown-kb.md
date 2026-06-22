# MoP (5.4.x) Raid Cooldown Knowledge Base — starter

Source: healiocentric "Raid Cooldowns Overview" (MoP-era, 2013) + general knowledge, then
**audited spell-by-spell against wowhead `mop-classic` on 2026-06-22** (see "Verification" below).
All recharge and duration values now reflect 5.4.x game data.

This is the `ability` table the engine reads. Fields the engine needs per ability:
`class, spec, recharge (s), duration (s), type, target, magnitude`.

`type` taxonomy (drives coverage matching):
- `aoe_heal` — raid-wide healing throughput
- `dr_magic` / `dr_physical` / `dr_all` — raid-wide damage reduction (typed by school)
- `effective_health` — temporary max-HP (Rallying Cry)
- `external` — single-target defensive cast on another player

| Ability | Class / Spec | Recharge | Duration | Type | Magnitude |
|---|---|---|---|---|---|
| Tranquility | Druid / Resto | 180s | 8s channel | aoe_heal | strong heal + HoT |
| Tranquility | Druid / non-Resto | 480s | 8s | aoe_heal | weak |
| Revival | Monk / Mistweaver | 180s | instant | aoe_heal | burst + dispel |
| Healing Tide Totem | Shaman / Resto | 180s | 11s | aoe_heal | pulsed heal |
| Ascendance | Shaman / Resto | 180s | 15s | aoe_heal | copies healing |
| Ancestral Guidance | Shaman / Ele,Enh | 120s | 10s | aoe_heal | dmg→heal |
| Divine Hymn | Priest / Holy | 180s | 8s channel | aoe_heal | heal + 10% healing taken |
| Spirit Shell | Priest / Disc | 60s | 10s | aoe_heal | absorb conversion |
| Light's Hammer | Paladin / Holy | 60s | 14s | aoe_heal | ground AoE |
| Vampiric Embrace | Priest / Shadow | 180s | 10-15s | aoe_heal | dmg→heal |
| Power Word: Barrier | Priest / Disc | 180s | 10s | dr_all | -25% in area |
| Devotion Aura | Paladin / Holy | 180s | 6s | dr_all | -20% **all** dmg, 40yd |
| Devotion Aura | Paladin / Ret,Prot | 180s | 6s | dr_magic | same aura, **magic only**, 40yd |
| Spirit Link Totem | Shaman / Resto | 180s | 6s | dr_all | -10% + HP redistribute |
| Anti-Magic Zone | Death Knight | 120s | 3s | dr_magic | -40% magic |
| Smoke Bomb | Rogue | 180s | 5s | dr_all | targeting block, tiny radius |
| Demoralizing Banner | Warrior | 180s | 15s | dr_all | -10% enemy dmg dealt |
| Rallying Cry | Warrior | 180s | 10s | effective_health | +20% max HP |
| Pain Suppression | Priest / Disc | 180s | 8s | external | -40% dmg taken |
| Guardian Spirit | Priest / Holy | 180s | 10s | external | +60% healing / death save |
| Ironbark | Druid / Resto | 60s | 12s | external | -20% dmg taken |
| Hand of Sacrifice | Paladin | 120s | 12s | external | redirect 30% dmg (60s w/ Unbreakable Spirit) |
| Hand of Protection | Paladin | 300s | 10s | external | physical immunity |
| Life Cocoon | Monk / Mistweaver | 120s | 12s | external | absorb + 50% healing |
| Zen Meditation | Monk / Mistweaver | 180s | 8s | external | -90% all dmg to self + redirect |

## Verification (2026-06-22)

Every ability above was checked against its wowhead `mop-classic` spell page
(`wowhead.com/mop-classic/spell=<id>`). The game is frozen at 5.4.x, so these values are final —
this is a one-time audit, not something to re-poll. Corrections applied:

| Ability | Was | Now | Note |
|---|---|---|---|
| Devotion Aura | Holy only, `dr_magic` | Holy `dr_all`; Ret/Prot `dr_magic` | Holy reduces **all** dmg; Ret/Prot same aura, **magic only** |
| Hand of Sacrifice | 150s | 120s | base CD; Unbreakable Spirit talent halves to 60s |
| Light's Hammer | 16s | 14s | Arcing Light duration |
| Healing Tide Totem | 10s | 11s | totem duration |
| Smoke Bomb | 5–7s, "-20%" | 5s, "targeting block" | primary effect is a targeting/LoS block, not a flat DR |
| Guardian Spirit | +40% healing | +60% healing | MoP value |
| Zen Meditation | "-90% magic to self" | "-90% all dmg to self + redirect" | all schools, plus ally-spell redirect |

Confirmed unchanged (recharge/duration matched): Tranquility (480s base / 8s; Resto 180s via spec
passive), Revival (180s), Ascendance (180s/15s), Ancestral Guidance (120s/10s), Divine Hymn
(180s/8s), Spirit Shell (60s/10s), Vampiric Embrace (180s/15s), Power Word: Barrier (180s/10s),
Spirit Link Totem (180s/6s), Anti-Magic Zone (120s/3s), Demoralizing Banner (180s/15s), Rallying Cry
(180s/10s), Pain Suppression (180s/8s), Ironbark (60s/12s), Hand of Protection (300s/10s), Life
Cocoon (120s/12s).

**Method note:** wowhead has no official API; these are the `mop-classic` spell tooltip pages. For
any future bulk validation, prefer a one-shot static DBC/DB2 export (e.g. wago.tools CSV for the
5.4.x build, or a 5.4.8 private-server `spell_dbc` dump) over repeated page fetches.

**Talents/glyphs are roster-invisible** (Raid-Helper gives class/spec only) and aren't modelled — the
KB lists **base** values only. Examples that would change supply: Clemency (+1 charge on the Hands),
Unbreakable Spirit (Hand of Sac → 60s). WCL logs expose per-player talents, so the demand stage is the
natural place to resolve these later.

## Why each field matters to the engine
- **recharge** + **fight duration** → how many *charges* of this ability exist in the fight (supply).
- **type** → which events it can cover (a `dr_magic` CD can't cover a physical spike).
- **duration** → whether one cast covers the whole damage window or just an instant.
- **target** (`external`) → those are assigned to a *recipient* (usually a tank), a different sub-problem.
