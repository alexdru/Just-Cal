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

Room schema v3 stores original per-100-g values and consumed grams in integer
hundredths, recorded local epoch day, timestamp, source and a portable UUID.
The 1→2 migration assigns UUIDs without replacing existing entries/IDs.
Dates do not shift with time zones; Today refreshes on resume and across midnight.
Portions/totals are exact derived values, rounded for display; no persisted total cache.

Editor restores user edits before OCR/recognition prefills. FoodValidator gates Save;
busy/completion guards prevent duplicates. Date edits save with other changes on
the same ID. Row date changes use a single day-only UPDATE. Duplication transactionally
copies persisted data with a new ID/UUID/timestamp, preserving source and unsaved
editor work. Calendar milliseconds use UTC; chosen dates remain local calendar days.

[Backup formats 1 and 2](DATA_BACKUP.md) preserve portable identity; v2 also carries the catalog graph. Full validation and
preview precede transactional merge; conflicts leave existing data untouched.
Explicit replacement fsyncs an atomic private recovery snapshot before deletion.
Models, diagnostics, photographs and settings are excluded. No destructive migration.

## Reusable foods and matching

FoodCatalogRepository separates reusable identities from diary nutrition snapshots.
Room v3 adds products (UUID, creation time, archive state), immutable product_revisions
(UUID, product/parent UUIDs, complete per-100-g integer hundredths, optional independent
brand/manufacturer/retailer/delivery/package mass and bounded field origins), and
entry_product_links (diary UUID, revision UUID, explicit selection provenance).
Revision parents stay within one product. There is no catalog consumed amount, verified
flag, automatic identity merge, historical promotion or catalog-driven diary recalculation.
Archive hides suggestions and preserves links; Forget removes the catalog graph/links,
leaving every diary snapshot intact. Ordinary edits preserve prior associations without
changing catalog nutrition.

Rebuildable food_search, food_tokens and food_history_members are excluded from backups.
Unlinked history is grouped by full conservative normalized name plus exact nutrition,
with representative UUID, count and last date. Groups are display evidence, not product
identity. The restartable builder processes at most 100 diary rows and 100 revisions per
transaction off-main; UI shows partial indexing. Writes maintain projections transactionally;
imports invalidate them. Membership cascades/changes remove obsolete groups. Normalization
version changes invalidate derived data. Neither historical entries nor their IDs are rewritten.

Retrieval queries token postings before capped prefix/length fuzzy variants (50 per token).
Nutrition range postings can retrieve candidates from two known nonzero nutrients. Candidates
are capped at 200 total, initially reserving 100 per source; unused capacity is filled.
Revision/history hydration is batched. Distinct products show their best retrieved revision,
while different historical recipes stay separate. UI shows three candidates, expandable to
five; alternate immutable versions are fetched in pages of 20. Caps and incomplete indexing
are disclosed; fuzzy prefix retrieval can miss first-letter OCR errors. Editable name/details
and explicit Refresh provide recovery.

FoodMatcher is deterministic Kotlin with cancellation checks. NFKC/ROOT case, ё→е,
mixed-script-only homoglyph repair and a 160-character/32-token comparison budget retain
original display values. Maximum one-to-one weighted Dice uses exact=1, edit distance
one at length ≥5=0.8, distance two at length ≥9=0.6. Numeric/recipe tokens weigh two;
ordinary tokens weigh one. Internal ranking remains floor(40N+15B+5M+5R+5W+30V), capped
at 99, with exact known metadata agreement, package mass within 2%, and nutrient tolerances
max(2 kcal,2%) / max(0.3 g,3%). Major conflicts use max(10 kcal,10%) / max(1 g,15%).
Contradictory identity/known recipe evidence precedes ranking. Possible requires N≥0.55
and informative comparable nutrition or exact known identity; name-only requires N≥0.8
and two distinctive tokens. Missing/basis-unknown/uncertain/conflicting/AI-recovered evidence
remain distinct. Zero-only agreement and AI-only name recovery do not establish identity.
Only Possible, Conflicting and Insufficient exist; scores are never consumer confidence.
These are unvalidated ranking heuristics, not measured population accuracy.

The existing EditorViewModel owns editable fields, manual dirty flags, association,
inspection, retention intent and one SavedStateHandle undo snapshot. Inspection is read-only.
Apply/scan restore/undo change only name and four nutrients after inline confirmation when
manual changes differ. Amount text and date are preserved exactly. Undo is consumed rather
than swapped. Pending prompts are dropped on recreation. Matching uses immutable scan
evidence plus explicitly edited search identity, never copied candidate nutrition; changes
to name/details debounce 400 ms with latest-job cancellation. Expanded/applied results stay
stable until Refresh. Failures cannot block manual entry. No inference call is added.

FoodEditor hosts two retained Navigation 3 child pages with one parent EditorViewModel.
Package scans start on Smart Food Matching: recognition summary, expandable candidates,
adaptive OCR/saved comparison and explicit source selection, current draft status and amount.
Check values contains the editable fields, retention controls, evidence and sole diary Save.
Manual/existing entries start on Check values and can open matching explicitly. Page changes
clear focus/pending prompts, preserve the exact draft/amount/date, and never persist anything.
Native/predictive Back and Activity recreation retain child page/scroll state. Matching rows
can collapse without undoing an explicit application. Both pages use semantic M3/CalMotion
and content clearance behind transparent pinned controls. The screen split was verified with
200 unit and 12 focused connected tests, including navigation, exact amount preservation,
manual draft/page restoration after Activity recreation and bottom-action placement. Debug
assembly, lint and release/ocrCheck Kotlin compilation pass. HD1900/API 30 walkthroughs used
synthetic demo data; predictive-gesture previews on newer Android versions remain unverified.

New package scans default Remember for reuse on. Copying historical values switches retention
off until explicit promotion. Changed applied fields default to diary-only; new revision or
different product requires selection. Duplicate hints offer explicit reuse/keep separate/
diary-only without merging. Entry/product/revision UUIDs and creation time survive retries;
the complete save is one transaction and mismatched reuse of a creation identity is rejected.
Disappearing selections fall back to diary-only while preserving applied values. Diagnostics
label catalog/history substitutions separately from later manual corrections.

Device evidence uses synthetic scan evidence and isolated Room databases. EN/light and
RU/dark comparison screenshots cover narrow layouts and 100/150% font sizes. Permissioned
real scan/history walkthroughs, full spoken TalkBack, larger-window/dynamic-color and
additional-device validation remain open. Initial latency is measured by
FoodMatchingPerformanceTest on HD1900/API 30, debug in-memory Room, with 10k products and
3k history rows: cold projection construction 14,123 ms; 20 warm requests had median 565 ms,
p95 614 ms and maximum 615 ms (median retrieval 340 ms, scoring 226 ms). Batched hydration
and indexed revision lookup reduced an earlier 3,006 ms median, but the 100 ms warm p95
remains unmet. These synthetic debug measurements do not establish release-device latency
or matching accuracy. Current verification: 200 unit tests, 27 selected connected tests,
debug assembly and lint; connected tests include migrations, full backup recovery, editor
state/actions and bundled OCR on synthetic RU/EN pixels.

Debug Settings also exposes Smart Food Matching — demo via a debug-source-set Activity.
Four synthetic scenarios use the production EditorViewModel, matcher and Room repositories:
incomplete OCR, conflicting nutrition, diary history and manual edits/undo. Each scenario
editor owns a fresh in-memory database; Save/Archive/Forget affect only that database.
Activity recreation retains the session and editor draft; leaving the editor cancels/joins
its jobs and closes its database off-main. Release/ocrCheck compile empty Settings hooks
and include neither the Activity nor its fixtures. No OCR/model runtime is invoked by demo.

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
Optional package inference uses a non-exported bound service in :package_ai, with metadata-only Messenger IPC and the same admission/prompt/crop/consumer. OCR stays in the parent and is checkpointed first. The client holds the parent runtime lease, so Playground/meal loading and removal cannot overlap the worker. Stages are acknowledged only after diagnostic checkpointing. Normal completion joins native disposal; cancellation allows 15 seconds to join, then unbinding terminates only the single-use worker and waits for Binder death. The request deadline is 120 seconds. Worker death/failure/deadline returns OCR after cleanup; caller cancellation propagates.
Meal support validates isFood/singleDish/name only; refusal retains bounded diagnostic
evidence and opens no empty successful editor. Numerical meal estimates and consumed
mass from photos are disabled. Meal names retain MANUAL source, preserving backup schema.
Only explicit editor Save writes food.

## Models, runtime and evidence

The bundled catalog pins source revision, bytes/hash, license and task admissions.
SAF import and explicit HTTPS transfers stream into private noBackupFilesDir staging
and atomically install validated immutable UUID directories. Legacy metadata remains
readable/lazily validated. No automatic download, selection or inference.
Range/ETag checkpoints, pause/cancel and process restart are separate from runtime state.
Only queued transfers may enter the writer; late callbacks cannot reinstall completed work.
A restart truncates/fsyncs the partial file before persisting its new ETag. Pause cancels
work and disconnects the active socket off-main before joining the writer; cancelled I/O
remains paused rather than becoming a network failure. API 34+ uses UIDT JobService,
API 30–33 a non-sticky dataSync service.
Full network/OS transfer validation remains open; see [AI closeout](AI_REDESIGN_PLAN.md).

LiteRT-LM 0.18.1 ModelInfo is inspected/closed off-main before Engine creation.
BackendSelector intersects actual TEXT/VISION capabilities. One process-wide lease
covers production, Playground and removal. Unknown artifacts get a fresh Engine per
independent run. At most one allowed recoverable init fallback is attempted; malformed
artifacts, memory failure and cancellation never retry on another backend.
Cache identity includes artifact/runtime/backend/device/profile. Cancellation waits for
native terminal completion before disposal; an interrupted-operation marker prevents
silent assumptions after process death. A deadline cannot safely free stuck in-process JNI. Package inference contains that case by terminating its separate worker process; meal and Playground remain in-process.

Playground retains LocalAiLab and an independent session without DiaryRepository or
editor navigation. [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md) describes current tasks and evidence.
Success/valid JSON/smoke and task quality are distinct; admission is reviewed and versioned.

[Diagnostics](OCR_DIAGNOSTICS.md) retain bounded local OCR/AI evidence in a separate
backup-excluded database. Optional JSON fields preserve older rows; failures never
block diary Save. Changed editor values are corrections, unchanged values are not
ground truth. Consumer reports include retained OCR details; sanitized AI exports omit
prompts/raw responses. Production scan photos are normalized into a private bounded attachment store by default. Photos/native tombstones require an explicit export option; clearing diagnostics removes attachments too.

## Build and direct updates

version.properties is the editable version source, independent of Git.
[RELEASING.md](RELEASING.md) owns trusted-source/signing/tag/checksum/immutable-publication
procedures and current-release notes. Debug and ocrCheck use distinct app IDs and debug
signing; release requires the persistent key.

[GitHub updates](APP_UPDATES.md) are isolated from diary/OCR state. Bounded HTTPS metadata/
APK delivery accepts only the official release asset/digest. Package/version/current
signer/SDK checks precede a scoped FileProvider URI and explicit Android installer.
Automatic prompts wait for an eligible root state; no silent install.
