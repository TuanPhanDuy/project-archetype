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

### Ad hoc QA outside the phase pipeline

`/qa-validate` ([`.claude/skills/qa-validate/SKILL.md`](../skills/qa-validate/SKILL.md)) is a
standalone skill, not a numbered phase — call it any time to hunt bugs or backfill
regression tests. Where `test-engineer` (phase 4) proves acceptance criteria already agreed
on, `qa-validate` is for "did this actually work" on demand: it insists on driving the real
running service (curl/`RestTestClient` against a live instance, not just reading the diff)
before calling anything a confirmed bug, then writes the regression test and re-verifies the
fix the same way it found the break.

## Every task is implemented on its own branch — never on main

Implementation (phase 3) always starts by checking out a feature branch
(`feature/<jira-key>-<slug>`, per `CONTRIBUTING.md`) off the latest `main` — `main` itself
is never touched directly. `senior-developer` does this itself on the direct path;
`work-planner` does it once for the whole story before splitting into per-package worktree
branches, all forked from that one feature branch rather than `main`. Creating and
switching to a local branch is the one git action these agents take without being asked
first — it's local and reversible, and the entire point is to guarantee nothing lands on
`main` unreviewed. Committing, pushing the branch, and opening the PR remain separate,
explicitly-requested actions, same as always (see each agent's Constraints/Environment
notes). Code Review and Security Review (phase 5) require a PR to already be open —
`/review-pr` reviews an existing PR, it doesn't create one — and the only way a change
reaches `main` is that PR's squash-merge (`vcs.auto_merge_requires` in `sdlc.yaml`), never
a direct push.

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

## Daily knowledge refresh

Generated projects run `.github/workflows/knowledge-refresh.yml` (needs `ANTHROPIC_API_KEY`);
the archetype repo itself uses a Claude cloud routine (claude.ai/code/routines) instead. Both
run `/refresh-knowledge` every day: Claude updates
[`docs/knowledge/TECH_RADAR.md`](../../docs/knowledge/TECH_RADAR.md) (latest stable versions,
security advisories, new features, deprecations) and opens one PR for human review. Agents read
the radar before relying on training data. Only security patch bumps touch `pom.xml`.

## Cross-service requests: each service evaluates and acts on its own inbox

When several sessions each own a service, no session edits another service's code. Instead:

- **Ask** with `request-service-change`: it files a `service-request` issue in the owner's
  repo (what, why, acceptance criteria, contract, urgency) and nudges the owner's live
  session with `SendMessage` if one is running. A service that changes a contract it `owns`
  files one to every service that `consumes` it (`cross_service.registry` in `sdlc.yaml`).
- **Receive** with `service-inbox`: it collects open requests, messages from other sessions
  and radar **Action needed** items, then decides for each: `not-ours` (re-filed to the
  owner), `done`, `needs-info`, `needs-decision`, or `act`. `act` claims the issue, runs the
  tier's normal pipeline and `/review-pr`; `high_risk` never auto-merges. The outcome is
  commented back on the issue and the origin.

It runs in a live session (`/loop 30m /service-inbox`) or, with no session open, in
`.github/workflows/service-inbox.yml` on every new request and every 4 hours. Give that
workflow a `SERVICE_INBOX_TOKEN` (PAT or GitHub App) so its PRs trigger CI and it can comment
in other repos. Only requests from authors with write access, or from registered services,
are acted on automatically.

## Misread requirements: retry at most 3 times, then clarify

If work is wrong because the context or requirement was misunderstood, an agent may redo it
at most `clarification_retry_limit` times (default 3, `sdlc.yaml`). After that it stops and
asks: what it understood, what it tried, and one clear question — to the requester, or to
`requirements-analyst` for story-level gaps.

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

## Model and effort selection: two axes, picked at every dispatch point

Any agent that dispatches another is the one positioned to control token cost/latency and
review depth for that unit of work — that's `work-planner` for Implementation/Testing, and
`/review-pr` (or whoever else dispatches `code-reviewer`/`security-auditor`/`team-lead`
directly) for the review phases. Both pick from `sdlc.yaml`'s `model_selection` (which
model) and `effort_selection` (how thorough a pass):

| Model | Fits | Doesn't fit |
|-------|------|--------------|
| `haiku` | Mechanical, pattern-following work — a config/copy tweak, a CRUD endpoint mirroring an existing one 1:1, test scaffolding copied from an existing test; `fast_path` review items | Anything with real design judgment left open |
| `sonnet` | The default — typical implementation/test packages; `standard`-tier review items | — |
| `opus` | Concurrency/idempotency logic, security-sensitive code, anything in a `high_risk` story, a package whose design leaves significant judgment to the implementer, or any item already in a 2nd+ review round | Purely mechanical packages — wastes tokens for no quality gain |

| Depth (`effort_selection`) | Tier | Means |
|------|------|-------|
| `low` | `fast_path` | code-reviewer's own spot-check substitutes for security_review; Trivy/Semgrep optional unless a dependency/Dockerfile changed |
| `standard` | `standard` | Full checklist; security-auditor always runs Trivy + Semgrep scoped to the diff |
| `high` | `high_risk` | Same as `standard`, plus: Critical/High Trivy hits block even outside the diff's surface, non-functionals get explicitly re-checked, and either agent may recommend escalating to `/code-review ultra` |

Both are per-dispatch decisions, not the story's overall tier applied blindly — a
`high_risk` story can still contain a trivial package that doesn't need `opus`/`high`
depth, and a `standard` story can contain one gnarly package that does. Every agent's own
frontmatter stays `model: inherit`; the override is applied only at dispatch time (an
explicit `model:` on the `Agent` call, plus a tier/depth line in the dispatch prompt), so
invoking an agent directly with no explicit model/tier just inherits the session's model
and reviews at `standard` depth.

Every other dispatch — `scrum-master`, a human, or the main session calling an agent
directly — starts from `model_selection.defaults_by_role`:

| Agent | Default | Step down / up |
|-------|---------|----------------|
| `product-owner`, `ux-designer` | `sonnet` | — |
| `scrum-master` | `sonnet` | `haiku` for standup / status roll-up |
| `requirements-analyst` | `sonnet` | `opus` if the item looks `high_risk` or the tier is unclear |
| `solution-architect` | `opus` | `sonnet` if the design mirrors an existing component 1:1 |
| `work-planner` | `sonnet` | `opus` for a `high_risk` story |
| `senior-developer`, `test-engineer` | per the model table above | — |
| `devops-engineer` | `sonnet` | `haiku` for a CI/config tweak; `opus` for production deploy, secrets or network exposure |
| `code-reviewer`, `security-auditor` | per tier, as `/review-pr` does | — |
| `team-lead` | `sonnet` | `haiku` for `fast_path`; `opus` for `high_risk` or a 2nd+ review round |

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
resolves the item's risk tier (tracker/PR body, default `standard`), dispatches
`code-reviewer` and `security-auditor` in parallel with the model and depth that tier maps
to (per the table above — `security-auditor` runs Trivy + Semgrep at `standard`/`high`
depth), posts their findings as a PR review, and auto-merges **only** if both come back
fully clean (code-reviewer `APPROVE`, security-auditor `PASS` — no qualifiers). Anything
less is left for a human. Each posted
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
