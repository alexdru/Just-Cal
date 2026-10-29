#!/usr/bin/env bash
set -euo pipefail

# Read only committed highlights from the selected source, never the working tree.
fail() { printf 'Release notes failed: %s\n' "$*" >&2; exit 1; }
tag="${1:-}"
[[ "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || fail "Expected vMAJOR.MINOR.PATCH."
: "${GH_REPO:?Set GH_REPO to owner/repository}"
[[ "$GH_REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || fail "Invalid repository."
commit="$(git rev-parse --verify "${RELEASE_COMMIT:-HEAD}^{commit}")"
version=""
version_count=0
while IFS='=' read -r key value; do
    if [[ "$key" == versionName ]]; then
        version="$value"
        version_count=$((version_count + 1))
    fi
done < <(git show "$commit:version.properties")
[[ "$version_count" == 1 && "$tag" == "v$version" ]] || fail "Tag does not match selected source version."
draft="$(git show "$commit:docs/RELEASE_HIGHLIGHTS.md")" || fail "Commit a current highlights draft."
base=""
while IFS= read -r candidate; do
    if [[ "$candidate" != "$tag" && "$candidate" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
        base="$candidate"
        break
    fi
done < <(git tag --merged "$commit" --sort=-version:refname)
# First release: compare against the initial source commit.
[[ -n "$base" ]] || base="$(git rev-list --max-parents=0 "$commit")"
[[ "$base" != *$'\n'* ]] || fail "Ambiguous initial history."
count=0
pending=false
body=""
line_number=0
while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    if [[ "$line_number" == 1 ]]; then
        [[ "$line" == "# $tag" ]] || fail "Highlights version must be # $tag."
    elif [[ -z "$line" ]]; then
        continue
    elif [[ "$line" == "- "* ]]; then
        [[ "$pending" == false ]] || fail "Each highlight needs an Evidence commit."
        change="${line#- }"
        [[ "$change" =~ [[:alnum:]] && ${#change} -le 180 ]] || fail "Use concise nonempty highlights."
        body+="$line"$'\n'
        count=$((count + 1))
        pending=true
    elif [[ "$line" =~ ^\ \ Evidence:\ ([0-9a-f]{40})$ ]]; then
        [[ "$pending" == true ]] || fail "Evidence must follow a highlight."
        evidence="${BASH_REMATCH[1]}"
        [[ "$(git cat-file -t "$evidence")" == commit ]] || fail "Evidence must name a commit object."
        git merge-base --is-ancestor "$evidence" "$commit" || fail "Evidence is outside the selected source."
        if [[ "$base" != "$evidence" ]] || [[ "$base" == v* ]]; then
            if git merge-base --is-ancestor "$evidence" "$base"; then
                fail "Highlight was already included in the comparison base."
            fi
        fi
        pending=false
    else
        fail "Unexpected highlights format at line $line_number."
    fi
done <<< "$draft"
[[ "$pending" == false && "$count" -ge 3 && "$count" -le 6 ]] || fail "Require 3–6 highlights with evidence."
printf '## Changes\n\n%s\n**Full Changelog**: https://github.com/%s/compare/%s...%s\n' "$body" "$GH_REPO" "$base" "$tag"
