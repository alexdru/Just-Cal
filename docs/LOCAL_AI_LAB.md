# AI Models and AI Playground

AI Models manages private model files in Settings. AI Playground is experimental;
its saved route remains LocalAiLab. It never writes to the diary. Production uses
FoodRecognitionRouter, editable confirmation and explicit Save.

## Current model roles

| Component | Installation | Supported use |
| --- | --- | --- |
| PP-OCRv5 detector/eslav recognizer | Bundled, 12.13 MiB | Always the offline package OCR baseline |
| Gemma 4 E2B | Explicit download/SAF import, 2,588,147,712 bytes | Admitted meal name-only and missing package P/F/C; separate Playground experiments |
| PaddleOCR-VL-1.6 | Explicit download/SAF import, 1,390,305,472 bytes | Experimental label transcription in Playground; no production admission |

PP-OCRv5 and PaddleOCR-VL are different models/runtimes. Removing downloaded weights
never removes bundled OCR. Exact revisions, hashes, budgets, licenses and admissions
live in [ai_models.json](../app/src/main/assets/ai_models.json); current runtime is
LiteRT-LM 0.18.1. Do not infer compatibility from a filename or a prior runtime run.

## Installation and task selection

Settings → AI Models offers installed files, pinned catalog downloads and Android
document-picker import. No download, task choice or inference starts automatically.
Imports stream a self-contained .litertlm into noBackupFilesDir, validate its hash/
actual SDK container and atomically install a private UUID directory. Invalid,
text-only/embedding, unsupported-runtime and plugin-dependent formats are rejected.
Context/backend compatibility is checked for production selection and runtime loading. Unknown valid vision files remain Playground experiments. Original SAF
documents and legacy installed files are preserved.

Downloads require explicit Download/Resume. HTTPS hosts/redirects, size, Range/
If-Range/ETag and final hash are checked. Pause retains the partial checkpoint;
Cancel joins its writer before removal. Process restoration marks partial work
paused. API 34+ uses UIDT JobService; API 30–33 uses a non-sticky dataSync service.
A 512 MiB storage reserve is not a peak-RAM estimate. Controlled 32-byte fixtures
exercise 200/206/416, changed/weak ETags, progress, interrupted reads, restart,
insufficient-space checks, cancellation and concurrent deletion on API 30/37.
These use simulated HTTP/storage with real repository/checkpoint/private-file code;
they do not establish real server, filesystem-full or OS scheduler behavior.
An API 37 emulator force-stop/relaunch restored an 8-byte partial as paused and
resumed it correctly using simulated 206. A real pinned HTTPS range fetched only
64 KiB on that emulator (2,151 ms); API 30 still failed DNS. API 37 accepted foreground
UIDT scheduling without a queued transfer. Full HTTPS install/resume, real connection
loss/recovery and API 34+ transfer/OS-stop qualification remain open.

Installing never selects a recognition task. Choose Scan package or Scan meal
explicitly. Selection and production share supportsRecognition: exact artifact/
runtime/task admission plus container/context/image-backend compatibility.
A preliminary Playground image probe is unnecessary. First user-requested inference
records runtime evidence; it does not grant quality admission. The fresh-selection
regression was exercised on API 30 without a probe, using already installed weights;
it was not a fresh network download. Removing a model clears only its task choices,
under the process-wide runtime lease.

## Package and meal behavior

To enable package assistance: Settings → AI Models → Scan package → Gemma 4 E2B.
Photo Review has a per-photo switch; turning it off bypasses optional model lookup,
loading and inference while preserving the selected default. The choice survives
recreation and a trip to Models, but resets for a new scan. Use photo stays pinned;
the processing label names the model only when its native stage actually starts.

Gemma runs only with explicit per-100-g OCR basis, a blank P/F/C field and a reliable
nutrition-crop-v2. Its missing-only consumer enforces exact JSON/decimal/basis bounds
and total macros ≤100 g. It cannot change OCR calories, name, mass or a nonempty
macro. Model calories/name/mass are ignored. Failure preserves OCR after cleanup.
Raw output remains untrusted review evidence and Save remains explicit.

Optional package model inference runs in the private :package_ai process; OCR and the
editor stay in the main process. Native worker death falls back to the checkpointed OCR.
The parent lease also blocks model deletion and other engines until worker cleanup/death.
Cancellation first joins normally, then terminates only a stuck worker after 15 seconds;
the complete request has a 120-second deadline. Meal/Playground are still in-process.
Model files, selected tasks, prompts, crop profile and quality admission are unchanged.
A OnePlus 12/Android 16 export reported four native exits (reason 5), without a native
stack or AI stage; it does not establish the failing native operation. The new package
worker passed a real Gemma structured-image smoke on OnePlus 7T/API 30 (9,593 ms init,
28,182 ms inference), using a disposable copy of the installed artifact. A separate
SIGABRT fixture verified native exit 5, parent survival, OCR retention and retry.
This contains the crash; the underlying OnePlus 12 runtime defect still needs a fresh
report with the optional native trace, and a device retest. No new quality admission.

The [27-photo package gate](ai-verification/2026-10-28/README.md) records 82/127 correct
OCR prefills and 94/127 with Gemma: 12 recovered macros on 6 independent photos,
zero wrong accepted values or OCR losses. **33 fields remain empty.** The full
Gemma seven-field JSON has 14 wrong numbers and is not admitted. Paddle crop-v2
adds a wrong fat value (19.5 rather than 4.4), with empty/deadline failures; its
older crop also lost a decimal (196.8 → 1968) and repeated text. It remains experimental.

Meal assistance accepts only isFood/singleDish/name. It opens an editable name
suggestion with empty consumed amount; nutrition and amount are supplied manually.
Non-food/ambiguous/multiple-food or invalid output retains bounded diagnostic
evidence and offers recovery. No numerical meal estimate, recipe decomposition or
photo-derived consumed mass is enabled. Conservative compound-name rejection can
also reject a legitimate dish. The seven-case gate is a small sample, not general
complex-meal accuracy.

## Playground

1. Install/import in Models, then select/load in Playground with Auto; bundled OCR
   can be used without a VLM.
2. Take/select a photo through the shared bounded pipeline.
3. Choose package transcription, meal description or a custom prompt. Package
   transcription uses OCR coordinates for a bounded crop; no reliable crop means
   a closer photo is needed.
4. Run/Cancel; inspect raw/structured output, previous answer and measured metrics.
   The constrained seven-field nutrition preset is experimental. Valid JSON or
   all-null output is not a successful extraction.
5. Unload before switching/removing. Exit joins cancellation/cleanup before releasing
   owned images; process death restores neither native engines nor image resources.

Diagnostics retain bounded prompt/raw/runtime evidence locally. Production scan photos
are saved privately by default with bounded retention. Sanitized AI exports omit
prompt/raw; saved photos and native tombstones require an explicit report attachment
option. See [OCR_DIAGNOSTICS.md](OCR_DIAGNOSTICS.md). No diary-save action exists.

## Runtime evidence and limits

ModelInfo is inspected/closed off-main before Engine creation. Auto intersects actual
TEXT/VISION capabilities: the pinned Gemma runs CPU/CPU; pinned Paddle ran GPU/GPU.
No CPU/GPU picker, forced unsupported pair or unmeasured fastest-backend selection.
Unknown artifacts use a fresh Engine for independent runs. One process-wide lease
covers production, Playground and model deletion; caches include hash/runtime/device/
backend/profile. At most one allowed recoverable initialization fallback is attempted;
OOM, malformed files and cancellation do not fallback.

Native initialization/inference/close stay off-main. Cancellation/deadlines await
terminal native completion before freeing conversation/engine/images. A stuck JNI
call can still hang/crash; an interrupted-operation marker is surfaced next start.
Partial output and incomplete metrics are never reported as completed benchmarks.

Retained [October 22–23 evidence](ai-verification/2026-10-22/quality-audit.md) includes
exact hashes, raw Paddle failures, seven meal outcomes and CPU cancellation/retry.
Six meal answers obeyed the schema; contradictory apple+banana output was safely
rejected. Correct pipeline refusal is distinct from correct model output.
Real package and meal photo → editor → recreation → single explicit Save were recorded
on OnePlus 7T/API 30 with original date, empty amount and restored test-owned changes.
The package macro flow is recorded in
[implementation-verification.json](ai-verification/2026-10-28/implementation-verification.json).
Later fresh-selection/per-photo-switch device checks were documented in the retained
Git history of this guide and have corresponding opt-in tests; that JSON describes
its own macro-flow run and is not raw evidence for those later checks.

Historical October 22 post-call process PSS samples were 3,155,710 KiB for Gemma
and 2,943,053–3,222,535 KiB for Paddle. Immediate post-unload snapshots sometimes stayed high. These are process
samples, not measured model peak requirements or proof of a leak/its absence.

On 2026-10-29, OnePlus 7T/API 30 (4-KiB pages) ran both existing pinned models from
disposable local copies. Hash/container inspection, two load/unload cycles per model,
three alternating synthetic-image inferences per cycle, cancel/join/retry, model
switching and Activity STOP → LocalAiSession.close during inference passed. This is
a lifecycle harness, not full Playground navigation coverage or recognition evidence.
Unsupported Gemma GPU selection released the runtime lease before successful Auto.
Original diary/preferences/admission metadata hashes and installed file sizes were unchanged.

| Measured operation | Gemma CPU | Paddle GPU |
| --- | --- | --- |
| First initialization / reload | 15,976 / 660 ms | 16,515 / 12,428 ms |
| Six completed image runs per model | 10,851–13,587 ms | 10,894–16,861 ms |
| Cancellation joined native work | 7,921 ms | 8,921 ms |
| Sampled native malloc peak / after final unload | 1,220,962,576 / 21,908,992 bytes | 609,512,032 / 27,599,376 bytes |

These are sampled malloc counters, excluding mapped weights and GPU driver allocations,
not total/peak process requirements. OEM PSS stayed at 48,197 KiB throughout and graphics
PSS reported zero; neither provides usable GPU-memory or leak evidence. Two cycles do
not prove leak absence. Long-duration native/GPU profiling remains open.
API 37/Android 17 emulator loaded JNI and rejected an invalid container; real inference
was physical API 30 only. Packaged ELF alignment and bundled OCR smoke passed on both;
ZIP alignment is checked separately. No Android 16 or true 16-KiB execution was available.
The reliability change passed the full JVM suite, debug/test APK assembly, debug and
release lint, release-note/publication fixture tests and local documentation links.
Ten transfer fixture tests passed on each device; the two process-death methods skip
in a normal run and were exercised separately on the emulator as described below.
Full requirement disposition/open gates: [AI_REDESIGN_PLAN.md](AI_REDESIGN_PLAN.md).

## Reproducing tests without implicit downloads

Private originals belong in ignored test-data/package-photos, outside APK assets.
[JVM tests](../app/src/test/java/com/justcal/app/ai) use in-code/small local fixtures.
Opt-in device tests check flags before accessing private inputs/models:
packagePhotoBatch=true for batch/scoring, realPackageMacros=true for package UI,
realAiModels=true for runtime/selection/meal UI. Without flags they skip, which
is not a quality pass. Requested runs fail if required fixtures are absent.
ModelRuntimeDeviceTest may explicitly download missing models when opted in;
review its setup and avoid enabling it during ordinary cleanup.

Reliability fixtures: [ModelTransferReliabilityTest](../app/src/androidTest/java/com/justcal/app/ai/ModelTransferReliabilityTest.kt)
uses only owned 32-byte files and simulated HTTP/storage. Its normal run skips two
external process-death methods. To reproduce those on a disposable emulator, run
seedForProcessDeath with processTransferFixture=<UUID>, wait for PROCESS_TRANSFER_READY,
force-stop the target app, then run resumeAfterProcessDeath with the same UUID;
the second method verifies restoration and deletes that owned fixture directory.
[ModelRuntimeReliabilityTest](../app/src/androidTest/java/com/justcal/app/ai/ModelRuntimeReliabilityTest.kt)
requires runtimeReliability=true and both already installed pinned models. It makes
private copies (roughly 4 GiB plus 2 GiB cache headroom), never downloads or changes
selections, and removes only its copies/caches. Rehashes protect diary/preferences/
admission files. A killed test cannot run finally cleanup: remove only its identified
runtime-reliability-UUID cache directory before retrying, never installed weights.
[ModelPlatformReliabilityTest](../app/src/androidTest/java/com/justcal/app/ai/ModelPlatformReliabilityTest.kt)
requires modelNetworkProbe=true for a bounded 64-KiB HTTPS range (200 is refused unread),
or uidtProbe=true on API 34+ for scheduling only. No flag enables implicit installation.
[PackageWorkerProcessTest](../app/src/androidTest/java/com/justcal/app/ai/PackageWorkerProcessTest.kt)
uses the debug-only worker fixture: native SIGABRT, cancellation, a nonresponsive worker,
lease ordering and retry, with no installed models or diary writes.
[PackageWorkerModelTest](../app/src/androidTest/java/com/justcal/app/ai/PackageWorkerModelTest.kt)
requires packageWorkerModel=true and an already installed pinned Gemma. It creates a
private disposable UUID copy, uses a synthetic label and the production service, then
removes only its copy/cache and restores backend cache preferences. It never downloads,
changes task selection or saves diary entries. Allow model bytes plus 2 GiB headroom.

Use the [package reproduction instructions](ai-verification/2026-10-28/README.md#reproduction)
and [earlier meal reproduction](ai-verification/2026-10-22/quality-audit.md#reproduction).
Preserve the exact run ID/hash/profile/input geometry; coverage completion and task
admission remain separate results. Do not overwrite retained reference evidence.
