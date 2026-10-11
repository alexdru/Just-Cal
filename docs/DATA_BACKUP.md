# Diary backup format 1

Settings → Data & Backup uses Android CreateDocument/OpenDocument (SAF), with no storage permission. The JSON is independent of package name, build variant and device. It contains diary entries only, never diagnostic logs, photographs or preferences.

Required metadata: `format: "just-cal-diary"`, `schemaVersion: 1`, `exportedAtMillis`, `appVersion`, `entries`. Each entry carries a stable UUID `id`, ISO local calendar `date`, `createdAtMillis`, `name`, `source`, `consumedAmount`, `consumedUnit: "g"`, `nutritionBasis: "per100g"`, `caloriesKcal`, `proteinGrams`, `fatGrams`, `carbohydratesGrams`. Nutrition and consumed amounts are decimal strings with at most two decimal places, preserving Room's exact integer hundredths without floating point. Nutrients are per 100 g; consumed amount is independent.

Room version 2 adds a unique portable UUID to every existing entry. Local numeric primary keys remain private and existing IDs, dates and values are preserved. Edits retain portable identity. No destructive migration fallback is used.

Import validates the complete file before preview or writes: known format/version, explicit units, UUID uniqueness, required fields, UTF-8, dates, source, nonnegative exact numeric values and positive consumed amounts. Unsupported/newer schemas are rejected. Input is bounded to 32 MiB and 50,000 entries; exceptionally larger exports require a future streaming importer. Export includes all existing entries.

Merge is the default. A new UUID is inserted; an identical UUID and payload is skipped; different data under an existing UUID counts as a conflict and leaves the existing entry untouched. A repeated import creates no duplicate entries. Two independently created entries with different UUIDs remain distinct even if their content matches.

Replace All requires a second dialog and explicit acknowledgement. The pre-replacement diary is written and fsynced to an atomic private recovery JSON before any deletion. All deletes/inserts share one Room transaction; a failure rolls them back. A recovery-write failure aborts replacement. The latest recovery survives app restart in noBackupFilesDir and can be exported from this screen, then imported to restore records. A later replacement replaces that recovery; uninstall removes it. Save recovery externally before another replacement.

Backups contain personal food diary content. SAF destinations are chosen by the user; keep exported files private. There are no automatic uploads.
