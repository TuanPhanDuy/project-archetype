#!/usr/bin/env bash
#
# Generate a new Spring Boot service from this archetype.
#
# It copies the archetype, then renames the base package, Maven coordinates, and the
# application name throughout (sources, tests, config, k6, observability, docs).
#
# Run it with no arguments to be prompted for the project name and groupId interactively,
# or pass everything as flags for non-interactive/CI use.
#
# Usage:
#   scripts/new-project.sh                                  # interactive prompts
#   scripts/new-project.sh --name <artifact> --group <groupId> [--package <base.pkg>] [options]
#
# Options:
#   -n, --name      Artifact id / app name (e.g. order-service)        [prompted if omitted]
#   -g, --group     Maven groupId         (e.g. com.acme)              [prompted if omitted]
#   -p, --package   Java base package     (e.g. com.acme.orderservice) [default: <group>.<name-without-dashes>]
#   -o, --output    Target directory                                   [default: ../<name>]
#       --force     Overwrite the target directory if it exists
#       --no-git    Do not run `git init` in the new project
#   -h, --help      Show this help
#
# Example:
#   scripts/new-project.sh -n order-service -g com.acme -p com.acme.orderservice -o ~/code/order-service
#
set -euo pipefail

# ---- locate the archetype root (the parent of this script's directory) ----
ARCHETYPE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OLD_PACKAGE="com.anbit.archetype"
OLD_PACKAGE_PATH="com/anbit/archetype"
OLD_GROUP="com.anbit"
OLD_NAME="service-archetype"

NAME="" GROUP="" PACKAGE="" OUTPUT="" FORCE=false DO_GIT=true

NAME_RE='^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'
PKG_RE='^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$'
INTERACTIVE=false; [[ -t 0 ]] && INTERACTIVE=true

die() { echo "error: $*" >&2; exit 1; }
usage() { awk 'NR>1{ if(/^#/){sub(/^# ?/,"");print} else exit }' "${BASH_SOURCE[0]}"; exit "${1:-0}"; }

# ask <varname> <prompt> <regex> <errmsg> [default] — loops until the input matches.
ask() {
  local __var="$1" prompt="$2" re="$3" err="$4" def="${5:-}" ans
  while true; do
    if [[ -n "$def" ]]; then read -r -p "$prompt [$def]: " ans; ans="${ans:-$def}"
    else read -r -p "$prompt: " ans; fi
    if [[ "$ans" =~ $re ]]; then printf -v "$__var" '%s' "$ans"; return 0; fi
    echo "  $err" >&2
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--name)    NAME="$2"; shift 2 ;;
    -g|--group)   GROUP="$2"; shift 2 ;;
    -p|--package) PACKAGE="$2"; shift 2 ;;
    -o|--output)  OUTPUT="$2"; shift 2 ;;
    --force)      FORCE=true; shift ;;
    --no-git)     DO_GIT=false; shift ;;
    -h|--help)    usage 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

# ---- gather inputs: prompt for anything not passed as a flag (when interactive) ----
if [[ -z "$NAME" ]]; then
  $INTERACTIVE && ask NAME "Project name (artifactId)" "$NAME_RE" "lowercase alphanumeric/dashes, e.g. order-service" \
    || die "--name is required (or run interactively)"
fi
[[ "$NAME" =~ $NAME_RE ]] || die "invalid name '$NAME' (lowercase alphanumeric/dashes, e.g. order-service)"

if [[ -z "$GROUP" ]]; then
  $INTERACTIVE && ask GROUP "Maven groupId" "$PKG_RE" "valid package id, e.g. com.acme" \
    || die "--group is required (or run interactively)"
fi
[[ "$GROUP" =~ $PKG_RE ]] || die "invalid groupId '$GROUP' (e.g. com.acme)"

# default package: <group>.<name with dashes/underscores removed>
DEFAULT_PACKAGE="${GROUP}.${NAME//[-_]/}"
if [[ -z "$PACKAGE" ]]; then
  if $INTERACTIVE; then ask PACKAGE "Base package" "$PKG_RE" "valid package id, e.g. com.acme.orderservice" "$DEFAULT_PACKAGE"
  else PACKAGE="$DEFAULT_PACKAGE"; fi
fi
[[ "$PACKAGE" =~ $PKG_RE ]] || die "invalid package '$PACKAGE' (e.g. com.acme.orderservice)"

DEFAULT_OUTPUT="$(dirname "$ARCHETYPE_ROOT")/$NAME"
if [[ -z "$OUTPUT" ]]; then
  if $INTERACTIVE; then read -r -p "Output directory [$DEFAULT_OUTPUT]: " OUTPUT; OUTPUT="${OUTPUT:-$DEFAULT_OUTPUT}"
  else OUTPUT="$DEFAULT_OUTPUT"; fi
fi
PACKAGE_PATH="${PACKAGE//.//}"

echo
echo "Generating project:"
echo "  name (artifactId): $NAME"
echo "  groupId:           $GROUP"
echo "  base package:      $PACKAGE"
echo "  output:            $OUTPUT"
echo

if $INTERACTIVE; then
  read -r -p "Proceed? [Y/n]: " _yn
  [[ "${_yn:-Y}" =~ ^[Nn] ]] && { echo "aborted."; exit 1; }
fi

if [[ -e "$OUTPUT" ]]; then
  $FORCE || die "target '$OUTPUT' already exists (use --force to overwrite)"
  rm -rf "$OUTPUT"
fi

# ---- copy the archetype, excluding build output, VCS, IDE files, and this generator ----
mkdir -p "$OUTPUT"
rsync -a \
  --exclude '.git/' \
  --exclude 'target/' \
  --exclude 'build/' \
  --exclude 'scripts/' \
  --exclude '.idea/' \
  --exclude '*.iml' \
  --exclude '.DS_Store' \
  "$ARCHETYPE_ROOT"/ "$OUTPUT"/

# ---- move Java sources/tests from the old package path to the new one ----
for root in src/main/java src/test/java; do
  old="$OUTPUT/$root/$OLD_PACKAGE_PATH"
  new="$OUTPUT/$root/$PACKAGE_PATH"
  if [[ -d "$old" ]]; then
    mkdir -p "$(dirname "$new")"
    mv "$old" "$new"
  fi
done
# prune now-empty old package directories
find "$OUTPUT/src/main/java" "$OUTPUT/src/test/java" -type d -empty -delete 2>/dev/null || true

# ---- rewrite identifiers in every text file (order matters: longest/most-specific first) ----
replace_in_tree() {
  local search="$1" replace="$2"
  # only touch text files; skip anything binary
  while IFS= read -r -d '' f; do
    if grep -Iq . "$f"; then
      perl -i -pe "s{\Q${search}\E}{${replace}}g" "$f"
    fi
  done < <(find "$OUTPUT" -type f -print0)
}

replace_in_tree "$OLD_PACKAGE"      "$PACKAGE"        # dotted package refs
replace_in_tree "$OLD_PACKAGE_PATH" "$PACKAGE_PATH"   # slash package paths (docs, comments)
replace_in_tree "$OLD_GROUP"        "$GROUP"          # Maven groupId
replace_in_tree "$OLD_NAME"         "$NAME"           # artifactId + spring.application.name

# ---- optional: fresh git repo ----
if $DO_GIT && command -v git >/dev/null 2>&1; then
  git -C "$OUTPUT" init -q
  git -C "$OUTPUT" add -A
fi

echo "Done."
echo
echo "Next steps:"
echo "  cd \"$OUTPUT\""
echo "  ./mvnw test            # unit tests (no Docker)"
echo "  ./mvnw verify          # + Testcontainers integration tests (needs Docker)"
echo "  # then trim the sample features you don't need (product/category/order/job)"
