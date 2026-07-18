#!/usr/bin/env bash
#
# One-time setup: point this clone's git hooks at the versioned .githooks/ directory,
# so the commit-msg (Conventional Commits) hook runs automatically. See CONTRIBUTING.md.
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

git -C "$HERE" config core.hooksPath .githooks
chmod +x "$HERE"/.githooks/*

echo "core.hooksPath set to .githooks — commit-msg now enforces Conventional Commits."
