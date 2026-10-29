# Local package-photo batch — 2026-10-28–29

Device: OnePlus 7T HD1900, Android 11/API 30. Native batch application: 0.17.2-debug (45), AGP 9.5.0. Final macro consumer and fresh production-flow verification: 0.18.0-debug (46). The original 27 JPEGs remain in the ignored `test-data/package-photos/` inbox; they are not Android assets or repository fixtures. All 27 file hashes differ. Two visually repeated views are conservatively grouped with their earlier photographs, leaving 25 independent views for admission decisions.

## Completed baseline and full-frame Gemma results

| Configuration | Correct visible numbers | Wrong visible numbers | Omitted visible numbers | Invented absent mass |
| --- | ---: | ---: | ---: | ---: |
| PP-OCRv5 deterministic parse | 91/127 | 0 | 36 | 0 |
| PP-OCRv5 actual editor prefill | 82/127 | 0 | 45 | 0 |
| Gemma full-frame standalone JSON | 22/127 | 14 | 91 | 1 |

These are field counts over all 27 files, including the two repeated views. The OCR editor prefills 76/117 numbers across the 25 independent views; it has no wrong nonempty numbers in this sample, but only 11/27 files have every visible numeric field prefilled. Eight unprinted masses stay empty. End-to-end batch wall time per OCR photo is 4,772–15,542 ms (mean 8,682 ms); that includes preparation and test report overhead rather than only native model inference.

[ocr-results.json](ocr-results.json) retains recognized lines/confidence, parser evidence, editor fields and stage timings. [gemma-full-results.json](gemma-full-results.json) retains all 27 exact model responses and metrics. Gemma reads several missing macros correctly, but also substitutes whole-dish energy for per-100-g energy (529 instead of 189; 374 instead of 150; 301 instead of 121), swaps nutrients on the torn label, emits unprinted package mass 576 g, and mistakes prominent protein 15.8 g for package mass. These are errors in a syntactically valid response, not runtime initialization failures. The full-frame task is not admitted. Product names are sometimes useful, but are missing, truncated or corrupted on other labels; this batch does not establish a package-name-only contract.

## Superseded crop-v1 results and discovered crop bug

[paddle-results.json](paddle-results.json) retains all 27 initial Paddle runs: 22 OCR-located crops and five full-frame exploratory attempts, with a fresh Engine each time. Ten runs produced parser candidates, fifteen produced no numeric extraction, and two reached the local inference deadline. Standalone transcription parsing yielded 20 correct visible numbers, four wrong and 103 missing; wrong examples include 11 → 1 g fat, 7.7 → 17 g fat, 9.9 → 99 g protein and 6.4 → 64 g fat. Decimal loss and degenerate repeated text still occurred without reusing a previous Engine.

Following the actual production skip/failure rules, the candidate merge recovered three missing fields (protein/fat on `1792612058338.jpg`, protein on `1792612059984.jpg`), added no incorrect nonempty numbers, but cleared two already correct OCR fields due to false conflicts (`1792612059644.jpg` fat and `1793208395872.jpg` protein). That is 83/127 correct fields versus the baseline's 82/127, with two regressions. **The original numerical minimum of at least two recoveries and zero new incorrect accepted numbers was met.** This is distinguished from a recommendation to enable a candidate without regressions; that recommendation was not met. The quality scorer reports the minimum and the zero-loss/current-contract recommendation separately.

Inspection found a concrete input bug: the old cropper matched any `пищев` substring, so `соль пищевая` in ingredients could precede the real heading. The initial Gemma crop-v1 comparison was stopped after ten saved reports once this was established; it is a partial experiment, not 27-photo coverage. Full-frame Gemma and initial Paddle coverage above remain complete and are retained as historical measurements.

The corrected `nutrition-crop-v2` matches an actual RU/EN heading at its beginning, tolerates OCR-merged `Пищеваяценность` / `иэнергетическая`, excludes earlier ingredients and later storage sections, and excludes a clearly separate column explicitly headed `Всё блюдо` / `Per serving` on either side. It does not guess splits through overlapping OCR boxes. Five focused crop tests cover these boundaries, alongside existing coordinate-rotation and policy tests. Transcription admission is now `label-transcription-v2`, so earlier task evidence cannot automatically admit the changed pipeline.

A saved v2 crop of `1792612058338.jpg` was visually inspected: 150 kcal and 598 kJ are clearly legible and the whole-dish column is absent. Gemma still returned 598 as calories in the control call. This is retained as an actual unit-confusion result, not a successful extraction. The full corrected-crop batches are recorded separately from the old task configuration.

## Final crop-v2 results and limited Gemma admission

[Gemma crop-v2](gemma-crop-v2-results.json) and [Paddle crop-v2](paddle-crop-v2-results.json) each contain **all 27 real native responses**, including empty and deadline cases. Each used 17 reliable nutrition crops and 10 exploratory full-frame inputs when no reliable crop was available. The source OCR baseline was reused unchanged: hashes/decoded geometry were checked for every VLM input. Native inference was not repeated for the later deterministic consumer replay.

| Configuration | Correct / visible | Wrong | Missing | Correct OCR fields lost |
| --- | ---: | ---: | ---: | ---: |
| OCR editor baseline | 82/127 | 0 | 45 | 0 |
| Gemma crop-preferred standalone seven-field JSON | 68/127 | 14 | 45 | 31 |
| OCR + Gemma missing-P/F/C consumer | **94/127** | **0** | **33** | **0** |
| OCR + Paddle crop-v2 transcription consumer | 89/127 | 1 | 37 | 0 |

The standalone Gemma row is a transport-quality comparison, not a proposed overwrite of the editor. Its P/F/C subset reads 59/81 printed macro fields correctly with zero wrong and 22 missing, but its energy/mass fields remain unsafe. The missing-only scope was declared after reviewing the first 17 responses and before the last 10; this is development-sample admission, **not an independent holdout accuracy estimate**.

The separate production consumer accepts only **blank protein/fat/carbohydrate** fields, requires OCR and response basis 100 g plus a reliable crop, bounds each macro and their combined sum to 100 g, and preserves energy/name/amount/every nonempty OCR field. It ignores model energy, mass and name rather than trying to reinterpret them. Twelve correct macros were recovered on six independent photos; there were no new incorrect values, invented absent fields or OCR losses. Across 25 independent views that is 88/117 correct versus 76/117 baseline. Model calorie/unit mistakes, including 598 kJ returned as kcal, remain in raw evidence and cannot enter these fields through this consumer.

[quality-comparison-crop2.json](quality-comparison-crop2.json) scores all 108 configuration/photo rows. The separate [package-macros-gate.json](package-macros-gate.json) replays the 27 exact native Gemma responses through the actual production consumer and asserts the gate. It binds the pinned Gemma SHA-256, LiteRT-LM 0.18.1, nutrition-crop-v2, exact prompt/structured transport and `package-macros-v1-nutrition-crop-v2`. This evidence admits that task only; meal name-only retains its separate earlier evidence.

Paddle crop-v2 yielded six nonempty parser results, sixteen empty extractions and five 90-second inference deadlines. Its actual routed merge recovered seven missing fields but incorrectly accepted **fat 19.5 instead of 4.4 g** on `1792612058945.jpg` (the printed 19.5 is carbohydrate). Thus both its numerical minimum and final gate fail; it stays Playground-only. Its old crop-v1 numerical minimum above is not retroactively changed or used to admit v2.

The new package task is offered under **Settings → AI Models → Scan package → Gemma 4 E2B**. Installation never selects it automatically. The 0.18.0 build used a prior SDK/device image probe; 0.19.1 removes that onboarding prerequisite and checks the first real scan instead, retaining the same artifact/task quality admission (see LOCAL_AI_LAB.md). PP-OCRv5 remains available without any VLM. Missing basis/crop, complete OCR, unsupported model, timeout or native failure preserve baseline rather than trigger a download.

## Final implementation verification

- 22 focused macro/crop/admission/fallback unit tests passed, followed by **165/165 unit tests**, debug application/test APK builds and lint.
- The on-device four-variant quality replay passed its explicit macro-quality assertion (1.654 s).
- A **fresh real production flow** passed (40.759 s): Photo Picker original JPEG → actual OCR and selected Gemma → editor, 150 kcal preserved, 4.5/3.6 g recovered, 24.8 g OCR carbs retained, initially empty amount, original October 1 diary date, edits retained across Activity recreation and exactly one explicit Save of 120 g with PACKAGE_SCAN source. It checked Room remained unchanged before Save, then removed only its own test entry/photos and restored the previous package selection.
- IDE inspections found no errors; optional Kotlin style suggestions remain. The editor screenshot and Models task sheet were visually inspected on OnePlus 7T/API 30. Gemma is an enabled package choice, Paddle VLM is absent from that task, and the prior built-in OCR selection remains unchanged. Two earlier UI attempts had no Compose hierarchy while the phone was locked/asleep; after waking and dismissing its lock screen the real workflow completed. They were not model-quality failures.
- No model download, Room/backup schema change, release publication or automatic task selection was performed. Original photos remain ignored and untouched. API 34+ devices, broader independent labels, sustained native-memory cycles and general meal accuracy are not established by this package batch.

## Method and test boundary

The existing AndroidJUnit4/AndroidJUnitRunner stack executes real bundled PP-OCRv5 and the already installed, pinned LiteRT-LM models. Native batch/scoring runs use no new test dependencies, model downloads, task selection changes or diary writes. The separate fresh UI test temporarily selects the installed model, explicitly saves its own entry, then restores/removes only those test-owned changes. An Activity stays foreground to avoid the previously observed OnePlus background CPU kill. Every VLM photo gets a fresh native Engine; model weights/caches remain private. Initialization, inference and cleanup finish before the next photo. A 90-second inference deadline retains partial output, joins native cancellation and then unloads.

[package-reference.json](package-reference.json) records visible print, missing values and review corrections. Nutrition is explicitly per 100 g. Printed net weight is separate from consumed amount, and absent mass is never inferred from whole-dish energy. Numeric comparison is exact decimal equality (ignoring insignificant trailing zeroes); omitted fields count as omissions, and any invented absent mass counts as an error. Names need a separate semantic review rather than exact capitalization/spacing equality.

The baseline report retains the deterministic parsed label and the actual editor draft. A confident OCR value is not assumed correct. Paddle uses `OCR:`, the production nutrition crop when available and the actual `TranscriptionNutrition.merge`; full-frame attempts without a reliable crop are exploratory and cannot establish production-crop admission. Gemma uses the seven-field constrained schema with a direct print-transcription prompt, without the old all-null example. Its full-frame and optional crop runs are separate task configurations. Valid JSON, nonempty output and completed instrumentation coverage are never quality assertions.

The existing package admission criterion is at least two correctly recovered missing fields on independent views, with zero new incorrect accepted numbers, preserved basis/conflicts/missing values and explicit editor Save. A good response on a field already supplied by OCR is not a recovery. Gemma's package JSON experiment is not the existing transcription task and cannot inherit its admission automatically. The separately admitted meal-name task is outside this package-only batch.

## Reproduction

For the final `2026-10-28-crop2` run, use the same native batch command below with that run ID; Gemma additionally needs `-e batchCrop true`. Complete all 27 photos for OCR and each VLM, then score the saved native responses with the production consumer:

```sh
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ai.PackagePhotoBatchQualityDeviceTest#compareSavedLocalPackagePhotos' \
  -e packagePhotoBatch true -e batchRun 2026-10-28-crop2 \
  -e batchVariants ocr,gemma4-e2b-crop,gemma4-e2b-macros,paddleocr-vl16 \
  -e batchRequireQuality true \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner

adb shell am instrument -w -r \
  -e class 'com.justcal.app.ui.PhotoPickerFlowTest#selectedPackageModelOnlyCompletesMissingMacrosUntilExplicitSave' \
  -e realPackageMacros true \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The replay requires the reviewed reference at the location below and all native reports for the exact inbox. The fresh UI test needs the already installed pinned Gemma (a prior image probe is not required), an unlocked phone and system Photo Picker; it temporarily creates two owned media copies of `1792612058338.jpg` and restores its own changes. Native photo batches do not mutate selections or diary data.

The following commands reproduce the original full-frame/crop-v1 run layout; current crop-v2 runs should use the new run ID to avoid overwriting historical evidence.

Build/install debug and debug AndroidTest APKs using the Gradle Wrapper. Copy the local inbox to the debug app's external fixture directory:

```sh
adb push test-data/package-photos/. /sdcard/Android/data/com.justcal.app.debug/files/package-batch-fixtures/
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ai.PackagePhotoBatchDeviceTest#evaluateLocalPackagePhotos' \
  -e packagePhotoBatch true -e batchEngine ocr -e batchRun 2026-10-28 \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Run the same instrumentation command sequentially with `batchEngine gemma4-e2b`, then `batchEngine paddleocr-vl16`. OCR must run first; both VLMs verify the source SHA-256 and decoded image geometry against its saved report. `-e batchCrop true` gives Gemma a separately named `gemma4-e2b-crop` result directory. `-e batchCases file1.jpg,file2.jpg` limits a diagnostic rerun; do not count it as full coverage.

Reports are atomically written after every photo under `/sdcard/Android/data/com.justcal.app.debug/files/package-batch-results/2026-10-28/`. They preserve exact artifact identity, task/prompt, backend, actual dimensions/crop mode, raw/partial output, parsed fields and measured latency. Pull that directory after the run. The opt-in test asserts coverage only; without `packagePhotoBatch true` it skips. Quality decisions must use the reference comparison, including rejected/empty/deadline cases.

For automated comparison, push the reviewed reference and run the separate opt-in scorer after all three engines:

```sh
adb push docs/ai-verification/2026-10-28/package-reference.json /sdcard/Android/data/com.justcal.app.debug/files/package-batch-reference.json
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ai.PackagePhotoBatchQualityDeviceTest#compareSavedLocalPackagePhotos' \
  -e packagePhotoBatch true -e batchRun 2026-10-28 \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The scorer verifies every input hash, uses `BigDecimal` equality, writes `quality-comparison.json` and reports both all-file coverage and independent-view totals. Its passing JUnit result verifies coverage/comparison completion; `packageCandidateGatePassed` is a separate data field and never changes the catalog. Paddle's `candidateActual` preserves the merge experiment on every photo. Its scored `actual` follows the production router: complete OCR, no reliable crop and native failures retain the OCR baseline. Only an incomplete OCR result with a reliable crop can count toward production recoveries. Correct OCR fields removed by conflicts on that route are counted as regressions. Gemma crop/full variants are standalone JSON comparisons. The separate gemma4-e2b-macros variant evaluates the actual missing-only consumer and its own task/version gate; it cannot inherit the Paddle transcription admission by changing a catalog flag. `batchVariants` can select a completed subset or include `gemma4-e2b-crop`; all reference photos must have a report in each selected variant.
