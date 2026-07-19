# SDLC subagents

This project ships a set of Claude Code subagents covering an agile team: **role agents**
that decide *what* to build and *how the team works*, and **SDLC agents** that execute each
phase. Each is a specialist with a system prompt that encodes this archetype's conventions,
and they hand off to one another explicitly.

The whole thing runs as Scrum: [`sdlc.yaml`](../../sdlc.yaml) is the machine-readable source
of truth for the roles, ceremonies (standup/planning/review/retro), phase gates, and the
Jira tracker wiring. Write backlog items in `PRD.md` (template:
[`docs/templates/PRD_TEMPLATE.md`](../../docs/templates/PRD_TEMPLATE.md)), then run
`/prd-to-jira` to turn it into linked Epics/Stories/Subtasks in Jira — see
[`.claude/skills/jira/SKILL.md`](../skills/jira/SKILL.md) for how the tracker connection
works (Atlassian MCP preferred, REST API token fallback).

## Agile & design roles

These wrap the engineering pipeline — they shape and schedule the work, but don't write code.

| Role            | Agent            | Owns                                   | Hands off to               |
|-----------------|------------------|----------------------------------------|----------------------------|
| Product Owner   | `product-owner`  | Backlog, priorities, enhancement items | scrum-master, requirements-analyst |
| Scrum Master    | `scrum-master`   | Sprint plan, board, ceremonies, blockers | the SDLC pipeline        |
| UI/UX Designer  | `ux-designer`    | API/UX & error ergonomics; frontend flows/wireframes | solution-architect, senior-developer |

Flow: **product-owner** defines and prioritizes the backlog → **scrum-master** plans a
sprint and coordinates → the SDLC pipeline below executes, with **ux-designer** informing
the API/UX shape alongside the architect. Role agents can manage work in a connected issue
tracker (Jira/Linear/Asana/monday) or design tool (Figma) via MCP, or fall back to Markdown
artifacts under `docs/`.

## The pipeline

| # | Phase                    | Agent                 | Writes code? | Hands off to            |
|---|--------------------------|-----------------------|--------------|-------------------------|
| 1 | Requirements             | `requirements-analyst`| No           | solution-architect      |
| 2 | Design                   | `solution-architect`  | No           | senior-developer, or work-planner |
| 2.5 | Work Planning & Orchestration (optional) | `work-planner` | No (dispatches those who do) | scrum-master (status), then code-reviewer/security-auditor |
| 3 | Implementation           | `senior-developer`    | Yes          | test-engineer, reviewer |
| 4 | Testing                  | `test-engineer`       | Yes (tests)  | code-reviewer           |
| 5 | Review                   | `code-reviewer`       | No           | (back to dev if changes — capped, see below)|
| 5 | Security review          | `security-auditor`    | No           | (back to dev if changes — capped, see below)|
| 6 | Team Lead approval       | `team-lead`           | No           | devops-engineer (or back to any earlier phase) |
| 7 | Build / Release          | `devops-engineer`     | Yes (infra)  | —                       |

Phases 5 (review + security) run in parallel and gate phase 6. `team-lead` is the final
go/no-go: it confirms the acceptance criteria are genuinely met and every earlier gate
actually holds (not just claimed) before release — it doesn't re-review code or re-audit
security, and it sends work back to whichever phase's gate didn't really hold, not just to
`senior-developer`.

## The dev↔review loop is capped at 3 rounds

A rejection at Code Review, Security Review, or Team Lead sends work back for a fix and
another pass — but `sdlc.yaml`'s `review_iteration_limit` (default 3) caps how many times
that can happen on essentially the same diff before it stops being automatic. Reaching the
cap without a clean verdict isn't "try again" — `team-lead` escalates instead: it compiles
every round's findings into one report (what's been tried, what's still failing, and why
the same class of issue keeps recurring — usually a fix addressing symptoms, not the actual
finding) and hands it to `scrum-master` as a blocker for a human, rather than dispatching a
4th automatic attempt. `code-reviewer` and `security-auditor` name their round number in
any non-first verdict so the pattern across rounds is visible, not just the latest one.

## Task classification: not every item needs the full pipeline

Before Design, `requirements-analyst` classifies the task into a tier
(`sdlc.yaml`'s `task_classification`) and states which phases that tier's
`workflow_profiles` entry actually runs:

| Tier | When | Skips | Definition of done |
|------|------|-------|---------------------|
| `fast_path` | Low risk: no schema change, no new dependency, doesn't touch authn/authz/payment/PII, single tightly-scoped change | Design, Work Planning, Security Review — `code-reviewer` absorbs a lightweight security spot-check instead | `fast_path` in `sdlc.yaml` |
| `standard` | The default — anything not clearly Fast Path or High Risk | Nothing | `standard` in `sdlc.yaml` |
| `high_risk` | Touches authn/authz, payment, PII, a breaking API/schema change, a new external dependency, or is explicitly flagged | Nothing — same phases as Standard, but every gate is stricter (Security Review must be an unqualified `PASS`; Team Lead re-verifies non-functionals; a rollback plan is required) | `high_risk` in `sdlc.yaml` |

Misclassification is corrected forward, not silently absorbed: any agent at any phase that
notices the task doesn't match its assigned tier (a `fast_path` diff that turns out to
touch a migration, say) stops and re-classifies to at least the next-stricter tier rather
than continuing under lighter gates. `team-lead`'s final check re-verifies the tier
actually held, not just that the phases which ran passed.

**Phases 3 and 4 can be orchestrated as one fan-out** (independent of tier — a `standard`
or `high_risk` item can still be wide enough to parallelize; `fast_path` items are single
packages by definition and never need this). For a story wide enough to split,
`work-planner` breaks the design into independent work packages, then dispatches each to
the specialist agent that owns it (`senior-developer` for code, `test-engineer` for a
standalone test suite, `devops-engineer` for CI/infra) — running independent packages
concurrently, each in its own git worktree, merging them back one at a time (a real merge
conflict there means the "independent" call was wrong, and is surfaced rather than resolved
silently). Once everything is merged it dispatches `test-engineer` against the whole batch
and reports status to `scrum-master`. Small/atomic changes skip straight from Design to a
single `senior-developer` run, same as before.

Once a PR is open, run `/review-pr <pr-number>` to drive phase 5 against it directly: it
checks the PR out into an isolated git worktree (your own working tree is never touched),
runs `code-reviewer` and `security-auditor` in parallel, posts their findings as a PR
review, and auto-merges **only** if both come back fully clean (code-reviewer `APPROVE`,
security-auditor `PASS` — no qualifiers). Anything less is left for a human. Each posted
comment carries a hidden round marker so re-running `/review-pr` on the same PR counts
against the cap above automatically — by round 3 it stops reporting per-round and posts a
single escalation summarizing every round instead. See
[`.claude/commands/review-pr.md`](../commands/review-pr.md) and the `vcs:` block in
[`sdlc.yaml`](../../sdlc.yaml). GitHub access is via the `gh` CLI by default; the GitHub MCP
server in [`.mcp.json`](../../.mcp.json) is also available for agents that need structured
tool calls instead.

## How to drive it

Claude Code routes to a subagent when you describe phase-shaped work, or you can name one:

```
> Use product-owner to propose and prioritize the next enhancements for {{PROJECT_PURPOSE}}.
> Use scrum-master to plan a sprint from the top backlog items.
> Use ux-designer to design the API UX (errors, pagination, OpenAPI examples) for it.
> Use requirements-analyst to spec a "discount code" feature for the catalog service.
> Use requirements-analyst to spec fixing a typo in the product description field — expect it to classify as fast_path.
> Use solution-architect to design it.
> Use senior-developer to implement the agreed design.
> Use work-planner to split a wide design into work packages and dispatch them in parallel.
> Use test-engineer to cover the acceptance criteria.
> Use code-reviewer and security-auditor to review the diff.
> Use team-lead to give the final go/no-go before merge.
> Use devops-engineer to add the CI job and build the native image.
```

Each agent reads the existing `product` feature as the reference implementation, so the
output stays consistent with the codebase.

## Design principles baked into the agents

- **Roles plan, engineers build.** Product owner, scrum master, ux-designer, analysts,
  architects, reviewers, and team-lead don't edit source — they produce backlog items,
  sprint plans, designs, specs, findings, and verdicts. Only the developer, test, and
  devops agents write code. This keeps responsibilities honest.
- **`work-planner` is the one deliberate exception.** Every other agent's `tools:`
  frontmatter omits `Agent`, so it can't spawn another subagent — `work-planner` is the
  single agent trusted with `Agent` (plus `Bash`, for git worktrees) so a wide design can
  be dispatched to `senior-developer`/`test-engineer`/`devops-engineer` in parallel without
  a human re-invoking each one by hand. It still doesn't write source itself — see its
  Constraints section — it only plans, dispatches, merges, and reports.
- **Definition of done is testable — and tiered.** Backlog items get acceptance criteria,
  requirements become Given/When/Then, which become tests, which the reviewer checks for.
  Nothing is "done" without a green build, but what "done" actually requires scales with
  risk: see Task classification above and the `definition_of_done` map in `sdlc.yaml`
  (`fast_path` / `standard` / `high_risk`).
- **Conventions over re-explanation.** Every agent points at the same rules (package-by-
  layer, Flyway-owned schema, DTO boundaries, native-image safety) so they don't drift.
- **Don't design/implement against stale knowledge.** `solution-architect`, `senior-developer`,
  `test-engineer`, and `devops-engineer` are instructed to pull current library docs via the
  Context7 MCP server ([`.mcp.json`](../../.mcp.json)) before committing to an API shape they
  aren't certain is still current, falling back to `WebSearch` if Context7 isn't connected.
  See [`docs/MCP_SERVERS.md`](../../docs/MCP_SERVERS.md#core).

Customize freely: edit the `tools:`/`model:` frontmatter or the prompts to match your
team's process. See the Claude Code subagents docs for the full frontmatter spec.
