#!/usr/bin/env bash
set -euo pipefail

# Exercise failure paths in a disposable Git repository; never tag the app checkout.
validator="$(pwd)/scripts/validate-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
cd "$fixture"
git init -q
git config user.name "Release validation test"
git config user.email "release-test@example.invalid"

commit_version() {
    printf 'versionName=%s\nversionCode=%s\n' "$1" "$2" > version.properties
    git add version.properties
    git commit -qm "Version fixture"
}
reject() {
    if bash "$validator" "$1" > result.log 2>&1; then
        printf 'Expected validation to reject %s\n' "$1" >&2
        exit 1
    fi
}
commit_version 0.1.0 1
git tag v0.1.0
bash "$validator" v0.1.0
printf 'versionName=0.1.0\nversionCode=2\n' > version.properties
reject v0.1.0
git restore version.properties
reject v0.2.0
reject v0.1.0-beta.1
reject v00.1.0
commit_version 0.2.0 1
git tag v0.2.0
reject v0.2.0
git tag -d v0.2.0
commit_version 0.2.0 2
git tag v0.2.0
bash "$validator" v0.2.0
reject v0.1.0
commit_version 0.3.0 0
git tag v0.3.0
reject v0.3.0
git tag -d v0.3.0
commit_version 0.3.0 2100000001
git tag v0.3.0
reject v0.3.0
printf 'Release validation tests passed.\n'
