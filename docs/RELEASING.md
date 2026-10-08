# Releasing Just Cal

## Version and source policy

[version.properties](../version.properties) remains the only editable version source. Gradle never derives or rewrites it from Git.

- `versionName`: `MAJOR.MINOR.PATCH`, no leading zeroes or `v` prefix.
- `versionCode`: integer in 1..2100000000, greater than every other existing SemVer release tag's code, including releases from older branches.
- Release tag: exactly `v` plus `versionName`. Published tags and public release assets are immutable.
- 0.x versions are development versions, published as normal GitHub Releases.
- Application changes get one explicit version increment; infrastructure/documentation changes and verification retries retain the existing version.

The workflow validates the chosen clean commit before creating its tag. It accepts a missing tag or an existing tag resolving to that same commit (including annotated tags). A tag on another commit is rejected. Existing tags are the release-code ledger; do not delete them or publish outside the workflow.

## CI and Android SDK

Android CI runs for pull requests to `master` and pushes to `master`, the current default branch. It uses Ubuntu 24.04, Temurin JDK 17, the Gradle Wrapper and Gradle dependency caching. It runs release-tooling regression checks, unit tests, debug lint and debug assembly. Only successful default-branch pushes upload an Actions artifact named `Just-Cal-debug-vVERSION-COMMIT`, retained for 14 days. Debug artifacts are never GitHub Releases.

Both workflows provision command-line tools, `platform-tools`, `platforms;android-37.0` and `build-tools;36.0.0` with `android-actions/setup-android`. The Google repository currently publishes API 37 under the minor-version package ID `platforms;android-37.0`; `platforms;android-37` is absent. The old setup failed before Gradle because it requested the absent package. `compileSdk = 37` and `targetSdk = 37` remain unchanged. Build tools 36.0.0 are also used for artifact verification; AGP may install its required build tools after license acceptance.

To inspect the published SDK catalog yourself:

```sh
sdkmanager --list --channel=3
```

SDK package IDs and Gradle API levels are distinct. Check the live catalog before changing package IDs; do not downgrade the app's compile SDK to work around SDK provisioning.

## One-time signing setup

If you already created the release key, keep it; do not generate a replacement. For first-time setup only, create it outside the repository using a JDK's `keytool`:

```sh
keytool -genkeypair -v -storetype JKS \
  -keystore "$HOME/just-cal-release.jks" \
  -alias just-cal -keyalg RSA -keysize 3072 -validity 10000
```

Let keytool prompt for both passwords and identity details. Do not use the Android debug key. Make at least two secure backups of the keystore, passwords and alias; store them separately from the Git checkout and record the certificate's SHA-256 fingerprint:

```sh
keytool -list -v -keystore "$HOME/just-cal-release.jks" -alias just-cal
```

**Losing the signing key can prevent all future direct APK updates to existing installations.** Every future GitHub APK release must use this same stable key. Never generate a new key per release or commit the keystore, Base64 key or passwords.

For future Google Play App Signing, distinguish the Play app-signing key from the upload key. An AAB signed with an upload key does not imply Play-delivered APKs share the direct-download APK signing identity. Plan this explicitly before publishing to Play.

## GitHub Actions Secrets

On macOS, encode and copy the entire keystore without line breaks:

```sh
base64 -i "$HOME/just-cal-release.jks" | tr -d '\n' | pbcopy
```

Base64 is not encryption. Store the following as **Environment secrets**, under **Settings → Environments → release**, rather than repository-wide secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Complete Base64-encoded keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias, for example `just-cal` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Clear the clipboard after setting the secret. If these Secrets were already added at repository level, add the same values to Environment `release`, verify their names, then delete the repository/organization-level copies exposed to this repository. Environment values override repository values, but repository copies remain accessible to other workflows and can silently act as fallback. The workflow cannot determine the scope of a resolved secret value.

Create/configure `release` before running the workflow:

1. Add at least one **Required reviewer**. Use an independent trusted reviewer and **Prevent self-review** when one is available; for a solo-maintainer project, self-approval is an explicit weaker policy, not an independent review.
2. Set **Deployment branches and tags → Selected branches and tags**, with exactly one **Branch** rule named `master`. Add no tag or wildcard rules. Do not use “Protected branches only” as a substitute; it can allow all branches when no protection rules exist.
3. Disable administrator bypass of Environment protection where supported. Review the pinned source SHA linked by the Environment deployment before approving.
4. Protect `master`: require reviewed PRs and the Android CI check, prohibit force pushes/deletion, and restrict direct pushes and bypass privileges. Review changes to workflows, Gradle, Wrapper and release scripts as code capable of using the signing key.
5. Enable GitHub **release immutability** for new published releases where available, and protect `v*` tags from updates/deletion with a tag ruleset. Allow the release workflow to create new tags without giving it an update/delete bypass.

Preflight checks Environment reviewers and the exact master-only rule through the API and stops if those settings are missing/unreadable. YAML alone does not provision protection rules. Repository secret migration, master/tag rules and administrator bypass settings must be checked manually in GitHub.

No PAT is needed: the workflow uses `GITHUB_TOKEN`. Preflight has `contents: read` and `actions: read` for inspection; the Environment-protected release job has `contents: write`. Ordinary CI has `contents: read`.

Gradle consumes `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. GitHub supplies the path after decoding the Base64 secret into a runner-private temporary file. It removes that file on success/failure, keeps secret values out of command arguments and disables caches for signed builds. PR/debug CI needs none of these secrets. Missing signing variables fail clearly before release packaging.

## Local verification and signed builds

Use JDK 17 or Android Studio's compatible bundled JDK and the Gradle Wrapper. Run commands from the repository root. On macOS, if `java` reports “Unable to locate a Java Runtime”, select the installed Android Studio Preview JDK in the same terminal first (adjust the app path for a different Studio installation):

```sh
export JAVA_HOME='/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Then run:

```sh
bash scripts/test-release-environment.sh
bash scripts/test-release-ref.sh
bash scripts/test-release-validation.sh
bash scripts/test-release-publication.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The release-tooling tests use disposable local Git repositories and a mocked GitHub CLI; they never create app tags or GitHub Releases. `bash scripts/test-aab-signature.sh` tests strict JAR signature validation using an existing Android debug keystore and disposable archive fixtures (not full AAB structure); it never creates a key or reads the production keystore. It reports a skip if no existing debug keystore is available.

For a signed local build, supply credentials through your shell/secret manager. Paste this into an interactive Bash or zsh terminal; both password prompts hide input and preserve spaces/backslashes. Use the alias chosen when creating your existing key. If its private-key password is the same as the keystore password, enter that same password at both prompts; an empty second input is not a fallback.

```sh
export ANDROID_KEYSTORE_PATH="$HOME/just-cal-release.jks"
export ANDROID_KEY_ALIAS=just-cal
printf 'Keystore password: '
IFS= read -rs ANDROID_KEYSTORE_PASSWORD
printf '\nKey password: '
IFS= read -rs ANDROID_KEY_PASSWORD
printf '\n'
export ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD
./gradlew :app:validateReleaseSigning :app:testDebugUnitTest :app:lintRelease \
  :app:assembleRelease :app:bundleRelease --no-daemon --no-configuration-cache --no-build-cache
unset ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_PASSWORD
```

Do not enable shell tracing (`set -x`) or paste passwords into command arguments, chat or logs. Bash's `read -p` is not a portable prompt: in zsh it means coprocess input and fails with `no coprocess`. If Gradle reports missing signing variables, rerun the prompts and build in the same terminal; an IDE build does not inherit variables exported in a separate terminal. A keystore/alias/password error requires checking your existing key's credentials rather than creating a replacement key.

Outputs are `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`. To compile/lint release without credentials, use `:app:compileReleaseKotlin :app:lintRelease`; release packaging itself requires signing.

## Run a release

1. Review the licensing/attribution checklist in [LICENSING.md](LICENSING.md) before binary distribution.
2. Edit both version properties for the intended release, commit and push the source and workflow changes. Wait for Android CI to pass.
3. Open **Actions → Android Release → Run workflow** and select **master**. Enter `version` exactly as in the chosen source (currently `0.4.0`). Leave `ref` blank to use the workflow commit, enter `master` for its fetched tip, or a full lowercase 40-character commit SHA reachable from master. Other branch/tag names, PR refs, short SHAs and Git expressions are rejected. Preflight pins the resolved SHA before access to signing secrets. Approve the Environment deployment only after reviewing that exact source commit.
4. The workflow checks version format/match, code monotonicity, clean source, tag conflicts and required secrets; runs tests/lint; builds signed APK and AAB; checks package/version metadata and signatures against the supplied key; rejects debuggable artifacts and Android debug certificates; calculates checksums. AAB verification uses `jarsigner -verify -strict` with the configured keystore and alias, rejecting unsigned entries and certificate/signature failures. Release verification/publication scripts are retained from the reviewed workflow revision before checkout of the pinned source.
5. Only after those checks does it create `vX.Y.Z` using the GitHub API, create a draft release with GitHub-generated notes and all assets, and make it public:
   - `Just-Cal-vX.Y.Z.apk`
   - `Just-Cal-vX.Y.Z.aab`
   - `Just-Cal-vX.Y.Z-SHA256SUMS.txt`

No manual `git tag` step is required. Pushes and PRs never publish production releases. The requested source must use compatible Gradle signing/version configuration. Source and workflow revisions are rechecked against fetched master before signing, and source ancestry plus the release-code ledger are refreshed before publication.

If a run fails before publication, fix the cause and rerun the same revision/version. A missing release with an existing same-commit tag is recoverable. Interrupted drafts are completed by uploading only missing assets, never overwriting existing ones. The workflow downloads all three hosted assets and compares their bytes to the verified local outputs before publication. A mismatched existing asset causes a safe failure; inspect the unpublished draft and remove the inconsistent draft assets manually before recovery. Do not change the tag or any public release.

Remote tag/draft state is rechecked before uploads and final publication. A different-commit tag, a released draft, unexpected assets or already public release fails. Correcting public releases requires a new versionName/versionCode.

The fixed `android-release` concurrency group serializes this workflow across refs/versions, but GitHub concurrency is not a FIFO queue and newer pending runs can replace older pending runs. It does not serialize administrator/API actions outside this workflow. GitHub has no atomic compare-and-publish operation covering tag, draft and uploads; tiny check/mutation races remain. Platform release immutability and tag/master rules are needed to close that external-actor risk.

## Verify downloads and updates

Download all three release assets into one directory and verify their final bytes:

```sh
shasum -a 256 -c Just-Cal-vX.Y.Z-SHA256SUMS.txt
# Linux: sha256sum -c Just-Cal-vX.Y.Z-SHA256SUMS.txt
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify \
  --verbose --print-certs Just-Cal-vX.Y.Z.apk
jarsigner -verify -strict -keystore "$HOME/just-cal-release.jks" Just-Cal-vX.Y.Z.aab just-cal
keytool -printcert -jarfile Just-Cal-vX.Y.Z.aab
```

Compare the APK/AAB certificate SHA-256 fingerprint to the one recorded from your keystore. A passing checksum detects changed bytes; it does not by itself authenticate the signing identity.

Install and later update on the same device/profile:

```sh
adb install Just-Cal-vX.Y.Z.apk
# After logging test data and publishing a higher-code version with the same key:
adb install -r Just-Cal-vNEXT.apk
```

Check Settings → About and confirm the diary survives the update. Debug builds have a different signing identity; they cannot be updated directly with the release key. Use a separate test device/profile if existing debug data matters.

## Trust boundary and remaining risks

A commit reachable from master is treated as reviewed release code. Gradle and its plugins execute with signing credentials during a signed build; a malicious approved master commit can still steal the key or tamper with artifacts. Environment approval is not a sandbox. Required reviews, restricted maintainers, dependency/Wrapper review, runner isolation and protected master are essential.

An older master commit may contain known vulnerabilities. Ancestry checks establish provenance, not the security of that revision. Review the exact pinned SHA before Environment approval. Secret values, real production signatures, Environment enforcement and a GitHub release end-to-end run cannot be verified through local mocked tests.

## Provenance

The exact tag/commit, workflow run, verified signing certificate and final checksums identify the release. Optional GitHub artifact attestations are deferred: repository availability could not be confirmed with authenticated tooling locally, and adding OIDC/attestation write permissions would expand the release job beyond `contents: write`. Checksums are not provenance attestations.

No real production signing key, tag or GitHub Release is generated during local infrastructure verification.
