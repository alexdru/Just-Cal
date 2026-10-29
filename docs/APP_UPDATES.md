# In-app GitHub updates

Just Cal is distributed directly through [official GitHub Releases](https://github.com/alexdru/Just-Cal/releases).
The updater is independently implemented; mpvRx serves only as a workflow reference.

## User flow

- Automatic checks are enabled by default and run when the app resumes, at most once every 24 hours. No periodic background worker, foreground service or notification permission is needed.
- Settings → Updates can disable automatic requests or perform a manual check. Attempts, including network failures, are throttled persistently. Manual checks bypass the interval. If an APK is already verified, or its download is running, the check button reopens that state without another network request. The tonal check button keeps the request in Settings, showing the default Material 3 LoadingIndicator only while checking, with no idle icon. Symmetric reserved slots preserve the label position, dimensions and colors. No bottom sheet opens until a newer release or manual request error is known. Release details, error/retry, download progress and installation use the native sheet. A completed manual check with no newer release only shows “Latest version installed” in the shared snackbar, without an icon. Automatic and already-dismissed checks stay quiet. No dedicated update screen or navigation entry is created. The Settings group observes loaded/automatic/preference-saving/checking values; only the loading indicator visibility and accessible state description change during checking. Download progress stays in the sheet. Repeated check taps join the existing request.
- An available release shows its version, download size, plain-text release notes and GitHub page. Automatic prompts wait until a root destination has no food sheet, splash or keyboard.
- Swipe-down, tapping the scrim and Back dismiss the sheet. There are no Later, Done or Skip version actions. Dismissing a running check does not cancel its bounded request, but its eventual result cannot reopen the sheet. The current-version snackbar has no trailing punctuation.
- Download APK is explicit. Measured progress uses Material 3 Expressive's full-width determinate LinearWavyProgressIndicator (flat-and-wavy) with a numeric percentage above the track. Waiting uses ContainedLoadingIndicator. In Settings, the current-version snackbar sits 12 dp above the measured navigation pill, without an offset for the hidden FAB. Cancellation is available; closing the sheet keeps the download running while the app process lives. Activity recreation and tab changes retain it. Process termination interrupts the download; a later attempt restarts partial downloads. Completed cached APKs are revalidated before reuse.
- The ready-to-install state presents two compact steps in a secondary-container tonal block with an attention icon: Android's per-source permission and the Play Protect unknown-developer prompt. Install opens Android's source-permission setting if needed, then the system installer after permission is granted. Denial/cancellation retains the APK. The guidance uses the visible Install anyway option for the unknown-developer warning shown in the user-provided screenshot; it never disables Play Protect or applies this instruction to an explicit malware warning. Google documents the different warning categories in [Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).
- Debug and ocrCheck packages may check releases but cannot download/install production updates. Their package names and signing identities differ.

Diary, images, OCR and Local AI remain local and usable offline. The updater uses the network only for GitHub release metadata and APK delivery; explicit model transfers are a separate feature. GitHub receives the client's IP address and the Just Cal version in User-Agent; no diary/profile/image data or identifiers are sent.

## Release and file boundary

The endpoint is fixed to `https://api.github.com/repos/alexdru/Just-Cal/releases/latest`.
Only a normal release with an exact `vMAJOR.MINOR.PATCH` tag is accepted. Version ordering compares numeric components, never strings. The asset must be named exactly `Just-Cal-vMAJOR.MINOR.PATCH.apk` at the matching official repository URL. Drafts/prereleases are ignored; missing/invalid metadata gives an actionable error.

Metadata is limited to 1 MiB and APKs to 300 MiB. APK redirects stay on GitHub's HTTPS delivery hosts, with bounded redirects and connection/read timeouts. No tokens, GitHub account, external updater library or new dependency is used.

Downloads stream off the main thread into app-private, backup-excluded cache. Truncation, excess bytes and SHA-256 mismatches are rejected. Only a completely verified file is renamed to an APK. Cancellation and failures remove the partial file. The cache retains at most the selected release APK and can be reclaimed by Android.

Before producing a FileProvider URI, Android archive metadata must match the installed package and the release version, have a strictly higher versionCode, support the current SDK and have exactly the installed app's current signing certificate set. Signing-key rotation is intentionally rejected; the release process requires a stable persistent key. Android performs its own final signature/install checks.

The updater provider is non-exported, exposes only the private update directory, and grants temporary read permission for the individual APK. No broad storage permission, exported receiver or automatic installer launch is introduced. Installation is never silent. The implementation is for GitHub distribution; a future Play variant needs its own update/permission policy.

## Verification

JVM tests cover metadata/asset rejection, strict numeric versions, trusted redirect hosts, daily/offline throttling, manual checks, skipping, preference failure, double download taps, cancellation/retry and bounded streaming/hash verification.

Build verification uses the Gradle Wrapper: targeted update tests first, then the full unit suite, debug/release lint and debug assembly.

### Debug sheet mock

Debug Settings → Updates → **Preview update bottom sheet · mock** first shows its default button loading indicator for 1.5 seconds, then opens the production `UpdateSheet` over Settings. No mock Activity, route or separate screen exists. Debug-only fixture controls inside the sheet offer Checking, Available, Up to date, Download, Ready and Error; Download APK simulates progress and completion. The initial mock check lasts 1.5 seconds. Selecting Up to date dismisses the fixture and emits the same shared snackbar as the production result. No GitHub, diary, preferences, files, browser or package installer are used. Fixture code/text lives in `app/src/debug`; release and ocrCheck provide an empty source-set hook. Both English and Russian fixtures are included.

Recorded API 30 checks cover offline failure, persisted automatic-check choice,
stable loading-button bounds, mock sheet states/progress, dismissal and snackbar
placement. Debug fixtures validate UI only. Successful live GitHub retrieval,
production APK download and Android installer/permission behavior remain unverified
on hardware; OnePlus 12 coverage is also open. Historical per-version checks remain
in Git. Keep the current release's user-facing changes in
[RELEASE_HIGHLIGHTS.md](RELEASE_HIGHLIGHTS.md), not in this contract document.

Device acceptance for a future signed release pair:

1. Install an older production APK signed with the persistent key. Keep diary data and an unsaved editor draft.
2. Publish a newer normal release using the existing workflow; launch the older app. Confirm a prompt at a root and deferred prompting from the editor/camera.
3. Exercise swipe/scrim/Back dismissal, manual recheck, reopening an active download/verified APK, and the automatic switch across restart.
4. Download/cancel/retry; disconnect the network and verify errors do not affect the diary.
5. Deny then grant per-source installation access. Cancel Android's installer and retry; finally confirm the update and verify diary/settings data survive.
6. Attempt mismatched/corrupt APK fixtures in an isolated test installation; none should reach the installer.

A debug build cannot establish production-to-production installation coverage. Publishing a release is a separate, explicitly authorized action.
