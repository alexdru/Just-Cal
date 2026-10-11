# OnePlus 7T OCR optimization verification — 2026-10-11

The same three original phone photographs were executed through the actual bundled
FP32 ONNX models. Original pixels/model hashes match the initial verification.
Photos remain private local fixtures, not repository or APK assets.

- `baseline-t2-case*.json`: original full recognition with two threads, including
  every line's crop/preparation/native inference/CTC timings in microseconds.
- `selected-t4-case*.json`: accepted region-aware candidate with four threads,
  the same measurements, and original text retained after the case 2 energy retry.
- `production-case*.json` and `production-parsed-case*.json`: production defaults,
  strict real-photo regression, all **15/15 numerical fields** matching.
- `benchmark-summary.json`: thread matrix, rejected initial prefix candidate,
  cache comparison, memory snapshots and ncnn conversion qualification.

Production retains per-scan session disposal. Region selection probes every detected
line's bounded left prefix; only complete full lines enter the parser. Unknown,
incomplete or uncertain results invoke a full pass. Scores are OCR confidence,
not proof that a label is correct. Names match expected words ignoring case/quotes.

The case 2 retry recognizes `183,2 ккал.` directly from a tighter crop; the original
`183,2 кал.` remains in the raw output. Numerical tokens must agree before accepting
a retry. No reference values or spelling substitution are used in recognition.

Single sequential benchmark passes are observed samples, not medians or an accuracy
estimate beyond these three photographs. RSS is process-wide cumulative high water;
PSS is a post-recognition snapshot, not an isolated model heap profile. The final
rendered-label heartbeat had 49 main-looper pulses and maximum 2 ms scheduling delay;
that is not a real-photo frame/jank profile.

ncnn models were converted with the portable official pnnx binary. This is **not**
an Android inference/accuracy/APK comparison. Candidate tensor parity, dynamic shape
behavior and identical image/device benchmarks remain unverified; ncnn is not shipped.
See [PACKAGE_OCR.md](../../PACKAGE_OCR.md) for implementation and reproduction.
