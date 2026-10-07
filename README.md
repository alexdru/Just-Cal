# Just Cal

SPDX-License-Identifier: GPL-3.0-or-later

## License

Copyright (C) 2026 alexdru and Just Cal contributors.

Just Cal is free software licensed under the GNU General Public License, version 3 or (at your option) any later version (**GPL-3.0-or-later**). You may redistribute and modify it under those terms. It is provided without warranty. See [LICENSE](LICENSE) for the unchanged official text and [NOTICE](NOTICE) for project attribution.

Third-party components retain their own licenses. See [the licensing review](docs/LICENSING.md) for attribution requirements and items to verify before distributing binaries. Imported AI models are separate and are not relicensed by this project.

[![Android CI](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml)

## App version

The current app version and Android version code are defined in [version.properties](version.properties), the single editable source of truth. Settings → About shows the installed build's version, credits alexdru as the author and opens the [source repository](https://github.com/alexdru/Just-Cal) in the external browser. User-facing versions follow SemVer; release tags add a `v` prefix. Versions below 1.0 are development releases.

A local-first calorie diary for Android 11+ (`com.justcal.app`). Language can follow the system or be set to English or Russian in the app; Android 13+ also exposes Just Cal in system app-language settings. Brightness (System/Light/Dark) and color style are saved separately. Just Cal's green leaf palette is the default; Material You opts into dynamic wallpaper colors on Android 12+, with a branded fallback on older devices.

## Diary and navigation

Home shows today's consumed calories, daily goal, remaining calories, macros and food entries. Diary groups history by recorded calendar day; its calendar opens any past day, including one without entries. Entries can be added, edited or deleted within that day. Settings contains an optional local profile name, calorie goal, independently optional macro goals and independent brightness/color-style choices. Language changes apply immediately through Android application locales. Blank macro goals are absent, not zero targets; configured goals appear alongside consumed grams.

A content-sized Compose navigation pill stays horizontally centered on Home, Diary and Settings, with compact animated selection. Its transparent host reserves bottom content space. The separate Add Food FAB sits above/right of the pill; hiding it leaves the pill's geometry unchanged. On a day page, the FAB adds to that date; on Home and Diary root, it adds to today. The FAB is hidden in Settings. Navigation 3 maintains a separate saved back stack for each top-level destination and handles predictive back. Open days, scroll positions and settings drafts survive tab switching and configuration changes. No external navigation component or Telegram code is used. The FAB opens a compact launcher for Scan package, Scan meal or the existing manual editor.

## Implementation

- Single `app` module; Compose Material 3, Navigation 3 and Hilt.
- Screen ViewModels own StateFlow state and actions; Compose collects with lifecycle awareness. Editor drafts use SavedStateHandle.
- Room stores the original nutrition per 100 g, consumed weight, source, immutable local calendar day and creation timestamp. Queries use the stored epoch day, so changing time zones does not reassign previous entries. The active Today date refreshes on resume and across midnight.
- Nutrition inputs accept comma or dot decimal separators and up to two fractional digits. Integer hundredths are persisted; BigDecimal arithmetic computes exact portions and totals before display rounding. Calculations and validation live in `domain`.
- DataStore atomically persists the local profile name, calorie goal, nullable macro goals, brightness and color style, retaining the original preferences file and goal key. The initial 2000 kcal is an editable placeholder, not a personalized recommendation.
- English/Russian Android resources, AGP-generated locale config and AppCompat application locales provide synchronized per-app language selection without a duplicate DataStore preference. Dates and exact nutrition values use the active app locale.
- Room schema version 1 is exported under `app/schemas`. Future schema changes must include data-preserving migrations.
- CameraX rear-camera capture and Android Photo Picker feed a shared local image preparation and review flow. Camera access is requested only for taking a photo; choosing a photo needs no broad storage permission.
- Review preserves scan mode and target diary date, supports retake/reselect and ends with an explicit photo-ready state. Production food recognition is not implemented; Add Food does not invoke AI.
- Private temporary images use URI references, EXIF-aware decoding and bounded downsampling. No network permission, uploads, cloud backup, accounts or external food database.

## Experimental Local AI Lab

Settings → Local AI Lab provides debug/developer functionality using the LiteRT-LM Kotlin API. Import a compatible Gemma 4 E2B vision `.litertlm` with Android's document picker; the Lab stores a private copy rather than bundling the model in the APK. Choose CPU or GPU, load the model, choose/take a food photo through the shared image pipeline, edit a prompt and run multimodal inference entirely on-device. Streaming, cancellation, repeated runs without reloading, explicit backend errors and runtime metrics are available.

The Nutrition extraction preset enables JSON Schema constrained decoding and displays both the raw answer and a validated structured result. Values remain unverified and never enter the diary. This is experimental infrastructure, not production food recognition. There are no cloud AI calls, API keys, OCR, barcode services or model downloads inside the app. See [docs/LOCAL_AI_LAB.md](docs/LOCAL_AI_LAB.md) for compatible-model setup, lifecycle checks and verified coverage.

## Build and test

Open this directory as a Gradle project in Android Studio and use its compatible bundled JDK, or JDK 17. Install Android SDK platform 37 and build tools 36.0.0, then use the Gradle Wrapper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Connected tests require an emulator/device. They cover real Room history queries, day isolation, reopen persistence, edit/recalculation and deletion; DataStore profile/goal/theme persistence; and settings keyboard input, draft retention across tabs and Activity recreation, invalid-input handling, retained day navigation through both history and calendar, selected-date FAB/CRUD, and independently optional goals with reactive screen updates. JVM tests cover exact decimal arithmetic, locale-aware number presentation, independent brightness/palette drafts, validation, daily history summaries, selected-day creation, editor failure/retry/restoration, image decode budgets and scan/date navigation serialization, transactional model imports, strict nutrition JSON validation, and Lab load/run/repeat/cancel/cleanup state transitions. Image instrumentation tests cover all eight EXIF orientations, provider URIs, oversized/corrupt/missing images, private-file containment, launcher restoration, camera capture/retake and the real system Photo Picker. Camera tests require a working rear camera (the emulator virtual scene camera is sufficient); the picker system-UI fixture currently expects English device labels.

UI tests use Compose's v2 JUnit API and Espresso 3.7.0; the older transitive Espresso version cannot initialize its input manager on API 37.

## Release builds

Signed APK and AAB builds use environment-based credentials; debug builds and ordinary CI need no signing secrets. Pushing a matching `vMAJOR.MINOR.PATCH` tag validates, tests, builds and publishes signed binaries, generated notes and SHA-256 checksums through GitHub Actions. See [docs/RELEASING.md](docs/RELEASING.md) for signing-key setup and the release checklist.

The green leaf launcher identity uses adaptive foreground/background layers and an explicit monochrome layer for themed icons on Android 13+. Android supplies the mask.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the implemented navigation, persistence and global-action boundaries.
