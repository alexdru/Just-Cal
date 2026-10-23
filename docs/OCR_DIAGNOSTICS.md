# Local OCR diagnostics format 1

Settings → OCR Diagnostics provides swipeable overview, history, performance, errors and retailer/brand views. Tab clicks and horizontal swipes use the same Compose Pager state. One shared transparent bottom Save/Share host applies to the full history or selected scans, independent of the displayed tab. Lists extend behind it, with measured end padding and focus clearance. Overview emphasizes the scan count; individual scans open a native bottom sheet with optional text/runtime disclosure. It is completely local. No analytics service, telemetry, upload worker or network transmission is added. AI Playground uses the retained LocalAiLab route and records separate EXPERIMENT attempts. Additive optional AI fields retain artifact/runtime/backend, prompt profile, measured stages and bounded raw output without changing diary Room or the format-1 envelope. Sanitized exports omit experimental prompts and raw responses; no photograph is attached automatically. See [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md).

## Capture and interpretation

Every package-recognition attempt has a UUID and wall-clock timestamp, app version, Android release/device model (no sensitive device ID), model/runtime provenance, normalized upright image dimensions and outcome. Existing Paddle metrics supply initialization, detection and recognition times; monotonic instrumentation measures parsing and total time. Unfinished stages are null, never estimated. Region count and finite line-confidence mean/min/max are descriptive model output, not nutrition accuracy.

Outcomes distinguish running, success, partial extraction, failure, cancellation and process interruption. A queued start marker is updated under the same UUID. On process restart an unfinished marker becomes interrupted; its unmeasured total is unavailable. Android 11+ historical process-exit information supplies a `PROCESS_EXIT_REASON:<Android reason code>` when available. No process trace, private path, PID, UID or device identifier is retained. Native Java linkage failures are recorded as recoverable `OCR_RUNTIME_LINKAGE_FAILURE:<exception type>` instead of terminating the review flow. Timing aggregates exclude running/interrupted markers. Missing/ambiguous fields and recognition/parser error categories remain explicit. Failure messages are restricted to safe categories; native exceptions and paths are not logged to production Logcat. Timing aggregates include labelled attempt sample counts; cancelled/failed scans are distinguishable in history. Slowest scans are independently inspectable.

Explicitly changed editor fields are compared to the OCR prefill after a successful diary save, using normalized decimal comparisons. An unchanged field is not verified ground truth. Missing-field completion differs from an explicit correction; amount eaten is a user portion adjustment, never counted as evidence of extraction error. Net-weight diagnostic correction and optional retailer/brand annotations do not modify diary records.

Only explicit packaging attribution cues identify producer/brand/retailer in Russian or English. Arbitrary product titles do not imply a retailer. Unknown remains Unknown. Brand-group counts and correction frequencies are descriptive; small samples cannot establish model accuracy.

## Privacy and retention

Detailed local diagnostics are enabled for all future scans by default, including upgrades where the former detailed setting was disabled. They retain bounded OCR text, parsed fields, product/brand and later corrections. There is no basic/detailed switch or consent dialog. Original photos and private URIs/paths are not retained. Previously captured basic records remain basic; their missing details cannot be reconstructed. The format-1 field `detailedConsent` is retained for compatibility and identifies records with detailed capture; it no longer represents a user-selected setting.

The diagnostics database resides in noBackupFilesDir, separate from diary Room and isolated from diary failures. Serial asynchronous writes preserve attempt/correction order. Clear invalidates in-flight attempts. Records expire after 30 days with at most 500 scans and an 8 MiB serialized-payload budget, pruned on initialization, writes and periodically while the process is active. The budget bounds payload rather than promising an exact SQLite file size. No original image is retained automatically.

## ZIP reports

Save uses SAF CreateDocument. Share invokes Android Sharesheet through a private cache FileProvider with temporary read permissions. Both require explicit report preview. Choose all scans or selected scans. Consumer report exports include retained full OCR text, product/brand names, parsed nutrition and correction values by default, without an inclusion checkbox. Historical basic scans remain missing these details. Sensitive device IDs and private paths stay excluded; original photos are never attached automatically. Optional photos are selected explicitly through Photo Picker for that export, at most five bounded attachments; their association with scans is not inferred. They may contain personal content and EXIF metadata and require the sensitive-content warning. Photos are not added to persistent diagnostic history.

Archive members:

- `summary.json`: schemaVersion 1, creation timestamp, sample counts, outcome/timing summary, model/runtime information and content flags.
- `scans.jsonl`: one structured scan per line, stable session ID, stages/status/confidence/missing/error/correction-field data.
- `corrections.json`: session-linked correction records, including recorded values in consumer reports.
- `README.txt`: units, null semantics, privacy and interpretation limitations.
- `photos/selected-N.image`: only explicitly selected attachments, when requested.

Reports expire from private cache after 24 hours on subsequent report generation. Exported external files are controlled by the user. Clear history does not delete previously saved external reports. No report is uploaded automatically.
