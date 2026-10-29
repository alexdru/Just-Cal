# Just Cal

SPDX-License-Identifier: GPL-3.0-or-later

[![Android CI](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/alexdru/Just-Cal/actions/workflows/ci.yml)

A privacy-first calorie diary for Android 11+, ARM64. Food logging, photographs,
OCR and inference work locally without an account or external food database.
English and Russian, light/dark brightness and optional Material You are supported.

## Logging food

Home shows today's calories, optional goals, macros and entries. Diary history and
its calendar open the same date-aware day view. Add manually, scan a package or use
an explicitly selected meal-name model; review and correct values before Save.
Entries can be edited, duplicated, moved to another date or deleted.

Package scanning uses bundled PP-OCRv5 and deterministic parsing. Optional local
Gemma assistance can fill missing protein/fat/carbohydrate values on a reliable
per-100-g label crop. It preserves OCR calories, mass and existing values.
Uncertain values remain empty. Meal assistance suggests an editable name only;
nutrition and consumed weight must be supplied by the user. Numerical meal
estimation is deferred. See [package OCR](docs/PACKAGE_OCR.md).

Settings contains optional macro goals, calorie target, appearance/language,
[diary backup](docs/DATA_BACKUP.md) and [local diagnostics](docs/OCR_DIAGNOSTICS.md).
The calorie default is an editable placeholder, not a personalized recommendation.
Backup import previews changes; merge preserves conflicts and explicit replacement
first writes a recovery snapshot. Room schema v2 retains the data-preserving 1→2 migration.

## On-device AI and network use

[AI Models and Playground](docs/LOCAL_AI_LAB.md) manage private, backup-excluded
weights imported through Android's document picker or downloaded by explicit action.
Installation never selects a task. Production requires exact artifact/runtime/task
admission; experimental Playground results never create diary entries.
Gemma has narrow package-macro and meal-name admissions. PaddleOCR-VL remains
experimental after numerical errors. Successful inference is not evidence of accuracy.
Current implementation status and open validation gates: [AI closeout](docs/AI_REDESIGN_PLAN.md).

Optional automatic [GitHub update checks](docs/APP_UPDATES.md) run on resume at most
daily; APK download and Android installation require user actions. Network use is
limited to update requests and explicit model transfers. No diary, photo or prompt
is uploaded. GitHub/model hosts receive normal connection metadata, including IP address.

## Build and test

Open in Android Studio with its bundled JDK, or use JDK 17. The project uses one
app module, Kotlin, Compose Material 3 Expressive, Navigation 3, Room and Hilt.
Install SDK `platforms;android-37.0` and build tools 36.0.0, then run the Wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
bash scripts/test-release-notes.sh
bash scripts/test-release-publication.sh
bash scripts/check-doc-links.sh
```

Connected tests need an ARM64 device/emulator. Private-photo/model tests are opt-in;
see the relevant evidence reproduction instructions before enabling them.
Debug uses a separate package/diary; production signing secrets are unnecessary.
[RELEASING.md](docs/RELEASING.md) covers all release-tooling checks, signing and recovery.

## Project references

- [AGENTS.md](AGENTS.md): contribution and completion rules.
- [DESIGN.md](DESIGN.md): visual source of truth.
- [ARCHITECTURE.md](docs/ARCHITECTURE.md): current implementation contracts.
- [version.properties](version.properties): explicit application version.
- [RELEASE_HIGHLIGHTS.md](docs/RELEASE_HIGHLIGHTS.md): small draft for the next release.

Copyright (C) 2026 alexdru and Just Cal contributors. Licensed under
[GPL-3.0-or-later](LICENSE), without warranty; see [NOTICE](NOTICE) and
[third-party licensing](docs/LICENSING.md). Imported/downloaded models retain their
own licenses. Version tags identify corresponding release source.
