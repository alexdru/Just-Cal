# Releasing Just Cal

## Version policy

Edit only [version.properties](../version.properties) for application versions:

- `versionName` is Semantic Versioning `MAJOR.MINOR.PATCH`, without leading zeroes.
- `versionCode` is an integer from 1 to 2100000000. Increase it for **every** published Android release, including fixes from older branches. Never reuse a code.
- Git tags are `vMAJOR.MINOR.PATCH`; the `v` is not part of Android's version name.
- `0.x.y` means development before 1.0. These are normal GitHub Releases, not GitHub pre-releases. Alpha/beta tag syntax is not supported yet.

Agents update the explicit version once when completing application changes: patch for fixes, minor for new features, and an increased version code alongside either. A version already chosen by the user for that task is retained. Implementation/verification retries and documentation-only changes do not trigger extra increments. Version updates do not create tags or publish releases.

Gradle consumes explicit properties without Git. Source archives build normally. Release validation compares the tag to the committed version and requires a code greater than every other SemVer release tag. Release validation also rejects uncommitted tracked changes. Keep published tags immutable; never move or delete them.

## Create the signing key manually

Use a JDK's `keytool`. Keep this key outside the repository and back it up securely; losing it prevents updating existing installations signed with it.

```sh
keytool -genkeypair -v -keystore "$HOME/just-cal-release.jks" \
  -alias just-cal -keyalg RSA -keysize 3072 -validity 10000
```

Let keytool prompt for passwords. Do not put real passwords in command arguments, tracked files, issues or logs. The same signing identity must be used for APK updates. If Google Play App Signing is introduced later, plan upload-key/app-signing-key ownership separately; this milestone does not publish to Play.

## GitHub secrets

In repository **Settings → Secrets and variables → Actions**, configure exactly:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Entire keystore encoded as a single Base64 string |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias, e.g. `just-cal` |
| `ANDROID_KEY_PASSWORD` | Private-key password (normally the keystore password with default PKCS12 keys) |

On macOS or Linux, copy the output of:

```sh
base64 < "$HOME/just-cal-release.jks" | tr -d '\n'
```

Base64 is **not encryption**. Treat the output as a secret. The workflow decodes the key in the runner's private temporary directory, disables Gradle caching for signed builds, and removes the temporary key on success or failure. Ordinary CI needs none of these secrets. No production key is created by repository tooling.

## Build locally

Use JDK 17 (or Android Studio's compatible bundled JDK), SDK platform 37 and SDK build tools 36.0.0. Always use the Gradle Wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For signed local builds, set environment variables in your local shell or secret manager:

```sh
export ANDROID_KEYSTORE_PATH="$HOME/just-cal-release.jks"
export ANDROID_KEY_ALIAS=just-cal
read -rs -p 'Keystore password: ' ANDROID_KEYSTORE_PASSWORD; printf '\n'
read -rs -p 'Key password: ' ANDROID_KEY_PASSWORD; printf '\n'
export ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease \
  --no-configuration-cache --no-build-cache
unset ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD
```

The `read -p` examples use Bash; in zsh use `read -rs 'ANDROID_KEYSTORE_PASSWORD?Keystore password: '` (and likewise for the key password). Credentials are never passed as Gradle command arguments. Signed outputs are in `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/`.

Release packaging fails with an actionable message when signing variables are incomplete. To check release compilation without credentials, use `:app:compileReleaseKotlin :app:lintRelease`; no unsigned artifact is presented as a release.

## Create a release

Both GitHub workflows explicitly provision Android command-line tools, platform 37 and build tools 36.0.0 with `android-actions/setup-android` before running Gradle. They do not depend on `sdkmanager` being preinstalled or present on the runner's PATH.

Android CI validates pushes and pull requests; it does not create tags. Android Release starts when a version tag is pushed, following the steps below.

1. Update both fields in `version.properties`, commit all intended changes to `master`, and push.
2. Ensure **Android CI** succeeds (unit tests, lint and debug assembly).
3. Fetch tags and create the tag at the intended clean commit:
   ```sh
   git fetch origin --tags
   git tag -a vX.Y.Z -m "Just Cal X.Y.Z"
   git push origin vX.Y.Z
   ```
4. Watch **Android Release** in GitHub Actions. It checks the exact tagged commit, tag/version agreement, increasing code, tests, release lint, APK/AAB metadata and signatures.
5. Open the generated GitHub Release. It contains generated notes and:
   - `Just-Cal-vX.Y.Z.apk`
   - `Just-Cal-vX.Y.Z.aab`
   - `Just-Cal-vX.Y.Z-SHA256SUMS.txt`

The workflow creates a draft with all assets before making it public. If publication is interrupted, inspect/delete the incomplete draft before rerunning; do not change the tag. Failed validation, tests, builds or signing never reach publication.

Download all three assets into one directory and verify:

```sh
shasum -a 256 -c Just-Cal-vX.Y.Z-SHA256SUMS.txt
# Linux alternative:
sha256sum -c Just-Cal-vX.Y.Z-SHA256SUMS.txt
```

Install the APK and check Settings → About, then verify an update retains the existing diary. Debug and release signing identities differ; test releases on a separate device/profile rather than uninstalling a debug build containing important data.

## Build provenance

For this foundation, provenance is the exact Git tag/commit plus the workflow run, verified Android signatures and SHA-256 checksums. Native GitHub artifact attestations were evaluated: they are available for this public repository, but need additional OIDC/attestation write permissions. They are deferred to keep the initial release job limited to `contents: write`; checksums alone are not attestations.

The workflows do not upload keys, local signing configuration, intermediate Gradle directories or source secrets. Protect access to repository signing secrets and keep published tags immutable.
