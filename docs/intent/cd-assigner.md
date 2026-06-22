# Intent — CD Assigner

_Confirmed via `interview-me`, 2026-06-22. This is the rooted statement of intent that further
development hangs off. Specs and plans are downstream of this; if a spec contradicts this, this wins
(or this gets re-interviewed)._

## The adventure (meta-goal)

- **Outcome:** Replace ad-hoc direct prompting with a repeatable, skill-driven workflow the user
  trusts.
- **Why now:** The user has identified their way of interfacing with AI as their weakest link; a
  skills-focused workflow is the single biggest leverage available right now.
- **Success:** Any project can be driven through a structured `spec → plan → build → review` loop;
  this repo is the proof.

## The tool (the problem the workflow is proven on)

- **Outcome:** Given a **roster + encounter**, auto-suggest an **optimal raidwide cooldown
  schedule** — which player presses which raid CD, and when.
- **User:** The raid leader (RL), weekly, on raid night — chosen *over* their Excel sheet.
- **"Optimal" =** mathematically **maximize damage mitigated assuming perfect play**. A measurable
  objective, not a style-match to what a pro would draw.
- **Logs =** the **warm-start prior**, never the objective. Mine logs with similar kill-times (and,
  in progression, good guilds) for patterns so the scheduler starts from a solid basis.
- **Success:** The RL runs the suggested schedule because it is both *sound* and *defensible*.
- **Hard constraint:** Every suggestion must be **explicable** — grounded in code and logical
  reasoning, never a black box. The code itself must be **readable, maintainable, idiomatic, and
  performant**. Inexplicable scheduling = the tool gets abandoned.

## Out of scope (for now)

- Externals / single-target cooldowns (later).
- WeakAuras export (excluded from v1).
- RL-editable output (later).
- Roster-composition advice (never).
