# Licensing review

Review date: 2026-10-07. Project license: GPL-3.0-or-later.

## Scope and findings

Reviewed the version catalog, app dependencies and packaging configuration, resource inventory, and existing documentation. This is a source-level review, not a complete audit of every resolved transitive dependency or native binary.

- AndroidX (including Compose, Material 3, CameraX, Navigation 3, Room and DataStore), Dagger/Hilt, Kotlin libraries and LiteRT-LM use Apache-2.0 upstream. No conflict with GPL v3 was identified for these declared runtime dependencies. Their original license, copyright and applicable NOTICE obligations remain.
- LiteRT-LM v0.17.1: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/LICENSE . The Android runtime includes native components; verify their bundled third-party notices as well as the top-level Apache license before redistribution.
- JUnit 4 is a test dependency under EPL-1.0, not an application runtime dependency. Android test/tooling dependencies are not automatically covered by the application license.
- Resources are local XML vector icons, theme values and Android resource files; no bundled font or AI model was found in the main resource inventory. Icon paths have no provenance/attribution metadata. Confirm whether any were copied or adapted from an external icon set and preserve its required notices. The launcher artwork's provenance should also be confirmed by the maintainer.
- Gemma model weights are imported separately through SAF, not bundled. Gemma has separate terms: https://ai.google.dev/gemma/terms . GPL licensing of Just Cal does not grant rights to redistribute these weights. Check the exact model's terms and required notices before any future model distribution.

## Manual attention before binary distribution

1. Produce and verify a complete resolved runtime dependency/native-component license inventory for the release APK/AAB. Retain all required copyright, license and upstream NOTICE texts; the current build excludes `/META-INF/{AL2.0,LGPL2.1}` and has no dedicated attribution viewer or generated notice bundle. A project NOTICE is not a replacement for those notices.
2. Confirm icon/artwork provenance and any existing contributors' rights to license their work under GPL-3.0-or-later.
3. Distribute the GPL text and corresponding source for each published binary, including its build scripts and the exact release revision, in accordance with GPL requirements. A moving repository default branch alone does not identify a binary's corresponding source.

Settings About displays the project license and source link. No Open source licenses row was added because the project has no existing license inventory/viewer; an incomplete list would imply coverage it does not provide.
