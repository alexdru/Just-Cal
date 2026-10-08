# Just Cal — Agent Instructions

## Licensing

Just Cal is licensed under GPL-3.0-or-later. Contributed code must remain compatible with this license.

## Project

Just Cal is a cutting-edge, privacy-first, offline-first Android calorie tracker focused on making food logging nearly effortless through on-device intelligence and carefully designed native Android UX.

Its main future feature is fast calorie logging from photos of packaged prepared food using on-device AI.

The application should remain usable without an internet connection.

Unless explicitly requested, do not introduce:
- backend services;
- cloud AI APIs;
- user accounts;
- external food databases;
- barcode scanning.

## Product and technology direction

Just Cal is intended to be a cutting-edge native Android application with first-class UI/UX.

"Cutting-edge" means using modern Android platform capabilities and current production-ready Jetpack technologies when they materially improve the product. It does not mean adopting experimental dependencies without a concrete benefit.

Prefer modern stable Android approaches over legacy compatibility patterns when the project's supported Android versions allow it.

The application should feel native to current-generation Android rather than like a generic cross-platform or legacy Material application.

For UI work:

- use Jetpack Compose;
- use Material 3 and Material 3 Expressive intentionally;
- prefer Navigation 3 for Compose navigation;
- design edge-to-edge;
- preserve predictive-back behavior;
- handle system insets correctly;
- use responsive, meaningful motion;
- treat typography, spacing, shapes and hierarchy as part of the product;
- support accessibility, font scaling, dark appearance and appropriate system personalization.

Do not blindly use default Material components without considering the resulting hierarchy and interaction design.

Do not overuse cards, containers, gradients, borders or animations.

Prefer a small coherent design system over one-off styling in individual screens.

UI/UX quality is a functional requirement of Just Cal, not a final polishing phase.

The primary UX objective is to minimize the effort required to log food.

## Technology

The Android application uses:

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Hilt
- Kotlin Coroutines
- StateFlow

Use current Android APIs and established project dependencies.

Do not add a new dependency when the platform or an existing dependency already solves the problem adequately.

## Architecture

Keep the architecture simple and appropriate for the current project size.

Use a single `app` Gradle module unless there is a demonstrated reason to split it.

Prefer packages organized by responsibility, including:

- `ui`
- `data`
- `domain`
- `camera`
- `ai`

Keep UI, persistence, deterministic business logic, and AI inference separated.

Do not introduce generic base classes, speculative abstractions, or architectural layers that have no current use.

Prefer explicit, readable Kotlin over clever abstractions.

## Nutrition calculations

Nutrition calculations must be deterministic Kotlin code.

AI may extract or estimate input values, but must not be responsible for arithmetic used to calculate diary totals.

Keep units explicit.

Distinguish between:
- values per 100 g;
- serving/package weight;
- amount actually eaten.

Avoid floating-point assumptions that can produce incorrect displayed nutrition values.

## AI

Keep experimental on-device AI separate from the core diary functionality.

Keep AI implementations behind a small application-facing interface so that models and runtimes can be replaced without changing diary or UI logic.

Do not couple domain models to a specific AI framework.

AI-produced nutrition information must be treated as untrusted input and validated before persistence.

Users must be able to review and correct recognized values before they are added to the diary.

- Keep Local AI Lab experimental and independent from production Add Food until that integration is explicitly requested. Lab results must never create diary entries.
- Import external models through SAF into app-private, backup-excluded storage; never bundle multi-GB models or pass content URIs as native filesystem paths.
- Keep native AI engine/conversation types inside the runtime adapter. Initialize, infer and close off the main thread; cancellation must finish native processing before freeing resources.
- Reuse the generic bounded image boundary. Keep model preprocessing in the AI adapter and large Bitmaps out of ViewModels/saved state.
- Report backend failures explicitly and benchmark only measured values. JSON-constrained output remains untrusted nutrition input; preserve raw responses and explicit missing values.

## UI

Optimize for fast calorie logging and low interaction count.

Use Material 3 and Jetpack Compose.

Support system light and dark themes.

Prefer clear native Android interactions over custom UI components unless the custom interaction materially improves the workflow.

Avoid premature visual polish when implementing infrastructure or domain work.

- Telegram is a navigation geometry and interaction reference only; do not copy its source or styling.
- Keep Just Cal's floating navigation content-sized and horizontally centered in a transparent host; its separate global FAB sits above/right. Reserve the action row when Settings hides the FAB, and reserve enough screen content space for all floating controls.
- Keep pinned bottom actions in transparent hosts. Scroll content behind floating controls with end padding for the final item; focus scrolling must clear the controls and keyboard.
- Retain each top-level Navigation 3 stack when switching tabs.
- History selection and direct “go to day” navigation must converge on the same Day Detail flow.
- Macro goals are independently optional; clearing a goal removes it. Never display an absent goal as a zero target.
- Keep brightness and color style independent in the existing DataStore. Just Cal is the branded default; Material You uses dynamic colors on Android 12+ and the branded fallback below that. Use semantic theme colors in components.
- Language belongs to Android application locales, never a duplicate DataStore key. Retain AppCompatActivity/autoStoreLocales for Android 11/12 and AGP-generated locale config filtered to supported resource languages.
- Keep user-visible strings in matching English/Russian resources, route identifiers language-independent, and displayed dates/nutrition numbers locale-aware.

## Image acquisition

- Package and meal scans share the local-only CameraX / Photo Picker pipeline; always retain scan mode and target diary date.
- Use Photo Picker for individual images, never broad media/storage permissions. Request camera access only for Take photo.
- Treat external images as URIs; never resolve content URIs to filesystem paths. Keep owned temporary files private and release them when their flow ends.
- Keep Bitmaps and camera resources out of ViewModels and saved navigation state. Decode off the main thread within the image budget.
- Generic orientation/decoding belongs in the image boundary; model-specific preprocessing and inference belong in the future AI layer.
- Use photo confirms the reviewed image; it must not invent nutrition or add a diary entry.

## Persistence

Use Room for persistent application data.

Database migrations must preserve existing user data.

Do not use destructive migration strategies for production schema changes unless explicitly requested.

Repositories should isolate Room implementation details from UI code.

## Testing

Write tests for:
- nutrition calculations;
- parsing and validation;
- database behavior where meaningful;
- non-trivial state transitions.

Do not add tests that only repeat trivial implementation details.

When changing existing behavior, run the narrowest relevant tests first.

Before completing a substantial task, run the relevant build and test commands.

## Working with Android Studio

Use IDE-provided project information, build diagnostics, Android SDK tools, terminal access, and emulator controls when available.

Inspect existing code before creating new abstractions or changing architecture.

Resolve warnings or errors introduced by your changes.

Do not modify unrelated files solely for cleanup.

## Scope control

Implement only the requested milestone.

Future features described in documentation are context, not permission to implement them.

When a request exposes a significant architectural tradeoff, explain the tradeoff briefly before committing to a difficult-to-reverse design.

For small reversible decisions, choose the simplest reasonable implementation and proceed.

## Release engineering

- Edit application versions explicitly in `version.properties`; never derive or rewrite them from Git.
- Automatically update the version once per completed application-changing task unless the user has already supplied or bumped the version for that task. Use a patch increment for fixes and a minor increment for new features; increase `versionCode` with each version change. Keep that version during implementation and verification retries. Do not bump versions for discussion or documentation-only changes.
- Release tags must equal `v` plus `versionName`. Increase `versionCode` for every published Android release and preserve published tags.
- Release APK/AAB must be signed with the persistent release key; never publish debug or unsigned builds as production releases.
- Never commit keystores, signing credentials, encoded keys or generated release binaries. Debug CI must work without release secrets.
- Debug CI artifacts and production GitHub Release assets are distinct; create release tags only after successful build, signature and version verification.
- Preserve adaptive foreground/background and explicit monochrome launcher support.
- CI and releases use the Gradle Wrapper; ordinary CI must work without release signing secrets.
- Keep release procedures in `docs/RELEASING.md`.

## Completion

Before declaring a coding task complete:

1. verify the changed code compiles;
2. run relevant tests;
3. inspect relevant IDE/build diagnostics;
4. verify the affected workflow when practical;
5. summarize the changes and any unresolved issues.