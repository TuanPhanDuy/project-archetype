#!/usr/bin/env bash
#
# Populate the archetype's resources from the real project, so the archetype and the
# reference project never drift. Run this before `mvn -f archetype/pom.xml install`.
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
DEST="$HERE/src/main/resources/archetype-resources"

rm -rf "$DEST"
mkdir -p "$DEST"
rsync -a \
  --exclude '.git/' \
  --exclude '/target/' \
  --exclude '/build/' \
  --exclude '/archetype/' \
  --exclude '/scripts/' \
  --exclude '.idea/' \
  --exclude '*.iml' \
  --exclude '.DS_Store' \
  "$ROOT"/ "$DEST"/
# NOTE: leading-slash excludes are anchored to $ROOT — important because an unanchored
# "archetype/" would also match the com/anbit/archetype PACKAGE directory and drop
# all the Java sources.

# The archetype plugin force-filters the root pom.xml through Velocity (it can't be turned
# off). The token ${org.mockito:mockito-core:jar} (in argLines AND comments) has colons that
# break Velocity's reference parser, so wrap every occurrence in a literal block #[[ ... ]]#
# — Velocity emits the contents verbatim into the generated pom.
perl -0pi -e 's{(\$\{org\.mockito:mockito-core:jar\})}{#[[$1]]#}g' "$DEST/pom.xml"

echo "Synced archetype-resources <- $ROOT"
