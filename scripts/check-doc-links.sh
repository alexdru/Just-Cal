#!/usr/bin/env bash
set -euo pipefail

# Offline check of project-owned Markdown links, including heading fragments.
# Third-party notices, ignored private fixtures and remote URLs are outside this check.
root="$(git rev-parse --show-toplevel)"
cd "$root"
failures=0
checked=0
slug() {
    local value="$1"
    value="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
    value="${value//[^[:alnum:] _-]/}"
    printf '%s' "${value// /-}"
}
has_anchor() {
    local file="$1" wanted="$2" line heading id previous suffix fence=false
    local seen=()
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" == '```'* || "$line" == '~~~'* ]]; then
            if [[ "$fence" == true ]]; then fence=false; else fence=true; fi
            continue
        fi
        [[ "$fence" == false ]] || continue
        if [[ "$line" =~ ^#{1,6}\ (.+)$ ]]; then
            heading="${BASH_REMATCH[1]}"
            id="$(slug "$heading")"
            suffix=0
            for previous in "${seen[@]+"${seen[@]}"}"; do
                [[ "$previous" != "$id" ]] || suffix=$((suffix + 1))
            done
            seen+=("$id")
            [[ "$suffix" == 0 ]] || id="$id-$suffix"
            [[ "$id" != "$wanted" ]] || return 0
        fi
    done < "$file"
    return 1
}
check_target() {
    local source="$1" target="$2" path fragment=""
    [[ "$target" != *:* && "$target" != //* ]] || return 0
    target="${target#<}"; target="${target%>}"
    path="${target%%#*}"
    [[ "$target" != *#* ]] || fragment="${target#*#}"
    if [[ -z "$path" ]]; then path="$source"; else path="${source%/*}/$path"; fi
    checked=$((checked + 1))
    if [[ ! -e "$path" ]]; then
        printf '%s: missing %s\n' "$source" "$target" >&2
        failures=$((failures + 1))
    elif [[ -n "$fragment" && "$path" == *.md ]] && ! has_anchor "$path" "$fragment"; then
        printf '%s: missing heading %s\n' "$source" "$target" >&2
        failures=$((failures + 1))
    fi
}
link_pattern='\]\(([^()]*)\)'
reference_pattern='^\[[^]]+\]:[[:space:]]+([^[:space:]]+)'
while IFS= read -r source; do
    case "$source" in docs/*|*.md) ;; *) continue ;; esac
    [[ "$source" != */* || "$source" == docs/* ]] || continue
    [[ -f "$source" ]] || continue
    source="./$source"
    fence=false
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" == '```'* || "$line" == '~~~'* ]]; then
            if [[ "$fence" == true ]]; then fence=false; else fence=true; fi
            continue
        fi
        [[ "$fence" == false ]] || continue
        if [[ "$line" =~ $reference_pattern ]]; then check_target "$source" "${BASH_REMATCH[1]}"; fi
        while [[ "$line" =~ $link_pattern ]]; do
            match="${BASH_REMATCH[0]}"
            target="${BASH_REMATCH[1]}"
            line="${line#*"$match"}"
            check_target "$source" "$target"
        done
    done < "$source"
done < <(git ls-files --cached --others --exclude-standard -- '*.md' | sort -u)
[[ "$failures" == 0 ]] || exit 1
printf 'Documentation links passed (%s local targets; remote URLs not fetched).\n' "$checked"
