#!/usr/bin/env bash
# 🚀 Commit 146: ساخت و push تگ annotated برای انتشار.
# Usage: ./scripts/tag_release.sh 1.4.0
set -euo pipefail

VERSION="${1:?Usage: ./scripts/tag_release.sh <version>  (e.g. 1.4.0)}"

if git rev-parse "v${VERSION}" >/dev/null 2>&1; then
  echo "Tag v${VERSION} already exists — aborting." >&2
  exit 1
fi

git tag -a "v${VERSION}" -m "PumpWatch v${VERSION}"
git push origin "v${VERSION}"
echo "✅ Tagged and pushed v${VERSION}"
