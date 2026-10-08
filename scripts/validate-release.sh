#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'Release validation failed: %s\n' "$*" >&2; exit 1; }

# Deliberately accept only the two explicit, human-editable properties.
property() {
    local wanted="$1" key value found="" count=0
    while IFS='=' read -r key value || [[ -n "$key" ]]; do
        if [[ "$key" == "$wanted" ]]; then
            found="$value"
            count=$((count + 1))
        fi
    done
    [[ "$count" == 1 ]] || return 1
    printf '%s' "$found"
}

tag="${1:-}"
semver='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
[[ "$tag" =~ ^v$semver$ ]] || fail "Use vMAJOR.MINOR.PATCH without leading zeroes."
name="$(property versionName < version.properties)" || fail "Missing/duplicate versionName."
code="$(property versionCode < version.properties)" || fail "Missing/duplicate versionCode."
[[ "$name" == "${tag#v}" ]] || fail "Tag $tag does not match versionName $name."
[[ "$code" =~ ^[1-9][0-9]{0,9}$ ]] && (( code <= 2100000000 )) ||
    fail "versionCode must be an integer in 1..2100000000."
# A dispatch validates before creating a tag. Existing tags are immutable.
if git show-ref --verify --quiet "refs/tags/$tag"; then
    [[ "$(git rev-parse "$tag^{commit}")" == "$(git rev-parse HEAD)" ]] ||
        fail "Existing tag $tag points to a different commit."
fi
git diff --quiet && git diff --cached --quiet ||
    fail "Release source has uncommitted tracked changes. Build the exact tagged source."

# Tags are the release ledger. Fetch all tags before calling this script in CI.
# Refuse old/reused codes even when a patch is published from an older branch.
while IFS= read -r previous; do
    [[ "$previous" != "$tag" && "$previous" =~ ^v$semver$ ]] || continue
    properties="$(git show "$previous:version.properties")" ||
        fail "Cannot read version.properties at $previous."
    previous_code="$(property versionCode <<< "$properties")" ||
        fail "Cannot read versionCode at $previous."
    [[ "$previous_code" =~ ^[1-9][0-9]{0,9}$ ]] && (( previous_code <= 2100000000 )) ||
        fail "Invalid versionCode at $previous."
    (( code > previous_code )) ||
        fail "versionCode $code must exceed $previous_code from $previous."
done < <(git tag --list 'v*.*.*')

printf 'Validated %s (%s) at %s\n' "$tag" "$code" "$(git rev-parse --short HEAD)"
