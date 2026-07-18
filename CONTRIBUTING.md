# Contributing

This repo runs a lightweight, gated git workflow. The process is enforced by tooling (a
commit-msg hook, CI, branch protection), not just written down here — read this once, then
let the checks keep you honest.

## Branching

Trunk-based, short-lived branches: `feature/<slug>`, `fix/<slug>`, `chore/<slug>` (add a
Jira key if one exists — `feature/PROJ-123-idempotent-orders`). Branch from `main`, keep it
small, open a PR as soon as there's something to review — don't let branches live for days.

## Commits — Conventional Commits, enforced

Every commit message must match:

```
<type>(<optional scope>)!?: <description>
```

`type` is one of `feat fix docs style refactor perf test build ci chore revert`. The `!`
(e.g. `feat!:`) marks a breaking change. Examples:

```
feat(orders): add Idempotency-Key support to POST /api/v1/orders
fix: close the merge-vs-persist race on IdempotencyKey
docs: document the git workflow
```

This is enforced by a local `commit-msg` hook (see **Setup** below) and by a CI check on the
PR title (squash-merge makes the PR title the final commit message, so it's checked the same
way). Reference a Jira key in the body when one exists, but the `type: description` line is
what's actually validated.

## Setup (once per clone)

```bash
./scripts/setup-git-hooks.sh
```

This points `core.hooksPath` at the versioned `.githooks/` directory, so the commit-msg hook
runs automatically. It's a no-op to skip, but a malformed commit message will otherwise only
get caught later by CI on the PR — better to catch it before it exists.

## Before opening a PR

```bash
./mvnw verify   # full build: unit + Testcontainers integration tests
```

Don't open a PR on a red local build — CI runs the same thing and will just tell you what
you already knew.

## Pull requests

- Use [`.github/pull_request_template.md`](.github/pull_request_template.md) (auto-applied).
- CI (`Build & test`) and SonarCloud must both pass — this is enforced by branch protection,
  not just convention.
- Once open, run [`/review-pr <number>`](.claude/commands/review-pr.md) to get `code-reviewer`
  + `security-auditor` findings posted to the PR, with auto-merge only if both come back
  fully clean. Anything less is a human call — see the findings and decide.
- Merge strategy is **squash only** (repo setting) — keeps `main`'s history one commit per
  PR, and is why the PR title itself must be a valid Conventional Commit.
- The source branch is deleted automatically on merge.

## Definition of done

See `definition_of_done` in [`sdlc.yaml`](sdlc.yaml) — in short: acceptance criteria met and
tested, `./mvnw verify` green, no blocking review/security findings, docs updated for any
contract change.

## The SDLC agents

This repo's `.claude/agents/` implements the above as a team of Claude Code subagents (one
per phase, plus Scrum roles). See [`.claude/agents/README.md`](.claude/agents/README.md) and
[`sdlc.yaml`](sdlc.yaml) for the full pipeline.
