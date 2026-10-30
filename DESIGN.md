---
version: 0.17.0
name: Just Cal
description: Modern Material 3 Expressive for an offline-first native Android food diary.
colors:
  primary: "#244D32"
  on-primary: "#FFFFFF"
  primary-container: "#D9F294"
  on-primary-container: "#233817"
  secondary: "#526348"
  secondary-container: "#E3E9D6"
  on-secondary-container: "#26341F"
  tertiary: "#38665F"
  tertiary-container: "#BCECE1"
  on-tertiary-container: "#123C35"
  surface: "#FAFAF4"
  on-surface: "#1C211A"
  on-surface-variant: "#525A4D"
  surface-container: "#F0F2E8"
  surface-container-high: "#E8EBDD"
  outline: "#727C6B"
  outline-variant: "#D4DACB"
  dark-primary: "#B3D599"
  dark-on-primary: "#20351C"
  dark-primary-container: "#374B2B"
  dark-on-primary-container: "#D9F294"
  dark-secondary: "#C0CBAD"
  dark-secondary-container: "#3C4631"
  dark-on-secondary-container: "#E3E9D6"
  dark-tertiary: "#A1D0C5"
  dark-tertiary-container: "#214E45"
  dark-on-tertiary-container: "#BCECE1"
  dark-surface: "#121610"
  dark-on-surface: "#E4E8DB"
  dark-on-surface-variant: "#BFC8B5"
  dark-surface-container: "#20261B"
  dark-surface-container-high: "#2A3024"
  dark-outline: "#8D9883"
  dark-outline-variant: "#424B3B"
typography:
  font-family: Roboto Flex
  axes: [wght, opsz, wdth, GRAD]
  display-large: {fontSize: 76sp, lineHeight: 84sp, fontWeight: 720, width: 110, letterSpacing: -1sp}
  display-medium: {fontSize: 52sp, lineHeight: 60sp, fontWeight: 680}
  display-small: {fontSize: 40sp, lineHeight: 48sp, fontWeight: 650}
  headline-large: {fontSize: 34sp, lineHeight: 42sp, fontWeight: 680, letterSpacing: -0.6sp}
  headline-medium: {fontSize: 28sp, lineHeight: 36sp, fontWeight: 650}
  headline-small: {fontSize: 24sp, lineHeight: 32sp, fontWeight: 620}
  title-large: {fontSize: 22sp, lineHeight: 30sp, fontWeight: 620}
  title-medium: {fontSize: 16sp, lineHeight: 24sp, fontWeight: 600}
  title-small: {fontSize: 14sp, lineHeight: 20sp, fontWeight: 600}
  body-large: {fontSize: 16sp, lineHeight: 24sp, fontWeight: 400, grade: 5}
  body-medium: {fontSize: 14sp, lineHeight: 20sp, fontWeight: 400, grade: 5}
  body-small: {fontSize: 12sp, lineHeight: 18sp, fontWeight: 400, grade: 5}
  label-large: {fontSize: 14sp, lineHeight: 20sp, fontWeight: 600}
  label-medium: {fontSize: 12sp, lineHeight: 18sp, fontWeight: 600}
  label-small: {fontSize: 11sp, lineHeight: 16sp, fontWeight: 600}
rounded:
  extra-small: 8px
  small: 12px
  medium: 20px
  large: 28px
  extra-large: 36px
  navigation-shell: 32px
  navigation-selection: 24px
spacing:
  micro: 4px
  small: 8px
  related: 12px
  medium: 16px
  dense: 20px
  page: 24px
  section: 32px
components:
  calorie-summary:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.display-large}"
    rounded: "{rounded.extra-large}"
    padding: "{spacing.page}"
  calorie-summary-dark:
    backgroundColor: "{colors.dark-primary-container}"
    textColor: "{colors.dark-on-primary-container}"
    typography: "{typography.display-large}"
    rounded: "{rounded.extra-large}"
  page:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
  page-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-on-surface}"
  metadata:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.body-medium}"
  metadata-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-on-surface-variant}"
  add-food-fab:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    size: 56px
  add-food-fab-dark:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-on-primary}"
    size: 56px
  navigation-shell:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.navigation-shell}"
    padding: 6px
  navigation-shell-dark:
    backgroundColor: "{colors.dark-surface-container-high}"
    textColor: "{colors.dark-on-surface-variant}"
    rounded: "{rounded.navigation-shell}"
  portion-preview:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.large}"
    padding: 20px
  portion-preview-dark:
    backgroundColor: "{colors.dark-surface-container}"
    textColor: "{colors.dark-on-surface}"
  choose-day:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    height: 48px
  choose-day-dark:
    backgroundColor: "{colors.dark-secondary-container}"
    textColor: "{colors.dark-on-secondary-container}"
    height: 48px
---

# Just Cal design system

Modern Material 3 Expressive is the approved consumer direction. Compose Material3 targets `1.5.0-beta01` or a newer beta/stable release; the current explicit dependency is `1.5.0-beta02` (see `gradle/libs.versions.toml`). The stable Compose BOM remains independent. Preserve the evergreen/lime
leaf identity, warm neutral canvas, dominant calorie number, quiet macro strip and floating
navigation. Motion gives changes spatial meaning and makes controls feel immediate.
Composition, useful information and native interactions take priority over decoration.
AI Models and AI Playground use the shared Settings tonal navigation group, bounded reading width, typography and motion. The Playground retains the LocalAiLab route identifier.

## Product and composition

Manual logging, date-aware diary, offline package OCR, backup and diagnostic export remain
local. Package OCR opens editable values; only explicit diary confirmation persists food.
Meal recognition is limited to separately admitted, editable name-only assistance. The user supplies consumed weight and nutrition; numerical photo estimates remain unavailable. Never suggest model confidence certifies accuracy.
Keep quantities, units, per-100-g values and independently optional macro goals distinct.

Use one calorie emphasis container, flat macro summaries and flat interactive lists.
Do not turn every row into a Card. Preserve semantic branded/dynamic colors; no glass,
blur, gradients, ornamental shapes or copied mpvRx code/assets. mpvRx is an interaction
reference for pager, motion and theme continuity, independently implemented here.

## Typography

`ui/theme/Typography.kt` defines all 15 Material roles using one unmodified, officially
distributed Roboto Flex variable binary (about 1.7 MiB). Both English and Russian use this
family throughout consumer UI. Binary cmap inspection confirmed Russian А–Я, а–я and Ё/ё.
The Google Sans Flex demo binary in its official repository lacked these glyphs, so it
was not selected. Avoid mixed per-character fallback.

Use weight, optical size (scaled sp), width and grade axes. The calorie anchor is 76/84 sp,
weight 720, width 110; root headings 34/42, weight 680; section/amount titles 22/30, weight
620; food names 16/24, weight 600; body 16/24 or 14/20, grade 5. Metadata is 12/18 and
navigation 11/16. Only the anchor/headline use tightened tracking. Roboto Flex has no
roundness axis; do not invent one. Amounts retain exact, locale-aware formatting and tabular numerals. Binary metrics
confirm equal default advances for 0–9 (1156 units; 1113 at the anchor axes).
Roboto Flex's optional pnum feature is not enabled; existing tnum requests remain. Text grows with font scale; never shrink it to force a fit. The calorie/unit FlowRow
wraps its unit when needed; unusually long amounts scroll horizontally within the anchor,
preserving the complete value at 150% in narrow windows.

Source: [Google Fonts Roboto Flex](https://github.com/google/fonts/tree/main/ofl/robotoflex).
OFL attribution is bundled at `res/raw/roboto_flex_ofl.txt` and accessible from About.
The font is unmodified; no extra static weight assets or font dependency are included.

## Layout, shape and depth

CalSpacing remains 4 / 8 / 12 / 16 / 20 / 24 / 32 dp. Settings sections use 32 dp
separation and 12 dp related spacing; diary rows remain compact. Grouped navigation and diary/history rows use narrow surface gaps (Material ListItemDefaults.SegmentedGap), generous outer corners and small inner corners. Use native Expressive SegmentedListItem for navigation/selection, semantic tonal fills and native pressed shapes; no painted divider lines between rows. Keep informational form panels as coherent groups. Brightness and Themes, calorie and macro goals, and About information/actions use the same narrow segmented gaps and connected corners; never nest navigation rows inside a padded form panel. CalShapes remain
8 / 12 / 20 / 28 / 36 dp. Reading forms and review are bounded to 600 dp; diary to
840 dp. Center content within safe horizontal insets. Root titles align to the 24 dp axis.

MainActivity requests portrait orientation; there is no landscape-specific screen layout.
Android 16+ may ignore orientation restrictions on large displays: retain usable bounded,
scrollable composition in system-controlled windows. Width and font scale still determine
one/two-column nutrition fields. No landscape acceptance testing is required.

All new and updated screens, including AI Models and AI Playground, are edge-to-edge. Hosts are transparent. Scroll viewports extend behind system bars and the full-window app-bar host; safe-area, app-bar and final-item spacing belong inside scroll content (LazyColumn contentPadding or padding after verticalScroll). Consume insets once. IME bounds the input viewport before scrolling so focused prompts remain visible. Do not clip the entire scrolling surface to the safe area. Content scrolls behind floating controls with end
padding based on their measured complete height plus a gap. Focused fields clear both
controls and IME using overlay-aware BringIntoView. Below 280 dp usable IME height, the
editor hides its app bar and pinned action until Done/Back restores space.
Photo Review keeps an uncropped Fit image in a stable upper half and scrollable actions
below. Camera keeps a sharp 4:3 frame matching the capture viewport and contrasting
88 dp shutter. Native sheet/dialog scrims remain; no per-row shadows or borders.

## Navigation and motion

Home, Diary and Settings use a root HorizontalPager over independent Navigation 3 stacks.
Swipes directly move pages; tab taps use the same spring-settled pager. The compact active
capsule slides continuously with pager position, including in-flight gestures. Tab ripples
are clipped to their capsules. The pill stays at the window bottom behind the IME; it never
moves above the keyboard. FAB entry/exit uses scale/fade while its reserved row stays fixed. On child destinations, the complete floating navigation host exits and returns with a vertical spatial transition and effects fade; its measured content reservation stays fixed. Hidden navigation exposes no accessibility targets. The floating
pill remains horizontally centered and content-sized, with three labeled tabs, a transparent
host and the separate 56 dp FAB above/right. Reserve the FAB row when hidden in Settings.
Tab targets retain at least 52 dp height and nominal widths up to 120 dp; grow for text.

Swipe interception is enabled only on root destinations, without an open keyboard or
Add Food sheet. Child editor, camera, review, date detail and settings tools retain their
own gestures and Back. Inactive pages cannot handle Back or expose duplicate accessibility
content, and are capped at STARTED. Preserve ViewModels, scroll state and selected dates.
Do not exclude Android edge gestures or replace Navigation 3.

`CalMotion` centralizes roles using Material3 MotionScheme and native Compose specs:

| Role | Implemented behavior |
| --- | --- |
| Root navigation | Direct horizontal pager movement with a critically damped tab spring; root pages warm after splash |
| Child push/pop | Directional slide with short effects fade; reverse direction for Back |
| Predictive Back | Same spatial pop geometry; depth-based direction remains stable through cancellation/restart |
| Selected indicator | Continuous pager translation; semantic foreground interpolation |
| Sections/details | Spatial expand/shrink plus effects fade |
| Lazy list changes | Stable keys and animateItem placement/appearance/removal |
| Nutrition changes | Transition only when formatted value changes; no fabricated count-up arithmetic |
| Buttons | Native ripple and expressive pressed shape in shared primary/tonal actions |
| Add Food | Native modal sheet plus directional option/source content; FAB scale/icon coordination |
| Loading and progress | CalLoading / CalLoadingRow use ContainedLoadingIndicator for waiting; APK downloads use full-width determinate LinearWavyProgressIndicator (flat-and-wavy) with measured numeric progress. CalWorkProgressRow uses LinearWavyProgressIndicator for ongoing AI processing and OCR report export. Reduced motion keeps static progress feedback. Settings snackbars sit 12 dp above the measured navigation pill, without reserving the hidden FAB row; scroll content still reserves the complete floating host |
| Completion | Clear text/snackbar and selective confirmation haptic |
| Theme | 600 ms radial reveal over native drawing commands of the previous theme; cancelable for brightness and Themes |
| Splash exit | Immediate compatibility-splash removal on Android 11; 80 ms native exit on Android 12+, without a branding delay |

Spatial prominence uses expressive springs; frequent utilitarian effects use standard
fast effects specs. No scattered per-screen durations or decorative bouncing. Native
duration scaling is respected; animator-disabled mode snaps theme/tab transitions and
uses static loading feedback. Recheck animation settings on resume. Do not add a duplicate
reduced-motion preference or infer jank-free behavior from screenshots.

## Appearance and automatic settings

The Macros disclosure (Индивидуальные цели для БЖУ in Russian) starts collapsed even when targets are saved; the user's expansion choice is retained while navigating. Update check buttons show no idle icon; the default LoadingIndicator appears only while checking. Symmetric reserved slots keep the label centered and stationary. Update sheets have text-only titles and retry actions. A bottom sheet opens only for an available update or a manual error. A current-version result uses an icon-free snackbar.

Brightness (System/Light/Dark) and palette (Just Cal/Material You) are independent connected
ToggleButton groups. Required choices cannot be cleared. Unselected choices retain connected leading/middle/trailing corners with a visible surfaceContainerHighest fill. Selected and pressed choices use capsule shapes with one bounded 120 ms outline transition and native ripple. The real control and old-palette draw copies share press/roundness state; releasing a newly selected choice must not restart the morph. There is no additional scale animation or nested shape spring. Theme-frame capture waits until the control outlines finish, so readback cannot pause a morph halfway; no outer capsule clip may erase the connected contours. Minimum targets are 48 dp;
FlowRow wraps English/Russian text instead of hiding options behind overflow menus.
The Themes group offers Just Cal and Material You. Material You is disabled on Android 11; existing stored selections still render the branded fallback there.

Selections apply immediately and persist automatically. There is no Settings Save button.
The selected control center anchors the same radial reveal for brightness and Themes;
system-driven changes use the center. The entire Appearance panel follows this same circle: background, text and button colors must not switch early. Checked state stays current in both the old and new palettes, without a frozen previous choice. Small draw-only panel layers preserve the old palette outside the circle and retain interrupted color coverage on rapid selections. Replay includes the opaque parent surface around the panel, so its native rounded contour stays unchanged without a temporary outline. They have no accessibility, focus or preference actions. A frame is captured only for a committed palette change, never on pointer down; cancelled touches allocate no image. The old frame is captured from the Compose graphics layer at native resolution and discarded after the 600 ms reveal. Text, icons and strokes retain their original sharpness; never downsample or raster-scale the UI for this transition. Keep one temporary image within 24 MiB; oversized windows use the surface fade.
The new ColorScheme commits once beneath it; animation reads happen only in drawing.
Repeated selections capture the visible composite and replace the unfinished reveal.
Capture failure falls back to a surface fade; disabled animators apply the theme directly.
System icon contrast follows the reveal. The branded splash retains light system icons
until exit; the first consumer palette mounts only after preferences load.

Nutrition inputs remain editable even during saving. Profile Name is hidden while its existing stored value is preserved; editing goals/appearance never clears it. Debounce valid edits
550 ms, validate each edited field independently, preserve invalid/incomplete drafts and
persist only affected keys. Clearing a macro removes only that goal. Keep edits when
switching tabs or recreating the Activity; background saving continues away from Settings.
Keep persistence silent on success; show actionable errors and retry only on failure. Language uses Android per-app
locales immediately, with AndroidX storage on Android 11/12 and platform storage on 13+.
Handle locale/layoutDirection and related screenLayout changes in the existing Activity so switching language does
not restart the screen or show splash.

## Screen contracts

| Screen | Presentation and interaction |
| --- | --- |
| Home / Day Detail | Opaque surface app-bar host across the window, with scrolling content hidden beneath it; Home date below the app bar at titleLarge; wider calorie numerals; expressive calorie hierarchy, animated amounts/progress, flat macros; connected tonal food rows with leading tiles, narrow gaps and outer group corners; long press opens the same compact Expressive action menu as the food editor, with rounded copy/calendar/delete icons; stable item placement and date-aware FAB |
| Diary | Connected tonal day rows with leading date icon, aligned energy, narrow gaps and quiet count/macros; tonal Choose a day |
| Settings | Headings outside coherent tonal Goals/Appearance/Language groups and AI tool navigation; native navigation rows with leading icon tiles and narrow gaps; animated macro disclosure, immediate choices and autosave |
| Data & Backup | Quiet diary symbol and centered explanation above a bottom-aligned full-width export/import action group. Short content fills the edge-to-edge viewport; enlarged text/short windows scroll with safe-area spacing inside content. Recovery and completion stay with the actions; preserve merge default and explicit replace acknowledgement |
| OCR Diagnostics | Total and status summary, separate local-storage explanation, aligned label/value measurements that stack with enlarged text; connected interactive scan rows, grouped filters and sort choices; edge-to-edge paged content beneath the app bar/tabs; details separate timing, recognition quality, parsed fields, corrections and optional technical evidence; pinned Save/Share report actions |
| Add Food | Concise Package → Meal → Manual actions without explanatory subtitles; official Material Symbols Rounded in tonal leading tiles; native modal motion and source transition. The entire Package/Meal source header is one Back target with rounded ripple and merged button semantics |
| Food Editor / OCR confirmation | Name → per-100-g → consumed amount → animated portion preview; expressive explicit save, preserved IME/error focus, expandable raw OCR evidence |
| Camera | Unchanged lifecycle/capture crop; native media controls, shutter press/busy feedback and capture-confirmation haptic |
| Photo Review | Stable Fit image, one concise photo hint and compact secondary actions; tonal package-assistance switch with model name and explicit per-photo scope, or an AI Models setup row when unavailable; pinned Use photo in a transparent host above system bars, scroll viewport behind it with end clearance. Analysis replaces the entire control block with centered expressive loading and a short OCR/model stage; hide action targets while preserving photo and footer geometry. Back remains available; failure restores Retry, another photo and manual entry |
| AI Models | Recognition defaults first (package/meal navigation rows with task-selection sheets), then installed models, available catalog and compatible import; segmented tonal rows with memory/download/import icons; measured transfer progress and pause/cancel/retry, source/license sheets and confirmed removal; explicit task admission and no accelerator picker |
| AI Playground | Distinct Model → image → task/prompt → Run/Cancel → response → metrics sections with 32 dp separation; connected Models/photo-source navigation, mutually exclusive task/output ToggleButton groups, one full-width Run action; readable response and measured metrics with disclosed previous run; no diary-save action |
| Shared dialogs / sheets | Semantic colors/variable typography, native modal motion, explicit destructive confirmation and accessible actions |

Saving/deleting food gives a rounded inverse-surface parent snackbar with a confirmation icon after returning. The native Material3 SnackbarHost supplies MotionScheme entrance/exit, accessible announcements, timeouts and queuing; OCR success opens
editable confirmation instead of writing automatically. Do not vibrate on every tap.
Keep native pressed/focus/disabled/selected/loading/error semantics and clear empty states.
Native ripple and component shape changes provide feedback without delaying action.

## Splash and accessibility

Official Android SplashScreen compat API uses the unmasked leaf vector on evergreen, avoiding
a second adaptive-icon crop. The launcher adaptive/monochrome icon is unchanged. Splash exits
into the loaded theme once preferences are ready. No separate Activity, extra timeout or
duplicate Compose splash exists. Warm starts and animator-disabled exits remain direct.

Keep 48 dp accessible targets, localized names for icons, headings, checked/expanded/error
semantics, logical focus order and text contrast. Hide inactive pager semantics and
decorative graphics. Validate English/Russian, light/dark, Android 12+ dynamic colors,
narrow/wide portrait windows, IME, root swipes, gesture Back and disabled animations.
Font/UI scaling acceptance stops at **150%** per the approved scope; do not run 200%
or landscape checks. Recorded component/navigation tests cover API 30 and API 37;
OnePlus 12, release frame/startup benchmarks and full spoken TalkBack remain open.
An intermittent API 37 rapid hardware-key input reorder was observed historically;
later passing runs do not establish its cause or a definitive fix.
Static images document composition; device gestures/traces validate
motion separately. Do not claim TalkBack certification or leak/jank absence without evidence.
