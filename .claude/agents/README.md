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
| UI/UX Designer  | `ux-designer`    | API/UX & error ergonomics; frontend flows/wireframes | solution-architect, spring-developer |

Flow: **product-owner** defines and prioritizes the backlog → **scrum-master** plans a
sprint and coordinates → the SDLC pipeline below executes, with **ux-designer** informing
the API/UX shape alongside the architect. Role agents can manage work in a connected issue
tracker (Jira/Linear/Asana/monday) or design tool (Figma) via MCP, or fall back to Markdown
artifacts under `docs/`.

## The pipeline

| # | Phase             | Agent                 | Writes code? | Hands off to            |
|---|-------------------|-----------------------|--------------|-------------------------|
| 1 | Requirements      | `requirements-analyst`| No           | solution-architect      |
| 2 | Design            | `solution-architect`  | No           | spring-developer        |
| 3 | Implementation    | `spring-developer`    | Yes          | test-engineer, reviewer |
| 4 | Testing           | `test-engineer`       | Yes (tests)  | code-reviewer           |
| 5 | Review            | `code-reviewer`       | No           | (back to dev if changes)|
| 5 | Security review   | `security-auditor`    | No           | (back to dev if changes)|
| 6 | Team Lead approval| `team-lead`           | No           | devops-engineer (or back to any earlier phase) |
| 7 | Build / Release   | `devops-engineer`     | Yes (infra)  | —                       |

Phases 5 (review + security) run in parallel and gate phase 6. `team-lead` is the final
go/no-go: it confirms the acceptance criteria are genuinely met and every earlier gate
actually holds (not just claimed) before release — it doesn't re-review code or re-audit
security, and it sends work back to whichever phase's gate didn't really hold, not just to
`spring-developer`.

Once a PR is open, run `/review-pr <pr-number>` to drive phase 5 against it directly: it
checks the PR out into an isolated git worktree (your own working tree is never touched),
runs `code-reviewer` and `security-auditor` in parallel, posts their findings as a PR
review, and auto-merges **only** if both come back fully clean (code-reviewer `APPROVE`,
security-auditor `PASS` — no qualifiers). Anything less is left for a human. See
[`.claude/commands/review-pr.md`](../commands/review-pr.md) and the `vcs:` block in
[`sdlc.yaml`](../../sdlc.yaml). GitHub access is via the `gh` CLI by default; the GitHub MCP
server in [`.mcp.json`](../../.mcp.json) is also available for agents that need structured
tool calls instead.

## How to drive it

Claude Code routes to a subagent when you describe phase-shaped work, or you can name one:

```
> Use product-owner to propose and prioritize the next enhancements for this service.
> Use scrum-master to plan a sprint from the top backlog items.
> Use ux-designer to design the API UX (errors, pagination, OpenAPI examples) for it.
> Use requirements-analyst to spec a "discount code" feature for the catalog service.
> Use solution-architect to design it.
> Use spring-developer to implement the agreed design.
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
  sprint plans, designs, specs, findings, and verdicts. Only the developer, test, and devops
  agents write code. This keeps responsibilities honest.
- **Definition of done is testable.** Backlog items get acceptance criteria, requirements
  become Given/When/Then, which become `*IT` tests, which the reviewer checks for. Nothing is
  "done" without a green `verify`.
- **Conventions over re-explanation.** Every agent points at the same rules (package-by-
  layer, Flyway-owned schema, DTO boundaries, native-image safety) so they don't drift.

Customize freely: edit the `tools:`/`model:` frontmatter or the prompts to match your
team's process. See the Claude Code subagents docs for the full frontmatter spec.
