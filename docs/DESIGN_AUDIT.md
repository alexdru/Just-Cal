# Just Cal — visual audit and implementation roadmap

Date: 2026-10-10. Baseline: app 0.4.0. Scope: design system and documentation only.
Direction: **Refined Functional Minimalism**, defined in [DESIGN.md](../DESIGN.md).
No UI redesign is approved or implemented by this document.

## Method and evidence

Read AGENTS.md, README.md, ARCHITECTURE.md, the theme and launcher resources,
shared components, root navigation and every production screen implementation.
Used the installed material-design-3-ui and material-3 skills, including
accessibility, spacing/layout and anti-pattern guidance; reviewed the official
adaptive skill. Product requirements override generic advice to replace navigation
with a rail or scaffold. No new dependencies, screens or runtime theme layer.

Built and launched with Android Studio MCP. Captured and actually viewed rendered
screenshots through ui_state/take_screenshot, alongside hierarchy output. The
screenshots are in this audit session's tool results; no permanent screenshot
files were exported into the repository. IDs below are human evidence labels,
not invented file links. Reproduction routes are included for subsequent reviews.

### Visual coverage

| Evidence | Device / palette | Rendered state and route |
| --- | --- | --- |
| P01 | OnePlus 7T HD1900, API 30; Just Cal Dark through System | Home, empty diary, zero calories, absent macro goals |
| P02 | Same | Diary empty state; Choose a day calendar |
| P03 | Same | Day Detail opened through calendar for Oct 10, 2026; empty |
| P04 | Same | Add Food sheet: package, meal, manual; package source-choice sheet |
| P05 | Same | Manual editor empty/default amount; amount focused with decimal keyboard; keyboard dismissed |
| P06 | Same | Package camera, shutter and Choose photo controls |
| P07 | Same | Captured photo review, then Use photo → Photo ready disclosure |
| P08 | Same | Settings profile/goals/appearance; scrolled appearance/language/Save |
| P09 | OnePlus; Just Cal Light explicitly saved | Settings appearance/language/saved state; lower privacy, Lab entry and About |
| P10 | OnePlus; Just Cal Light | Local AI Lab, existing model selected but unloaded; backend/image/prompt and empty response regions |
| E01 | Medium_Phone_API_37.0 emulator; Just Cal Light through System | Home empty state |
| E02 | Same emulator; Material You Light | Settings palette applied; Home including custom floating controls |
| E03 | Same emulator; Material You Dark through temporary system night mode | Home including custom floating controls |

The physical camera image was nearly black; capture and review rendered successfully,
but this does not establish nutrition-label legibility or bright-scene contrast.
No food entry was saved or deleted. Existing model files were not loaded, altered
or removed. Theme choices changed only to inspect rendering; initial settings are
restored after the audit. The audit emulator was started for this task and stopped.

**Not visually verified:** populated Home/Diary/Day Detail and edit/delete states;
past-date-specific wording (calendar opened today); Russian layouts; large fonts;
landscape, tablet/expanded, foldable or desktop layouts; review errors/permission
denial/system photo picker; Light camera/review/editor; Dark Lab; all screens under
all dynamic palettes. About's lower edge was seen mid-scroll, not exhaustively
checked at the final scroll position. No separate statistics or production
recognition screen exists in the inspected graph.

This is a visual/code audit, not a complete accessibility or performance
certification. TalkBack speech, hardware keyboard, predictive-back gestures,
animation scaling and frame timing were not exercised. XML exposes semantics but
does not prove spoken experience. Screenshots do not prove smooth motion.

## Product and visual strengths to preserve

- Calorie hierarchy is immediate in branded dark/light and dynamic light/dark.
  One strong summary surface, separate flat macros and an open diary region
  avoid the usual collection of equal-weight dashboard cards (P01, P03, E01–E03).
- Evergreen/lime identity matches the adaptive leaf icon. Warm light and green-black
  dark surfaces are deliberate, with real semantic theme roles. Dynamic color
  changes the palette without changing composition.
- The centered, content-sized navigation pill and separate upper-right FAB form
  a recognizable interaction pattern. Settings hides the FAB without moving the
  pill (P01–P03, P08/P09). Active icon and label use the selected color.
- Native sheet, calendar, fields, keyboard and confirmation actions avoid a custom
  control framework. Photo Review gives its main action stronger emphasis than retake.
- Home and Day Detail share TodayScreen. History and calendar converge on DiaryDate.
  Independent Navigation 3 stacks and date-bearing image/editor routes are preserved
  in code; these are useful foundations for focused presentation changes.
- Optional macro goals, explicit units, locale formatting and on-device data
  boundaries should survive every visual refinement.
- Editor amount focus clears the primary action and keyboard in P05. Content
  behind floating controls is intentional, with measured end padding in code.
- Local AI Lab shows actual model/runtime controls and developer context rather
  than pretending to be production recognition.

## Findings

Priorities reflect user impact, not the cost of changing a few pixels.
“Code” evidence is deliberately distinguished from a rendered defect.

| ID / priority | Screen/component and evidence | Current problem and consequence | Recommended focused improvement |
| --- | --- | --- | --- |
| F01 **High** | AddFoodLauncher + PhotoReview; P04/P07 | Scan actions are offered without disclosing that production recognition is unavailable until after capture and confirmation. Users can invest several steps without adding food. | Explain current capability at entry, keep manual logging clear, retain package-first ordering. Do not connect Lab or implement recognition as a copy fix. |
| F02 **High** | Settings / Lab exclusive selectors; P08–P10, hierarchy, SettingsScreen and LocalAiLabScreen | Brightness, palette, language and backend use independent FilterChip checkbox semantics for mutually exclusive choices. Selected state is mainly a fill change without a check/radio mark. | Use a single-selection group with explicit state and non-color indication; choose radio rows or suitable native segmented controls after EN/RU and scale review. Preserve preference storage. |
| F03 **Medium** | Global layout; TodayScreen, HistoryScreen, SettingsScreen, EditorScreen (code) | fillMaxWidth layouts have no shared maximum content width/window strategy. PhotoReview alone has a width/height adaptation. Wide-window reading length and action reach have no defined implementation. This is a verified code gap, not a claimed tablet screenshot failure. | Add bounded content roles and short-height behavior first; review optional panes later. Keep the floating navigation concept and date/back-stack continuity. |
| F04 **Medium** | Global typography and spacing; Theme, Components, P01/P05/P08 | Eight type overrides coexist with inherited headlineSmall/bodySmall/labelSmall; repeated local 12/20/24 dp values bypass CalSpacing. Root app-bar title inset and 24 dp content margin differ visibly. Without role conventions, new screens drift. | Formalize semantic type/spacing/shape exceptions, then review a shared alignment rule. Do not mechanically replace every literal or override every Material default. |
| F05 **Medium** | Home / Day Detail; P01/P03, TodayScreen | Empty-state headline uses headlineMedium below a smaller titleLarge diary heading; large vertical gaps give the secondary empty message substantial emphasis. Historical detail also retains generic “Diary” title. | Keep calorie dominance, quiet empty-state copy, give the selected date clear identity. Retain the single summary surface unless comparison demonstrates a better composition. |
| F06 **Medium** | Diary rows and FoodRow; HistoryScreen/TodayScreen (code only for populated states) | Each day repeats a 28 sp energy headline and full 22 sp macro strip, plus multiple gaps; each food row embeds calories in a secondary paragraph alongside all macros. This impedes compact chronological/energy scanning as lists grow. | Evaluate date + aligned energy rows and quieter macro detail; make food name, consumed amount and energy easy to scan. Validate with realistic populated data before selecting exact density. |
| F07 **Medium** | Settings; P08/P09 and SettingsScreen | Profile heading, explanatory text and field are separate items with the same 24 dp gap as major sections. Appearance needs a distant Save while adjacent Language is immediate; saving inserts a status row and shifts lower controls. | Group related content more tightly; clarify draft versus immediate changes near the control. Review inline/persistent save feedback. Any change to transaction behavior is a separate approved decision. |
| F08 **Medium** | Manual editor; P05 and EditorScreen | Macro fields rendered as separate full-width inputs on this compact phone even though the source uses a weighted FlowRow. Name, section title and fields share a long vertical sequence. Correct but costly for frequent manual entry. | Audit measured constraints before assuming a two-column layout works; keep readable inputs and explicit per-100-g/amount grouping. Optimize grouping and IME progression, not target sizes. |
| F09 **Medium** | Settings focus/IME; SettingsScreen (code) | Settings has IME padding and a floating navigation overlay but lacks the editor's overlay-aware BringIntoViewSpec. Final padding alone does not prove focused fields clear the pill during keyboard input. | Reproduce focus on expanded macro fields with IME, then reuse the measured-overlay clearance principle if obstruction occurs. Treat as a risk requiring a targeted test, not a confirmed screenshot defect. |
| F10 **Low** | PhotoReview; P07 | Pixel dimensions occupy consumer-facing hierarchy while no guidance helps judge nutrition-label legibility. After confirmation, the image shrinks to make room for the capability explanation. | Move capability disclosure earlier; make review guidance about the image task; reduce technical metadata emphasis. Preserve image Fit and primary/secondary actions. |
| F11 **Low** | Lab; P10 and LocalAiLabScreen | Model setup/helper prose and controls occupy much of the first viewport; prompt/results/metrics require substantial scrolling. Delete-model text has routine primary color. | Keep functional density, expose state/output clearly, consider collapsible setup/help after load and distinguish destructive actions. Never add dashboard cards or decorative motion. |
| F12 **Low** | Motion / numeric roles; Theme, Components, JustCalApp (code) | Root and pushed navigation share a 160 ms fade; numeric roles have no explicit tabular feature. Reduced-motion and large-number behavior have no current visual evidence. | Preserve current restrained motion; review spatial distinction only if task clarity improves. Evaluate tabular figures and stable alignment. Verify animation-disabled and predictive-back behavior before changing motion. |

### Navigation requirements check

| Requirement | Result |
| --- | --- |
| Centered content-sized pill, stable root geometry | Pass for inspected compact English screens; code reserves FAB row |
| Separate FAB above/right; transparent outer host | Confirmed by screenshots and source |
| Filled compact active capsule; selected icon + label; transparent inactive items | Confirmed; active fill is 10% primary, not a new palette |
| Insets and content space | Present in code; rendered portrait controls clear gesture area; complete edge-case coverage pending |
| Selection motion | 160 ms color tween in source; no timing/performance measurement |
| Semantics | selectableGroup, Role.Tab, selected state and labels present; TalkBack review pending |
| Retained stacks and direct go-to-day | Implemented in Navigation 3; calendar-to-Day Detail exercised |
| Large fonts, Russian, narrow/expanded geometry | Not verified; retain as acceptance work, not a passed check |

No evidence justifies replacing the floating concept. Do not classify temporary
content scrolling behind the pill as permanent obstruction without checking final
scroll reach and focused controls.

### Color checks

Using the WCAG sRGB luminance formula on opaque Theme.kt token pairs:

| Pair | Light contrast | Dark contrast |
| --- | --- | --- |
| onSurface / surface | 15.64:1 | 14.69:1 |
| onSurfaceVariant / surface | 6.85:1 | 10.58:1 |
| onPrimaryContainer / primaryContainer | 10.35:1 | 7.75:1 |
| Goal text primary / primaryContainer | 7.82:1 | 5.85:1 |

These selected branded pairs exceed ordinary-text 4.5:1. They are token
calculations, not a screenshot-wide contrast scan. The dynamic blue scheme was
visually inspected but not numerically sampled. Alpha selection fills, input
boundaries, disabled states and camera overlays need their own validation;
there is no evidenced global contrast failure in this audit.

## Category assessment

Scores are bounded judgments of the inspected baseline, not measured usability
outcomes or a complete release certificate.

| Requested category | Assessment / evidence |
| --- | --- |
| Visual hierarchy | Strong calorie anchor; secondary empty-state hierarchy needs refinement (F05) |
| Composition | Flat macro/list direction is sound; settings grouping and history density need work (F06/F07) |
| Typography | Clear core scale; inherited/local roles and numeric treatment need formalization (F04/F12) |
| Spacing/alignment | 24 dp page rhythm exists; title axes and group-vs-section spacing differ (F04/F07) |
| Color consistency | Brand and dynamic role use strong; selected branded pairs checked |
| Component consistency | Native controls appropriate; exclusive-choice semantics and local sizing need review (F02/F08) |
| Navigation UX | Floating concept meets inspected requirements; preserve it |
| Interaction design | Manual IME behavior useful; scan promise and Settings commit boundary need clarity (F01/F07/F09) |
| Motion | Restrained source implementation; experiential verification incomplete (F12) |
| Accessibility | Labels/headings/targets have foundations; single-selection issue and untested coverage remain (F02) |
| Adaptiveness | Flow wrapping/image landscape branch exist; global bounded-width strategy absent (F03) |
| Visual identity | Coherent leaf/evergreen/lime identity worth preserving; no rebrand needed |

Material-3 audit scores (0–10): colors 8, typography 7, shapes 7, elevation 8,
components 7, layout 4, navigation 8, motion 6, accessibility 5, theming 8.
Total **68/100**, with layout/accessibility/motion limitations described above.
The floating pill is judged against explicit product requirements, not penalized
for declining a generic Material NavigationBar.

material-design-3-ui self-audit (0 missing, 1 partial, 2 ready within scope):
task clarity 1; information hierarchy 1; component semantics 1; token discipline 1;
adaptive behavior 1; states/feedback 1; accessibility 1; expressive restraint 2.
This supports a focused improvement plan; it does not assert production readiness
across untested configurations.

## Prioritized roadmap — proposals, not implementation authorization

| Stage | Priority / scope | Acceptance evidence |
| --- | --- | --- |
| 1. Shared foundation | High enabling work: semantic spacing/type roles and explicit existing shape exceptions; common content-width/alignment policy | Theme/component changes remain small; existing colors and navigation geometry preserved; compare Home + Settings/editor in EN/RU, both brightness modes and dynamic sample |
| 2. Capability and selection clarity | High: F01 entry disclosure and F02 accessible exclusive selectors; bring these forward because they affect successful use | Users know the scan endpoint before capture; manual action remains clear; TalkBack announces single selection; selected state has a non-color cue |
| 3. Global composition + Home | Medium: grouping/axes/short-window behavior, then calmer empty state and calorie/metric rhythm | Empty/populated Home, long names and nonzero/over-goal values; final row clears floating controls; font scaling preserved |
| 4. Diary and Day Detail | Medium: compact chronological rows, aligned energy, clear selected date; preserve direct calendar access | Several populated days plus empty past date; both entry paths reach same flow; FAB retains target date |
| 5. Floating navigation refinements | Medium validation, low visual churn: formalize existing geometry and test constraints | Stable roots/Settings, EN/RU/200% text, gesture and three-button navigation, short landscape and wider windows; no replacement control |
| 6. Settings | Medium: meaningful grouping, clear save boundary, overlay-aware focus where necessary | Optional/cleared macro goals, invalid drafts, language recreation, keyboard navigation and focused field clearance |
| 7. Add Food editor | Medium: readable dense field grouping and predictable IME order | Per-100-g versus consumed amount unmistakable; errors readable; preview and confirmation reachable; no arithmetic changes |
| 8. Camera / Photo Review | Low composition refinement: image guidance and metadata emphasis | Bright/dark scenes, denied permission, missing image, retake/reselect, portrait/landscape; no diary side effect |
| 9. Motion and final coverage | Low stylistic changes; high acceptance discipline throughout | Predictive back, disabled animations, TalkBack/keyboard, large fonts and dynamic contrast; trace only if jank is suspected |

Accessibility corrections and acceptance checks happen in every stage, not only
stage 9. Keep changes in small reviewed slices, preserving existing StateFlow,
Room, Navigation 3, image ownership and AI separation.

**Recommended first implementation milestone:** “Design foundation v1” — formalize
the existing shared tokens and semantic typography roles, define bounded content
and alignment behavior, and prove them on Home plus one form. Include the narrow
scan capability disclosure and single-selection semantics as an early parallel
priority in the implementation plan, not a broad redesign. Review before choosing
new radii, reducing summary height, changing Settings persistence or adding panes.

## Validation and limits

- Android Studio deploy built and launched the existing app on the physical device
  and then the API 37 emulator.
- IDE Gradle command `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
  succeeded: **48 passed, 0 skipped, 0 failed**. No instrumented test suite run.
- A direct shell wrapper attempt could not locate Java; the IDE Gradle runner
  resolved that environment limitation. No JDK or build configuration was changed.
- The natural-language journey tool returned `Model query failed: UNKNOWN`;
  visual inspection continued with explicit input, hierarchy and screenshot tools.
- Google CLI **@google/design.md 0.4.0**, alpha format:
  `npx --yes --package=@google/design.md@0.4.0 designmd lint DESIGN.md`
  returned exit 0, **0 errors, 5 warnings, 1 info**.
- All five warnings are `orphaned-tokens`: dark-tertiary,
  dark-tertiary-container, dark-on-tertiary-container, dark-outline,
  dark-outline-variant. They describe existing palette roles intentionally included
  without artificial component declarations. No broken references or contrast
  warnings were reported for the declared component pairs.
- The CLI checks the document's token model. It does not validate Android dp/sp
  semantics, runtime dynamic colors, actual typography rendering, Compose code,
  screenshot composition or accessibility. The Android unit mapping is explicit
  in DESIGN.md.
- Application code and version.properties are unchanged. Existing untracked
  .agents/ and skills-lock.json are unrelated and preserved.
