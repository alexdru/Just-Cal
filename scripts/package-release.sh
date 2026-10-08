#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'Release packaging failed: %s\n' "$*" >&2; exit 1; }
tag="${1:-}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$script_dir/validate-release.sh" "$tag"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to the verified bundletool JAR}"
: "${ANDROID_KEYSTORE_PATH:?Set ANDROID_KEYSTORE_PATH}"
: "${ANDROID_KEYSTORE_PASSWORD:?Set ANDROID_KEYSTORE_PASSWORD}"
: "${ANDROID_KEY_ALIAS:?Set ANDROID_KEY_ALIAS}"

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
tools="$ANDROID_HOME/build-tools/36.0.0"
[[ -f "$apk" && -f "$aab" ]] || fail "Signed APK and AAB are required."
code=""
while IFS='=' read -r key value; do
    [[ "$key" != versionCode ]] || code="$value"
done < version.properties

# Inspect the binaries, not just the Gradle source or output filenames.
badging="$("$tools/aapt2" dump badging "$apk")"
[[ "$badging" == *"package: name='com.justcal.app'"* &&
   "$badging" == *"versionCode='$code'"* &&
   "$badging" == *"versionName='${tag#v}'"* ]] || fail "APK package/version mismatch."
[[ "$badging" != *"application-debuggable"* ]] || fail "A debug APK cannot be published."
java -jar "$BUNDLETOOL_JAR" validate --bundle="$aab"
for attribute in versionName versionCode; do
    actual="$(java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$aab" \
        --xpath="/manifest/@android:$attribute")"
    expected="$code"
    [[ "$attribute" != versionName ]] || expected="${tag#v}"
    [[ "$actual" == "$expected" ]] || fail "AAB $attribute mismatch: $actual."
done
[[ "$(java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$aab" --xpath='/manifest/@package')" == com.justcal.app ]] ||
    fail "AAB package mismatch."

debuggable="$(java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$aab" \
    --xpath='/manifest/application/@android:debuggable')"
[[ "$debuggable" != true ]] || fail "A debuggable AAB cannot be published."

# Both binaries must be signed with the supplied release key, never a debug key.
key_details="$(keytool -list -v -keystore "$ANDROID_KEYSTORE_PATH" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -alias "$ANDROID_KEY_ALIAS")"
[[ "$key_details" != *"CN=Android Debug"* ]] || fail "The Android debug key is not a release key."
certificate="$(keytool -exportcert -keystore "$ANDROID_KEYSTORE_PATH" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -alias "$ANDROID_KEY_ALIAS" |
    shasum -a 256)"
certificate="${certificate%% *}"
apk_signing="$("$tools/apksigner" verify --verbose --print-certs "$apk")"
[[ "$apk_signing" == *"Number of signers: 1"* &&
   "$apk_signing" == *"Signer #1 certificate SHA-256 digest: $certificate"* ]] ||
    fail "APK is not signed with the configured key."
aab_signing="$(bash "$script_dir/verify-aab-signature.sh" "$aab")"
[[ "$aab_signing" == *"jar verified."* ]] || fail "AAB signature verification failed."
aab_certificate="$(keytool -printcert -jarfile "$aab")"
fingerprint="$(printf '%s' "$certificate" | tr '[:lower:]' '[:upper:]' | fold -w2 | paste -sd: -)"
[[ "$aab_certificate" == *"SHA256: $fingerprint"* ]] ||
    fail "AAB is not signed with the configured key."

mkdir -p release-files
cp "$apk" "release-files/Just-Cal-$tag.apk"
cp "$aab" "release-files/Just-Cal-$tag.aab"
(
    cd release-files
    shasum -a 256 "Just-Cal-$tag.apk" "Just-Cal-$tag.aab" > "Just-Cal-$tag-SHA256SUMS.txt"
)
printf 'Verified signed release artifacts and checksums for %s\n' "$tag"
