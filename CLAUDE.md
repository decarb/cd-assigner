# CD Assigner

Auto-suggests **raid cooldown assignments** for WoW **Mists of Pandaria (5.4.8) Siege of Orgrimmar**.
Input: a raid roster (Raid-Helper API or manual). Output: suggested cooldown assignments for a boss.
Extensible to other instances.

**Core insight:** existing tools (Viserio, the raid leader's Excel sheet) are *manual* placement —
nobody auto-solves the assignment, and automating that is the whole point. The engine is a
deterministic **supply-vs-demand / interval-scheduling** problem; AI is bounded to labeling log data
and explaining, **not** doing the assignment.

## Read first
- `HANDOVER.md` — project state, decisions, verified facts, next steps. **Start here.**
- `research/engine-design.md` — the suggestion engine (the crux).

## Scope
- v1 is **roster-in → suggestions-out only**. WeakAuras export is **out of scope**.

## Secrets — strict
- WCL API credentials live in `.env` (gitignored): `WCL_CLIENT_ID`, `WCL_CLIENT_SECRET`.
- **Never** `cat`/Read/print `.env` or its values. Load via environment at runtime only.
- **Never** ask the user to paste secrets into chat — they edit `.env` directly in their editor.

## Stack & conventions
- **Scala 3 via scala-cli**, script-first. Mirrors `~/scala-cli/ranking-table`: cats-effect, opaque
  types, `-Wunused:all`, packages under `io.github.decarb.cdassigner`, decline-effect CLI, munit tests.
  HTTP + JSON = sttp client4 (cats-effect backend) + circe.
- Keep deterministic logic and AI/LLM calls cleanly separated — the solver must stay deterministic.

## Build / run
- `scala-cli compile .` · `scala-cli test .`
- `scala-cli run . -- pull <raidplan-url-or-id> [-o out.json]` — fetch + normalize a roster to JSON.
- `scala-cli run . -- supply --roster <roster.json> --kill-time <s|m:ss>` — supply ledger (raid CDs + charges).
- Format: `scala-cli fmt .` · Lint: `scala-cli fix --power .` (configs `.scalafmt.conf`, `.scalafix.conf`).

## Repo
- `git@github.com:decarb/cd-assigner.git` (branch `master`). Commit/push only when asked; conventional
  commit + bulleted body; no `Co-Authored-By` trailers.
