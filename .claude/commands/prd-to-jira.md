---
description: Parse PRD.md into Epics/Stories/Subtasks and create (or sync) them in Jira
argument-hint: [path-to-PRD.md — defaults to PRD.md at the repo root]
---

Run the **Backlog Refinement** ceremony from `sdlc.yaml`: turn a written PRD into real Jira
issues. Use the Jira skill (`.claude/skills/jira/SKILL.md`) for every tracker call — prefer
its MCP path, fall back to `.claude/skills/jira/jira.sh` (REST) if MCP isn't connected.

**Target file:** `$ARGUMENTS`, or `PRD.md` at the repo root if no argument was given.

## 1. Preflight

- If the target file doesn't exist: tell the user to copy
  `docs/templates/PRD_TEMPLATE.md` to `PRD.md` (or the path they intended) and fill it in,
  then stop — do not invent PRD content yourself.
- Confirm Jira is reachable: check for MCP Jira tools via `ToolSearch`, else confirm
  `JIRA_BASE_URL`/`JIRA_EMAIL`/`JIRA_API_TOKEN`/`JIRA_PROJECT_KEY` are set (repo `.env` or
  shell env). If neither path is available, point the user at `.env.example` and
  `.claude/skills/jira/SKILL.md` and stop.

## 2. Parse the PRD

Read the file and walk it top to bottom:

- `## Epic: <title>` starts an Epic. Everything under "**Description:**" until the next
  `##`/`###` heading is its description.
- `### Story: <title>` under an Epic starts a Story belonging to that Epic. Its body (the
  "As a … I want … so that …" line plus the "**Acceptance Criteria:**" list) becomes the
  Story description — keep the Given/When/Then bullets verbatim, they're the acceptance
  criteria the test-engineer will implement against later.
- A `**Subtasks:**` checklist (`- [ ]` lines) under a Story becomes one Jira Subtask per
  line, parented to that Story.

**Skip anything already synced.** If a heading or checklist line already has a trailing
`<!-- jira: KEY -->` marker, it has a Jira issue — do not recreate it. If you're updating an
already-synced item (title/description changed since last sync), use `jira.sh get <key>` to
compare and only call an update/transition, never `create-*`, for a marked item.

**Sanity-check before creating anything:** a Story with no acceptance criteria, or an Epic
with no Stories, is not Ready — flag it in your summary and skip creating that item rather
than filing an empty ticket. Report what you skipped and why.

## 3. Create in dependency order

For each **new** (unmarked) item, Epics first, then their Stories, then each Story's
Subtasks — a Story needs its Epic's key before it can be created, same for Subtasks and
their Story:

```bash
.claude/skills/jira/jira.sh create-epic    "<epic title>"  "<epic description>"
.claude/skills/jira/jira.sh create-story   "<story title>" "<story body incl. AC>" <epic-key>
.claude/skills/jira/jira.sh create-subtask "<subtask text>" ""                     <story-key>
```

(Or the MCP equivalents, if that's the connected path — same order and parenting.)

## 4. Write the keys back

After each successful create, edit `PRD.md` in place to append `<!-- jira: KEY -->` right
after that heading or checklist line. This is what makes reruns idempotent — a second
`/prd-to-jira` on the same file should create **zero** new issues and only report "already
synced" plus any genuine updates.

## 5. Report

A table: Epic/Story/Subtask · title · Jira key · `$JIRA_BASE_URL/browse/<key>` link. Call
out anything skipped (not Ready) or failed, with the reason.
