# Just Cal

A local-first calorie diary for Android 11+ (`com.justcal.app`). English and Russian follow the device language; light/dark appearance and dynamic colors follow the system.

## Milestone 1

Today shows consumed calories, an editable daily goal, remaining calories, macros and diary entries. Manual entry supports preview, add, edit and confirmed deletion. Camera and AI are intentionally deferred.

## Implementation

- Single `app` module; Compose Material 3, Navigation 3 and Hilt.
- Screen ViewModels own StateFlow state and actions; Compose collects with lifecycle awareness. Editor drafts use SavedStateHandle.
- Room stores the original nutrition per 100 g, consumed weight, source, immutable local calendar day and creation timestamp. Queries use the stored epoch day, so changing time zones does not reassign previous entries. The active Today date refreshes on resume and across midnight.
- Nutrition inputs accept comma or dot decimal separators and up to two fractional digits. Integer hundredths are persisted; BigDecimal arithmetic computes exact portions and totals before display rounding. Calculations and validation live in `domain`.
- DataStore persists the calorie goal. The initial 2000 kcal is an editable placeholder, not a personalized recommendation.
- Room schema version 1 is exported under `app/schemas`. Future schema changes must include data-preserving migrations.
- No network permission, cloud backup, accounts, food database, camera or AI integration.

## Build and test

Open this directory as a Gradle project in Android Studio and use its bundled JDK. Install Android SDK platform 37, then run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected test requires an emulator/device. It exercises a real Room database: day isolation, reopen persistence, edit/recalculation and deletion. JVM tests cover decimal calculation, rounding, input validation and editor failure/retry/restoration behavior.

For this implementation, debug build, 19 JVM tests, the Room device test and Lint passed on the API 37 emulator. The UI was also checked manually for add, process restart, edit, delete, live totals, light/dark appearance and 200% system font scaling.
