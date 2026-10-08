#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_KEYSTORE_PATH:?Set ANDROID_KEYSTORE_PATH}"
: "${ANDROID_KEYSTORE_PASSWORD:?Set ANDROID_KEYSTORE_PASSWORD}"
: "${ANDROID_KEY_ALIAS:?Set ANDROID_KEY_ALIAS}"
# Trust the configured key, require every content entry to be signed by its alias,
# and reject unsigned additions, signature failures and invalid certificates.
jarsigner -verify -strict -keystore "$ANDROID_KEYSTORE_PATH" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD "${1:?Specify the AAB}" "$ANDROID_KEY_ALIAS"
