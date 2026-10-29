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
A 512 MiB storage reserve is not a peak-RAM estimate. **Full on-device HTTPS/resume,
API 34+ OS-stop and disk-full/race qualification remain open.** The earlier API 30
network test stopped at DNS failure; successful inference used USB-supplied weights.

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

Diagnostics retain bounded prompt/raw/runtime evidence locally. Sanitized AI exports
omit prompt/raw; photos require explicit report selection. No diary-save action exists.

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
Repeated GPU cancel/retry/exit, controlled Java/native/graphics memory cycles,
OnePlus 12/Android 16 and true 16-KiB-page execution remain unverified.
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

Use the [package reproduction instructions](ai-verification/2026-10-28/README.md#reproduction)
and [earlier meal reproduction](ai-verification/2026-10-22/quality-audit.md#reproduction).
Preserve the exact run ID/hash/profile/input geometry; coverage completion and task
admission remain separate results. Do not overwrite retained reference evidence.
