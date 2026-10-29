# AI redesign closeout and validation gates

The implementation plan is **closed as an implementation roadmap**. It is not a
release-quality certificate: transfers, device coverage and native-memory qualification
remain open. This matrix replaces the original proposals with current contracts checked
against source, tests and retained evidence. The 2026-10-29 reliability verification
updated transfer/lifecycle boundaries below; it used small network fixtures and existing
weights, with no new model installation or recognition-quality admission.

Status vocabulary:

- **Implemented and verified**: the named bounded behavior has tests/device evidence;
  this does not imply all devices or arbitrary recognition accuracy.
- **Implemented but incompletely verified**: mechanism exists; the stated acceptance
  requirement still needs evidence.
- **Experimental / not production-admitted**: available for investigation, without task admission.
- **Not implemented / intentionally deferred**: outside current product capability.

## Original requirements and disposition

Kotlin paths refer to app/src/main/java/com/justcal/app. Existing unit and opt-in device
tests remain under app/src/test and app/src/androidTest; private photo originals remain
ignored. Prior completed reports remain in Git history.

| Original requirement | Classification | Current implementation and verification boundary |
| --- | --- | --- |
| Single module, Hilt/StateFlow/Room, shared CameraX/Picker, Navigation 3, offline core | Implemented and verified | Existing architecture retained; build/unit and recorded diary/photo-flow regressions. No cloud inference or external database. |
| LiteRT-LM 0.18.1 spike; ARM64/API 30 packaging; exact Gemma/Paddle hashes and real images | Implemented and verified | LiteRtLocalAiEngine, ModelCatalog/asset metadata; both exact artifacts ran on OnePlus 7T/API 30. Runtime success is separate from task quality. |
| ModelInfo type/vision/context/min-runtime and separate TEXT/VISION backend checks | Implemented and verified | LiteRtModelInspector and ModelDescriptor; malformed/text-only/unsupported containers rejected. Current Gemma VISION is CPU-only; historical GPU runs do not override it. |
| Immutable metadata: source/revision/bytes/hash/license, profile/context/output, installed UUID and validation/probe | Implemented and verified | ModelDescriptor, ai_models.json and LocalModelStore. Not every originally proposed metadata field is present; see next row. |
| Explicit per-artifact measured RAM/cache estimates, device exclusion lists and fully configurable backend-pair policy | Not implemented / intentionally deferred | Storage reserve and runtime memory checks exist; no measured universal RAM requirement or broad device matrix. BackendSelector currently intersects same CPU/CPU or GPU/GPU capabilities; mixed pairs are deferred. |
| Bounded SAF import, private no-backup staging, stream/hash/container validation, atomic commit, preserve original | Implemented and verified | ModelFilesTest plus recorded physical SAF import. No content URI is passed as a native path. Invalid input/cancellation cleanup is covered. |
| Legacy UUID/name metadata read without reimport; lazy validation | Implemented and verified | LocalModelStore/ModelFilesTest preserve old files and defaults; unsupported legacy artifacts are not deleted. |
| Pinned bundled catalog; only explicit HTTPS Download/Resume; no arbitrary URL/login/auto-selection | Implemented and verified | ModelCatalog, allowlisted ModelResumePolicy and Models UI; unit policy checks. Catalog availability alone grants no production admission. |
| One streamed transfer; Range/If-Range, 200 restart, exact 206 range/ETag, 416 recovery, redirect/page/size/hash rejection | Implemented but incompletely verified | Real repository/private checkpoint code passed 32-byte simulated 200/206/416, weak/changed ETag, progress and interrupted-read recovery on API 30/37. Restart truncation now precedes the new ETag checkpoint. Live pinned HTTPS fetched only 64 KiB on API 37; API 30 DNS failed. Full real HTTPS install/resume remains open. |
| Storage preflight, reserve/cache headroom, periodic space checks, ENOSPC and preserve installed model until commit | Implemented but incompletely verified | StorageManager preflight, 512 MiB reserve and copy-time checks exist. Simulated preflight ENOSPC/copy-time space exhaustion preserve installation safety on API 30/37. Actual filesystem exhaustion, measured cache headroom and complete UI update/delete races remain open. File bytes are not peak RAM. |
| Durable queued/downloading/paused/verifying/installed/failed; separate import/download cleanup; pause/cancel/process restoration | Implemented and verified | ModelTransferReliabilityTest/ModelDownloadDeviceTest cover queued/active stop, late callbacks, cancellation cleanup and restored partials in isolated storage. Pause disconnects a blocked read before joining. Actual API 37 emulator process kill/relaunch restored 8 bytes as paused and resumed simulated 206. Real OS transfer-stop remains open. |
| API 34+ UIDT; API 30–33 dataSync service; progress/pause notification and OS-stop checkpoint | Implemented but incompletely verified | ModelDownloadJobService/Service/receiver and manifest exist. API 37 emulator accepted production UIDT scheduling from a foreground activity with no queued transfer. Complete UIDT transfer/notification, OS-stop and Android 14/16 physical-device behavior remain unqualified. |
| Delete selected model/cache safely, clear only its choices, serialize against runtime/writer | Implemented but incompletely verified | LocalModelStore lease and Models ViewModel coordinate removal. API 30/37 test-owned deletion was rejected while the runtime lease was busy and succeeded after release during an unrelated blocked simulated transfer; queue/cancel writer serialization passed. Full UI/real-network/OS-stop races remain open. |
| Shared runtime adapter; task-specific budgets/prompts/parsers; Paddle OCR: greedy; thinking/tools disabled | Implemented and verified | Catalog/profile handling in LiteRtLocalAiEngine and strict parser tests; device raw output records actual configurations. No universal Gemma template or context assumption. |
| Auto accelerator, actual backend/fallback evidence, at most one recoverable initialization retry | Implemented and verified | BackendSelector, engine/session tests and API 30 backend records. Malformed/OOM/cancellation do not fallback. No CPU/GPU UI picker or unmeasured fastest-backend claim. |
| Off-main JNI, one process-wide lease, fresh engine for unknown independent tasks, config-specific caches, interrupted marker | Implemented and verified | LocalAiSession/ModelRuntimeLease/engine code and bounded CPU/device lifecycle evidence. Marker records interruption; it cannot make JNI crash-safe. |
| Three repeated runs, different images, cancel/retry, leave/re-enter on both backends; memory returns after cycles | Implemented but incompletely verified | OnePlus 7T/API 30 ran both exact models from disposable copies: two cycles, three alternating synthetic images per cycle, cancel/join/retry, reload, model switching and Activity STOP/session close. Sampled malloc fell after unload; cached OEM PSS/zero graphics PSS cannot qualify total/GPU memory or leak absence. Full Playground navigation, long-duration profiling, Android 16 and true 16-KiB execution remain open. ELF/ZIP alignment is not execution. |
| Deadline waits for native terminal completion before image/conversation/engine disposal | Implemented and verified | LocalAiSession lifecycle and PackageFallbackTest cover ordering and caller cancellation; real CPU cancellation joined. A stuck native call can still hang or kill the process. |
| Preserve PP-OCRv5 baseline; optional model stage, skip/failure/manual recovery; no implicit download | Implemented and verified | FoodRecognitionRouter, PackageFallbackTest and PhotoPickerFlowTest evidence. Per-photo switch bypasses optional lookup/load/inference without changing the default. |
| Reliable bounded nutrition crop, explicit OCR rotation, no broad tiling | Implemented but incompletely verified | LabelRegionSelection, LabelImageCropper, unit tests and 27-photo nutrition-crop-v2 batch. Broader 90°/180°/EXIF/mirrored multi-column end-to-end corpus remains open. One bounded crop is used, not the proposed maximum-two retry pipeline. |
| Paddle transcription → deterministic nutrition parsing without invented confidence; conflict review/separate mass | Experimental / not production-admitted | TranscriptionNutrition and RecognitionDraft retain basis/conflicts/nulls/evidence. Paddle crop-v2 adds a wrong fat value; no production package admission. |
| Package benefit gate: at least two missing fields recovered, zero new wrong accepted numbers, retain OCR | Implemented and verified | Gemma missing-only P/F/C consumer: 12 correct recoveries on 6 independent photos, 94/127 vs 82/127 OCR fields, zero wrong accepted numbers or OCR loss in this sample. Exact hash/runtime/task admission only. |
| Full package nutrition JSON, model calories/name/mass and rescue without reliable OCR crop | Experimental / not production-admitted | Gemma full seven-field JSON has 14 wrong numbers. Only missing P/F/C is admitted. Paddle has decimal-loss/degeneration and crop-v2 nutrient errors. Neither may infer consumed weight. |
| Nullable recognition navigation/editor payload, restore edits/date, explicit single Save, old package snapshots | Implemented and verified | RecognitionDraft/PackageScanPrefill/EditorViewModel; unit tests and recorded real-model package and meal flows verify no writes before Save, recreation and original date. |
| Assign a meal-specific persistence source and migrate only if justified | Not implemented / intentionally deferred | EntrySource already includes MEAL_SCAN and backups can read it; current name-only saves use MANUAL. PACKAGE_SCAN remains for packages. No new source/schema migration was needed; retain existing enum/backup compatibility. |
| Meal single-food/single-dish editable assistance with 6–10 food/non-food/ambiguous fixtures | Implemented and verified | Seven-case Gemma model+validator gate and real-model UI Save evidence. Six schema-valid model answers; contradictory collage safely rejected. This is a bounded name-only admission. |
| Broad real complex-dish recognition, approximate nutrients per 100 g, recipe decomposition/photo mass | Not implemented / intentionally deferred | Numerical meal estimates and photo-derived amount remain disabled. Conservative name validation may reject legitimate compound names; independent complex-dish evaluation is required. |
| Diagnostics: stages/hash/profile/runtime/backend/timings/raw/provenance, additive old-row compatibility | Implemented but incompletely verified | DiagnosticAiAttempt, optional JSON fields and serialization/privacy tests exist. Meal/Playground populate rich attempts; production package optional-model fallback does not yet retain every failed attempt/backend/timing detail. |
| Diagnostics failure cannot block diary; bounded raw/partial/rejected evidence, correction tracking and explicit photo export | Implemented and verified | Separate private diagnostics DB, serializer/repository/export tests and existing editor Save boundary. Sanitized AI exports omit raw/prompts; unchanged fields are not ground truth. |
| AI Models/Playground Settings children; flat groups/status/source/license/import/download/delete, saved LocalAiLab route | Implemented and verified | Existing UI/navigation tests and recorded API 30 light/dark/IME checks; files managed in Models, independent Playground has no diary-write path. |
| Fresh install selection without Playground prerequisite, retain request/date when visiting Models, no auto-inference | Implemented and verified | supportsRecognition shared by UI/router; ModelPolicyTest plus three API 30 opt-in regressions temporarily removed only probe metadata, tested first real scans and restored test-owned changes. No fresh transfer was verified. |
| EN/RU, Expressive shared tokens, edge-to-edge, predictive Back, 48 dp targets/font/TalkBack states | Implemented but incompletely verified | Source and bounded navigation/IME/light/dark/device checks exist. Comprehensive spoken TalkBack, large-font/device/performance acceptance is incomplete. DESIGN.md defines approved scope. |
| Hide Profile Name without clearing DataStore/AppSettings/SettingsDraft value | Implemented and verified | Existing Settings test changes goals/tabs/recreation while retaining the hidden value. |
| Diary and backup safety through AI integration | Implemented and verified | AI lacks diary-write authority; FoodValidator/exact arithmetic and explicit Save remain. Unit backup/editor tests plus retained migration/persistence/UI evidence; cleanup does not rerun destructive device workflows. |
| Qwen/SmolVLM comparisons, FastVLM/restricted weights, NPU/plugins, converter/second runtime, isolated inference process | Not implemented / intentionally deferred | No curated expansion, new runtime or license assumption. Unknown compatible self-contained imports are Playground-only and use fresh engines. |

## Admission evidence and open gates

- [27-photo package comparison](ai-verification/2026-10-28/README.md) retains raw
  reports, references and [macro gate](ai-verification/2026-10-28/package-macros-gate.json).
  There are still **33 empty fields**. Independent new labels are needed before
  generalizing the six-photo benefit. Crop/consumer changes invalidate this task version.
- [Meal and earlier Paddle evidence](ai-verification/2026-10-22/quality-audit.md)
  retains exact artifacts, seven-case outcomes, decimal loss/degeneration and real
  editable single-Save evidence. Its historical package gate was superseded only
  for the narrow Gemma macro task; Paddle remains closed.
- [Current Models/Playground guide](LOCAL_AI_LAB.md) describes supported operations.
  The catalog's evidence paths/hashes and all machine-readable admission reports,
  OCR fixtures, Room schemas, native notices and minified JNI probe are retained.

Before broader qualification: complete real HTTPS/resume/UIDT/filesystem-full/UI race testing;
extend lifecycle testing with controlled total/GPU memory measurements; test Android 14/16
and a true 16-KiB device; independently expand label/meal corpora; verify production-signed
update/backup survival. Successful inference, valid JSON, smoke coverage and safely
rejected output must continue to be reported separately from correct recognition.
