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

Both workflows provision command-line tools, `platform-tools`, `platforms;android-37.0` and `build-tools;36.0.0` with `android-actions/setup-android`. SDK package IDs and Gradle API levels are distinct; use the configured minor-version package path. Build tools 36.0.0 are also used for artifact verification; AGP may install its required build tools after license acceptance.

To inspect the published SDK catalog yourself:

```sh
sdkmanager --list --channel=3
```

SDK package IDs and Gradle API levels are distinct. Check the live catalog before changing package IDs; do not downgrade the app's compile SDK to work around SDK provisioning.

## In-app GitHub updates

The direct-distribution updater consumes the latest non-draft, non-prerelease release from `alexdru/Just-Cal`, with tag `vMAJOR.MINOR.PATCH`. Keep the production APK asset named exactly `Just-Cal-vMAJOR.MINOR.PATCH.apk` and ensure GitHub's asset `digest` is a SHA-256 checksum. AAB, debug and ocrCheck assets are not eligible. The existing publication workflow already supplies this contract. Retain the persistent signing key; the updater rejects APKs with a different current signer, even if a signing lineage is present. See [APP_UPDATES.md](APP_UPDATES.md) for behavior and acceptance checks.

The `REQUEST_INSTALL_PACKAGES` permission serves user-confirmed self-updates in the GitHub distribution. A future Play distribution requires a separate update/permission policy; do not publish this direct-update configuration to Play unchanged.

## Native OCR packaging

APK/AAB builds include bundled offline OCR models and their license notices. The supported native ABI is `arm64-v8a` (including OnePlus 7T/12 and ARM64 emulators). Upstream OpenCV's x86 builds include Intel IPP under incompatible binary-only terms; restoring x86 support requires an OpenCV build without IPP and a fresh native license/16-KiB alignment check. Do not remove the ABI filter merely to run an x86 emulator. See [PACKAGE_OCR.md](PACKAGE_OCR.md).

### Local minified OCR verification

Release R8 configuration includes `app/proguard-rules.pro`. Preserve the
upstream-required `ai.onnxruntime.**` classes and members: ONNX Runtime 1.23.2's
AAR does not ship consumer keep rules, and its JNI bridge looks up Java types and
constructors by fixed names. The OpenCV addition preserves only
`org.opencv.core.CvException` and its public String constructor; Android's
default native-method rules remain in effect. A successful R8 build alone does
not verify native calls.

The `ocrCheck` variant inherits release minification/resource shrinking and is
non-debuggable. It uses the standard Android debug key, application ID suffix
`.ocrcheck` and version suffix `-ocrcheck`, so it needs no production secrets
and installs separately from release/debug. Its self-contained
`app/src/ocrCheck` instrumentation probe is compiled into the same minified APK,
avoiding a separate test runner's shared-classpath assumptions.

```sh
./gradlew :app:assembleOcrCheck
adb install -r app/build/outputs/apk/ocrCheck/app-ocrCheck.apk
adb shell am instrument -w -e large true \
  com.justcal.app.ocrcheck/com.justcal.app.OcrJniProbe
```

Expect `MINIFIED_OCR_OK` only after actual bundled-model execution and the
synthetic parser assertions pass. Run this on each target device when changing
native runtimes or R8 rules. The variant is a local regression artifact signed
with the debug key; never publish it as a production release or treat its
certificate as release-signature verification. The OnePlus 7T
synthetic result and remaining OnePlus 12 qualification are documented in
[PACKAGE_OCR.md](PACKAGE_OCR.md). The rendered-label check does not replace
real-package accuracy or normal diary-flow tests.

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

Use JDK 17 or Android Studio's compatible bundled JDK and the Gradle Wrapper. Run commands from the repository root. On macOS, if `java` reports “Unable to locate a Java Runtime”, select the installed Android Studio JDK in the same terminal first (adjust the app path for a different Studio installation):

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Then run:

```sh
bash scripts/test-release-environment.sh
bash scripts/test-release-ref.sh
bash scripts/test-release-validation.sh
bash scripts/test-release-notes.sh
bash scripts/test-release-publication.sh
bash scripts/check-doc-links.sh
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

## Current-release highlights

[RELEASE_HIGHLIGHTS.md](RELEASE_HIGHLIGHTS.md) is a small replacement draft, not a
historical changelog. Its first line must exactly match the intended tag:

```text
# vMAJOR.MINOR.PATCH

- Concise factual change visible to users.
  Evidence: THIS_COMMIT
```

Keep **3–6** single-line bullets, at most 180 characters each, each followed by one
Evidence line. Cite a commit that implements the claim and is included after the
comparison base. Several bullets may cite the same commit when it contains distinct
changes. Review the actual diff/tests; ancestry validation cannot judge the truth of
prose. Do not invent changes to fill the count or claim unfinished quality/transfer
work as shipped. If fewer than three changes are ready, accumulate a meaningful
release before publication. Use Evidence: THIS_COMMIT when committing implementation
and highlights together: no second evidence-only commit is needed. The exact marker
resolves to the pinned RELEASE_COMMIT, not a later checkout HEAD or the last commit
that edited the draft. A full lowercase 40-character implementing commit SHA remains
supported for changes already committed in the release range. Both forms undergo the
same ancestry/comparison-base checks. PENDING, HEAD and THIS_COMMIT: are invalid.
Review each claim against the actual release diff; the marker does not prove prose
is factual or turn an already released feature into a new change. Agents do not
automatically commit, push, tag or publish.

scripts/create-release-notes.sh reads version.properties and the draft using
git show from RELEASE_COMMIT (HEAD by default), ignoring working-tree/later edits.
It rejects a wrong version, empty/malformed draft, wrong count, missing/unknown/
non-ancestor evidence and evidence already included in the comparison base.
The base is the highest SemVer tag reachable from the source, excluding the intended
tag; a first release uses the root commit. Review this range when drafting notes.
The public body includes only the bullets and a **Full Changelog** GitHub comparison
link; Evidence lines remain in source for review.

Refresh published tags before validating a draft: a checkout missing recent tags can
incorrectly accept changes that already shipped. After committing the draft, preview
without a GitHub write:

```sh
git fetch origin --tags
CHECK_PROJECT_DRAFT=true bash scripts/test-release-notes.sh
GH_REPO=alexdru/Just-Cal bash scripts/create-release-notes.sh vMAJOR.MINOR.PATCH
```

If notes fail because a highlight was already included in the comparison base, replace
it with an actual change after that base; do not weaken the evidence check or relabel
an old feature. A documentation-only revision need not be published as an app release.
After correcting a committed draft, start a new workflow dispatch from the updated
master commit with its matching version (leave ref blank). Rerunning an old failed
run retains its old source and cannot pick up the corrected draft.

The release job validates notes before signing and the publisher regenerates them
before any tag/draft mutation. Creation uses --notes-file; recovery supplies the
same notes on the final draft edit, after asset-byte and state rechecks. Public
releases are rejected before mutation. All tools are retained from the reviewed
workflow revision, while the draft is read from the pinned release source.
Selecting older source without a valid matching committed draft safely fails.
The notes/publication regressions use disposable Git fixtures and mocked gh only.

## Run a release

1. Review the licensing/attribution checklist in [LICENSING.md](LICENSING.md) before binary distribution.
2. Set both version properties for the intended release and replace the current highlights draft as described below; commit and push the reviewed source/workflow changes. Wait for Android CI to pass.
3. Open **Actions → Android Release → Run workflow** and select **master**. Enter `version` exactly as in the chosen source (without a `v` prefix). Leave `ref` blank to use the workflow commit, enter `master` for its fetched tip, or a full lowercase 40-character commit SHA reachable from master. Other branch/tag names, PR refs, short SHAs and Git expressions are rejected. Preflight pins the resolved SHA before access to signing secrets. Approve the Environment deployment only after reviewing that exact source commit.
4. The workflow checks version format/match, code monotonicity, clean source, tag conflicts and required secrets; runs tests/lint; builds signed APK and AAB; checks package/version metadata and signatures against the supplied key; rejects debuggable artifacts and Android debug certificates; calculates checksums. AAB verification uses `jarsigner -verify -strict` with the configured keystore and alias, rejecting unsigned entries and certificate/signature failures. Release verification/publication scripts are retained from the reviewed workflow revision before checkout of the pinned source.
5. Only after those checks does it create `vX.Y.Z` using the GitHub API, create a draft release with the validated highlights, Full Changelog comparison and all assets, and make it public:
   - `Just-Cal-vX.Y.Z.apk`
   - `Just-Cal-vX.Y.Z.aab`
   - `Just-Cal-vX.Y.Z-SHA256SUMS.txt`

No manual `git tag` step is required. Pushes and PRs never publish production releases. The requested source must use compatible Gradle signing/version configuration. Source and workflow revisions are rechecked against fetched master before signing, and source ancestry plus the release-code ledger are refreshed before publication.

If a run fails before publication, fix the cause and rerun the same revision/version. A missing release with an existing same-commit tag is recoverable. Interrupted drafts are completed by uploading only missing assets, never overwriting existing ones. The same committed notes are supplied at final draft publication, so older interrupted drafts recover their notes as well. The workflow downloads all three hosted assets and compares their bytes to the verified local outputs before publication. A mismatched existing asset causes a safe failure; inspect the unpublished draft and remove the inconsistent draft assets manually before recovery. Do not change the tag or any public release.

A GitHub HTTP 5xx during draft creation can occur after the tag or draft was saved.
The publisher does not repeat creation blindly: it rereads release state, resumes
only a confirmed draft and applies the same missing-asset, hosted-byte and final
state checks. If no draft is confirmed or the API remains unreadable, it stops and
preserves the tag. Failed state reads are rejected even if partial output says
`true`. Retry the same version/source after GitHub recovers; never delete or move
the tag to work around a server error.

When applying a release-tooling fix after a tag was created, dispatch a **new** run
from updated master so it uses the corrected policies, but set `ref` to the
**original full release source SHA** and keep the same version. Leaving ref blank
would select the tooling commit and correctly conflict with the existing tag.
A public release remains immutable.

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

Check Settings → About and confirm the diary survives the update. Debug builds use application ID `com.justcal.app.debug`, label `Just Cal Debug`, a `-debug` version suffix and the standard Android debug key. Release retains `com.justcal.app`, label `Just Cal` and the production signing key. Both variants can be installed side by side with separate diaries/settings; their data is not shared or migrated automatically. Debug installations made before the `.debug` suffix used the production application ID and cannot be updated with the release key; preserve any needed old debug data before uninstalling that legacy installation.

## Trust boundary and remaining risks

A commit reachable from master is treated as reviewed release code. Gradle and its plugins execute with signing credentials during a signed build; a malicious approved master commit can still steal the key or tamper with artifacts. Environment approval is not a sandbox. Required reviews, restricted maintainers, dependency/Wrapper review, runner isolation and protected master are essential.

An older master commit may contain known vulnerabilities. Ancestry checks establish provenance, not the security of that revision. Review the exact pinned SHA before Environment approval. Secret values, real production signatures, Environment enforcement and a GitHub release end-to-end run cannot be verified through local mocked tests.

## Provenance

The exact tag/commit, workflow run, verified signing certificate and final checksums identify the release. Optional GitHub artifact attestations are deferred: repository availability could not be confirmed with authenticated tooling locally, and adding OIDC/attestation write permissions would expand the release job beyond `contents: write`. Checksums are not provenance attestations.

No real production signing key, tag or GitHub Release is generated during local infrastructure verification.
