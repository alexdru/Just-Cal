# Just Cal — Agent Instructions

## Product and scope

GPL-3.0-or-later; contributions must remain compatible. Just Cal is a privacy-first,
offline-first Android calorie diary. Optimize for low-effort food logging and native UX.
Implement only the requested milestone; future documentation is context, not permission.
Inspect current code before changing it. Preserve unrelated work. Explain significant,
difficult-to-reverse tradeoffs; choose simple solutions for small reversible decisions.

Without an explicit request, do not add backends, cloud AI, accounts, external food
databases or barcode scanning. Diary, OCR and inference must work offline. Network use
is limited to configured update checks and explicit model transfers.

## Architecture and data

Use the single app module, Kotlin, Compose Material 3/Expressive, Navigation 3, Room,
Hilt, Coroutines and StateFlow. Prefer current stable Android approaches when useful;
do not add dependencies or speculative layers when existing APIs suffice. Organize by
responsibility: ui, data, domain, camera, ai and ocr. Keep UI, persistence, deterministic
logic and inference separate. Keep repositories between Room and UI.

Nutrition arithmetic belongs in deterministic Kotlin with exact decimal/integer units.
Distinguish per-100-g values, package/serving weight and consumed amount. Validate
untrusted recognition before persistence; review is editable and Save is always explicit.
Preserve Room migrations, portable IDs, exported schemas and backup compatibility.
Never use destructive production migrations without an explicit request.
Current contracts: [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## UI

Read [DESIGN.md](DESIGN.md) before UI edits and use relevant design skills. It is the
visual source of truth; refinements need design approval. UX quality is functional:
consider hierarchy, typography, spacing, motion, accessibility, font scaling, EN/RU,
dark appearance and system personalization. Use semantic colors and shared components;
avoid one-off cards, gradients, borders and decorative motion. Validate major changes
visually on a device/emulator.

- Preserve predictive Back and independent retained top-level Navigation 3 stacks.
  History and direct calendar navigation converge on Day Detail.
- Every updated screen is edge-to-edge. Scroll viewports extend behind bars and
  floating controls; put safe-area/app-bar/final spacing inside content, consume
  insets once, and clear focused fields above controls and IME.
- Use shared Expressive segmented rows with narrow surface gaps, connected corners
  and meaningful Material Symbols; no painted row dividers.
- Keep the floating pill content-sized and centered in a transparent host, with a
  separate FAB above/right. Reserve the action row when Settings hides the FAB.
  Pinned bottom actions also use transparent hosts and measured content clearance.
- Brightness and color style stay independent in the existing DataStore. Just Cal is
  default; Material You uses dynamic colors on API 31+ and branded fallback below it.
  Macro goals are independently optional; clearing removes the goal, never shows zero.
- Language uses Android application locales, AppCompatActivity/autoStoreLocales on
  API 30–32 and AGP-generated supported EN/RU locale config. No duplicate locale key.
  Keep matching EN/RU strings, language-independent routes and localized dates/numbers.
- Telegram is a geometry/interaction reference only; never copy its code or styling.

## Images and AI

- Package/meal scans share local CameraX/Photo Picker acquisition with mode/date retained.
  Ask for camera permission only for Take photo; use no broad media/storage permission.
  Treat external images as URIs, never native paths. Keep owned files private and release
  them on flow exit. Decode off-main within the shared bounded image boundary; no large
  Bitmaps or camera/native runtime objects in ViewModels or saved state.
- Generic decoding/orientation belongs in camera; OCR/model preprocessing belongs in
  its adapter. Preserve OCR raw evidence, basis, independent mass and missing values.
  Bundled hashes, dictionaries and notices: [PACKAGE_OCR.md](docs/PACKAGE_OCR.md).
- PP-OCRv5 stays the package baseline. Optional Gemma fills only admitted missing macros;
  failure preserves OCR. Meal assistance is admitted name-only; numerical meal estimates
  and photo-derived consumed weight stay disabled. Offer Models/manual recovery.
- FoodRecognitionRouter requires exact artifact/runtime/task quality admission.
  Successful inference or valid JSON never proves quality. No automatic model download
  or task selection. Playground retains LocalAiLab, an independent session and no
  diary-writing capability. Keep raw responses, explicit nulls and measured metrics.
- SAF imports and explicit downloads go into private backup-excluded storage; never
  bundle multi-GB weights or pass content URIs to JNI. Keep native types in adapters.
  Initialize/infer/close off-main. Cancellation must finish native work before disposal.
  Preserve the process-wide runtime lease, bounded images and explicit backend failures.
- New manual entries have empty consumed amount and one-shot empty-name focus.
  Edits retain saved amount without initial focus. Recognition always opens review.
- Preserve model metadata, task-quality evidence, reproducible fixtures and license notices.
  AI status and open gates: [AI_REDESIGN_PLAN.md](docs/AI_REDESIGN_PLAN.md).

## Verification and cleanup

Use Android Studio project information, diagnostics, terminal and SDK/device tools.
Use its file tools to inspect/edit files and respect unsaved buffers. Do not clean
unrelated code. Remove only confirmed obsolete content; use Git history for completed
reports. Never delete a test merely because production does not reference it.

Test calculations, parsing/validation, meaningful database behavior and non-trivial
state changes. Run the narrowest affected tests first. Before completing substantial
work: compile, run relevant/full practical tests, inspect IDE/build diagnostics and
exercise the workflow when practical. Resolve introduced errors/warnings and state
remaining gaps precisely. Private photos, large model transfers and AI benchmarks
require explicit scope; skipped opt-in tests are not quality passes.

## Versions, releases and completion

- Edit version.properties explicitly, never derive/rewrite it from Git. Once per
  completed application-changing task, increment patch for fixes or minor for features
  and increase versionCode, unless already supplied/bumped for that task. Retain it
  through retries; discussion/documentation-only work needs no bump.
- Maintain [RELEASE_HIGHLIGHTS.md](docs/RELEASE_HIGHLIGHTS.md) for meaningful user-facing
  changes: replace the small current-release draft, retain 3–6 factual concise bullets,
  update its version and cite included evidence commits. Never describe unfinished
  features as released. See [RELEASING.md](docs/RELEASING.md) for format and verification.
- Release tags equal v + versionName; published tags/releases are immutable.
  Production APK/AAB use the persistent release key. Never commit keys, credentials
  or generated binaries. Debug CI needs no secrets; use the Gradle Wrapper.
  Preserve signing/ref/version/checksum/recovery safeguards and adaptive/monochrome icons.
- Do not automatically commit, push, tag or publish.
- Every completed coding task ends with a concise implementation summary, build/test
  verification and remaining limitations, followed by exactly one short English Git
  commit message. Use imperative mood, describe the main change, preferably at most
  72 characters, and omit version numbers unless necessary.
