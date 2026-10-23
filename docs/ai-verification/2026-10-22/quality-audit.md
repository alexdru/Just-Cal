# AI quality and implementation audit — 2026-10-22–23

App: **0.17.1 (44)**. Device: OnePlus 7T HD1900, Android 11 / API 30, arm64. Runtime: **LiteRT-LM 0.18.1**. Existing installed, hash-verified models and local fixtures were reused; this audit did not download new weights. Images remain outside the repository/APK.

This is a bounded regression sample, not a general accuracy or device-support claim. Runtime startup, a syntactically valid answer, safe validator refusal and correct task output are reported separately.

## Decisions

- **Meal:** retain explicit, editable **name-only** assistance for the pinned Gemma artifact. No photo-derived nutrients, calories or consumed mass. The seven-case pipeline gate passes; one model answer violates the schema and is rejected.
- **Package:** keep bundled PP-OCRv5 as production baseline. Its strict complete-extraction gate still fails. **Do not enable package VLM fallback:** no demonstrated recovery of at least two previously missing fields with zero new incorrect accepted numbers.
- Playground remains independent of diary persistence. Model installation, metadata compatibility and image smoke never select or admit a production task automatically.

## Artifact identity

| Model | Revision | Bytes | SHA-256 | Actual backend |
| --- | --- | ---: | --- | --- |
| Gemma 4 E2B | `6e5c4f1e395deb959c494953478fa5cec4b8008f` | 2,588,147,712 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | CPU / CPU |
| PaddleOCR-VL-1.6 | `eb04490d43ce6197686ddc878e1093eacc7610a2` | 1,390,305,472 | `314df8b34d6911990d488e825602550a5bbcc5d6700cb1a8914e853e9043f4f1` | GPU / GPU |

Budgets remain catalog-specific: Gemma context/output 4096/512, Paddle 4096/1024. Paddle uses `OCR:` and greedy sampling. The Gemma artifact advertises VISION CPU only; historical GPU runs of another runtime configuration do not override that metadata.

## Meal: fresh seven-image run

Evidence: [meal-name-gate.json](meal-name-gate.json), including exact prompt, runtime/task version, raw answers and measured timings. Sources and earlier measurements are retained in [LOCAL_AI_LAB.md](../../LOCAL_AI_LAB.md#measurements-on-2026-10-22). Fixtures: three foods, two non-foods, a generated blank image and a generated apple+banana collage. This does not cover arbitrary cooked or mixed meals.

| Fixture | Model response | Accepted suggestion | Inference ms |
| --- | --- | --- | ---: |
| Apple | Valid single food, `apple` | apple | 13,999 |
| Banana | Valid single food, `banana` | banana | 13,985 |
| Pizza | Valid single dish, `pizza` | pizza | 13,742 |
| Dog | Valid non-food, name null | None | 13,455 |
| Bus | Valid non-food, name null | None | 13,526 |
| Blank | Valid refusal, name null | None | 12,433 |
| Apple + banana | Contradictory: `singleDish:false`, name `apple and banana` | None; validator rejects | 13,821 |

Six of seven answers satisfy the schema; all seven pipeline outcomes satisfy this small gate. The collage is **not** a correct schema-following model response. The negative dog/bus/blank checks now require a valid null answer, so malformed output cannot count as correct non-food classification. Warm initialization was 628 ms; first visible response ranged 11,233–11,768 ms. One cancellation was joined and an apple retry completed. This is one CPU cancellation/retry check, not a GPU stress or native-memory leak test.

The conservative validator rejects names containing conjunctions as possible separate foods; legitimate compound dish names can therefore fall back to manual entry. Broader labeled meal evaluation remains necessary.

## Packages: fresh 12-photo bundled OCR run

Evidence: [package-quality-gate.json](package-quality-gate.json), with reference values, parsed values, actual editor prefills, basis and stage timings for each original photo. Ground truth and observed failure classes remain documented in [PACKAGE_OCR.md](../../PACKAGE_OCR.md#supplied-12-package-review--0160-2026-10-22).

- **42/54** visibly printed numeric fields were correctly extracted.
- **37/54** were confidently prefilled in the editor; **0** incorrect nonempty numeric prefills in this sample.
- **12** visible numbers remain omitted; five additional correct extractions stay below the confidence threshold. Six genuinely unprinted masses remain empty.
- Sandwich nutrition/basis remains unresolved; corrupted short protein/fat labels and two printed masses remain missing. Product names can be truncated, incorrect or absent.
- The unchanged strict `suppliedPackageReviewFixtures` assertion **fails**, explicitly reporting the omissions and unknown sandwich basis. It was not weakened to turn the result green.

The foreground sequential pass took 112.142 s including instrumentation/reporting. Per-image OCR totals ranged 4,839–14,982 ms; initialization 228–250 ms. These are single-run device observations, not a cross-device benchmark. No OCR weights or arithmetic changed during this audit.

## Paddle package VLM regression probe

On 2026-10-23, the same pinned Paddle artifact was run on the current bounded OCR nutrition crops for cases 1 and 3, consecutively in one loaded engine with a new conversation per request, with prompt `OCR:`, Auto → GPU/GPU and the catalog 4096/1024 budget. Initialization: 13,934 ms. Both native calls completed; the nonempty-output smoke assertion passed in 145.865 s overall. **Numerical quality did not pass.**

| Crop | Reference kcal / protein / fat / carbs | Raw answer and Kotlin parsing | Inference / first response ms |
| --- | --- | --- | ---: |
| Case 1, rice/chicken brizol | 196.8 / 9.7 / 8.8 / 19.7 | Raw energy **1968**, corrupted carbohydrate label. Parser retains only protein 9.7 and fat 8.8, both unscored; energy/carbs stay absent | 33,172 / 15,042 |
| Case 3, Asian beef bowl | 98.5 / 5.9 / 3.7 / 10.4 | Degenerate repeated `H.` text; no nutrition/basis extracted | 78,008 / 10,961 |

Exact outputs: [case 1 raw](paddleocr-vl16-AUTO-case1.txt), [case 1 parsed](paddleocr-vl16-AUTO-case1-transcription.json), [case 3 raw](paddleocr-vl16-AUTO-case3.txt), [case 3 parsed](paddleocr-vl16-AUTO-case3-transcription.json). Empty parsed JSON means default null/UNKNOWN fields, not zero nutrition.

This bounded regression probe reproduces decimal loss and degeneration; it is not a full twelve-photo hybrid benefit evaluation. Both references are already complete in bundled OCR, so these runs cannot establish recovery of previously missing fields. Crops do not include a reliable net-mass reference; no mass accuracy is claimed. No decimal correction was guessed from the reference, and no new package admission was created.

Post-call PSS and the immediate post-unload sample were 2,943,919 KiB. Such potentially cached process snapshots do not establish native peak memory, a leak or memory return to baseline.

## Changes completed in this audit

1. Catalog admission now requires an exact artifact match plus runtime/task version and a reviewed evidence reference. Runtime/task upgrades invalidate old admission; absent or wrong-profile admission does not permit production selection.
2. A local optional package VLM deadline/failure returns completed OCR only after cleanup. Caller cancellation/deadline propagates; native resources are not freed early.
3. Meal parser rejection/refusal retains bounded raw evidence and an explicit validation category in detailed local diagnostics. It cannot create an empty successful editor prefill. The new optional JSON field preserves older diagnostic records; sanitized exports still omit raw output/prompts.
4. Device reports distinguish valid model answers, accepted names, safe refusal and correct extraction. New unit coverage checks admission expiry, whitespace/control names, raw-preserving rejection, optional fallback cleanup/cancellation and legacy/sanitized diagnostic payloads.
5. The plan now separates implemented mechanisms from unpassed quality and device gates. Version was incremented once to 0.17.1 (44); diary/backup schema and release signing are unchanged.

## Verification and remaining work

Full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug` passed. Scoped AI/OCR/diagnostics/editor unit tests passed first. Relevant IDE inspections found no errors; existing formatting/style suggestions and Gradle/native-access deprecation notices remain. Device tests require separate opt-in fixtures and are not part of the unit suite.

The real-model `selectedMealModelKeepsPhotoAndDateUntilExplicitEditorSave` device test **passed in 28.177 s** and passed again in **30.908 s** on the final build with explicit hash-bound admission ([final instrumentation output](meal-editor-flow.txt)). It verifies Photo Picker → review → Models → review without losing image/date, actual Gemma apple naming, empty consumed amount, no diary write before Save, edited fields surviving Activity recreation, and exactly one explicit save to the original selected date. Preferences and test-owned photos/entry are restored/removed in `finally`. The earlier 90 s timeout was a test synchronization issue: it clicked Use photo before asynchronous preview decoding enabled the button. The test now waits for the enabled button; inference/save assertions remain unchanged.

Remaining gates from AI_REDESIGN_PLAN.md:

- Improve Russian package extraction on the omitted fields without lowering confidence or guessing numbers; meet the VLM benefit gate before any package admission.
- Expand meal fixtures to real complex/ambiguous dishes; numerical estimates and photo-derived consumed mass remain disabled.
- Repeated GPU cancel/retry/exit and controlled native-memory cycles; crop/rotation/EXIF end-to-end cases; other devices including API 34+ and 16-KiB page execution.
- Full on-device HTTPS download/resume with 200/206/416/ETag changes, interruptions/process restart, ENOSPC and concurrent download/delete; real UIDT OS-stop behavior. Policy/state unit tests do not establish these outcomes. Earlier on-device DNS failure remains unresolved evidence, not a successful transfer.

## Reproduction

Use the Gradle Wrapper and the configured Android Studio JBR. Build/install the debug app and AndroidTest APK first. Keep each device run sequential and foreground; the tests restore their preference choices and delete only their own diary/photo fixtures.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb shell am instrument -w -r -e class com.justcal.app.ai.ModelRuntimeDeviceTest#mealNameImageGateAndCancellation -e realAiModels true com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.justcal.app.ocr.PackageOcrDeviceTest#suppliedPackageReviewFixtures -e packageReviewFixtures true com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.justcal.app.ai.ModelRuntimeDeviceTest#modelImageSmoke -e realAiModels true -e modelId paddleocr-vl16 -e aiCrops true -e aiCases 1,3 com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.justcal.app.ui.PhotoPickerFlowTest#selectedMealModelKeepsPhotoAndDateUntilExplicitEditorSave -e realAiModels true com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The runtime smoke test can explicitly download a missing pinned model; this audit reused installed files. Seven meal fixtures go in the app's external `meal-fixtures/`, three original package comparisons in `ocr-fixtures/case{1,2,3}.jpg`, and the twelve original package review basenames in `package-review-fixtures/`. Tests emit reports under `ai-results/` and `ocr-results/`. Inspect instrumentation results: `adb shell am instrument` exit code alone does not indicate assertion success.
