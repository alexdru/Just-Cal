# UI/UX redesign — Stage 1

Implemented 2026-10-10. Application 0.5.0, versionCode 6.
This is an implementation and verification record, not another full design audit.

## Changes

- CalSpacing formalizes 4 / 8 / 12 / 16 / 20 / 24 / 32 dp.
  CalLayout centers diary content within 840 dp and Settings within 600 dp.
  CalTopBar aligns root titles with page margins while preserving child back controls.
- Platform sans-serif, existing shapes, branded and dynamic palettes remain.
  Numeric roles request tabular figures; bodySmall and labelSmall are explicit.
- Home retains one dominant calorie container, flat macros and quieter empty content.
  Food rows separate names, eaten weight, trailing calories and secondary macro detail.
- Diary uses compact chronological date/count + trailing energy rows.
  Day Detail leads with the selected date and weekday; both navigation entry paths remain.
- Settings groups profile, nutrition goals, appearance, language and About.
  Brightness, palette and language use radio rows with single-selection semantics.
  Save boundaries are stated explicitly; language remains immediate.
  Save status reserves space, and focus scrolling clears measured floating controls/IME.
- Add Food explains unavailable recognition before scan/source selection.
  Package, meal and manual actions remain; photos do not create entries.
- Floating navigation retains its content-sized centered pill, compact active fill,
  separate FAB, transparent host and reserved action row. Minimum nominal tab width
  is 96 dp, constrained by available space; horizontal label padding is 4 dp.

No new dependencies, navigation architecture, calculations, persistence changes,
production recognition or cloud functionality. Local AI Lab was not opened,
modified, reviewed or tested. Shared theme changes are necessarily application-wide.

## Audit findings

F01 resolved by capability disclosure.
F02 resolved for Settings only; the Lab part is explicitly deferred.
F03 addressed for Stage 1 screens through bounded, centered content.
F04 addressed through shared spacing, type and title-alignment conventions.
F05/F06 addressed through hierarchy, compact rows and selected-date identity.
F07 addressed through grouping and explicit Save/immediate boundaries.
F09 addressed with overlay-aware focus scrolling and a focused-field device check.
F12 numeric presentation improved; motion and predictive-back architecture unchanged.
Editor, camera/review and Lab redesign findings remain outside this milestone.

## Device evidence

Android Studio MCP built and launched the application on
Medium_Phone_API_37.0 (emulator-5554). Screenshots were captured and viewed in the
implementation session; they are tool-result evidence, not committed image files.

| Configuration | Screens / behavior actually inspected |
| --- | --- |
| English, Just Cal Light, default text | Empty and populated Home; two-day Diary; Oct 9 Day Detail with two entries; Settings profile/goals, radio selection and Save |
| Russian, Just Cal Dark | Populated Home, Settings selection/language, localized dates and decimal commas |
| Russian, 200% font | Home, two-day Diary, populated Day Detail, Add Food; Settings fields and focused carbohydrate goal with keyboard above floating navigation |
| Narrow 320 dp, 200% text | Russian Day Detail and Settings during refinement; final English empty Home and final scroll clearance; growing content, no text shrinking |
| Russian, Material You Light/Dark | Home and Settings; final capability/source sheets in dynamic Light |
| Short landscape / about 914 dp width | Home and Settings content bounds, common title/content axes, centered pill and independent FAB; vertical scrolling |
| Settings lower content | Language and About, GPL attribution/version and repository row |

The wide-window screenshots exposed an initially left-aligned bounded viewport;
the outer width was corrected and centered Home/Settings were verified again.
Russian screenshots exposed crowded navigation labels; tab sizing/padding were
refined and reviewed at enlarged text sizes. Automatic hyphenation was removed
after it produced undesirable three-line syllable breaks on the narrow window.
Ordinary font-scaled wrapping remains supported.

Temporary entries were created through the manual production flow, on the emulator
only: yogurt/berries today, chicken/rice and toast/avocado on Oct 9.
Historical FAB preserved the selected day. Editing yogurt from 180 g to 190 g
updated Home from 198 to 209 kcal. Each temporary entry was then deleted through
the existing confirmation flow; Home and history returned to empty.
No physical-device diary was changed. Theme/language choices were restored.

## Automated validation and limits

- Narrow domain tests first: 20 passed.
- Relevant domain and production UI unit tests: 29 passed, 0 skipped, 0 failed.
  Filters: com.justcal.app.domain.*, com.justcal.app.ui.EditorViewModelTest,
  com.justcal.app.ui.NutritionFormattingTest. AI test classes were excluded.
- :app:assembleDebug and :app:lintDebug succeeded.
- Changed-file IDE diagnostics were inspected; no compile errors remain.
- git diff --check passed.
- The natural-language journey tool failed with Model query failed: UNKNOWN.
  Verification continued using explicit input, hierarchy and screenshots.

Selection roles/state and visible radio indicators were checked, along with
headings, locale formatting, scrolling, IME clearance and native minimum targets.
This is representative visual coverage, not every screen in every palette/window.
TalkBack speech, hardware-keyboard traversal, physical predictive-back gestures,
foldables and numerical contrast sampling of arbitrary wallpaper palettes remain
unverified. No performance or accessibility certification is claimed.
