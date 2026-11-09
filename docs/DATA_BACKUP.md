# Diary and food catalog backup

Settings → Data & Backup uses Android CreateDocument/OpenDocument (SAF) without storage
permission. Just Cal exports one portable format: schemaVersion 2 with diary entries, saved
products, immutable revisions and explicit entry-product links. Photos, raw OCR, models,
settings, diagnostics, search projections and temporary editor UUID reservations are excluded.
Files are private user-selected exports; there are no automatic uploads.

## Portable representation

Every file requires format: "just-cal-diary", schemaVersion: 2, exportedAtMillis, appVersion,
entries, products, revisions, links and catalogUnits: "hundredths-per100g". Older schema
versions are unsupported and are rejected before preview with a clear format message.

Entry fields are stable UUID id, ISO local date, createdAtMillis, name, source,
consumedAmount, consumedUnit: "g", nutritionBasis: "per100g", caloriesKcal,
proteinGrams, fatGrams and carbohydratesGrams. Nutrition and amount are exact decimal
strings with at most two places. Local numeric database IDs are not exported.

Product fields are id, createdAtMillis and archived. Revision fields are id, productId,
previousRevisionId, name, energy, protein, fat, carbs, savedAtMillis, brand, manufacturer,
retailer, deliveryService, packageMassHundredths and origins. Nutrition values are
nonnegative integer hundredths of kcal/g per 100 g; package mass is optional positive
integer hundredths of grams, independent of consumed amount. Identity fields are
independently nullable. Origins are bounded field-origin metadata, not independent
nutritional verification. Links carry entryId (diary UUID), revisionId and provenance
(USER_SELECTED, HISTORY_PROMOTION, SCAN_ACCEPTED or USER_CORRECTION). No revision contains
a historical consumed portion.

## Validation and limits

Validate the complete graph before preview or writes: format/version, UTF-8, explicit units,
canonical unique UUIDs per type, dates/source enums, exact valid numbers, metadata bounds,
references, same-product revision parents and acyclic ancestry. Validation is iterative for
long revision chains. Missing nutrition never becomes zero. Matcher name truncation is
unrelated to backup validation; the diary name limit remains 1000 characters.

Files are bounded to 32 MiB, 50,000 entries, 10,000 products, 50,000 revisions and 50,000
links. The graph bounds and a 50,000-revision chain have synthetic unit fixtures. These
object-count limits do not guarantee the combined JSON fits 32 MiB. Export validates its
entire snapshot and encoded byte size before writing; oversized or invalid data fails clearly
rather than truncating or producing a file the importer cannot read.

## Merge, replacement and recovery

Merge preserves local values. Same UUID/same payload skips; different payload reports a
conflict without overwriting. Different UUIDs remain distinct, even for identical names or
nutrition. Conflicting products, revisions/ancestors or diary entries block dependent
revisions/links. Link equality includes provenance. Preview and results show diary and
per-catalog-type added/skipped/conflicting counts. Counts are recomputed transactionally
when committing; another local write between preview and import can change them.
Repeated imports are idempotent. Export reads a consistent Room transaction snapshot.

Replace All replaces the entire diary/catalog/link graph. The acknowledgement names this
scope explicitly. Before replacement, the complete previous graph is serialized as format 2,
fsynced and atomically moved to noBackupFilesDir/diary-recovery.json. Failure aborts before
deletion. All database deletes/inserts share one transaction; an error rolls back the graph.
Recovery remains exportable after restart, including when replacement itself rolled back.
Import the exported recovery file to restore the previous graph. A later replacement replaces
this single recovery snapshot; uninstall removes it, so export it before replacing again.

Portable backup compatibility and installed Room schema continuity are independent. Room
migrations preserve existing local IDs, UUIDs, sources, exact hundredths and dates.
1→2 UUID assignment remains unchanged; 2→3 adds empty catalog/search tables and indexes.
History projections build lazily without modifying diary entries. Direct 2→3 and chained
1→2→3 migrations, graph round-trip/reimport/conflicts, full replacement, failed recovery
write and transactional rollback remain covered by tests.
