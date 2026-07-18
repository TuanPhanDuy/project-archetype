#!/usr/bin/env bash
#
# Minimal Jira REST API v3 client backing .claude/skills/jira/SKILL.md.
# All writes go through jq so free-text summaries/descriptions (quotes,
# backticks, newlines) can never break out of the JSON payload.
#
# Requires: JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, JIRA_PROJECT_KEY
# Optional: JIRA_EPIC_LINK_FIELD (classic/company-managed projects only),
#           JIRA_ISSUE_TYPE_{EPIC,STORY,SUBTASK,BUG} (override if your site
#           renames the default issue types).
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

: "${JIRA_BASE_URL:?Set JIRA_BASE_URL (e.g. https://yourteam.atlassian.net), see .env.example}"
: "${JIRA_EMAIL:?Set JIRA_EMAIL, see .env.example}"
: "${JIRA_API_TOKEN:?Set JIRA_API_TOKEN, see .env.example}"
: "${JIRA_PROJECT_KEY:?Set JIRA_PROJECT_KEY, see .env.example}"
: "${JIRA_ISSUE_TYPE_EPIC:=Epic}"
: "${JIRA_ISSUE_TYPE_STORY:=Story}"
: "${JIRA_ISSUE_TYPE_SUBTASK:=Subtask}"
: "${JIRA_ISSUE_TYPE_BUG:=Bug}"

API="${JIRA_BASE_URL%/}/rest/api/3"
die() { echo "error: $*" >&2; exit 1; }

_curl() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -X "$method" "$API$path" -H "Content-Type: application/json" -u "${JIRA_EMAIL}:${JIRA_API_TOKEN}")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

_check_error() {
  local resp="$1"
  if echo "$resp" | jq -e '((.errorMessages // []) | length > 0) or ((.errors // {}) | length > 0)' >/dev/null 2>&1; then
    echo "Jira API error:" >&2
    echo "$resp" | jq . >&2
    exit 1
  fi
}

_adf() {
  # Wrap plain text as Atlassian Document Format (required for description/comment bodies).
  jq -n --arg text "$1" '{ type: "doc", version: 1, content: [{ type: "paragraph", content: [{ type: "text", text: $text }] }] }'
}

create_issue() {
  # create_issue <issuetype-name> <summary> [description] [parent-key]
  local issuetype="$1" summary="$2" description="${3:-}" parent="${4:-}"
  local fields resp key

  fields=$(jq -n \
    --arg project "$JIRA_PROJECT_KEY" \
    --arg issuetype "$issuetype" \
    --arg summary "$summary" \
    '{ project: { key: $project }, issuetype: { name: $issuetype }, summary: $summary }')

  if [[ -n "$description" ]]; then
    fields=$(jq --argjson d "$(_adf "$description")" '. + { description: $d }' <<<"$fields")
  fi

  if [[ -n "$parent" ]]; then
    if [[ "$issuetype" == "$JIRA_ISSUE_TYPE_SUBTASK" ]]; then
      fields=$(jq --arg p "$parent" '. + { parent: { key: $p } }' <<<"$fields")
    elif [[ -n "${JIRA_EPIC_LINK_FIELD:-}" ]]; then
      fields=$(jq --arg f "$JIRA_EPIC_LINK_FIELD" --arg p "$parent" '. + { ($f): $p }' <<<"$fields")
    else
      # Team-managed project convention: Story -> Epic also uses `parent`.
      fields=$(jq --arg p "$parent" '. + { parent: { key: $p } }' <<<"$fields")
    fi
  fi

  resp=$(_curl POST "/issue" "$(jq -n --argjson fields "$fields" '{ fields: $fields }')")
  _check_error "$resp"
  key=$(jq -r '.key // empty' <<<"$resp")
  [[ -z "$key" ]] && die "no key returned: $resp"
  echo "$key"
}

case "${1:-}" in
  create-epic)    shift; create_issue "$JIRA_ISSUE_TYPE_EPIC"    "${1:?summary required}" "${2:-}" "${3:-}" ;;
  create-story)   shift; create_issue "$JIRA_ISSUE_TYPE_STORY"   "${1:?summary required}" "${2:-}" "${3:-}" ;;
  create-subtask) shift; create_issue "$JIRA_ISSUE_TYPE_SUBTASK" "${1:?summary required}" "${2:-}" "${3:-}" ;;
  create-bug)     shift; create_issue "$JIRA_ISSUE_TYPE_BUG"     "${1:?summary required}" "${2:-}" "${3:-}" ;;

  get)
    shift; key="${1:?issue key required}"
    resp=$(_curl GET "/issue/$key"); _check_error "$resp"
    jq '{key, summary: .fields.summary, status: .fields.status.name, issuetype: .fields.issuetype.name}' <<<"$resp"
    ;;

  search)
    shift; jql="${1:?JQL required}"
    body=$(jq -n --arg jql "$jql" '{ jql: $jql, maxResults: 50, fields: ["summary","status","issuetype"] }')
    # Jira Cloud's newer search endpoint; if your site is on an older API version,
    # switch this path to /search.
    resp=$(_curl POST "/search/jql" "$body"); _check_error "$resp"
    jq '[.issues[] | {key, summary: .fields.summary, status: .fields.status.name, issuetype: .fields.issuetype.name}]' <<<"$resp"
    ;;

  transition)
    shift; key="${1:?issue key required}" target="${2:?target status name required}"
    resp=$(_curl GET "/issue/$key/transitions"); _check_error "$resp"
    id=$(jq -r --arg t "$target" '.transitions[] | select(.name==$t) | .id' <<<"$resp" | head -1)
    if [[ -z "$id" ]]; then
      die "no transition named '$target' from the current status. Available: $(jq -r '[.transitions[].name] | join(", ")' <<<"$resp")"
    fi
    _curl POST "/issue/$key/transitions" "$(jq -n --arg id "$id" '{ transition: { id: $id } }')" >/dev/null
    echo "$key -> $target"
    ;;

  comment)
    shift; key="${1:?issue key required}" text="${2:?comment text required}"
    resp=$(_curl POST "/issue/$key/comment" "$(jq -n --argjson body "$(_adf "$text")" '{ body: $body }')")
    _check_error "$resp"
    echo "commented on $key"
    ;;

  *)
    cat >&2 <<'EOF'
Usage: jira.sh <command> [args]
  create-epic    <summary> [description]
  create-story   <summary> [description] [epic-key]
  create-subtask <summary> [description] [parent-story-key]
  create-bug     <summary> [description] [parent-key]
  get            <key>
  search         <jql>
  transition     <key> <target-status-name>
  comment        <key> <text>
EOF
    exit 1
    ;;
esac
