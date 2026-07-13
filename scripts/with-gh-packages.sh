#!/usr/bin/env bash
# Export GitHub Packages credentials from `gh` for this shell command, then exec.
# Usage: ./scripts/with-gh-packages.sh ./gradlew :tool:assembleDebug
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required (https://cli.github.com/)" >&2
  exit 1
fi

export GH_PACKAGES_USER="${GH_PACKAGES_USER:-$(gh api user --jq .login)}"
export GH_PACKAGES_TOKEN="${GH_PACKAGES_TOKEN:-$(gh auth token)}"
# Also set the names used by root build.gradle.kts publishing block
export GITHUB_ACTOR="${GITHUB_ACTOR:-$GH_PACKAGES_USER}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-$GH_PACKAGES_TOKEN}"

if [[ $# -eq 0 ]]; then
  echo "Exported GH_PACKAGES_USER / GH_PACKAGES_TOKEN / GITHUB_ACTOR / GITHUB_TOKEN for user: $GH_PACKAGES_USER" >&2
  echo "Usage: $0 <command...>" >&2
  exit 0
fi

exec "$@"
