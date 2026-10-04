# Just Cal

[![Android CI](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml)

## App version

The current app version and Android version code are defined in [version.properties](version.properties), the single editable source of truth. Settings → About shows the installed build's version. User-facing versions follow SemVer; release tags add a `v` prefix. Versions below 1.0 are development releases.

A local-first calorie diary for Android 11+ (`com.justcal.app`). English and Russian follow the device language. Appearance can follow the system or use a saved light/dark override; supported devices use dynamic colors.

## Diary and navigation

Home shows today's consumed calories, daily goal, remaining calories, macros and food entries. Diary groups history by recorded calendar day; its calendar opens any past day, including one without entries. Entries can be added, edited or deleted within that day. Settings contains an optional local profile name, calorie goal, independently optional macro goals and system/light/dark appearance. Blank macro goals are absent, not zero targets; configured goals appear alongside consumed grams.

A custom floating Compose navigation surface provides Home, Diary and Settings alongside an Add Food FAB. On a day page, the FAB adds to that date; on Home and Diary root, it adds to today. The FAB is hidden in Settings. Navigation 3 maintains a separate saved back stack for each top-level destination and handles predictive back. Open days, scroll positions and settings drafts survive tab switching and configuration changes. No external navigation component or Telegram code is used. The FAB opens a compact launcher for Scan package, Scan meal or the existing manual editor.

## Implementation

- Single `app` module; Compose Material 3, Navigation 3 and Hilt.
- Screen ViewModels own StateFlow state and actions; Compose collects with lifecycle awareness. Editor drafts use SavedStateHandle.
- Room stores the original nutrition per 100 g, consumed weight, source, immutable local calendar day and creation timestamp. Queries use the stored epoch day, so changing time zones does not reassign previous entries. The active Today date refreshes on resume and across midnight.
- Nutrition inputs accept comma or dot decimal separators and up to two fractional digits. Integer hundredths are persisted; BigDecimal arithmetic computes exact portions and totals before display rounding. Calculations and validation live in `domain`.
- DataStore atomically persists the local profile name, calorie goal, nullable macro goals and appearance, retaining the original preferences file and goal key. The initial 2000 kcal is an editable placeholder, not a personalized recommendation.
- Room schema version 1 is exported under `app/schemas`. Future schema changes must include data-preserving migrations.
- CameraX rear-camera capture and Android Photo Picker feed a shared local image preparation and review flow. Camera access is requested only for taking a photo; choosing a photo needs no broad storage permission.
- Review preserves scan mode and target diary date, supports retake/reselect and ends with an explicit photo-ready state. On-device AI recognition and nutrition extraction remain future work.
- Private temporary images use URI references, EXIF-aware decoding and bounded downsampling. No network permission, uploads, cloud backup, accounts or external food database.

## Build and test

Open this directory as a Gradle project in Android Studio and use its compatible bundled JDK, or JDK 17. Install Android SDK platform 37 and build tools 36.0.0, then use the Gradle Wrapper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Connected tests require an emulator/device. They cover real Room history queries, day isolation, reopen persistence, edit/recalculation and deletion; DataStore profile/goal/theme persistence; and settings keyboard input, draft retention across tabs and Activity recreation, invalid-input handling, retained day navigation through both history and calendar, selected-date FAB/CRUD, and independently optional goals with reactive screen updates. JVM tests cover exact decimal arithmetic, validation, daily history summaries, selected-day creation, editor failure/retry/restoration, image decode budgets and scan/date navigation serialization. Image instrumentation tests cover all eight EXIF orientations, provider URIs, oversized/corrupt/missing images, private-file containment, launcher restoration, camera capture/retake and the real system Photo Picker. Camera tests require a working rear camera (the emulator virtual scene camera is sufficient); the picker system-UI fixture currently expects English device labels.

UI tests use Compose's v2 JUnit API and Espresso 3.7.0; the older transitive Espresso version cannot initialize its input manager on API 37.

## Release builds

Signed APK and AAB builds use environment-based credentials; debug builds and ordinary CI need no signing secrets. Pushing a matching `vMAJOR.MINOR.PATCH` tag validates, tests, builds and publishes signed binaries, generated notes and SHA-256 checksums through GitHub Actions. See [docs/RELEASING.md](docs/RELEASING.md) for signing-key setup and the release checklist.

The green leaf launcher identity uses adaptive foreground/background layers and an explicit monochrome layer for themed icons on Android 13+. Android supplies the mask.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the implemented navigation, persistence and global-action boundaries.
