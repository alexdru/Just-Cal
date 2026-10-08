#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'Release ref validation failed: %s\n' "$*" >&2; exit 1; }
requested="${1:-}"
: "${GITHUB_REF:?Set GITHUB_REF}"
: "${GITHUB_SHA:?Set GITHUB_SHA}"
[[ "$GITHUB_REF" == refs/heads/master ]] || fail "Dispatch the workflow from master only."
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid workflow commit SHA."
# Only a literal master or a full commit ID; never Git expressions, PR refs or options.
case "$requested" in
    "") candidate="$GITHUB_SHA" ;;
    master|refs/heads/master) candidate="$(git rev-parse --verify "refs/remotes/origin/master^{commit}")" ;;
    *) [[ "$requested" =~ ^[0-9a-f]{40}$ ]] ||
           fail "ref must be blank, master, or a full lowercase 40-character commit SHA."
       candidate="$requested" ;;
esac
[[ "$(git cat-file -t "$candidate")" == commit ]] || fail "ref is not an available commit."
git merge-base --is-ancestor "$GITHUB_SHA" refs/remotes/origin/master ||
    fail "The workflow revision is no longer in origin/master."
git merge-base --is-ancestor "$candidate" refs/remotes/origin/master ||
    fail "The requested commit is not reachable from origin/master."
printf '%s\n' "$candidate"
