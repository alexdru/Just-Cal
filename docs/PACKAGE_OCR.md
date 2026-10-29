# Offline package-label OCR

Persistent local content diagnostics, measured scan history and structured local reports are described in [OCR_DIAGNOSTICS.md](OCR_DIAGNOSTICS.md). The diagnostic instrumentation does not change model weights, preprocessing or nutrition calculations.

## Production flow and boundary

Scan package → CameraX / Photo Picker → Photo Review → Use photo → local OCR →
deterministic parsing → existing editable FoodEditor → explicit Add to diary.
The selected diary date is retained. No OCR result writes Room automatically.
Repeated submission is guarded by editor busy/completed state. Every field can be
corrected; missing or uncertain values remain empty and normal FoodValidator rules
apply. Net package grams can initialize Amount eaten, with a separate explanation.
New manual amounts start empty; edits retain the saved amount. Empty new manual
names request focus/keyboard once, with saved event state.

Production recognition is coordinated by FoodRecognitionRouter. PP-OCRv5 always
runs first. Explicitly selected Gemma may fill only blank protein/fat/carbohydrate
fields from a reliable nutrition-crop-v2, with OCR and model both referring to
100 g. It preserves OCR energy, title, mass and nonempty macros; model energy,
name and mass are ignored. Failure preserves the baseline after native cleanup.
The separate Paddle transcription task has no production admission. The new
[27-photo comparison and task gate](ai-verification/2026-10-28/README.md) records
82/127 correct OCR editor fields versus 94/127 with twelve correct macro
recoveries, zero wrong accepted values and zero OCR losses in this sample.
Thirty-three visible fields remain empty. This is a narrow task admission, not
full nutrition extraction; other devices/labels require broader evaluation.
AI Models and the separate AI Playground are described in [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md).
Meal assistance is name-only; numerical estimates and photo-derived weight are disabled.
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


## Verification and reproducible evidence

- [Initial OCR fixtures](ocr-verification/2026-10-11/README.md) retain raw/parsed
  reports for the original three photos. [Optimization evidence](ocr-verification/2026-10-11-optimization/README.md)
  retains the thread/latency/quality comparison behind the current four-thread profile.
  Historical implementation narratives and rejected runtime investigations remain in Git.
- [Twelve-photo review](ai-verification/2026-10-22/quality-audit.md#packages-fresh-12-photo-bundled-ocr-run)
  records 42/54 extracted and 37/54 prefilled numbers, with zero wrong nonempty numeric
  prefills in that sample. The strict complete-extraction assertion still fails;
  it is not weakened or counted as a passing quality gate.
- [Current 27-photo reference/comparison](ai-verification/2026-10-28/README.md) retains
  source hashes, baseline omissions, raw VLM attempts and the narrow Gemma macro gate.
  A safe missing value and a correctly recognized value are different outcomes.

Build/install the debug app and AndroidTest APK with the Wrapper. Place original
case1.jpg/case2.jpg/case3.jpg under the debug app's external ocr-fixtures directory:

```sh
adb shell am instrument -w -r \
  -e class 'com.justcal.app.ocr.PackageOcrDeviceTest#realLabelFixtures' \
  -e realOcrFixtures true \
  com.justcal.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Requested missing fixtures fail; absent opt-in flags skip before fixture access.
Reports under ocr-results retain raw/parsed results and measured values. Tests on
rendered synthetic pixels exercise models/rotation, not real-label accuracy.
The twelve/27-photo instructions and other flags are in the linked evidence.

## Minified JNI contract

Keep app/proguard-rules.pro: ONNX Runtime's JNI looks up fixed Java class/member
names; its pinned Android archive does not supply the required consumer rules.
Preserve ai.onnxruntime classes/members and OpenCV CvException's String constructor.
A controlled build without the rules reproduced native SIGABRT on API 30; that
does not prove every historical interrupted scan had the same cause.

The debug-signed, non-debuggable ocrCheck variant exercises actual minified JNI in a
separate package. Its OcrJniProbe returned MINIFIED_OCR_OK on OnePlus 7T/API 30 using
a 3000×4000 rendered RU/EN label. The corrected repeat measured initialization 292 ms,
detection 1495 ms, recognition 955 ms, total 3060 ms. This is a single synthetic run,
not a real-label benchmark or production-signature qualification.
Reproduction: [RELEASING.md](RELEASING.md#local-minified-ocr-verification).

## Limits

Glare, perspective, tiny/curved text, mixed orientations and multi-column tables can
lose detection or reading order. No dense-label tiling/dewarping runtime is bundled.
Serving-only/ambiguous layouts require manual per-100-g input. Names/confidence need
broader calibration; uncertainty remains editable rather than guessed.
Minified real-package recognition, OnePlus 12/Android 16 and production-signed device
execution need separate validation. Native/driver memory-cycle evidence is incomplete.
