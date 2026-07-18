# MCP servers

[`.mcp.json`](../.mcp.json) registers 44 MCP servers, project-scoped (shared via git, no
secrets committed — every credential is `${ENV_VAR}` substitution). Registering a server
here does **not** mean it's usable yet: most need a credential in your shell environment
(see `.env.example`), and a few need local software running. This doc says, per server,
what state it's actually in.

**Read this before relying on any "Needs verification" entry** — for services without one
dominant, obviously-official MCP server, the entry below is a best-effort placeholder using
the most-referenced package name at the time this was written. Confirm the command/package
still exists and does what you expect before pointing it at anything real.

Legend: 🟢 ready now (no setup beyond what's in `.mcp.json`) · 🟡 needs a credential/config ·
🔴 needs local software running (Docker, Ollama, Obsidian, Figma) · ⚠️ needs verification
(no single canonical package — confirm before use).

## Core

| Server | Status | Notes |
|---|---|---|
| `github` | 🟡 | Official remote server. Needs `GITHUB_PERSONAL_ACCESS_TOKEN` (`repo` scope). Reuse `gh auth token`. Also used by [`/review-pr`](../.claude/commands/review-pr.md). |
| `context7` | 🟢/🟡 | Official remote server (up-to-date library docs). Works unauthenticated at low rate limits; set `CONTEXT7_API_KEY` (free at context7.com) to raise them. |
| `filesystem` | 🟢 | Official reference server, scoped to `.` (repo root) by its arg. No credentials. |
| `playwright` | 🟢 | Official Microsoft browser-automation server. First run may need `npx playwright install` for browser binaries. |

## Git

| Server | Status | Notes |
|---|---|---|
| `git` | 🟢 | Official reference server, local repo ops. Needs `uv`/`uvx` installed (`brew install uv`). |
| `gitlab` | 🟡⚠️ | Community package (`@zereight/mcp-gitlab`). Needs `GITLAB_PERSONAL_ACCESS_TOKEN` + `GITLAB_API_URL`. Verify it's still maintained before relying on it. |

## DevOps

| Server | Status | Notes |
|---|---|---|
| `docker` | 🔴 | Docker Desktop's MCP Toolkit (Settings → Beta features → MCP Toolkit) must be enabled and Docker running. |
| `kubernetes` | 🟡⚠️ | Community package (`mcp-server-kubernetes`). Needs `KUBECONFIG`. Verify before use. |
| `terraform` | 🔴 | Official HashiCorp image, pulled via Docker on first run. Just needs Docker running. |
| `aws` | 🟡 | Official AWS Labs server (`awslabs.aws-api-mcp-server`, needs `uv`). Uses the standard AWS credential chain — set `AWS_PROFILE` or the usual `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, plus `AWS_REGION`. |
| `gcp` | 🟡⚠️ | **No single canonical "GCP MCP" exists.** This points at a Cloud Run-focused package as the closest general fit — Google otherwise ships product-specific servers (BigQuery/AlloyDB/Spanner via the "MCP Toolbox for Databases"). Replace with the specific one you need. |
| `azure` | 🟡 | Official Microsoft server (`@azure/mcp`). Needs `az login` done locally, or service-principal env vars per its docs. |
| `cloudflare` | 🟡 | Points at Cloudflare's docs server (no auth) as a safe default. Cloudflare ships many product-specific remote servers (Workers bindings, DNS analytics, Radar, etc.) — swap the URL for the one you need; each does its own OAuth on first connect. |
| `vercel` | 🟡 | Official remote server. Needs `VERCEL_API_TOKEN` (vercel.com/account/tokens). |

## Database

| Server | Status | Notes |
|---|---|---|
| `postgres` | 🟡 | Official reference server. Needs `POSTGRES_CONNECTION_STRING` (this archetype's own DB — see `compose.yaml`). |
| `mysql` | 🟡⚠️ | Community package (`@benborla29/mcp-server-mysql`). Needs `MYSQL_HOST`/`PORT`/`USER`/`PASS`/`DB`. |
| `mongodb` | 🟡 | Official MongoDB Inc. server. Needs `MDB_MCP_CONNECTION_STRING`. |
| `redis` | 🟡 | Official Redis Inc. server (`redis-mcp-server`, via `uv`). Needs `REDIS_HOST`/`PORT`/`PASSWORD`. |

## Search & Research

| Server | Status | Notes |
|---|---|---|
| `firecrawl` | 🟡 | Needs `FIRECRAWL_API_KEY` (firecrawl.dev). |
| `tavily` | 🟡 | Needs `TAVILY_API_KEY` (tavily.com). |
| `exa` | 🟡 | Needs `EXA_API_KEY` (exa.ai/api). |
| `perplexity` | 🟡 | Needs `PERPLEXITY_API_KEY` — the Sonar API key, not a consumer Perplexity account. |

## Communication

| Server | Status | Notes |
|---|---|---|
| `slack` | 🟡 | Needs `SLACK_BOT_TOKEN` + `SLACK_TEAM_ID` — create a Slack app with `chat:write`/`channels:read` etc. first. |
| `discord` | 🟡⚠️ | Community package (`mcp-discord`). Needs `DISCORD_TOKEN` (a bot token from the Discord developer portal). |
| `gmail` | 🟡⚠️ | Community package, OAuth flow on first run (opens a browser). Requires a Google Cloud OAuth client set up per the package's README before that flow works. |
| `google-calendar` | 🟡⚠️ | Same OAuth pattern as `gmail`. Community package. |

## Project Management

| Server | Status | Notes |
|---|---|---|
| `atlassian` | 🟡 | Official remote server — **covers both Jira and Confluence** from your list in one entry. OAuth on first connect, no token in config. Already the recommended path in [`.claude/skills/jira/SKILL.md`](../.claude/skills/jira/SKILL.md). |
| `linear` | 🟡 | Official remote server. OAuth on first connect. |
| `notion` | 🟡 | Official remote server. OAuth on first connect. |

## Data Platform

| Server | Status | Notes |
|---|---|---|
| `kafka` | 🟡⚠️ | **Placeholder only** — no single dominant official Kafka MCP as of this writing (Confluent, Aiven, and others each publish their own). Replace with whichever your org standardizes on. Needs `KAFKA_BROKERS`. |
| `opensearch` | 🟡 | Official OpenSearch project server (`opensearch-mcp-server-py`, via `uv`). Needs `OPENSEARCH_URL`/`USERNAME`/`PASSWORD`. |
| `elasticsearch` | 🟡 | Official Elastic server. Needs `ES_URL` + `ES_API_KEY`. |
| `snowflake` | 🟡⚠️ | Snowflake Labs server (`mcp-server-snowflake`, via `uv`). Needs `SNOWFLAKE_ACCOUNT`/`USER`/`PASSWORD` (check its docs for key-pair auth as an alternative). |
| `bigquery` | 🟡⚠️ | **Placeholder name.** Google's more official route is the "MCP Toolbox for Databases" (`genai-toolbox`) with a `tools.yaml` config, which doesn't fit a single-line `.mcp.json` entry cleanly — treat this as a starting point to replace. |
| `databricks` | 🟡⚠️ | **Placeholder name** — verify against Databricks' current MCP offering before relying on it. |

## AI

| Server | Status | Notes |
|---|---|---|
| `openai` | 🟡⚠️ | Community wrapper exposing OpenAI's API as MCP tools (useful mainly for cross-model comparison from within Claude Code). Needs `OPENAI_API_KEY`. |
| `huggingface` | 🟡 | Official Hugging Face remote server. Needs `HF_TOKEN` for anything beyond public read access. |
| `ollama` | 🔴 | Community package, talks to a local Ollama daemon. Needs Ollama installed and running; set `OLLAMA_HOST` if it's not on the default `localhost:11434`. |
| *"Anthropic MCP"* | — | **Intentionally not added.** There's no distinct "Anthropic MCP" product — Claude Code already talks to the Anthropic API directly. If you meant something specific (Claude Agent SDK, Console org/usage management), say so and I'll wire up the real thing instead of a placeholder that wouldn't do anything. |

## Monitoring

| Server | Status | Notes |
|---|---|---|
| `sentry` | 🟡 | Official Sentry remote server. OAuth on first connect. |
| `grafana` | 🟡 | Official Grafana Labs server, run via Docker (`mcp/grafana` image). Needs `GRAFANA_URL` + a Grafana service-account `GRAFANA_API_KEY`. |
| `prometheus` | 🟡⚠️ | **Placeholder** — Prometheus itself has no official MCP as of this writing; several community ones exist. Verify before use. Needs `PROMETHEUS_URL`. |

## Productivity

| Server | Status | Notes |
|---|---|---|
| `obsidian` | 🟡🔴 | Needs the "Local REST API" community plugin installed and enabled inside Obsidian, plus `OBSIDIAN_API_KEY` (the plugin's key) and `OBSIDIAN_HOST`. |
| `figma` | 🔴 | Figma's own Dev Mode MCP Server — needs the Figma desktop app open with it enabled (Figma menu → Preferences). Serves locally on `127.0.0.1:3845`; no token, but only works while the app is running. |
| *"Chrome MCP"* | 🟢 | Mapped to `chrome-devtools` — Google's official Chrome DevTools MCP (`chrome-devtools-mcp`). Launches/controls a real Chrome instance. No credentials. |

## Setup

1. Copy `.env.example` to `.env` and fill in whichever servers you actually plan to use —
   leave the rest blank; an unset `${VAR}` just makes that server fail to start, which is
   harmless (Claude Code just won't offer its tools).
2. **Export the vars in your actual shell**, not just `.env` — Claude Code substitutes
   `${VAR}` from the process environment at startup, it doesn't source `.env` itself:
   ```bash
   set -a; source .env; set +a
   ```
3. `npx`/`uvx`-based servers install themselves on first use (need Node.js / `uv`
   respectively on your `PATH`). Docker-based ones need Docker running.
4. Remote (`http`/`sse`) servers with no `Authorization` header do their own OAuth the first
   time Claude Code connects — a browser tab will open.
