# UI/UX redesign — Stage 2

Implemented on 2026-10-10 in app **0.6.0 (versionCode 7)**, using the installed
material-design-3-ui and material-3 skills and the approved DESIGN.md.
This is an implementation/verification record, not another full audit.
Local AI Lab was excluded from implementation, inspection and test selection.

## Changes

- Manual entry has explicit name / per-100-g / consumed amount groups, a compact
  tabular calorie preview and secondary macro detail. Two-column nutrition inputs
  require 160 dp per field multiplied by font scale; narrow/enlarged layouts stack.
  Fields, preview and confirmation share the centered 600 dp reading bound.
- IME Next follows name → calories → protein → fat → carbs → amount; Done clears
  focus. Submission focuses the first invalid field and exposes localized error
  semantics with polite announcements. Existing overlay-aware focus scrolling
  remains; confirmation stays above IME in ordinary windows.
- A verified short-landscape/200% text obstruction is corrected: when IME leaves
  less than 280 dp height, app bar and confirmation temporarily yield to the field.
  Done or keyboard Back restores the normal chrome. Draft/calculation/persistence
  behavior is unchanged.
- Camera retains dominant preview and CameraX behavior. The photo-picker alternative
  is an accessible icon, avoiding long Russian app-bar action collisions. Horizontal
  safe insets and bounded fallback content are explicit. The 88 dp black/white
  shutter composition remains visible against mixed bright/dark scenes; busy
  capture has a localized state description.
- Photo Review uses Fit throughout, meaningful package/meal guidance and early
  recognition disclosure. Pixel dimensions no longer compete with the image.
  Portrait image/actions retain stable half-height regions; actions scroll
  independently. Landscape remains side-by-side, bounded to 840 dp. Confirmation
  changes copy/action without shrinking the image and never logs food.
- Settings macro disclosure exposes expanded/collapsed state. Shared macro units
  and “100 g” stay with their numbers using nonbreaking spaces in English/Russian.
- Existing restrained fades, selection feedback, progress animation and Navigation 3
  predictive back remain. No decorative motion, dependencies or new architecture.

## Audit follow-through

| Finding | Stage 2 outcome |
| --- | --- |
| F01 | Existing launcher disclosure preserved; review now discloses before confirmation, with truthful endpoint copy |
| F03 | Reading bounds extend to editor/source fallback/review; short-IME adaptation implemented |
| F08 | Explicit grouping, measured one/two columns, IME progression and invalid-field focus implemented |
| F10 | Useful guidance replaces pixel metadata; image geometry stays stable after confirmation |
| F09 | Stage 1 overlay-aware Settings scrolling retained; no new Settings architecture |
| F12 | Tabular numeric roles retained; disabled Compose animation and gesture Back exercised; timing/gesture-cancellation coverage remains incomplete |
| F02/F11 Lab portion | Outside this milestone; no Lab review or tests |

## Visual and interaction evidence

Actual screenshots were captured and inspected through Android Studio/explicit
ADB on **Medium_Phone_API_37.0, emulator-5554**, with gesture navigation.
These rows describe sampled states, not every screen × locale × palette combination.
Screenshots were shown in the implementation session; temporary PNGs remain under
/tmp/justcal-stage2-*.png and are not release assets or checked-in fixtures.

| Sample | Configuration and inspected state |
| --- | --- |
| Home / Diary / Day Detail | Empty and populated branded Light; populated branded Dark Russian; populated Material You Light/Dark English; day row, selected date, food name/weight/macros/energy |
| Settings | English Light and Russian Dark; radio state, Save reach after scrolling, immediate language recreation; Material You Light selection and applied palette |
| Add Food | English and Russian disclosure/manual access; narrow 320 dp Russian at 200% requires normal sheet scrolling |
| Editor | Empty and populated English Light; validation/first-invalid focus; 180 g yogurt at 110 kcal/100 g gives 198 kcal; Russian Dark at 200%, single column, amount/helper clear of IME |
| Editor adaptation | 320 dp narrow window, wide/short landscape and centered width; Material You Light/Dark; 200% docked landscape IME shows unobstructed name after chrome yields; closing IME restores confirmation |
| Camera | Denied-permission recovery/photo alternative; virtual camera scene with bright window and dark furniture; Russian Dark at 200% and Material You Light; contrasting shutter and icon-only source action |
| Review | Russian Dark at 200% portrait/landscape; scroll reaches Use photo/Retake; Material You Light before/after confirmation with identical image geometry; full-image Fit retained |
| Back / motion | Back from editor/source/review; committed edge-swipe editor → Home; editor opens and returns with animator_duration_scale=0 |

The disposable Stage2 yogurt entry was created through the manual UI, viewed in
Home/history/detail/editor, then deleted through the confirmed delete action.
Home returned to zero and Diary to empty. Photo confirmation left the 198 kcal
diary unchanged before deletion. Automated fixtures clean up their diary/gallery
and owned-image data. Appearance, language, font scale, window size/rotation,
temporary IME/animation settings and camera permission were restored; the emulator
started for this task was stopped. No user diary entries were overwritten.

## Accessibility and limits

Material targets remain at least 48 dp; shutter, confirmation and native icon
buttons retain their target sizes. Selection has visible radio indicators, not
color alone. Verified input order, first-invalid focus, enlarged-text reach,
localized descriptions, error/live-region semantics and expanded/collapsed state.
Camera black/white opaque pairing is 21:1; branded semantic role pairs retain
the baseline contrast results. Dynamic colors were visually sampled, not
numerically certified for every wallpaper.

TalkBack spoken output was not exercised. Hardware Tab traversal, predictive-back
cancellation, frame timing, physical camera/real nutrition-label legibility,
foldables, all split-screen sizes and the complete palette/locale cross-product
remain unverified. Emulated camera evidence establishes control contrast and
acquisition/review behavior, not photo-recognition quality. No recognition exists.

## Build and tests

Android Studio Gradle completed:

```
:app:testDebugUnitTest
  --tests 'com.justcal.app.domain.*'
  --tests 'com.justcal.app.ui.EditorViewModelTest'
  --tests 'com.justcal.app.ui.NutritionFormattingTest'
  --tests 'com.justcal.app.camera.ImageInputTest'
:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

**34 unit tests passed; debug application/test APK compilation and lint passed.**
Final assembleDebug/lintDebug repeated after the compact-IME refinement passed.
Relevant IDE inspections reported no errors; weak formatting suggestions remain
in existing concise Kotlin. Git whitespace validation passed.

Explicit-serial instrumentation selection:

```
EditorInteractionTest,CameraFlowTest,PhotoPickerFlowTest,DiaryNavigationTest
```

**6 UI tests passed** (39.758 s). Coverage includes IME order/error recovery/200%
text, capture cycles and recreation/rotation/cleanup, picker review/disclosure,
date-aware add/edit/delete, retained diary navigation and goals.
Existing date expectations were updated to Stage 1's localized MEDIUM presentation;
summary assertions disambiguate clickable food rows from totals. Settings Save
in the navigation test uses its semantics action after scrolling; separate manual
visual verification confirmed the button can scroll above the floating pill.

IDE deployment later targeted a disconnected physical phone; explicit
emulator-5554 APK installation/instrumentation was used instead. No broad test
suite or Local AI Lab test was run.
