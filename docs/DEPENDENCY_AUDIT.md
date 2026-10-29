# Dependency audit — 2026-10-28

App version: 0.17.2 (45). The user's AGP update to **9.5.0-rc02** was verified, then advanced to the newly published **9.5.0 stable**. Android Studio dependency version lookup was run for every artifact in the version catalog and the additional Room, Hilt and Foojay Gradle plugins. Release channels were checked separately; availability alone is not a reason to replace a validated inference runtime.

## Toolchain and selected updates

[AGP 9.5 compatibility](https://developer.android.com/build/releases/agp-9-5-0-release-notes) lists minimum Gradle 9.6.0, JDK 17 and maximum supported API 37. The project uses Wrapper 9.8.0, Java/Kotlin JVM target 17 and compile/target SDK 37. IDE sync succeeded with AGP 9.5.0-rc02 before and after the library edits. The initial AGP runtime lookup still reported stable 9.4.1, whereas the plugin-marker lookup and IDE inspection reported 9.5.0. A direct HEAD request to the [Google Maven 9.5.0 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.0/gradle-9.5.0.pom) returned HTTP 200 with Last-Modified 2026-10-28 14:11:57 GMT, confirming today's publication. The final version is therefore 9.5.0.

| Dependency | Before | Selected | Reason |
| --- | --- | --- | --- |
| Android Gradle Plugin | 9.5.0-rc02 | **9.5.0** | Final stable release published on the audit date |
| Kotlin Compose/serialization plugins | 2.4.20 | **2.4.21** | Stable bug-fix release; both compiler plugins share the version |
| Preferences DataStore | 1.2.1 | **1.3.0** | Current stable; existing preferences files, keys and APIs retained |
| kotlinx.serialization JSON | 1.11.0 | **1.12.0** | Current stable; validate existing backup, diagnostic and model JSON contracts |

Kotlin Android support remains built into AGP; no legacy Android Kotlin plugin was added. Release version was incremented once for these application dependency updates.

## Retained dependencies

| Family | Retained version | Audit decision |
| --- | --- | --- |
| Compose BOM | 2026.09.00 | Current stable BOM; resolved UI/foundation artifacts observed at 1.12.1 |
| Material 3 | **1.5.0-beta02** | Current beta; preserves the approved Expressive target. Stable remains 1.4.0 |
| Core / Splashscreen / AppCompat | 1.19.1 / 1.2.0 / 1.8.0 | Current stable |
| Activity / Lifecycle / Navigation 3 | 1.13.0 / 2.11.0 / 1.2.0 | Current stable; newer alpha channels do not require an upgrade |
| CameraX | 1.6.2 | Current stable; 1.7.0-rc01 is a separate preview |
| Room runtime/compiler/plugin | 2.8.5 | Current stable; no schema or migration changes |
| Dagger Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 | Current stable |
| Coroutines / KSP | 1.11.0 / 2.3.12 | Current stable |
| LiteRT-LM | **0.18.1** | Current release in the lookup and the runtime bound to existing model-quality evidence |
| ONNX Runtime Android / OpenCV | **1.23.2 / 4.12.0** | Deliberate pins validated with bundled OCR exports; newer 1.31.1 / 5.0.0.1 were found, but require dedicated native/OCR regression and packaging checks |
| JUnit / AndroidX Test ext JUnit | 4.13.2 / 1.3.0 | Retain stable |
| AndroidX Test runner/core / Espresso | 1.7.0 / 3.7.0 | Retain stable; newer test artifacts are alpha |
| Foojay resolver | 1.0.0 | Current stable |

The Compose BOM manages common Compose artifact versions while Material 3 is pinned independently. Existing successful model runs do not establish OCR accuracy on a new native runtime; those two upgrades are intentionally outside this general toolchain update.

## Verification

Focused tests passed first: **48 passed, 0 failed, 0 skipped** across AI, backup serialization, diagnostics and settings domain tests.

On AGP 9.5.0-rc02 with the updated libraries, **156 unit tests passed** and debug app, AndroidTest APK, minified ocrCheck and lint tasks completed successfully. On final **AGP 9.5.0**, sync succeeded again and the full repeat passed: **156 unit tests, 0 failed, 0 skipped**, plus `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleOcrCheck` and `:app:lintDebug`. The minified `ocrCheck` build is debug-signed validation, not a production release.

No new model inference/quality benchmark or on-device workflow was performed for this dependency audit. Existing 2026-10-22–23 quality measurements remain historical, exact-artifact evidence; package VLM admission remains closed. See [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md#which-model-is-used-today--2026-10-28) for the actual model roles and Playground steps.

For terminal builds after this IDE update, use the installed Android Studio JBR:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleOcrCheck :app:lintDebug
```

The shell has no standalone Java runtime configured; IDE Gradle builds already use their configured JBR. The previous `Android Studio Preview.app` path is obsolete on this machine.

## Primary references checked

- [AGP API release channels](https://developer.android.com/reference/tools/gradle-api) and [AGP 9.5 compatibility](https://developer.android.com/build/releases/agp-9-5-0-release-notes).
- [Kotlin release history](https://kotlinlang.org/docs/releases.html).
- [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore).
- [kotlinx.serialization 1.12.0 release](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0).
- [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3).
