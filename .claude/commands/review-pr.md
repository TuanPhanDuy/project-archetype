---
description: Review a GitHub PR with code-reviewer + security-auditor and merge only if both come back fully clean
argument-hint: <PR number> [--merge-strategy squash|merge|rebase] [--no-merge]
---

Run the **Code Review + Security Review** phases from `sdlc.yaml` against a real GitHub PR,
then gate the merge on the verdicts. Uses the `gh` CLI (already authenticated in this
environment) — the GitHub MCP server in `.mcp.json` is available too, but `gh` is simpler
for this mechanical flow.

**Arguments:** `$ARGUMENTS` — first token is the PR number (required). Optional flags:
`--merge-strategy squash|merge|rebase` (default `squash`), `--no-merge` (review + comment
only, never merge even if clean — use this to just get a second opinion).

## 1. Resolve the PR and isolate it

Never touch the user's current working tree or branch for this — always review in a
separate git worktree so an in-progress checkout/uncommitted work is never disturbed.

```bash
N=<pr-number>
gh pr view "$N" --json title,body,baseRefName,headRefName,url,mergeable,mergeStateStatus,isCrossRepository
```

- If `mergeable` is `CONFLICTING`, tell the user now and stop — don't review a PR that can't
  merge cleanly; they need to resolve conflicts first.
- Fetch the PR head without switching branches, then add a worktree for it:
  ```bash
  BASE=$(gh pr view "$N" --json baseRefName -q .baseRefName)
  git fetch origin "pull/$N/head:pr-$N"
  WT="$(mktemp -d)/pr-$N"
  git worktree add "$WT" "pr-$N"
  ```

## 2. Review in parallel

Spawn both agents concurrently (single message, two Agent tool calls) — each is read-only
and independent:

- **code-reviewer**: "Review the PR changes in `$WT` (diff against `origin/$BASE`). Follow
  your standard checklist. End with your verdict line."
- **security-auditor**: same worktree/base, same instruction, its own checklist and verdict
  line.

Wait for both to finish before proceeding — the merge gate needs both verdicts together.

## 3. Post the review to the PR

Combine both findings lists into one comment (clearly split into a Code Review section and
a Security Review section, each with its verdict) and post it:

```bash
gh pr comment "$N" --body-file <(echo "$COMBINED_FINDINGS")
```

Use `gh pr comment`, not `gh pr review`, here — GitHub rejects a formal review (`--approve`/
`--request-changes`, and on some setups even `--comment`) from the PR's own author with
`"Can not request changes on your own pull request"`. Since this command usually runs as the
same account that opened the PR, a plain issue comment is the reliable path; only reach for
`gh pr review` if you've confirmed the acting account differs from the PR author.

## 4. Gate the merge

Auto-merge **only** if code-reviewer's verdict is exactly `APPROVE` (not "APPROVE WITH
NITS") **and** security-auditor's verdict is exactly `PASS` (not "PASS WITH
RECOMMENDATIONS") **and** `--no-merge` was not passed:

```bash
gh pr merge "$N" --squash --delete-branch   # or --merge / --rebase per --merge-strategy
```

Otherwise, do **not** merge — the comment from step 3 already carries the findings and both
verdicts, so there's nothing further to post. Just tell the user this PR needs a human
decision, quoting the exact verdicts and why they fell short of the clean-merge bar.

## 5. Clean up

Always remove the worktree and the temporary branch, whether or not you merged:

```bash
git worktree remove "$WT" --force
git branch -D "pr-$N"
```

## 6. Report

State: the PR, both verdicts, whether you merged (and how) or left it for human review, and
a link to the posted comment/review.
