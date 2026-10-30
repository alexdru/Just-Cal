#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'Release publication failed: %s\n' "$*" >&2; exit 1; }
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tag="${1:-}"
# Refresh the ledger without force-updating release tags.
git fetch origin refs/heads/master:refs/remotes/origin/master --tags
if [[ -n "${RELEASE_COMMIT:-}" ]]; then
    [[ "$(git rev-parse HEAD)" == "$RELEASE_COMMIT" ]] || fail "Source changed after preflight."
    bash "$script_dir/validate-release-ref.sh" "$RELEASE_COMMIT"
fi
bash "$script_dir/validate-release.sh" "$tag"
: "${GH_TOKEN:?Set GH_TOKEN (the workflow GITHUB_TOKEN)}"
: "${GH_REPO:?Set GH_REPO to owner/repository}"
commit="$(git rev-parse HEAD)"
apk="release-files/Just-Cal-$tag.apk"
aab="release-files/Just-Cal-$tag.aab"
sums="release-files/Just-Cal-$tag-SHA256SUMS.txt"
[[ -s "$apk" && -s "$aab" && -s "$sums" ]] || fail "Verified release assets are missing."
(cd release-files && shasum -a 256 -c "Just-Cal-$tag-SHA256SUMS.txt")

notes="$(mktemp)"
trap 'rm -f "$notes"' EXIT
bash "$script_dir/create-release-notes.sh" "$tag" > "$notes"
[[ -s "$notes" ]] || fail "Release notes are empty."

remote_commit=""
check_tag() {
    local direct="" peeled="" sha ref refs
    refs="$(git ls-remote --tags origin "refs/tags/$tag" "refs/tags/$tag^{}")"
    while read -r sha ref; do
        case "$ref" in
            "refs/tags/$tag") direct="$sha" ;;
            "refs/tags/$tag^{}") peeled="$sha" ;;
        esac
    done <<< "$refs"
    remote_commit="${peeled:-$direct}"
    [[ "$remote_commit" == "$commit" || ( "$1" == optional && -z "$remote_commit" ) ]] ||
        fail "Remote tag $tag is missing or points elsewhere; it will not be overwritten."
}
release_state() {
    gh api "repos/$GH_REPO/releases" --paginate --jq ".[] | select(.tag_name == \"$tag\") | .draft"
}
require_draft() {
    check_tag required
    local state
    state="$(release_state)" || fail "Cannot read release state; no mutation is allowed."
    [[ "$state" == true ]] || fail "Release is no longer a draft; no mutation is allowed."
}
check_tag optional
draft="$(release_state)" || fail "Cannot read release state; no mutation is allowed."
[[ -z "$draft" || "$draft" == true ]] || fail "Release $tag is already public; assets are immutable."

if [[ -z "$remote_commit" ]]; then
    # POST fails on a concurrently created ref. No force-update is possible.
    gh api --method POST "repos/$GH_REPO/git/refs" -f "ref=refs/tags/$tag" -f "sha=$commit"
fi
check_tag required
if [[ -z "$draft" ]]; then
    if ! gh release create "$tag" --verify-tag --target "$commit" --draft \
        --title "Just Cal $tag" --notes-file "$notes" "$apk" "$aab" "$sums"; then
        # A failed response can follow a successful server-side write. Never repeat
        # creation blindly: inspect the draft and use the same recovery checks.
        draft="$(release_state)" || fail "Draft creation failed and state is unreadable. Retry the same source/version when GitHub recovers; keep the tag."
        [[ "$draft" == true ]] ||
            fail "Draft creation failed and no recoverable draft is confirmed. Retry the same source/version; do not delete or move the tag."
        printf 'Draft creation returned an error; checking the saved draft and assets.\n' >&2
    fi
fi
# Creation may have stopped partway through uploads. Complete only missing assets.
require_draft
names="$(gh release view "$tag" --json assets --jq '.assets[].name')"
while IFS= read -r name; do
    [[ -z "$name" || "$name" == "Just-Cal-$tag.apk" || "$name" == "Just-Cal-$tag.aab" ||
       "$name" == "Just-Cal-$tag-SHA256SUMS.txt" ]] || fail "Unexpected draft asset; inspect the draft."
done <<< "$names"
for asset in "$apk" "$aab" "$sums"; do
    found=false
    while IFS= read -r name; do
        [[ "$name" != "${asset##*/}" ]] || found=true
    done <<< "$names"
    if [[ "$found" == false ]]; then
        require_draft
        # Never --clobber: duplicate/racing uploads fail rather than overwrite bytes.
        gh release upload "$tag" "$asset"
    fi
done
require_draft
asset_names="$(gh release view "$tag" --json assets --jq '.assets[].name' | sort)"
expected_names="$(printf '%s\n' "Just-Cal-$tag.apk" "Just-Cal-$tag.aab" "Just-Cal-$tag-SHA256SUMS.txt" | sort)"
[[ "$asset_names" == "$expected_names" ]] || fail "Draft assets differ from the three verified outputs."
downloads="$(mktemp -d)"
trap 'rm -rf "$downloads"; rm -f "$notes"' EXIT
gh release download "$tag" --dir "$downloads" \
    --pattern "Just-Cal-$tag.apk" --pattern "Just-Cal-$tag.aab" --pattern "Just-Cal-$tag-SHA256SUMS.txt"
for asset in "$apk" "$aab" "$sums"; do
    cmp -s "$asset" "$downloads/${asset##*/}" ||
        fail "Existing/uploaded bytes differ; do not overwrite them. Inspect the draft."
done
require_draft
gh release edit "$tag" --notes-file "$notes" --draft=false --prerelease=false --latest
printf 'Published %s at %s\n' "$tag" "$commit"
