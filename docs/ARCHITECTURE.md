# Just Cal architecture

## Boundaries

One Android app module, minSdk 30, compile/target 37, arm64-v8a. Kotlin/Compose
screen state uses Hilt ViewModels, Coroutines and lifecycle-aware StateFlow.
Room stays behind DiaryRepository; deterministic validation/calculation lives in
domain. Native inference types stay inside adapters. No backend or cloud inference.

| Area | Owners and contract |
| --- | --- |
| UI/navigation | ui, Navigation 3 keys/stacks, entry-scoped ViewModels and SavedStateHandle drafts |
| Diary | data/DiaryDatabase, RoomDiaryRepository and domain/DiaryRepository |
| Exact nutrition | domain/FoodInput and Nutrition; integer hundredths and BigDecimal |
| Local images | camera/ImagePreprocessor, CameraCapture and serializable ScanRequest/PreparedImage references |
| OCR | ocr/PackageOcrEngine, PaddlePackageOcrEngine, NutritionLabelParser and PackageScanPrefill |
| Optional AI | ai/LocalModelStore, ModelDownloadRepository, LocalAiSession and FoodRecognitionRouter |
| Diagnostics | separate private Room database, OcrDiagnosticSession and explicit report exporter |
| Updates | AppUpdateRepository, GitHubUpdateRepository and activity-scoped UpdateViewModel |

## Diary and backup

Room schema v2 stores original per-100-g values and consumed grams in integer
hundredths, recorded local epoch day, timestamp, source and a portable UUID.
The 1→2 migration assigns UUIDs without replacing existing entries/IDs.
Dates do not shift with time zones; Today refreshes on resume and across midnight.
Portions/totals are exact derived values, rounded for display; no persisted total cache.

Editor restores user edits before OCR/recognition prefills. FoodValidator gates Save;
busy/completion guards prevent duplicates. Date edits save with other changes on
the same ID. Row date changes use a single day-only UPDATE. Duplication transactionally
copies persisted data with a new ID/UUID/timestamp, preserving source and unsaved
editor work. Calendar milliseconds use UTC; chosen dates remain local calendar days.

[Backup format 1](DATA_BACKUP.md) preserves portable identity. Full validation and
preview precede transactional merge; conflicts leave existing data untouched.
Explicit replacement fsyncs an atomic private recovery snapshot before deletion.
Models, diagnostics, photographs and settings are excluded. No destructive migration.

## Navigation, design and settings

Home, Diary and Settings retain independent Navigation 3 stacks, ViewModels and scroll
state in one root HorizontalPager. Root taps/swipes share pager state. Inactive pages
are capped at STARTED, hidden from accessibility and cannot intercept Back. Children
retain their gestures/predictive Back. History/calendar converge on Day Detail.
The launcher captures today or the viewed day and retains it through acquisition/editor.

[DESIGN.md](../DESIGN.md) owns visual tokens and behavior. Theme.kt supplies branded/
dynamic semantic palettes, spacing, widths and shapes; Typography.kt owns Roboto Flex;
CalMotion supplies shared spatial/effects roles. Expressive components and GroupedList
supply connected rows with surface gaps. Material3 is pinned independently of the BOM
in [libs.versions.toml](../gradle/libs.versions.toml). The Wrapper is pinned to Gradle
9.8.0 with JetBrains daemon JVM 25 criteria; application Java/Kotlin target remains 17.

Floating navigation is centered/content-sized with a separate FAB above/right.
The transparent host measures content clearance, retaining the FAB row in Settings.
The pill stays behind IME. Pinned editor/review/report actions use transparent hosts;
scroll viewports extend behind controls/bars and reserve clearance inside content.

Brightness and palette are independent DataStore choices. Settings serializes
key-specific writes, applies selections immediately and debounces valid goal edits
550 ms. Invalid drafts survive tabs/recreation. Optional macro clearing removes only
its key. Hidden profile-name storage is retained. No global Settings Save.
Language uses AppCompat application locales, AndroidX storage on API 30–32 and platform
storage on API 33+, with AGP-filtered EN/RU locale config and locale-aware formatting.

MainActivity loads preferences behind the native SplashScreen. Locale/uiMode changes
update the existing composition. ThemeAnimation captures one native-resolution old
GraphicsLayer frame (≤24 MiB) after control morphs settle, commits the palette once and
replays a 600 ms radial reveal. Appearance draw copies share current selection/motion
but no semantics or writes. Rapid changes replace the visible composite; disabled
animations skip capture and oversized/failed captures use a fade. No per-frame palette
recomposition. Portrait is requested; large-screen Android can override it.

## Image and recognition lifecycle

ScanRequest retains mode/date. PreparedImage carries private URI, source, dimensions
and MIME, without Bitmap/JNI state. CameraX owns resumed-entry use cases, stable rear
physical-camera selection, a shared 4:3 preview/capture viewport, rotation and AF/AE.
Photo Picker needs no storage permission; CAMERA is requested only for taking a photo.

ImagePreprocessor privately snapshots external URIs, validates input ≤64 MiB and
decodes EXIF-aware upright sRGB software pixels off-main, bounded to 12 MP/4096 px.
Review uses a smaller preview budget. Acquisition transfers ownership to review;
exit/replacement cancels and joins work before releasing images. Interrupted/missing
cache files have retake/reselect recovery; orphan files expire after 24 hours.
Original gallery files are never changed.

FoodRecognitionRouter always runs bundled PP-OCRv5 for packages. Deterministic
parsing retains raw evidence, basis, independent mass and null uncertainty. OCR net
mass can initialize editable amount; manual entries start empty with one-shot name
focus. See [PACKAGE_OCR.md](PACKAGE_OCR.md) for the native/preprocessing contract.

Package model readiness, Models selection and production use the same
ModelDescriptor.supportsRecognition checks: exact hash/runtime/task admission plus
container/context/image-backend compatibility. A prior image probe is optional runtime
evidence; a fresh admitted installation can be selected before its first scan.
Per-photo assistance state survives review recreation/Models navigation but never
changes the global selection. Opt-out bypasses optional model lookup/load/inference.

Gemma only fills blank P/F/C on the same explicit 100-g basis from nutrition-crop-v2.
It preserves energy/name/mass/nonempty OCR fields, enforces individual/combined macro
bounds and retains raw evidence. The separate transcription consumer preserves
conflicts for review and separate VLM mass, but Paddle has no production admission.
Optional-model failure/deadline returns OCR after cleanup; caller cancellation propagates.
Meal support validates isFood/singleDish/name only; refusal retains bounded diagnostic
evidence and opens no empty successful editor. Numerical meal estimates and consumed
mass from photos are disabled. Meal names retain MANUAL source, preserving backup schema.
Only explicit editor Save writes food.

## Models, runtime and evidence

The bundled catalog pins source revision, bytes/hash, license and task admissions.
SAF import and explicit HTTPS transfers stream into private noBackupFilesDir staging
and atomically install validated immutable UUID directories. Legacy metadata remains
readable/lazily validated. No automatic download, selection or inference.
Range/ETag checkpoints, pause/cancel and process restart are separate from runtime state;
API 34+ uses UIDT JobService, API 30–33 a non-sticky dataSync service.
Full network/OS transfer validation remains open; see [AI closeout](AI_REDESIGN_PLAN.md).

LiteRT-LM 0.18.1 ModelInfo is inspected/closed off-main before Engine creation.
BackendSelector intersects actual TEXT/VISION capabilities. One process-wide lease
covers production, Playground and removal. Unknown artifacts get a fresh Engine per
independent run. At most one allowed recoverable init fallback is attempted; malformed
artifacts, memory failure and cancellation never retry on another backend.
Cache identity includes artifact/runtime/backend/device/profile. Cancellation waits for
native terminal completion before disposal; an interrupted-operation marker prevents
silent assumptions after process death. A deadline cannot safely free stuck JNI.

Playground retains LocalAiLab and an independent session without DiaryRepository or
editor navigation. [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md) describes current tasks and evidence.
Success/valid JSON/smoke and task quality are distinct; admission is reviewed and versioned.

[Diagnostics](OCR_DIAGNOSTICS.md) retain bounded local OCR/AI evidence in a separate
backup-excluded database. Optional JSON fields preserve older rows; failures never
block diary Save. Changed editor values are corrections, unchanged values are not
ground truth. Consumer reports include retained OCR details; sanitized AI exports omit
prompts/raw responses. Photos require explicit export selection.

## Build and direct updates

version.properties is the editable version source, independent of Git.
[RELEASING.md](RELEASING.md) owns trusted-source/signing/tag/checksum/immutable-publication
procedures and current-release notes. Debug and ocrCheck use distinct app IDs and debug
signing; release requires the persistent key.

[GitHub updates](APP_UPDATES.md) are isolated from diary/OCR state. Bounded HTTPS metadata/
APK delivery accepts only the official release asset/digest. Package/version/current
signer/SDK checks precede a scoped FileProvider URI and explicit Android installer.
Automatic prompts wait for an eligible root state; no silent install.
