#!/usr/bin/env bash
set -euo pipefail
verifier="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-aab-signature.sh"
# Reuse a pre-existing disposable Android debug key. Never generate a key or read
# the user's production keystore. Fixtures test JAR signatures, not AAB structure.
export ANDROID_KEYSTORE_PATH="${SIGNATURE_TEST_KEYSTORE:-$HOME/.android/debug.keystore}"
export ANDROID_KEYSTORE_PASSWORD=android ANDROID_KEY_ALIAS=androiddebugkey
if [[ ! -f "$ANDROID_KEYSTORE_PATH" ]]; then
    printf 'Signature fixture skipped: no existing Android debug keystore.\n'
    exit 0
fi
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/content"
printf original > "$fixture/content/payload.txt"
jar --create --file "$fixture/unsigned.aab" -C "$fixture/content" .
reject() {
    if bash "$verifier" "$1" > "$fixture/result.log" 2>&1; then
        printf 'Expected AAB signature rejection\n' >&2; exit 1
    fi
}
reject "$fixture/unsigned.aab"
cp "$fixture/unsigned.aab" "$fixture/signed.aab"
jarsigner -keystore "$ANDROID_KEYSTORE_PATH" -storepass:env ANDROID_KEYSTORE_PASSWORD \
    -keypass:env ANDROID_KEYSTORE_PASSWORD "$fixture/signed.aab" "$ANDROID_KEY_ALIAS" > "$fixture/sign.log" 2>&1
bash "$verifier" "$fixture/signed.aab"
cp "$fixture/signed.aab" "$fixture/extra.aab"
printf unsigned > "$fixture/content/extra.txt"
jar --update --file "$fixture/extra.aab" -C "$fixture/content" extra.txt
reject "$fixture/extra.aab"
cp "$fixture/signed.aab" "$fixture/tampered.aab"
printf modified > "$fixture/content/payload.txt"
jar --update --file "$fixture/tampered.aab" -C "$fixture/content" payload.txt
reject "$fixture/tampered.aab"
ANDROID_KEY_ALIAS=wrong-alias reject "$fixture/signed.aab"
printf 'AAB strict-signature regression tests passed (no keys created).\n'
