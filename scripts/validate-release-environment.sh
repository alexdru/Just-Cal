#!/usr/bin/env bash
set -euo pipefail
fail() { printf 'Release Environment validation failed: %s\n' "$*" >&2; exit 1; }
: "${GH_TOKEN:?Set GH_TOKEN}"
: "${GH_REPO:?Set GH_REPO}"
reviewers="$(gh api "repos/$GH_REPO/environments/release" --jq \
    'any(.protection_rules[]; .type == "required_reviewers" and (.reviewers | length) > 0)')" ||
    fail "Cannot inspect Environment release. Create/configure it before releasing."
[[ "$reviewers" == true ]] || fail "Environment release needs at least one required reviewer."
custom="$(gh api "repos/$GH_REPO/environments/release" --jq '.deployment_branch_policy.custom_branch_policies')"
[[ "$custom" == true ]] || fail "Use selected deployment branches with a master-only rule."
policies="$(gh api "repos/$GH_REPO/environments/release/deployment-branch-policies" --paginate \
    --jq '.branch_policies[] | [.name, .type] | @tsv')"
[[ "$policies" == $'master\tbranch' ]] ||
    fail "Allow exactly the master branch and no tags/wildcards in Environment release."
printf 'Verified release Environment reviewers and master-only deployment policy.\n'
