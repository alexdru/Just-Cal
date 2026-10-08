#!/usr/bin/env bash
set -euo pipefail
validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-ref.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
git init -q "$fixture"
cd "$fixture"
git config user.name 'Ref validation test'
git config user.email test@example.invalid
git commit --allow-empty -qm base
base="$(git rev-parse HEAD)"
git commit --allow-empty -qm master
tip="$(git rev-parse HEAD)"
git update-ref refs/remotes/origin/master "$tip"
export GITHUB_REF=refs/heads/master GITHUB_SHA="$tip"
[[ "$(bash "$validator")" == "$tip" ]]
[[ "$(bash "$validator" master)" == "$tip" ]]
[[ "$(bash "$validator" refs/heads/master)" == "$tip" ]]
[[ "$(bash "$validator" "$base")" == "$base" ]]
reject() {
    if bash "$validator" "$1" > "$fixture/result.log" 2>&1; then
        printf 'Expected ref rejection\n' >&2; exit 1
    fi
}
reject feature/unsafe
reject refs/pull/1/head
reject HEAD~1
reject --upload-pack=bad
reject 'master;touch injected'
reject '$(touch injected)'
reject $'master\nINJECTED=true'
reject "${tip:0:12}"
reject 0000000000000000000000000000000000000000
git checkout -q --detach "$base"
git commit --allow-empty -qm unrelated
foreign="$(git rev-parse HEAD)"
reject "$foreign"
GITHUB_REF=refs/heads/feature reject master
GITHUB_SHA="$foreign" reject master
# A force-moved master must invalidate a formerly accepted candidate.
git update-ref refs/remotes/origin/master "$base"
GITHUB_SHA="$base" reject "$tip"
[[ ! -e injected ]]
printf 'Release ref regression tests passed.\n'
