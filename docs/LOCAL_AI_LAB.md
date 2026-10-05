# Experimental Local AI Lab

Local AI Lab is isolated developer functionality in Settings. Production Add Food recognition is not implemented. The Lab does not write nutrition or images to the diary.

## Runtime and model

The app uses `com.google.ai.edge.litertlm:litertlm-android:0.17.1`. The initial target is a **Gemma 4 E2B vision .litertlm** model, obtained separately by the developer. A text-only model or an unrelated file renamed to .litertlm is not compatible. Model compatibility, RAM requirements and GPU support depend on the actual artifact and device; successful compilation is not proof of inference.

Sources used for the adapter:

- [Official Gemma 4 model documentation](https://developers.google.com/edge/litert-lm/models/gemma-4)
- [Pinned Kotlin getting-started guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/docs/api/kotlin/getting_started.md)
- [Pinned conversation implementation](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
- [Pinned response-format API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/ResponseFormat.kt)

No installed LiteRT-LM skill was found in the available local skill directories during implementation; the official documentation and pinned SDK source were used directly.

The model is never bundled, automatically downloaded, uploaded or sent to a cloud API. Android's SAF file picker grants access to one chosen document; the import immediately streams it into backup-excluded private storage. A content URI is not a native path. The original document remains unchanged. Keep enough disk space for the original model, the private copy and native cache. Known document sizes receive a storage preflight; unknown sizes still use a bounded streaming copy and report IO failures.

CPU/GPU selects both the language and vision backend. GPU declarations for `libvndksupport.so` and `libOpenCL.so` are optional in the manifest as documented by LiteRT-LM. GPU errors remain visible; no CPU fallback changes the selected backend.

## Using the Lab

1. Open Settings → Local AI Lab and import a compatible .litertlm document. Check its displayed filename and size.
2. Choose CPU or GPU and tap Load model. Initialization runs in the background; wait for Ready or inspect the explicit error.
3. Choose photo or Take photo. Both use Just Cal's shared image pipeline. The Lab shows the prepared image; selecting another replaces the owned private snapshot.
4. Enter a free-form prompt, or choose Nutrition extraction and optionally edit its prompt.
5. Run inference. The screen appends actual SDK response chunks. Run again to create a fresh conversation without reloading the engine.
6. Cancel waits for native termination and conversation cleanup before enabling another run. Partial output remains visible.
7. Unload before switching backend/model or deleting a private model copy. Removing a model also removes its runtime cache.
8. Leaving the Lab cancels/joins active work and closes the engine. Re-entering restores the private model catalog and requires loading again. Configuration recreation retains the current ViewModel; process death cannot restore a native engine or image.

No model, importing, selected-but-unloaded, loading, ready, running, cancelling/cancelled, unloading, removing and error are distinct states. Acquisition and native work do not block the main thread. Cleanup can take time; the UI does not claim cancellation has finished early.

## Nutrition preset and parsing

SDK 0.17.1 supports constrained JSON through `ConversationConfig(enableResponseFormat = true)` and `ResponseFormat.json(schema)`; the Nutrition extraction preset enables both. It requests exactly:

```json
{
  "productName": null,
  "caloriesKcal": null,
  "nutritionBasisGrams": null,
  "proteinGrams": null,
  "fatGrams": null,
  "carbohydrateGrams": null,
  "packageWeightGrams": null
}
```

The prompt treats image text as data, asks for visible/confident information only, uses null for missing/uncertain values, prohibits invented or calculated missing nutrition numbers, and keeps the stated nutrition weight basis separate from package weight. Calories must be kcal. All nutrition fields must describe one basis.

Constrained decoding guarantees neither nutritional correctness nor consistent units. The parser independently rejects unexpected/missing fields, quoted numbers, negative/nonfinite/out-of-budget values, zero weight bases, trailing prose and incomplete output. It preserves exact decimal values as BigDecimal. Raw output is always retained; a parsed result is shown only when validation succeeds. No totals, serving conversions or persistence follow from this result.

## Metrics

- Initialization: monotonic wall time around native Engine.initialize.
- Inference duration: app monotonic elapsed time, including verification/decode/encoding and response consumption.
- First visible response: app elapsed time to the first nonempty streamed response, distinct from native TTFT.
- Native TTFT, prefill tokens/s, decode tokens/s: actual Conversation.getBenchmarkInfo values with SDK benchmarking enabled, available after completion.
- Selected backend: explicit CPU/GPU selection used to initialize the engine.

Unsupported, invalid or unavailable native values show Unavailable. Cancelled runs do not display a fabricated completed benchmark. These measurements include instrumentation overhead and are not a device-comparison study.

## Verification recorded on 2026-10-05

`./gradlew :app:testDebugUnitTest --tests 'com.justcal.app.ai.*' :app:assembleDebug :app:lintDebug` passed the 14 focused AI tests. The final full `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passed 48 unit tests, with no failed/skipped tests and no lint errors. Lint reports two existing warnings in LauncherIconPreview and the v26 launcher resource directory, outside this milestone.

JVM tests verify transactional streamed model import, provider/read failures and cancellation, stale partial cleanup, private containment, deletion/reopen, strict JSON/null/decimal validation, explicit backend errors, duplicate-operation guards, repeated inference without reloading, cancellation/retry and conversation-before-engine cleanup. State tests use a fake runtime; they do not prove actual model execution.

Manual verification used the arm64 API 37 Medium Phone emulator:

- Settings entry, empty/selected model states, filename and size.
- SAF import of a deliberately invalid 45-byte .litertlm file into private storage.
- Real native CPU initialization rejected that file with `INVALID_ARGUMENT: Invalid magic number` and left the UI usable.
- Shared CameraX capture returned a 960 × 1280 image and preview to the Lab.
- System Photo Picker selection returned a valid 1080 × 2400 image and preview to the Lab; an unavailable old fixture produced a recoverable acquisition error.
- Repeated native load/error with GPU selected returned the same explicit file-format error; this rejects the invalid artifact before any GPU inference and does not establish GPU compatibility.
- The model catalog persisted across an app restart. Removing the private model also removed its per-model CPU/GPU cache directories; the original SAF document remained 45 bytes.
- Back returned to Settings and released the newly owned camera image (image cache fell from 564 KiB to its prior 368 KiB baseline). The baseline included an older image left by a forced app restart, subject to the existing 24-hour orphan cleanup.
- The nutrition preset populated the editable seven-field prompt and displayed the constrained-JSON hint. No diary entry was created.

Android Profiler Live Telemetry was opened for the debug process; an initial idle memory timeline was approximately 100–110 MiB. An emulator disconnect interrupted the first session; subsequent Profiler UI observations timed out. This is a limited baseline observation, **not** a completed loaded-model memory/leak assessment.

No compatible model was available in the supplied workspace/Downloads and no physical Android device was connected. A successful real multimodal run, CPU/GPU execution, real native inference cancellation/repetition, throughput measurements and loaded-model memory cleanup therefore remain **unverified**. No model answers or benchmark numbers are fabricated.

## Required device follow-up

Use a physical Android device with enough RAM/storage and a compatible vision artifact:

1. Import the model; verify exact file size, private persistence after restarting the app, cancelled-import cleanup and insufficient-storage errors.
2. Load CPU, choose a package image and run a prompt. Save raw output and metrics. Repeat at least three times without loading again.
3. Cancel a long generation, wait for Cancelled and run again. Also leave while loading/generating, then re-enter and load again.
4. Unload, select GPU and repeat image + prompt, repetition and cancellation. If GPU initialization is unsupported, retain the exact error; do not count that as GPU inference.
5. Run the nutrition preset on legible and partially missing labels. Verify nulls, separate basis/package weight, schema parsing and unchanged diary contents.
6. In Android Profiler compare idle → load → image/run → repeated runs → cancel → unload → Lab exit, for both backends. Observe Java/native/graphics memory, force GC only as an observation aid, and inspect allocations/heap if growth persists. Cached native allocations and driver memory need interpretation; require repeated-cycle evidence before claiming a leak or its absence.
7. Remove the private model copy and confirm its runtime cache disappears while the original SAF document remains.

Keep this verification record explicit when compatible-device coverage is added.
