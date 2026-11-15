---
name: request-service-change
description: >
  Asks the service that owns something to change it, instead of patching another
  service's code from here. Files a structured request in the owner's repo (the durable
  record its `service-inbox` evaluates) and nudges its live session if one is running.
  Use when work in {{PROJECT_PURPOSE}} needs a change, fix or new contract in another
  service, when this service changes a contract other services consume, or when asked to
  "tell/ask service X to …".
---

# Request a service change

Core rule: **never edit another service's code from this session.** Its owner session
knows its context and runs its own pipeline; this session only asks, clearly enough that
the owner can decide without a follow-up question.

## 1. Find the owner

Look up `sdlc.yaml` › `cross_service.registry`: the service whose `owns` covers the API,
event or schema involved. For a contract change made here, the targets are every service
whose `consumes` lists this one. Unknown owner → ask the user, don't guess a repo.

## 2. File the request

One issue per target service and per independent change:

```bash
gh label create service-request -R "$REPO" --color 5319E7 --force >/dev/null
gh issue create -R "$REPO" --label service-request --title "<what, in one line>" --body-file - <<'EOF'
### What
<the change, from the owner's point of view>

### Why
<what breaks or is blocked without it>
Origin: <this repo>#<issue/PR> (or session name)

### Acceptance criteria
- Given … When … Then …

### Contract
<endpoint/event/schema before → after; "none" if internal>

Urgency: blocking | soon | whenever
Suggested tier: fast_path | standard | high_risk

<!-- service-request:v1 from=<this service name> -->
EOF
```

Say what is needed and why; leave how to the owner. Put no secrets or customer data in the
issue.

## 3. Nudge and track

- If `ListAgents` shows a live session for that service, `SendMessage` it one line: the
  issue link and the urgency. The issue stays the source of truth.
- Link the new issue from this side's work item. If this side is blocked on it, say so in
  the PR/issue and continue with whatever doesn't depend on it.
- Report each request filed: service · issue link · urgency.
