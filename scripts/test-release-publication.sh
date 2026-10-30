#!/usr/bin/env bash
set -euo pipefail

# Use a local Git remote and a mocked gh function: no GitHub writes, no app tags.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
publisher="$script_dir/publish-release.sh"
validator="$script_dir/validate-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
git init -q --bare "$fixture/remote.git"
git init -q "$fixture/source"
cd "$fixture/source"
git config user.name "Release publication test"
git config user.email "release-test@example.invalid"
git remote add origin "$fixture/remote.git"
mkdir -p scripts release-files docs
cp "$validator" scripts/validate-release.sh
printf 'versionName=0.4.0\nversionCode=5\n' > version.properties
git add scripts version.properties
git commit -qm fixture
{
    printf '# v0.4.0\n\n'
    for change in One Two Three; do
        printf -- '- %s user-facing change.\n  Evidence: THIS_COMMIT\n' "$change"
    done
} > docs/RELEASE_HIGHLIGHTS.md
git add docs && git commit -qm highlights
git push -q origin HEAD:refs/heads/master
export MOCK_REMOTE="$fixture/remote.git"
export MOCK_LOG="$fixture/calls.log"
export MOCK_DRAFT="" MOCK_RACE="" MOCK_DOWNLOAD_MISMATCH=false
export MOCK_COUNTER="$fixture/counter" MOCK_MISSING="$fixture/missing"
export MOCK_NOTES="$fixture/notes"
export MOCK_CREATE_FAILURE="" MOCK_STATE_ERROR=false MOCK_UNEXPECTED=false
export MOCK_FAULT_LOG="$fixture/fault-calls.log"
unset RELEASE_COMMIT
export GH_TOKEN=fixture-token GH_REPO=fixture/repository
gh() {
    if [[ "$1" == api && "$2" != --method && "$MOCK_STATE_ERROR" == true ]]; then
        # Partial output must not turn a failed API read into permission to publish.
        printf true
        return 1
    fi
    if [[ "$1" == release && "$2" == create && -n "$MOCK_CREATE_FAILURE" ]]; then
        printf 'create\n' >> "$MOCK_FAULT_LOG"
        printf 'HTTP 500 (mock release creation)\n' >&2
        case "$MOCK_CREATE_FAILURE" in
            before) ;;
            after) export MOCK_DRAFT=true; printf missing > "$MOCK_MISSING" ;;
            public) export MOCK_DRAFT=false ;;
            unreadable) export MOCK_DRAFT=true MOCK_STATE_ERROR=true ;;
            unexpected) export MOCK_DRAFT=true MOCK_UNEXPECTED=true ;;
        esac
        return 1
    fi
    if [[ "$1" == release && ( "$2" == create || "$2" == edit ) ]]; then
        local previous="" notes_file=""
        for argument in "$@"; do
            [[ "$previous" != --notes-file ]] || notes_file="$argument"
            previous="$argument"
        done
        [[ -s "$notes_file" ]] || return 1
        if [[ -f "$MOCK_NOTES" ]]; then
            cmp -s "$notes_file" "$MOCK_NOTES" || return 1
        else
            cp "$notes_file" "$MOCK_NOTES"
        fi
    fi
    if [[ "$1" == api && "$2" == --method ]]; then
        printf 'tag\n' >> "$MOCK_LOG"
        git --git-dir="$MOCK_REMOTE" update-ref refs/tags/v0.4.0 "$(git rev-parse HEAD)"
    elif [[ "$1" == api ]]; then
        if [[ "$MOCK_RACE" == read ]]; then
            if [[ -f "$MOCK_COUNTER" ]]; then printf true; return 1; fi
            printf seen > "$MOCK_COUNTER"
        fi
        if [[ "$MOCK_RACE" == public ]]; then
            if [[ -f "$MOCK_COUNTER" ]]; then printf false; return; fi
            printf seen > "$MOCK_COUNTER"
        fi
        printf '%s' "$MOCK_DRAFT"
    elif [[ "$1" == release && "$2" == view ]]; then
        if [[ "$MOCK_RACE" == tag ]]; then git --git-dir="$MOCK_REMOTE" update-ref -d refs/tags/v0.4.0; fi
        [[ "$MOCK_UNEXPECTED" != true ]] || printf '%s\n' unexpected.apk
        printf '%s\n' Just-Cal-v0.4.0.apk Just-Cal-v0.4.0.aab
        [[ -f "$MOCK_MISSING" ]] || printf '%s\n' Just-Cal-v0.4.0-SHA256SUMS.txt
    elif [[ "$1" == release && "$2" == download ]]; then
        cp release-files/* "$5/"
        if [[ "$MOCK_DOWNLOAD_MISMATCH" == true ]]; then printf changed >> "$5/Just-Cal-v0.4.0.apk"; fi
    else
        if [[ "$1" == release && "$2" == create ]]; then export MOCK_DRAFT=true; fi
        if [[ "$1" == release && "$2" == upload ]]; then
            [[ "$*" != *--clobber* ]] || return 1
            rm -f "$MOCK_MISSING"
        fi
        printf '%s\n' "$1 $2" >> "$MOCK_LOG"
    fi
}
export -f gh
reject() {
    if bash "$publisher" v0.4.0 > "$fixture/result.log" 2>&1; then
        printf 'Expected publication failure\n' >&2
        exit 1
    fi
}
# Missing assets must never create a tag.
reject
[[ ! -e "$MOCK_LOG" ]] || exit 1
# These are placeholder bytes for publication-state tests, not signed build tests.
printf fixture > release-files/Just-Cal-v0.4.0.apk
printf fixture > release-files/Just-Cal-v0.4.0.aab
(cd release-files && shasum -a 256 Just-Cal-v0.4.0.apk Just-Cal-v0.4.0.aab > Just-Cal-v0.4.0-SHA256SUMS.txt)
# Invalid committed highlights must fail before creating a tag or draft.
cp docs/RELEASE_HIGHLIGHTS.md "$fixture/good-highlights"
printf '# v0.5.0\n' > docs/RELEASE_HIGHLIGHTS.md
git add docs && git commit -qm invalid-highlights
git push -q origin HEAD:refs/heads/master
reject
[[ ! -e "$MOCK_LOG" && -z "$(git ls-remote origin refs/tags/v0.4.0)" ]] || exit 1
cp "$fixture/good-highlights" docs/RELEASE_HIGHLIGHTS.md
git add docs && git commit -qm restored-highlights
git push -q origin HEAD:refs/heads/master
bash "$publisher" v0.4.0
[[ "$(git ls-remote origin refs/tags/v0.4.0)" == "$(git rev-parse HEAD)"$'\trefs/tags/v0.4.0' ]] || exit 1
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 3 ]] || exit 1
# Existing same-commit tag with no release recovers without another ref mutation.
bash "$publisher" v0.4.0
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 5 ]] || exit 1
# An identical interrupted draft publishes without replacing any asset.
export MOCK_DRAFT=true
bash "$publisher" v0.4.0
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 6 ]] || exit 1
# Complete only a missing draft asset; never overwrite an existing one.
printf missing > "$MOCK_MISSING"
bash "$publisher" v0.4.0
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
# A draft published by another actor mid-run must prevent further mutation.
MOCK_RACE=public reject
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
# A deleted/moved tag during a run prevents final publication.
MOCK_RACE=tag reject
git --git-dir="$MOCK_REMOTE" update-ref refs/tags/v0.4.0 "$(git rev-parse HEAD)"
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
# Hosted bytes must match the locally verified output exactly.
MOCK_DOWNLOAD_MISMATCH=true reject
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
export MOCK_DRAFT=false
reject
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
export MOCK_DRAFT=""
# HTTP 500 before server creation: preserve the tag and recover on a later run.
(
    export MOCK_LOG="$fixture/before.log" MOCK_CREATE_FAILURE=before
    reject
    [[ ! -e "$MOCK_LOG" && "$(wc -l < "$MOCK_FAULT_LOG" | tr -d ' ')" == 1 ]] || exit 1
    [[ "$(git ls-remote origin refs/tags/v0.4.0)" == "$(git rev-parse HEAD)"$'\trefs/tags/v0.4.0' ]] || exit 1
    export MOCK_CREATE_FAILURE=""
    bash "$publisher" v0.4.0
    [[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 2 ]] || exit 1
)
# Error after draft creation/partial upload: inspect it, upload only the missing
# checksum asset and verify all hosted bytes before publishing, without another POST.
(
    export MOCK_LOG="$fixture/after.log" MOCK_CREATE_FAILURE=after
    bash "$publisher" v0.4.0
    [[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 2 ]] || exit 1
    [[ "$(wc -l < "$MOCK_FAULT_LOG" | tr -d ' ')" == 2 ]] || exit 1
)
# Ambiguous errors never permit mutation of public/unreadable/unexpected state.
for failure in public unreadable unexpected; do
    (
        export MOCK_LOG="$fixture/$failure.log" MOCK_CREATE_FAILURE="$failure"
        reject
        [[ ! -e "$MOCK_LOG" ]] || exit 1
    )
done
# A failed API response with draft-looking partial output is still a failure.
(
    export MOCK_LOG="$fixture/read-error.log" MOCK_DRAFT=true MOCK_STATE_ERROR=true
    reject
    [[ ! -e "$MOCK_LOG" ]] || exit 1
)
# The same failed-read check must hold after the initial successful draft read.
(
    export MOCK_LOG="$fixture/later-read-error.log" MOCK_DRAFT=true MOCK_RACE=read
    export MOCK_COUNTER="$fixture/read-counter"
    reject
    [[ ! -e "$MOCK_LOG" ]] || exit 1
)
# A remotely moved tag is rejected even if the local tag cache has not seen it.
git commit --allow-empty -qm different
git push -q origin HEAD:refs/heads/master
reject
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
# A legacy/interrupted draft gets the same reviewed notes on recovery.
[[ -s "$MOCK_NOTES" ]] || exit 1
# Corrupted final assets also prevent publication.
printf changed >> release-files/Just-Cal-v0.4.0.apk
reject
[[ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" == 8 ]] || exit 1
printf 'Release publication tests passed.\n'
