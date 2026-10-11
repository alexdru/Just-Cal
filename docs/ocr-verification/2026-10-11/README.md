# Real package photo verification

Device: OnePlus 7T HD1900, Android 11. Runtime: bundled PP-OCRv5 mobile detector
and eslav mobile recognizer, ONNX Runtime CPU, app version 0.7.0 / code 8.
Final run: `PackageOcrDeviceTest#realLabelFixtures`, `realOcrFixtures=true`.

| Case | Original phone Download file | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| 1 | `1791665298508_022034.jpg` | 2,464,605 | `f919b064de1215d1635fa8fbe712bbad628971f34626513bdaa492b0637e992a` |
| 2 | `1791665297791_022054.jpg` | 3,194,717 | `0b6a2512c778785d5fd172088c03b4ab8803377d091703dd2621d06b26d658d9` |
| 3 | `1791665298170_022115.jpg` | 2,630,512 | `86e8fa45f52a964c43b6e570bea71c1d5c9ca1a842fd120d669722a1ff0629b1` |

Originals: 3072 × 4096, decoded 3000 × 4000 within the existing image budget.
Only OCR/parsed reports are retained here; originals are not app assets.

- [Case 1 raw OCR](case1.json) and [parsed fields](case1-parsed.json).
- [Case 2 raw OCR](case2.json) and [parsed fields](case2-parsed.json).
- [Case 3 raw OCR](case3.json) and [parsed fields](case3-parsed.json).

The raw files include actual recognized strings, quadrilaterals, line confidence,
and measured timing. The parsed files preserve actual values, confidence and
evidence without replacing results with expectations.

Final numeric comparison: cases 1 and 3 match all five numeric reference fields.
Case 2 matches net weight and all macros, but energy is 184.18 kcal instead of
183.2 because OCR reads `183,2 кал.` and the parser falls back to 770.6 kJ / 4.184.
The strict integration test fails that assertion. All three product names contain
the expected words after ignoring case/quotation spacing. The full evaluation
and first-run parser issues are documented in [PACKAGE_OCR.md](../../PACKAGE_OCR.md).

These tests only recognize and parse; they do not save diary entries.
