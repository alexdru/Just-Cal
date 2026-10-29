# AI Models and AI Playground

AI Models manages private model files in Settings. AI Playground is the experimental image/prompt screen; its saved route remains `LocalAiLab`. It never writes to the diary. Production recognition uses a separate `FoodRecognitionRouter`, the existing editor and explicit Save.

## Earlier audit — 0.17.1 (2026-10-22–23)

The 0.18.0 package-macro admission below supersedes this historical package-gate status.

The follow-up [quality audit](ai-verification/2026-10-22/quality-audit.md) supersedes the earlier milestone completion notes below. It includes reproducible machine-readable reports, separate model-response and validator outcomes, current implementation gaps and the device workflow result. The meal gate remains name-only; the package VLM gate remains closed. The real-model photo → Models → review → edited editor → Activity recreation → explicit single Save workflow now passed in 28.177 s and again in 30.908 s on the final build, including original diary date and empty initial amount. Its earlier timeout came from clicking the disabled Use photo button before preview decoding completed; the test now waits for readiness. A fresh GPU Paddle crop regression on 2026-10-23 reproduced 196.8 → 1968 and degenerate repeated text on the second crop. The raw outputs and deterministic parser results are linked in the audit; nonempty smoke success is separate from numerical quality.

Production admission is bound to the pinned artifact hash, LiteRT-LM version and versioned task contract, with a reviewed evidence reference. Changing runtime/task invalidates the old admission; matching metadata or an image smoke is insufficient. An optional package VLM deadline now preserves completed OCR after cleanup without swallowing caller cancellation. Invalid/refused meal responses retain bounded raw evidence and a validation category in detailed local diagnostics; they do not open an empty successful editor. Sanitized exports omit raw responses and prompts.

## Local package photo fixtures

Place original food-package photos in `test-data/package-photos/` at the project root. The directory and all nested contents are Git-ignored and outside Android assets/resources. Product subdirectories are allowed. These local originals are the inbox for explicitly requested OCR/model quality checks; adding photos does not automatically run tests or inference. Preserve original resolution and include readable nutrition basis/units where possible.

The checked-in CI workflow runs `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; it does not run device-photo tests or require this ignored inbox. Unit tests use in-code data, not private photos. Opt-in Android tests check their instrumentation arguments **before** accessing fixtures/models: `packagePhotoBatch=true` for both batch classes, `realPackageMacros=true` for the real package editor flow, and `realAiModels=true` for real-model runtime/selection tests. Without those arguments JUnit reports a skip. An explicitly requested real-photo run still fails if its required files are absent; do not treat missing requested fixtures as a quality pass.

## Per-scan assistance control — 0.19.0

Photo Review shows the selected, admitted and image-checked package model and a native **Local model assistance** switch. It is enabled only when such a model is available; otherwise AI Models remains available to choose one explicitly. Turning it off bypasses optional model lookup/loading/inference for that photo while retaining bundled OCR. The choice survives Activity recreation and a trip to Models, but does not change the global default or apply to the next new scan. No new DataStore preference is introduced.

The loading label names the model only when a reliable crop has been found and the optional native stage actually starts. A complete OCR result or unsuitable basis/crop never implies an additional model call merely because the switch is on. Successful assistance retains its existing AI banner/raw evidence in the editor; failure preserves OCR. Use photo stays pinned in a transparent host while the action viewport scrolls behind it with final-item clearance.

Verification on OnePlus 7T/API 30: the real package flow passed in 53.110 s with assistance off → OCR-only empty P/F, switch state retained across recreation and Models, then a fresh scan on → Gemma 4.5/3.6 g, edits retained and one explicit Save on the original day. The default model choice and test-owned data were restored. Three further device tests passed in 39.236 s: generated-image picker/reselect/manual recovery, real meal-name editor flow and four stable task-selection transitions. The test now focuses edited fields and asserts the name before/after recreation; an earlier unfocused test-input attempt stopped at missing-name validation, without a successful save.

Without opt-in arguments, both photo-batch tests and the real-package UI test reported JUnit assumption skips before fixture access (three skips, zero failures). These skips are not quality passes. Final Gradle verification passed all 165 unit tests, lint, debug app and AndroidTest APK builds. This update does not repeat the 27-photo quality admission or establish new meal accuracy.

## Which model is used today? — 0.18.0 quality admission (2026-10-28–29)

| Component | Installation | Current use |
| --- | --- | --- |
| Bundled PP-OCRv5 detector + Eastern Slavic recognizer | Included in the APK; 12.13 MiB of model assets | Always the Scan package baseline, offline OCR + deterministic parsing + editable confirmation |
| PaddleOCR-VL-1.6 | Separate 1.39 GB download/import | Playground label transcription only; crop-v2 still supplied a wrong nutrient, so no production admission |
| Gemma 4 E2B | Separate 2.59 GB download/import | Explicitly selected, separately admitted meal name-only and package missing-macro assistance; Playground experiments |

The two Paddle names are different models/runtimes. PP-OCRv5 is specialized text detection/recognition through ONNX Runtime/OpenCV. PaddleOCR-VL-1.6 is a generative vision-language model through LiteRT-LM. Installation stores private weights; it neither replaces bundled OCR nor selects a task. Removing a downloaded model leaves bundled OCR available.

**Enable package assistance:** Settings → AI Models → Scan package → Gemma 4 E2B. The exact installed artifact also needs a successful image probe for this SDK/device; if needed, load it in Playground and run an image. Production uses its own session. No download, selection or inference starts automatically.

Gemma runs only when OCR has an explicit per-100-g basis, a blank protein/fat/carbohydrate field and a reliable nutrition crop. The strict consumer accepts only missing P/F/C on the same 100-g basis, with decimal bounds and combined macros ≤100 g. It cannot alter OCR calories, name, mass or any nonempty macro. Its own calorie/name/mass answers are ignored; weight remains editable and saving is explicit. Failed/unsupported inference preserves OCR.

[All 27 local package photos](ai-verification/2026-10-28/README.md) were processed by real OCR and both pinned VLMs. OCR prefills 82/127 visible numbers, all correct in this sample. The narrowly admitted Gemma consumer replays the measured crop responses into 94/127 correct fields: twelve missing macros recovered on six independent photos, zero new wrong numbers or removed OCR values. Its full seven-field JSON still has fourteen wrong numbers and is **not** admitted. Paddle crop-v2 recovers seven fields but adds a wrong fat value (19.5 instead of 4.4), with sixteen empty responses and five deadlines. It remains experimental.

The cropper now recognizes actual RU/EN nutrition headings instead of ingredient “соль пищевая”, excludes neighboring ingredient/storage lines and clearly separate whole-dish/per-serving columns. Crop, transport and consumer versions are pinned in the evidence. New admission is `package-macros-v1-nutrition-crop-v2`; changing these contracts invalidates it.

Fresh production photo → OCR + Gemma → editor → Activity recreation → explicit single Save passed on OnePlus 7T/API 30 in 40.759 s, including original date, empty initial amount, preserved 150 kcal and recovered 4.5/3.6 g macros. The test restored the prior model selection and deleted only its own photos/diary entry. Full 165-unit suite, debug application/test APKs and lint passed. This is a small local sample, not a general accuracy claim or a new meal-quality evaluation.

To try Paddle: Settings → AI Playground → Select model → PaddleOCR-VL-1.6 → Load model → label photo → Package text → Run inference. Loading initializes native memory; installation stores weights. Its `OCR:` crop preset relies on OCR coordinates, so it cannot independently rescue a missing reliable crop. Playground never creates diary entries.

Next quality work: new independently reviewed label photos, especially absent/ambiguous basis, kcal/kJ, decimal text and crop/EXIF boundaries; improve the remaining 33 empty fields without sacrificing baseline values. Package names/calories/mass and meal numerical estimates need separate evidence. Broader meal-name tests need real complex dishes. API 34+ transfer/UIDT, repeated GPU lifecycle and controlled memory/16-KiB-device checks remain open; installing more unmeasured models does not close them.

## Models and privacy

The bundled, versioned `assets/ai_models.json` contains pinned **Gemma 4 E2B** and **PaddleOCR-VL-1.6** descriptors, revisions, exact byte counts, SHA-256 and source/license links. Weights are never bundled in the APK. No catalog request, model download or inference starts automatically. Download/Resume requires a user action. There are no cloud inference APIs, accounts or telemetry.

SAF import streams a self-contained `.litertlm` document into backup-excluded private storage, computes its hash and inspects the actual SDK container before installation. Text-only/embedding, invalid container, unsupported runtime/context and unavailable image backend pairs are rejected. Renaming a file does not establish compatibility. Unknown valid vision files remain Playground experiments; matching a curated hash applies its profile. Legacy UUID/name metadata remains readable without reimport and is validated lazily. Original documents are preserved.

Downloads use HTTPS and an explicit host allowlist, bounded redirects, identity encoding, pinned length/hash, private streaming files and atomic commit. Range/If-Range resume validates 206 Content-Range and ETag; 200 or a changed validator restarts, 416 restarts an incomplete file or verifies an already complete file. HTML/login responses are rejected. Verification precedes installation. Pause retains a checkpoint; Cancel joins the writer before removing only its partial file. Process recreation restores interrupted transfers as paused and never restarts inference. Storage preflight uses StorageManager with a 512 MiB reserve, with further space checks while copying; that reserve is not an assertion about model peak RAM.

API 34+ uses a user-initiated JobService; API 30–33 uses a non-sticky dataSync foreground service. Notifications offer progress and Pause; Models offers Pause/Resume/Cancel and retry. OS stops may interrupt work. Android 14+ job execution still needs device coverage; the available physical device is API 30. On this device direct HTTPS download testing stopped at a DNS failure for huggingface.co; verified model fixtures were transferred by USB for offline inference tests. This is not evidence that the full in-app transfer completed.

Deleting an installed model is serialized against the process-wide runtime lease, removes its private model/cache and clears only task selections referencing that UUID. It never deletes the original SAF document or other models. Model installation does not select a recognition task.

## Runtime

The adapter pins **LiteRT-LM 0.18.1**, verified by compilation, arm64/API 30 image runs and a device ELF alignment test. Relevant APIs are the pinned [ModelInfo](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/ModelInfo.kt), [Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/docs/api/kotlin/getting_started.md) and [Config](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt).

Before native initialization, ModelInfo is opened/closed off the main thread and converted into app-owned capabilities: LLM type, image input, context, minimum runtime and separate TEXT/VISION backends. Auto intersects these capabilities and profile restrictions; there is no CPU/GPU picker. For the pinned Gemma artifact the actual metadata advertises TEXT CPU/GPU but VISION CPU only, so Auto chooses CPU/CPU. The pinned Paddle artifact advertises both pairs and ran GPU/GPU. Historical GPU evidence on 0.17.1 does not override current container metadata.

Context/output budgets are profile-specific, static contexts are respected, thinking is disabled and Paddle uses `OCR:` with greedy sampling. Custom/unknown artifacts close their engine after every independent run. One process-wide permit covers Playground, production and deletion. Auto permits at most one recoverable init/delegate retry for an allowed general-vision pair; malformed files, low-memory/OOM and cancellation do not trigger fallback. Cache identity includes hash, SDK, backend, device and profile/configuration. Successful backend/image probes are recorded for this SDK/device; a previous interrupted native operation is surfaced without automatic retry.

Cancellation and deadlines wait for native terminal completion before conversation/engine/image cleanup. A timeout cannot make stuck JNI safe to free. Raw partial output is retained, with no invented completed benchmark. Leaving a recognition entry joins cancellation before releasing its owned image. Native types and large Bitmaps remain inside adapters/image boundaries.

## AI screen layout — 0.16.2 (2026-10-22)

Settings → AI Integration uses meaningful memory/science icons and native
Material 3 Expressive segmented rows with narrow gaps instead of divider lines.
AI Models starts with package/meal recognition defaults; each opens a task sheet.
Installed models follow, then catalog entries not already installed, then SAF
import. Catalog sheets retain source/license, Download/Resume/Retry and safe
Pause/Cancel; installed details retain validation/hash and confirmed removal.
Installing never changes the selected recognition task.

Both AI screens use edge-to-edge scroll viewports with safe-area/app-bar/end
spacing inside scroll content. The Playground bounds its scroll viewport to the
IME once, retaining prompt focus clearance. Navigation, explicit task admission,
runtime cleanup and offline inference contracts are unchanged. The shared diary
and history row decoration also uses small inner corners and narrow gaps.

Verification on OnePlus 7T / Android 11: ten Settings/navigation UI tests passed,
including the task sheet and prompt clearance above the IME; the final build
repeated that IME test and passed the diary date/CRUD workflow. Eleven distinct
UI scenarios passed across these runs. Both AI screens were visually checked
in light and dark themes; the original System theme was restored. The full
149-unit suite passed before resource cleanup, followed by 36 focused UI unit
tests, final debug/application-test builds and lint. No new lint warnings or
errors remain. Recognition quality/model benchmarks were not repeated for
this UI change. Version: 0.16.2 (42).

## Playground

1. Install/import models in AI Models, then return to AI Playground.
2. Select a model and load it with Auto, or choose bundled PaddleOCR without loading a VLM.
3. Choose camera or Photo Picker using the existing bounded local image pipeline.
4. Choose package transcription, meal description or a custom experiment. Prompts are editable. Package transcription locates a bounded nutrition crop from OCR coordinates; unsupported/unreliable regions require a closer photo.
5. Run/Cancel; inspect raw text, strict structured output where supported, actual backend and measured metrics. A previous answer can be disclosed for comparison.
6. Diagnostics records bounded raw/prompt evidence and model/runtime/backend/timing information locally; default sanitized exports omit prompts and raw responses. Photos remain an explicit export choice.
7. Unload before switching/removing models. Leaving closes the session. Process death restores neither native engines nor images.

The seven-field constrained nutrition preset remains experimental. Exact keys, decimal numbers/nulls, units/basis and bounds are independently checked. All-null and repeated output are failed extraction, not successful nutrition recognition. Custom files cannot use an assumed Gemma JSON profile.

Production package scanning retains PP-OCRv5 and permits only explicitly selected, artifact/runtime/task-admitted consumers. Gemma's missing-P/F/C consumer is enabled after the 27-photo gate; the full nutrition JSON and Paddle transcription consumers remain unadmitted. The transcription path retains conflict review and separate VLM net mass. The macro-only consumer ignores out-of-scope model fields and cannot overwrite OCR. Neither can derive consumed weight or persist without Save.

Meal support is limited to a separately validated, editable **name-only** suggestion. The strict meal schema accepts only isFood/singleDish/name; non-food, unclear/multiple dishes or invalid output lead to manual entry. No photo-derived weight, total calories or numerical nutrient estimate is enabled. Model selection stays explicit in Settings. FoodValidator and NutritionCalculator remain the only path to a saved diary entry.

## Measurements on 2026-10-22

Device: OnePlus 7T HD1900, Android 11/API 30. Same supplied Russian package photographs, one sample per case/configuration; these are not general performance claims. Full raw outputs/metrics are emitted by the opt-in device test and retained in its local results.

Pinned artifacts:

| Model | Revision | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Gemma 4 E2B | 6e5c4f1e395deb959c494953478fa5cec4b8008f | 2,588,147,712 | 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c |
| PaddleOCR-VL-1.6 | eb04490d43ce6197686ddc878e1093eacc7610a2 | 1,390,305,472 | 314df8b34d6911990d488e825602550a5bbcc5d6700cb1a8914e853e9043f4f1 |

| Engine / input | Initialization | Case 1 | Case 2 | Case 3 | Observed quality |
| --- | ---: | ---: | ---: | ---: | --- |
| Bundled PP-OCRv5 | 593 / 303 / 304 ms | 8,640 ms | 8,887 ms | 10,096 ms | 15/15 numeric fields correct, names recognized |
| Gemma, CPU, complete photo | 13,420 ms | 65,389 ms | 59,442 ms | 43,902 ms | Incorrect nutrition in all three; case 2 also repeats text |
| Paddle VLM, GPU, complete photo | 20,958 ms | 121,811 ms | 119,078 ms | 120,436 ms | Case 1 reads main numbers; cases 2/3 degenerate/repeat and miss nutrition |
| Paddle VLM, GPU, first crop implementation + greedy | 20,899 ms | 65,371 ms | 60,676 ms | 81,645 ms | Case 1 retains nutrition; case 2 damages heading/units; case 3 changes decimal values. Not admitted |

The first crop included excess neighboring text; the selector was tightened to the heading/last nutrient block. A final case-1 run initialized in 16,794 ms and inferred in 42,944 ms (first visible response 15,553 ms; native TTFT 1.537 s, prefill 286.57 tokens/s, decode 9.689 tokens/s; PSS 2,941,131 KiB). It transcribed 196.8 kcal as **1968 kcal**, so it still failed the numerical gate. Further crop tuning was stopped; fallback remains experimental. Crop and full-frame timings are separate configurations.

Post-call PSS snapshots: PP-OCR 131,650 KiB with process peak RSS 535,840 KiB; Gemma full-photo 3,155,710 KiB; Paddle full-photo 2,943,053 KiB; Paddle crop run 3,222,535 KiB. These are app/process observations, not model peak-memory requirements. Immediate post-unload snapshots remained at the sampled value; this does not prove memory returned to baseline or establish absence of a native/driver leak.

Initial name-only tests correctly named apple, banana and pizza; dog, bus and blank returned null (22,098 / 17,260 / 16,917 / 16,461 / 16,593 / 16,363 ms; initialization 20,830 ms, CPU). The first multiple-food response incorrectly supplied a combined name. The prompt now explicitly addresses separate foods; the parser rejects contradictory flags/names and compound names. This conservative parser can also reject legitimate names containing conjunctions, sending them to manual entry.

The final seven-image **model + validator** gate passed: apple/banana/pizza names; dog/bus/blank no suggestion; the apple+banana response had `singleDish:false` but supplied a name and was rejected. It is a successful safe refusal, not a claim that the model followed the schema. Final CPU timings were 14,139 / 14,021 / 13,806 / 13,505 / 13,623 / 12,540 / 13,939 ms; warm initialization 645 ms. Native cancellation joined, then a repeat apple inference completed. This October 22 admission was limited to meal name-only assistance; the separate October 28–29 package-macro evidence is documented above. Both require an image probe for the current SDK/device and explicit Settings selection.

One earlier long-running instrumentation run was terminated by OnePlus background CPU policy (`OHPD BgDetect`, ActivityManager kill); no native crash was established. Runtime tests now hold a foreground Activity and the final run completed. No battery-policy setting was changed. Foreground success is not evidence of sustained background inference support.

Fixture sources used only for local testing (not bundled): [apple](https://commons.wikimedia.org/wiki/File:Red_Apple.jpg), [banana](https://commons.wikimedia.org/wiki/File:Banana-Single.jpg), [pizza](https://commons.wikimedia.org/wiki/File:Supreme_pizza.jpg), [dog](https://github.com/pytorch/hub/blob/master/images/dog.jpg), [bus](https://github.com/ultralytics/yolov5/blob/master/data/images/bus.jpg). A blank fixture and a multiple-food collage are generated by the test.

Both curated models **launched successfully** on 0.18.1. Failed package extraction is explicitly distinguished from runtime startup. Qwen/SmolVLM were not downloaded or tested in this milestone; no runtime evidence is claimed for them. No CPU fallback was claimed for Paddle and no unsupported Gemma GPU pair was silently forced.

## Earlier package-only continuation verification — 0.16.0 (2026-10-22)

Per the user's updated scope, meal-quality tests and comparisons between models
were not repeated. All twelve supplied package photographs were run with the
bundled production OCR. [PACKAGE_OCR.md](PACKAGE_OCR.md#supplied-12-package-review--0160-2026-10-22)
records each photo, observed omissions, latencies, memory and raw evidence.
42/54 visible numbers were correctly extracted, 37/54 confidently prefilled, with
zero incorrect nonempty numeric prefills in this sample. Six absent masses stayed
empty. The strict complete-extraction test remains failing on twelve omitted
numbers plus unknown sandwich basis; this is an explicit remaining quality gap.

Final Gradle verification passed: **149 unit tests**, assembleDebug,
assembleDebugAndroidTest, lintDebug and minifyReleaseWithR8, using the Wrapper and
one worker. IDE inspections found no errors in the final parser, parser tests or
Models ViewModel. Lint has no errors; remaining warnings concern existing
resources/update UI and dependency version suggestions. No production release
was signed, tagged or published.

Four device tests passed in the final build: queued pause/cancel/restart without
resurrecting stopped transfer work (isolated private storage, no network);
Models/Playground Settings navigation; hidden Profile Name surviving goal edits,
tabs and Activity recreation; actual bundled OCR → editable confirmation →
exactly one explicit save to the originally selected diary date. The tests
restore their preferences and remove only their own diary fixtures. Settings,
AI Models and AI Playground were also visually inspected in Russian on this
physical device; this was a light-theme check, not a full accessibility or
large-font/device matrix.

Live HTTPS transfer remains unverified because of the earlier device DNS failure;
API 34+ user-initiated job behavior and OnePlus 12 remain unverified. The previous
real-model meal UI end-to-end test did not complete successfully; it was not
rerun in this package-only continuation. Earlier model+validator name-only
evidence does not establish that entire UI workflow. Package VLM admission and
numerical meal estimates remain disabled. Signing, update infrastructure and
diary/backup formats were preserved; the ongoing task retains version 0.16.0 (40).

## Historical verification

The records below describe earlier builds and remain historical evidence only.

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

## Physical-device verification on 2026-10-06

Tested Just Cal 0.3.0 on OnePlus 7T HD1900, Android 11 / API 30, with `gemma-4-E2B-it.litertlm` (2,588,147,712 bytes). SAF import completed into private storage. Automated selection in the Downloads provider did not reliably finish; the successful selection followed opening Download through the internal-storage provider. The private model is retained for further experiments.

Real CPU and GPU image inference both completed. GPU logs confirm OpenCL / LITERT_CL delegation for language prefill/decode and vision graphs. The native sampler separately reported an unavailable OpenCL sampler library and used its C API fallback; this is not an application fallback from GPU to CPU.

The selected image was the fresh package label for chicken, spinach and cheese wraps, prepared at 3000 × 4000. Native LiteRT-LM logged resizing it to 672 × 912 (2394 patches). Results:
- CPU free-form description read the product name and the prominent 15.8 g protein per 100 g text, but missed small-print calories/macros and included unreliable extra label interpretations.
- CPU Nutrition extraction with constrained decoding returned all seven fields as null. Parsing succeeded, but this is an extraction failure.
- The identical Russian nutrition prompt in free-form mode returned the product description and `nutritionBasisGrams: "100 г"`, with the other nutrition values null. The quoted weight is invalid for the strict parser.
- GPU Nutrition extraction also returned all seven fields as null. Changing backend did not solve the extraction failure.

These observations confirm image delivery and actual multimodal processing, but do not establish the exact cause of poor extraction. Small text after native resizing, the long prompt with an all-null example, and constrained decoding require separate controlled comparisons. No food-recognition integration or diary writes were added.

Measured completed Nutrition extraction runs (one sample per backend, not a general speed comparison):

| Metric | CPU | GPU |
| --- | ---: | ---: |
| Initialization | 11,160 ms | 16,094 ms |
| App inference duration | 25,127 ms | 21,548 ms |
| First visible response | 21,334 ms | 17,548 ms |
| Native TTFT | 6.46 s | 3.20 s |
| Native prefill | 86.82 tokens/s | 178.60 tokens/s |
| Native decode | 10.17 tokens/s | 9.42 tokens/s |

Three CPU runs completed without reloading. A subsequent real CPU run was cancelled; controls became available and unloading/switching to GPU succeeded. GPU inference completed once. GPU repeated-run/cancellation testing remains outstanding.

After CPU unloading, process PSS fell from 3,394,179 KiB during inference to 295,015 KiB. After GPU completion and leaving the Lab, PSS was 421,920 KiB and the image-input cache returned to its empty-directory baseline of 8 KiB. These are ADB meminfo observations, not a full Android Profiler leak analysis or proof that all allocations returned to baseline. Original gallery photos and the source model were preserved. No code changes were made during this verification.
