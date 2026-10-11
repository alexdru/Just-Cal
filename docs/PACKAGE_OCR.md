# Offline package-label OCR

Persistent opt-in content diagnostics, measured scan history and structured local reports are described in [OCR_DIAGNOSTICS.md](OCR_DIAGNOSTICS.md). The diagnostic instrumentation does not change model weights, preprocessing or nutrition calculations.

## Production flow and boundary

Scan package → CameraX / Photo Picker → Photo Review → Use photo → local OCR →
deterministic parsing → existing editable FoodEditor → explicit Add to diary.
The selected diary date is retained. No OCR result writes Room automatically.
Repeated submission is guarded by editor busy/completed state. Every field can be
corrected; missing or uncertain values remain empty and normal FoodValidator rules
apply. Net package grams can initialize Amount eaten, with a separate explanation.
New manual amounts start empty; edits retain the saved amount. Empty new manual
names request focus/keyboard once, with saved event state.

Scan meal retains its photo-ready endpoint. This feature does not use or modify
Gemma, LiteRT-LM, Local AI Lab, barcode services or visual food estimation.
Recognition has no network or Google Play Services dependency and no Python runtime.

## Models, exports and provenance

These are official PaddlePaddle **published ONNX exports**, rather than an
unverified conversion performed locally. The original eslav Paddle configuration
is retained to make preprocessing/dictionary provenance reviewable.

| Asset | Source and pinned revision | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `ocr/det/inference.onnx` | [PP-OCRv5_mobile_det_onnx](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/tree/e6f4fa85f00e168c862bc462aebca69eef9b3d3d) | 4,826,518 | `a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d` |
| `ocr/eslav/inference.onnx` | [eslav_PP-OCRv5_mobile_rec_onnx](https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx/tree/9a32171fc5718746875e1a261818884517975013) | 7,887,627 | `b3018ef2b09a0250b6e0c8e871c927098363e5fd4df890cc68e8358eb0aaf1bd` |

Total model bytes: **12,714,145** (12.13 MiB). Models are bundled in main assets;
there is no runtime download. The dictionaries/configuration come from
[eslav_PP-OCRv5_mobile_rec](https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec/tree/7553801264d3379d8d2e854971989e5e26c22e03).
`characters.json` preserves the configuration's 517 characters in order, then
appends a space. CTC blank index 0 makes **519 output classes**. The adapter checks
input/output shapes and the dictionary size against the actual ONNX sessions.
The default Android Chinese recognition model is not used for Russian.

The Android geometry/DB postprocessing files under `com/paddle/ocr` come from
[PaddleOCR deploy/ppocr-android](https://github.com/PaddlePaddle/PaddleOCR/tree/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf/deploy/ppocr-android).
Their copyright/license headers remain; the immutable-local cleanup is marked.
The SDK was inspected against the
[official Android deployment guide](https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html).
Just Cal uses a small adapter around its postprocessing, avoiding the SDK's
default recognition model/color assumptions and its unrelated demo app.

## Runtime and preprocessing

- ONNX Runtime Android **1.23.2**, CPU; OpenCV Android **4.12.0**.
- Four intra-op CPU threads, one inter-op thread, serialized recognition.
  Session/tensor/result/native Mat lifetimes are explicitly scoped off main.
  CPU arenas are disabled to avoid retained buffers for dynamic line widths.
- Existing ImagePreprocessor applies all EXIF rotation/reflection and sRGB software
  decoding, bounded to 12 MP / 4096 px; content URIs are copied privately.
  Review uses its smaller independent preview budget.
- Detection scales to at most 1600 px with dimensions divisible by 32. BGR channels
  are divided by 255 and normalized by ImageNet mean/std in CHW format.
  DB threshold 0.3, box threshold 0.55, unclip 1.5; recognition is bounded to 384 lines.
- Perspective line crops use the original full-budget decoded image, preserving
  small text after detection downsampling. Crops run sequentially rather than
  retaining a bitmap for every region. Recognition height is 48, dynamic width
  up to 3200, minimum padded width 320, normalized padding zero.
- **eslav uses BGR**, as specified in its configuration, with normalization
  `pixel / 127.5 - 1`. The default Android SDK recognizer swaps BGR to RGB;
  that behavior is deliberately not applied to eslav.
- Greedy CTC removes blanks and repeated adjacent indices, preserving repeats
  separated by blanks. Confidence averages emitted character probabilities.
- Predominantly vertical labels are rotated and detected again; low-confidence
  line crops are also evaluated at 180°. Predominantly inverted recognition
  transforms coordinates before reconstructing paragraph order.
- Cancellation is checked between synchronous native operations. An in-flight
  native call finishes before its resources are closed; no main-thread inference.

Reading order uses box height/vertical proximity and horizontal order. It is a
heuristic for label paragraphs, not a complete document-layout engine.

## Deterministic extraction

NutritionLabelParser uses explicit RU/EN nutrition context and units. It handles
comma/dot decimals, common Latin/Cyrillic homoglyphs, wrapped nutrient labels and
hyphenated Russian words. It distinguishes per 100 g, per serving, ambiguous
tables, net weight and consumed amount. Ingredients, storage/manufacturer/cooking
sections terminate nutrition extraction. Conflicting candidates stay missing.

Declared kcal take priority over kJ. kJ-only energy uses exact BigDecimal division
by **4.184**, rounded HALF_UP to two places. No language model performs arithmetic.
Only values with confidence ≥ 0.8 and supported basis fill the editor; header
confidence participates in nutrition confidence. Raw text remains available.
OCR probability does not prove factual correctness. Product names use title
geometry and, for short categories, the explicit culinary product descriptor.

## Native packaging and licensing

Paddle code/model metadata: Apache-2.0; ONNX Runtime: MIT; OpenCV: Apache-2.0 with
third-party notices. Preserve the included `ocr/licenses` and `ocr/NOTICE.txt`.
The OpenCV SDK/ORT notice collections include optional providers/components and
do not imply every listed component is linked on Android. ITT uses its BSD option.
The actual ARM64 OpenCV build information was inspected on the OnePlus 7T: no
Intel IPP or non-free OpenCV algorithms are enabled. Its linked image libraries,
protobuf, TBB/ITT and ADE are covered by the SDK notices; additional full notices
are retained for libwebp, LLVM libc++, Carotene (NVIDIA BSD-3-Clause), and
[KleidiCV 0.5.0](https://gitlab.arm.com/kleidi/kleidicv/-/tree/f5996f99977c37f738344e027b4751158e4e3a48)
(Arm Apache-2.0), as pinned by the OpenCV 4.12.0 source.

The shipped ABI is **arm64-v8a**: OnePlus 7T/12 and ARM64 emulators. Upstream
OpenCV x86 includes proprietary Intel IPP whose binary-only/no-modification terms
must not be included in this GPL app. x86 emulator support requires an OpenCV
build without IPP plus renewed native licensing/alignment verification.

This software is based in part on the work of the Independent JPEG Group.
See [LICENSING.md](LICENSING.md) for remaining pre-existing app-wide inventory work.

## Initial implementation verification (2026-10-11)

The historical full-label results below are retained for comparison. Current
production optimization and the resolved case 2 energy field are documented next.

Physical device: OnePlus 7T HD1900, Android 11. OnePlus 12 was not connected.

A **rendered-text image, not a real package photograph**, exercised the actual
bundled models on-device. Russian and English lines were recognized and the parser
returned net 240 g, kcal 183.2, protein 11 g, fat 6.4 g and carbohydrates 20.4 g.

| Measured run | Session initialization | Detection | Recognition | Total |
| --- | ---: | ---: | ---: | ---: |
| First process smoke | 293 ms | 1002 ms | 774 ms | 2182 ms |
| Separate process smoke | 303 ms | 975 ms | 732 ms | 2139 ms |
| Final-build smoke | 299 ms | 1017 ms | 747 ms | 2212 ms |
| 90° rendered label | 302 ms | 1759 ms | 717 ms | 2890 ms |
| 180° rendered label | 291 ms | 891 ms | 1182 ms | 2401 ms |

Session initialization includes reading/loading both models, not all app startup
or OpenCV first-library-load time. Each recognition opens fresh sessions and
releases them after use. Timings are monotonic measured values, not estimates.

Smoke process high-water RSS: **387,512 KiB**; post-recognition PSS: **137,101 KiB**.
Rotation process high-water RSS: **409,812 KiB**; PSS: **155,969 KiB**.
Final-build smoke high-water RSS: **372,920 KiB**; PSS: **158,025 KiB**.
These include instrumentation/Android/native allocations and are not isolated
model heap profiling or measurements for 12-MP package images. A main-looper
heartbeat ran 55 times during this OCR run; maximum scheduling delay was **2 ms**.
This verifies responsiveness in that run, not a complete frame/jank profile.

ARM64 debug baseline from HEAD `15aed8e`: **65,508,686 bytes**.
Final OCR ARM64 debug APK: **122,994,444 bytes**; increase **57,485,758 bytes**
(54.82 MiB). Model and native-runtime packaging dominate the increase.
No production release was made.

### Build, tests and workflow verification

- Gradle `testDebugUnitTest`: full suite passed; final focused OCR/parser,
  FoodValidator and EditorViewModel rerun passed after the final parser changes.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` passed. Lint reports
  0 errors and 6 pre-existing warnings (LiteRT-LM version, launcher preview loading,
  obsolete launcher resource qualifier, three unused strings).
- `minifyReleaseWithR8` passed without signing/packaging a release. Release-minified
  OCR execution itself remains unverified on-device.
- Physical tests passed for actual-model Cyrillic/English pixels, 90°/180° labels,
  main-looper responsiveness, no proprietary IPP and 16-KiB ELF LOAD alignment
  of the four new native libraries. Final APK ZIP alignment also passed
  `zipalign -c -P 16 4`. This checks packaging, not runtime behavior on a 16-KiB-page
  Android device.
- Device UI tests passed: one-time manual-name focus/IME and recreation, picker
  failure/retry/manual fallback, private-image cleanup, explicit OCR confirmation
  and single save to the selected past day, existing diary CRUD, and five CameraX
  meal capture/retake cycles with recreation and orientation changes.
- The OCR confirmation screen was captured and visually inspected on the device.
  Scan meal and Local AI Lab remain independent.

### Real supplied photographs

The original photographs were subsequently supplied in the phone's Download
folder and visually matched to all three examples. They are 3072 × 4096 pixels;
the existing 12-MP decode budget produced 3000 × 4000 upright pixels. Both runs
executed the actual bundled models on OnePlus 7T, with no mock OCR or diary writes.
Expected values remain test assertions only.

The first run found parser failures for case 1's compact net-weight label and
detached/doubled protein dash, and case 2's Latin-lookalike net-weight label and
title contaminated by the culinary description. These general parser issues
were fixed and covered by focused regression tests. An isolated punctuation box
does not lower the confidence of otherwise confident nutrient words/numbers;
meaningful low-confidence evidence still prevents prefilling.

**Final structured results, compared with the supplied references:**

| Case | Name | Net g | kcal / 100 g | Protein g | Fat g | Carbs g |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 1: rice/chicken brizol | Correct words | 200 ✓ | 196.8 ✓ | 9.7 ✓ | 8.8 ✓ | 19.7 ✓ |
| 2: tagliatelle/chicken/cheese | Correct consumer title | 240 ✓ | **184.18 ✗**; reference 183.2 | 11 ✓ | 6.4 ✓ | 20.4 ✓ |
| 3: Asian beef bowl | Correct full name from legal title | 205 ✓ | 98.5 ✓ | 5.9 ✓ | 3.7 ✓ | 10.4 ✓ |

Names are assessed ignoring uppercase and quotation/spacing formatting. This is
a field comparison on three photographs, not a character-error-rate benchmark
or a general accuracy estimate. **14/15 numeric fields match**, and all three
names contain the expected words.

**Raw OCR accuracy is separate from parsing:** case 1 contains all target words
and numbers but merges `МАССАНЕТТО` and splits the protein separator into boxes.
Case 2 recognizes all target numerical strings, but transcribes mass as
`MACCA HETTO` and drops the first letter of `ккал`, yielding
`770,6 кДж/183,2 кал.`. Case 3's decorative title loses spacing/characters, but
its legal title and required nutrition/net-weight values are correctly readable.

Case 2's remaining energy mismatch is an OCR unit failure. The parser does not
invent a missing `к` or substitute the reference value; it converts the valid
770.6 kJ using 4.184 to **184.18 kcal**. This differs from the declared 183.2 kcal
and requires user correction. The strict `realLabelFixtures` integration test
therefore still **fails one field assertion**; it is not reported as passing.
All final extracted values exceed the current prefill threshold, illustrating
why model/line confidence cannot replace user review.

| Final photo run | Session initialization | Detection | Recognition | Total |
| --- | ---: | ---: | ---: | ---: |
| Case 1 | 294 ms | 1922 ms | 11321 ms | 13885 ms |
| Case 2 | 289 ms | 1750 ms | 16379 ms | 18658 ms |
| Case 3 | 291 ms | 1728 ms | 18027 ms | 20279 ms |

The dense full-label photographs take substantially longer than rendered-text
smoke images. Final-run process high-water RSS reached **505,352 KiB**;
post-recognition PSS was **133,003 KiB**. RSS is cumulative for the instrumentation
process, not a separately isolated peak for each image or a heap allocation profile.
The first run reached 510,500 KiB. These real-photo tests did not measure frame jank.

[Recorded OCR boxes, confidence, metrics and parser JSON](ocr-verification/2026-10-11/README.md)
preserve the final run. Original photos are not bundled into the app/repository.
The corrected debug APK was installed on the phone. Focused OCR unit tests,
debug/application-test builds and lint passed after the fixes; real-photo
integration retains the single energy failure described above.

### Reproducing device tests

Build/install the application and instrumentation APK using the Gradle Wrapper.
Push the original photos as `case1.jpg`, `case2.jpg`, `case3.jpg` into
`/sdcard/Android/data/com.justcal.app.debug/files/ocr-fixtures/`, then run:

```sh
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ocr.PackageOcrDeviceTest#realLabelFixtures' \
  -e realOcrFixtures true \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The test writes raw OCR JSON and parsed JSON into sibling `ocr-results/` and reports
measured timings, model confidence and process memory. Without the explicit flag,
real-photo tests are skipped, not reported as successful. Missing requested files
fail the test. Smoke/rotation tests execute real models on rendered pixels.

## Measured optimization — 0.7.1 (2026-10-11)

The following supersedes the initial full-label latency and case 2 energy limitation
above. The same original three images and unchanged FP32 model hashes were run on
the same OnePlus 7T HD1900 / Android 11. OnePlus 12 was unavailable.

### Per-region profile and accepted changes

Opt-in profiling records region index, phase, normalized width, crop/preparation,
synchronous ONNX inference and CTC decoding time in microseconds. Trace sections
also mark each native recognition operation. This is wall-time instrumentation,
not a sampled native call-stack or isolated heap profile.

With the original two-thread full pass, native recognition inference consumed
**93.5%, 94.1%, 96.2%** of recognition time for cases 1–3. Kotlin tensor preparation
was only 88–142 ms per photo, and CTC decoding 14–16 ms. Long ingredient, cooking
and address rows were the bottleneck: normalized widths reached 2564 px and
individual unnecessary lines took up to 1.10 seconds. Rewriting tensor loops or
CTC would address very little of the measured cost.

Production now probes each detected line's left prefix at the original glyph
scale, bounded to recognition width 320, then fully recognizes title, weight and
nutrition regions using geometry and section cues. Short complete probe lines may
be reused. **Truncated probes never enter the nutrition parser.** All six fields
must be confident and explicitly per 100 g before stopping. Incomplete/uncertain
results and predominantly inverted labels trigger the full remaining pass;
completed full lines are reused. Serving-only fallback was tested on real model
execution. Fallback can incur extra probe cost on difficult labels.

The first prefix candidate was rejected: a broad Russian cue matched ingredient
“пищевое” and occasional reversed decorative text forced full fallback. It took
13.956 / 17.969 / 18.028 s. The accepted selector uses the nutrition-heading cue
and the existing predominant-inversion rule. On these fixtures it required no
full fallback and returned 31 / 21 / 24 complete lines from 49 / 50 / 39 detections.

### Threads, latency and accuracy

These are individual sequential measured passes, not medians or a statistically
controlled benchmark. No frequency/thermal lock was applied.

| Mode / total seconds | Case 1 | Case 2 | Case 3 |
| --- | ---: | ---: | ---: |
| Full label, 1 thread | 22.032 | 30.011 | 33.818 |
| Full label, 2 threads | 14.271 | 19.025 | 20.803 |
| Full label, 4 threads | 12.317 | 15.629 | 16.275 |
| Selected regions + unit retry, 4 threads | 8.319 | 8.144 | 9.682 |
| Production defaults, strict regression run | 8.379 | 8.594 | 9.786 |

Production uses four intra-op threads, one inter-op thread, with serialized,
background inference and the same BGR/CHW normalization, 48-px recognition height,
dictionary, dynamic-width bound, detector thresholds and full-resolution crops.
Compared with the fresh two-thread profile, production total latency decreased
by **41–55%**, and recognition by **48–61%**.

| Production regression | Initialization | Detection | Recognition | Total |
| --- | ---: | ---: | ---: | ---: |
| Case 1 | 313 ms | 1656 ms | 6107 ms | 8379 ms |
| Case 2 | 301 ms | 1476 ms | 6585 ms | 8594 ms |
| Case 3 | 303 ms | 1464 ms | 7792 ms | 9786 ms |

All **15/15 numerical fields** match the original reference expectations. All
three product names still match the expected words ignoring case/quotes/spacing.
Strict `realLabelFixtures` now passes; its expected values were not relaxed.

For case 2, one retry trims 12% of the line crop's top/bottom detector margin and
reruns the same model/preprocessing. It actually recognizes `183,2 ккал.`.
A retry is accepted only with confidence ≥ 0.8, an explicit kcal unit and **all
numerical tokens unchanged**. The original `183,2 кал.` remains in the review
snapshot and raw JSON alternatives. No missing letter or energy amount is
invented. If this ambiguity remains, the deterministic parser retains the valid
kJ conversion as uncertain evidence and leaves energy prefill empty. Ordinary
kJ-only labels still use exact division by 4.184.

This is field accuracy on three images, not a general OCR accuracy estimate.
Raw OCR still contains unrelated word/spacing errors; only the required fields
are assessed. Numerical extraction and OCR text quality remain separate.

### Session reuse, memory and packaging

Case 2 was repeated twice in one process, using the same selected-region pipeline:

| Session policy | First total / init | Second total / init | Process high-water RSS |
| --- | ---: | ---: | ---: |
| Fresh each scan | 8393 / 310 ms | 8181 / 303 ms | 514504 KiB |
| Reused | 8434 / 315 ms | 5849 / 0 ms | 569628 KiB |

Warm reuse helps beyond skipping model loading, but this observed run adds about
54 MiB to cumulative high-water RSS. PSS snapshots were 136953 KiB fresh versus
141404 KiB reused. Production **keeps fresh sessions and closes them after every
scan**; reuse remains opt-in for benchmarks. Broader memory/lifecycle/device
qualification is required before retaining sessions.

The strict production regression process reached 517588 KiB high-water RSS;
post-recognition PSS was 116691 KiB. This includes the app, instrumentation,
decoded image and native buffers, not an isolated OCR heap peak. The rendered
RU/EN smoke test kept 49 main-looper pulses with maximum scheduling delay 2 ms;
real-photo frame jank was not recorded.

No models or runtime dependencies were added by this optimization. The final
installed ARM64 debug APK is **123095524 bytes (117.39 MiB)**, versus the initial
OCR APK's 122994444 bytes: +101080 bytes (98.71 KiB). A clean-build snapshot before
the final reading-order guard change was 122841269 bytes. Small differences
between clean/incremental DEX packaging are not evidence of model/runtime size
optimization. Bundled production models remain 12714145 bytes. Final APK ZIP
alignment passed `zipalign -c -P 16 4`; no experimental ncnn assets are packaged.

### ncnn investigation and qualification boundary

[Tencent ncnn](https://github.com/Tencent/ncnn) uses BSD-3-Clause, compatible with
GPL-3.0-or-later; the models retain their Apache-2.0 provenance. Its
[PP-OCRv5 example](https://github.com/Tencent/ncnn/blob/master/examples/ppocrv5.cpp)
and the [maintainer Android sample](https://github.com/nihui/ncnn-android-ppocrv5)
were inspected. The sample's default recognition assets/dictionary cannot be
assumed to support Russian, and its dynamic-shape handling cannot be blindly
copied for eslav.

The portable official [pnnx 20260704](https://github.com/pnnx/pnnx/releases/tag/20260704)
converter successfully exported **our exact detector and eslav ONNX models** with
`fp16=0`, `optlevel=2`, using the official two-shape recipes. Converter output
reports detector `[1,3,?,?]` and recognizer `[1,3,48,?]`. This does not establish
Android tensor parity or working dynamic widths. The archive SHA-256 is
`651d425937c5ca54b2052ed4a48339bbba89e1d5702f0b0068a49e7974992121`.

| FP32 converted candidate | Param bytes | Weight bytes |
| --- | ---: | ---: |
| Detector | 23506 | 4685856 |
| eslav recognizer | 19390 | 7795084 |
| Total | | **12523836** including both param files |

These local candidates are not bundled. **ncnn Android latency, memory, field
accuracy and APK size are unmeasured**, and it has not qualified to replace ORT.
There was no NDK/toolchain installed in this workspace, and no Android JNI
candidate was built. The next qualification must compare tensors/CTC classes
at actual dynamic widths, then both complete pipelines on the same three phone
fixtures, retaining eslav BGR normalization/dictionary and unchanged parsing.
Export success or a small converted weight file alone is insufficient.

Run future conversions on a copy under `.ocr-work/`: pnnx also generates a
`*.pnnxsim.onnx` beside its input even when explicit output paths are supplied.
The experimental intermediate files were moved out of production assets and
APK contents verified. Neither converter nor generated scripts enter Android.

### Verification and reproduction

Full unit suite, focused OCR regression, debug application/test builds and lint
passed. Final lint reports no errors and nine warnings on unchanged code/config:
the six existing warnings plus refreshed Gradle/Kotlin update suggestions.
The introduced trace-section lifetime warning was fixed. R8 minification passed; release-minified
execution remains unverified. Actual-device tests passed for all three real
photos, RU/EN pixels, 90°/180° reading order, and serving-only full fallback.
The package confirmation/persistence flow and separate meal/Lab flows are unchanged.

For the profile matrix, invoke the opt-in `profileRealLabelsBenchmark` method
with `realOcrFixtures=true`. Supported arguments: `ocrThreads=1..4`,
`ocrProgressive=true|false`, `ocrRefineEnergy=true|false`, `ocrReuse=true|false`,
`ocrRepeats=1..3`, `ocrCases=1,2,3`, `ocrRun=<safe-report-prefix>`.
Benchmark defaults are full/two-thread/no-reuse, independent of production
defaults, so supply flags explicitly:

```sh
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ocr.PackageOcrDeviceTest#profileRealLabelsBenchmark' \
  -e realOcrFixtures true -e ocrThreads 4 -e ocrProgressive true \
  -e ocrRefineEnergy true -e ocrRun selected-t4 \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Benchmark completion is distinct from accuracy; run `realLabelFixtures` for the
unchanged strict reference assertions.
[Curated profiles, raw text, parsed results and measured matrix](ocr-verification/2026-10-11-optimization/README.md)
are retained in Git. Private photographs, raw exploratory dumps, converter
binaries and generated models belong under ignored `/.ocr-work/`. Production
models, dictionary/configuration, notices and curated verification must remain
versioned so offline builds are reproducible.

## Limits

Glare, perspective, tiny/curved text, mixed orientations and complex multi-column
tables can lose detection or reading order. Detection has a 1600-px bound; no
dense-label tiling or dewarping model is currently bundled. Generic decoding keeps
the original information only within its 12-MP/4096-px budget. Serving-only and
ambiguous tables require manual per-100-g input. Missing units or unsupported
layouts remain editable rather than guessed. Confidence/name heuristics need
calibration beyond these three photographs. Selected-region recognition takes
8–10 seconds on the verified fixtures; difficult-label fallback can take longer.
Android 11 ARM64 is device-verified;
OnePlus 12/Android 15+ and release-minified OCR need their own device validation.
