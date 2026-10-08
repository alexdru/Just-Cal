#!/usr/bin/env bash
set -euo pipefail
validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-environment.sh"
export GH_TOKEN=fixture-token GH_REPO=fixture/repository
export MOCK_REVIEWERS=true MOCK_CUSTOM=true MOCK_POLICIES=$'master\tbranch' MOCK_API_ERROR=false
gh() {
    [[ "$MOCK_API_ERROR" != true ]] || return 1
    case "$*" in
        *required_reviewers*) printf '%s' "$MOCK_REVIEWERS" ;;
        *custom_branch_policies*) printf '%s' "$MOCK_CUSTOM" ;;
        *deployment-branch-policies*) printf '%s' "$MOCK_POLICIES" ;;
        *) return 1 ;;
    esac
}
export -f gh
reject() {
    if bash "$validator"; then printf 'Expected Environment rejection\n' >&2; exit 1; fi
}
bash "$validator"
MOCK_REVIEWERS=false reject
MOCK_CUSTOM=false reject
MOCK_API_ERROR=true reject
MOCK_POLICIES=$'*\tbranch' reject
MOCK_POLICIES=$'master\tbranch\nv*\ttag' reject
MOCK_POLICIES='' reject
printf 'Release Environment regression tests passed.\n'
