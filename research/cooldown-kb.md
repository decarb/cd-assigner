# MoP (5.4.x) Raid Cooldown Knowledge Base — starter

Source: healiocentric "Raid Cooldowns Overview" (MoP-era, 2013) + general knowledge.
⚠️ **Verify exact values against wowhead `mop-classic` before shipping** — a few numbers below
(e.g. Spirit Link CD, Anti-Magic Zone duration) need confirmation for 5.4.8 Classic.

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
| Healing Tide Totem | Shaman / Resto | 180s | 10s | aoe_heal | pulsed heal |
| Ascendance | Shaman / Resto | 180s | 15s | aoe_heal | copies healing |
| Ancestral Guidance | Shaman / Ele,Enh | 120s | 10s | aoe_heal | dmg→heal |
| Divine Hymn | Priest / Holy | 180s | 8s channel | aoe_heal | heal + 10% healing taken |
| Spirit Shell | Priest / Disc | 60s | 10s | aoe_heal | absorb conversion |
| Light's Hammer | Paladin / Holy | 60s | 16s | aoe_heal | ground AoE |
| Vampiric Embrace | Priest / Shadow | 180s | 10-15s | aoe_heal | dmg→heal |
| Power Word: Barrier | Priest / Disc | 180s | 10s | dr_all | -25% in area |
| Devotion Aura | Paladin / Holy | 180s | 6s | dr_magic | -20% magic, 40yd |
| Spirit Link Totem | Shaman / Resto | 180s* | 6s | dr_all | -10% + HP redistribute |
| Anti-Magic Zone | Death Knight | 120s | 3s* | dr_magic | -40% magic |
| Smoke Bomb | Rogue | 180s | 5-7s | dr_all | -20%, tiny radius |
| Demoralizing Banner | Warrior | 180s | 15s | dr_all | -10% enemy dmg dealt |
| Rallying Cry | Warrior | 180s | 10s | effective_health | +20% max HP |
| Pain Suppression | Priest / Disc | 180s | 8s | external | -40% dmg taken |
| Guardian Spirit | Priest / Holy | 180s | 10s | external | +40% healing / death save |
| Ironbark | Druid / Resto | 60s | 12s | external | -20% dmg taken |
| Hand of Sacrifice | Paladin | 150s | 12s | external | redirect 30% dmg |
| Hand of Protection | Paladin | 300s | 10s | external | physical immunity |
| Life Cocoon | Monk / Mistweaver | 120s | 12s | external | absorb + 50% healing |
| Zen Meditation | Monk / Mistweaver | 180s | 8s | external | -90% magic to self |

\* needs verification for 5.4.8 Classic.

## Why each field matters to the engine
- **recharge** + **fight duration** → how many *charges* of this ability exist in the fight (supply).
- **type** → which events it can cover (a `dr_magic` CD can't cover a physical spike).
- **duration** → whether one cast covers the whole damage window or just an instant.
- **target** (`external`) → those are assigned to a *recipient* (usually a tank), a different sub-problem.
