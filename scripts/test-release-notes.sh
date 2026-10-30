#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
generator="$script_dir/create-release-notes.sh"
project=""
if [[ "${CHECK_PROJECT_DRAFT:-false}" == true ]]; then
    project="$(git rev-parse --show-toplevel)"
fi
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
git init -q "$fixture"
cd "$fixture"
git config user.name "Release notes test"
git config user.email "release-test@example.invalid"
mkdir docs
printf 'versionName=0.3.0\nversionCode=4\n' > version.properties
git add .
git commit -qm base
base="$(git rev-parse HEAD)"
git tag v0.3.0
printf 'versionName=0.4.0\nversionCode=5\n' > version.properties
git add .
git commit -qm feature
feature="$(git rev-parse HEAD)"
draft() {
    {
        printf '# %s\n\n' "$1"
        for change in One Two Three; do
            printf -- '- %s user-facing change.\n  Evidence: %s\n' "$change" "$2"
        done
    } > docs/RELEASE_HIGHLIGHTS.md
    git add docs/RELEASE_HIGHLIGHTS.md
    git commit -qm highlights
}
export GH_REPO=fixture/repository
unset RELEASE_COMMIT
reject() {
    if bash "$generator" "${1:-v0.4.0}" > "$fixture/result" 2>&1; then
        printf 'Expected release-notes rejection\n' >&2
        exit 1
    fi
}
# Missing, wrong version, blank and too few highlights fail.
reject
draft v0.5.0 "$feature"
reject
printf '# v0.4.0\n' > docs/RELEASE_HIGHLIGHTS.md
git add . && git commit -qm empty
reject
printf '# v0.4.0\n- Only one.\n  Evidence: %s\n' "$feature" > docs/RELEASE_HIGHLIGHTS.md
git add . && git commit -qm short
reject
# Evidence already in the prior release, unknown or from a later source fails.
draft v0.4.0 "$base"
reject
draft v0.4.0 0000000000000000000000000000000000000000
reject
git tag -a evidence "$feature" -m evidence
draft v0.4.0 "$(git rev-parse evidence)"
reject
printf 'versionName=0.4.0\nversionCode=5\nversionName=0.4.0\n' > version.properties
git add . && git commit -qm duplicate-version
reject
printf 'versionName=0.4.0\nversionCode=5\n' > version.properties
git add . && git commit -qm valid-version
draft v0.4.0 "$feature"
selected="$(git rev-parse HEAD)"
notes="$(bash "$generator" v0.4.0)"
[[ "$notes" == *'- One user-facing change.'* &&
   "$notes" == *'https://github.com/fixture/repository/compare/v0.3.0...v0.4.0'* &&
   "$notes" != *Evidence:* ]] || exit 1
reject v0.5.0
# A same-source recovery tag must not become its own changelog base.
git tag v0.4.0
[[ "$(bash "$generator" v0.4.0)" == "$notes" ]] || exit 1
# Uncommitted edits and later highlights must not leak into the selected source.
printf broken > docs/RELEASE_HIGHLIGHTS.md
[[ "$(RELEASE_COMMIT="$selected" bash "$generator" v0.4.0)" == "$notes" ]] || exit 1
git add . && git commit -qm later
future="$(git rev-parse HEAD)"
draft v0.4.0 "$future"
# Forge an older selected snapshot referring to a non-ancestor commit.
tree="$(git write-tree)"
forged="$(printf 'non-ancestor evidence\n' | git commit-tree "$tree" -p "$selected")"
if RELEASE_COMMIT="$forged" bash "$generator" v0.4.0 > "$fixture/result" 2>&1; then exit 1; fi
# Too many highlights and missing evidence fail.
draft v0.4.0 "$feature"
for change in Four Five Six Seven; do
    printf -- '- %s change.\n  Evidence: %s\n' "$change" "$feature" >> docs/RELEASE_HIGHLIGHTS.md
done
git add . && git commit -qm long
reject
printf '# v0.4.0\n- No evidence.\n' > docs/RELEASE_HIGHLIGHTS.md
git add . && git commit -qm missing-evidence
reject
# No prior release tag still yields a comparison link for the first release.
git tag -d v0.3.0 v0.4.0
draft v0.4.0 "$feature"
[[ "$(bash "$generator" v0.4.0)" == *"/compare/$base...v0.4.0"* ]] || exit 1
# Self evidence allows implementation and highlights in one commit.
git tag v0.3.0 "$feature"
draft v0.4.0 THIS_COMMIT
self_source="$(git rev-parse HEAD)"
self_notes="$(bash "$generator" v0.4.0)"
[[ "$self_notes" == *'/compare/v0.3.0...v0.4.0'* &&
   "$self_notes" != *THIS_COMMIT* && "$self_notes" != *Evidence:* ]] || exit 1
git tag v0.4.0
[[ "$(bash "$generator" v0.4.0)" == "$self_notes" ]] || exit 1
# A later HEAD/tag and dirty draft cannot change the pinned source's self evidence.
draft v0.4.0 "$feature"
git tag v0.3.1
printf broken > docs/RELEASE_HIGHLIGHTS.md
[[ "$(RELEASE_COMMIT="$self_source" bash "$generator" v0.4.0)" == "$self_notes" ]] || exit 1
# A source already present in the comparison base is still rejected with self evidence.
git tag -d v0.3.1
git tag v0.3.1 "$self_source"
if RELEASE_COMMIT="$self_source" bash "$generator" v0.4.0 > "$fixture/result" 2>&1; then exit 1; fi
git tag -d v0.3.1
# Only the exact marker is accepted; no punctuation, expressions or unresolved placeholders.
for invalid in THIS_COMMIT: this_commit HEAD PENDING; do
    draft v0.4.0 "$invalid"
    reject
done
git tag -d v0.3.0 v0.4.0
draft v0.4.0 THIS_COMMIT
[[ "$(bash "$generator" v0.4.0)" == *"/compare/$base...v0.4.0"* ]] || exit 1
# Optionally validate the pending project draft against local project history.
# Keep ordinary fixture tests usable with a shallow CI/source checkout.
if [[ "${CHECK_PROJECT_DRAFT:-false}" == true ]]; then
git clone --quiet --no-hardlinks "$project" "$fixture/project"
cp "$project/version.properties" "$fixture/project/version.properties"
cp "$project/docs/RELEASE_HIGHLIGHTS.md" "$fixture/project/docs/RELEASE_HIGHLIGHTS.md"
cd "$fixture/project"
git config user.name "Release notes test"
git config user.email "release-test@example.invalid"
git add version.properties docs/RELEASE_HIGHLIGHTS.md
git diff --cached --quiet || git commit -qm "Pending draft fixture"
version=""
while IFS='=' read -r key value; do
    [[ "$key" != versionName ]] || version="$value"
done < version.properties
bash "$generator" "v$version"
fi
printf 'Release notes tests passed.\n'
