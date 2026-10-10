---
version: alpha
name: Just Cal
description: Refined Functional Minimalism for an offline-first native Android food diary.
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
  display-large:
    fontFamily: sans-serif
    fontSize: 64px
    fontWeight: 700
    lineHeight: 72px
    letterSpacing: -2px
  headline-large:
    fontSize: 32px
    fontWeight: 700
    lineHeight: 40px
    letterSpacing: -0.7px
  headline-medium:
    fontSize: 28px
    fontWeight: 600
    lineHeight: 36px
  title-large:
    fontSize: 22px
    fontWeight: 600
    lineHeight: 28px
  title-medium:
    fontSize: 16px
    fontWeight: 600
    lineHeight: 24px
  body-large:
    fontSize: 16px
    lineHeight: 24px
  body-medium:
    fontSize: 14px
    lineHeight: 20px
  label-large:
    fontSize: 14px
    fontWeight: 600
    lineHeight: 20px
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

## Overview

**Refined Functional Minimalism** makes a daily task feel immediate, calm and precise.
The identity is the existing evergreen/lime leaf, warm neutral canvas, confident calorie
number, quiet macro summary and compact floating navigation. Distinction comes from
composition and useful information, not additional decoration.

**Status:** Stage 1 implemented on 2026-10-10 in app 0.5.0, following the approved
redesign request. Frontmatter records implemented tokens and existing component
mappings. Remaining items explicitly marked **Proposed** are future refinements.
Stage 1 covers shared foundations, Home, Diary, Day Detail, Settings and Add Food
capability disclosure. Local AI Lab is excluded from this implementation and
verification. See [the baseline audit](docs/DESIGN_AUDIT.md) and
[Stage 1 verification](docs/DESIGN_STAGE_1.md).

### Product truth

The production app currently supports manual entry, local diary/history/day detail,
settings, camera/photo selection and photo review. Photo confirmation does not
recognize nutrition or create an entry. Local AI Lab is an isolated developer tool.
The future package-photo → recognition → verification → diary flow is the intended
destination; plated meals remain secondary. There is no dedicated statistics screen
in the inspected navigation graph. Do not design as though these future steps exist.

### Composition before components

For each screen decide, in order: user goal → primary information → secondary
information → hierarchy → grouping → spatial composition → typography → interaction
priority → component choice → motion/refinement.

Use one clear information anchor, stable alignment and whitespace. Nutrition remains
the content, with controls supporting it. Prefer compact rows over repeated cards.
A single calorie summary surface is purposeful emphasis, not a pattern to repeat
around every metric. Native behavior, predictable feedback and one-handed actions
matter more than stylistic novelty. Expressive techniques must improve hierarchy
or feedback; do not adopt them simply because the library offers them.

### Source and format contract

The [Google DESIGN.md alpha specification](https://github.com/google-labs-code/design.md/blob/main/docs/spec.md)
defines this file's YAML fields, references and section order. Its dimension units
are CSS-oriented. **In this Android document, YAML px is a serialization convention:
spacing, radii and component dimensions map 1:1 to dp; typography dimensions map
1:1 to sp. These are not physical screen pixels.** Compose must retain font scaling;
do not paste exported CSS dimensions into Android or convert sp to dp.

Unsuffixed colors are branded light; `dark-` names serialize branded dark values
because the format has no native Android theme selector. Runtime components use
`MaterialTheme.colorScheme`, never choose these suffixes themselves. Component
entries describe existing role pairings; height/size values do not override native
minimum touch targets or text growth. The YAML is a curated token set, not every
Material default. Unspecified type properties continue to resolve through Compose.

Implementation anchors:
[Theme.kt](app/src/main/java/com/justcal/app/ui/theme/Theme.kt),
[Components.kt](app/src/main/java/com/justcal/app/ui/Components.kt),
[FloatingNavigation.kt](app/src/main/java/com/justcal/app/ui/FloatingNavigation.kt).
Authority order: explicit product requirements, official Android platform behavior,
approved Just Cal decisions, relevant skill recommendations. The installed adaptive
skill's generic rail/scaffold conversion is intentionally not adopted: the product
requires the floating pill. No experimental Grid, Styles or new UI dependency is
needed for this milestone.

## Colors

### Existing identity and semantic roles

The launcher uses evergreen `#244D32` behind a lime `#D9F294` leaf. Preserve its
adaptive foreground/background and monochrome layer. The UI repeats these hues
through roles rather than reproducing the icon on each screen.

| Role | Use |
| --- | --- |
| primary / onPrimary | Primary action, FAB; readable text/icon on that fill |
| primaryContainer / onPrimaryContainer | Calorie summary and its content |
| secondaryContainer / onSecondaryContainer | Lower-emphasis actions and selections |
| tertiary / tertiaryContainer | Available supporting accent; no fixed macro rainbow |
| surface / onSurface | Canvas and primary reading content |
| onSurfaceVariant | Units, dates, helper text, secondary detail |
| surfaceContainer | Portion preview and purposeful grouping |
| surfaceContainerHigh | Floating navigation's own surface |
| outline / outlineVariant | Necessary input boundary / subtle separator |
| error / onError, errorContainer / onErrorContainer | Validation and failure, with text and recovery |

Error roles currently inherit Material defaults; they are not custom branded constants.
Remaining tonal levels, inverse roles and paired secondary/tertiary foregrounds are
defined in Theme.kt and should not be copied into screen code. Use intended on-color
pairs; alpha overlays and controls inside colored containers require separate checks.

### Independent personalization

Brightness is System / Light / Dark. Color style is Just Cal / Material You.
Both are independent DataStore preferences. Material You uses Android dynamic
schemes on API 31+ and branded fallback below that. Preserve geometry and hierarchy
across palettes. Never hardcode a wallpaper palette into DESIGN.md.

Camera black/white is an intentional media-control exception, not an alternative
app palette. It protects capture affordance against image content; validate on
bright as well as dark scenes.

**Proposed:** name component aliases for calorie surface, metadata, navigation
selection and camera controls in the existing theme layer if repeated use warrants
them. Do not add another color store or a second theme framework. The goal
TextButton now uses onPrimaryContainer on the calorie surface; retain palette
checks rather than inferring all contrast from semantic naming.

## Typography

Keep platform sans-serif rendering and English/Cyrillic coverage. No downloaded
font or new brand typeface. The eight YAML styles plus bodySmall and labelSmall are explicit CalTypography
overrides. Other Material styles continue to inherit library defaults, including
headlineSmall.

| Product role | Existing style and use | Direction |
| --- | --- | --- |
| Calorie anchor | displayLarge, 64/72 sp, bold | Dominant number; unit subordinate |
| Root screen title | headlineLarge, 32/40 sp, bold | One title per page |
| Empty-state emphasis | titleMedium, 16/24 sp | Quiet body copy below the diary heading |
| History energy | titleLarge, 22/28 sp | Trailing aligned amount with subordinate kcal unit |
| Section heading / macro amount | titleLarge, 22/28 sp | Preserve macro readability, quieter than calories |
| Food name / setting subgroup | titleMedium, 16/24 sp | Scan-friendly medium emphasis |
| Main explanation | bodyLarge, 16/24 sp | Short, useful copy |
| Date / units / helper text | bodyMedium, 14/20 sp | Secondary color, readable contrast |
| Action / summary label | labelLarge, 14/20 sp | Concise action or context |
| Navigation label / small metadata | explicit labelSmall / bodySmall | 11/16 sp navigation; 12/16 sp small metadata |

The large number has -2 sp tracking; root headline has -0.7 sp. Do not spread those
tracking choices to body text. Avoid arbitrary per-screen font sizes/weights.

**Implemented:** the calorie anchor, macro amounts and shared EnergyValue /
MacroDetail request tabular numerals (`tnum`) from the platform font. EnergyValue
uses titleLarge for days and titleMedium for food, with a subordinate kcal unit
and trailing alignment. Platform font feature support determines numeral rendering;
no font dependency is added. bodySmall (12/16 sp) and labelSmall (11/16 sp, medium)
are explicit. Locale formatting, explicit units and nullable macro goals remain.
Text grows rather than shrinking to fit a fixed-height container.

## Layout

### Implemented spacing and rhythm

CalSpacing formalizes 4 micro / 8 small (inline) / 12 related / 16 medium
(group) / 20 dense / 24 page / 32 section dp. Use these roles when they express
composition; local values are not mechanically replaced. Navigation retains its
6 dp geometry exception. Settings uses 12 dp within related groups and 32 dp
between sections; diary content uses a denser rhythm. CalTopBar aligns root
titles to the 24 dp content axis and preserves the back affordance on child
screens. Shapes and palettes are unchanged.

### Insets and floating controls

Use edge-to-edge layouts with safe horizontal/cutout insets and correctly consumed
Scaffold padding. A transparent floating host is deliberate. Content may scroll
behind controls, with end padding measured from the entire control host plus a
content gap. Last items and focused inputs must be able to settle unobstructed.
Do not interpret a mid-scroll overlap alone as a defect.

Food-editor confirmation remains reachable above IME/navigation bars. Retain its
BringIntoView behavior. Apply the same focus-clearance principle to other forms.
Avoid double-applied insets, fixed bottom spacers and clipping behind system bars.

### Adaptive direction

Design for available window dimensions, including short landscape and split screen,
rather than a device model. Preserve the pill and separate FAB, destination order,
saved stacks, date context and accessible targets.

**Implemented in Stage 1:** CalLayout / calContent center Home, Diary and Day
Detail in a maximum 840 dp viewport, and Settings in a maximum 600 dp reading
viewport. Page margins remain inside these bounds; safe horizontal insets are
outside. These are content constraints, not navigation breakpoints. Phone and
short landscape use the same scrollable composition. Other flows retain their
existing layout. Supporting panes remain a future proposal requiring review;
do not introduce panes solely to occupy width. Respect hinges and keep controls
within reachable regions. Photo Review already switches to side-by-side image
and actions when width exceeds height; preserve full-image Fit behavior.

### Screen composition contracts

| Screen | Goal and information order | Composition and action |
| --- | --- | --- |
| Home | Understand today's calories and remaining amount, then macros and entries | One calorie anchor; flat macro strip; readable list; separate Add Food FAB |
| Diary | Find a date and scan its summary | Chronological flat rows; compact date + energy, quieter macros; retain direct Choose a day |
| Day Detail | Understand and edit one date | Same summary language as Home; selected date obvious; rows open editor; FAB targets that date |
| Settings | Change local preferences confidently | Profile, Nutrition Goals, Appearance, Language, About grouped by meaning; no decorative section cards |
| Add Food launcher | Choose acquisition method quickly | Package first, meal secondary, manual clearly available; explain actual scan capability before effort |
| Manual editor | Enter label values and consumed quantity correctly | Name → per-100-g group → amount eaten → computed portion → one confirmation action |
| Camera | Capture a legible image | Image dominates; clear shutter and back; photo-selection alternative; no floating app navigation |
| Photo Review | Verify the image and continue | Uncropped image → concise guidance → primary confirmation → secondary retake/reselect |
| Local AI Lab | Inspect model/runtime/input/output | Dense functional sections, explicit state, raw/parsed output and measured metrics; no diary action |

**Implemented:** Day Detail leads with the selected localized date and weekday;
Home keeps the root title and full date. Empty states use quieter typography.
Diary uses compact date/count + trailing energy rows with secondary macros.
Food rows separate name, eaten weight, macro detail and trailing energy.
**Proposed for later milestones:** photo-review legibility guidance and metadata
refinement. Recognition and charts are not implemented.

## Elevation & Depth

Use surface tone, whitespace and typography first. Main reading surfaces are flat.
The calorie container is emphasis, not a raised card. Navigation alone has an
explicit 2 dp shadow in its floating shell; FAB/sheets/dialogs use existing Material
behavior. Editor confirmation has a transparent host. Preserve native modal scrims.

Do not add shadows or borders to every list row. Existing row dividers may be
retained until density is reviewed; a divider must aid scanning rather than act
as decoration. No blur, glass, gradient canvas or per-row rendering effects.

## Shapes

Existing CalShapes: 8 / 12 / 20 / 28 / 36 dp from extraSmall to extraLarge.
Inputs use medium (20 dp), portion preview large (28 dp), calorie summary
extraLarge (36 dp). Navigation intentionally uses its own 32 dp shell and
24 dp selection; the 56 dp FAB and camera shutter are circular.

Preserve the recognizable floating controls. Do not make every element a pill.
**Proposed:** document navigation/media exceptions beside shared shape roles,
then evaluate whether form corners should be quieter. No radius change is
approved by this audit; never replace the current scale with generic Material
defaults without composition review.

## Components

### Floating navigation contract

Keep the content-sized, horizontally centered three-destination pill with a
transparent full-width host. The FAB is a separate action above/right; reserve
its 56 dp row when hidden in Settings. Current host uses 16 dp horizontal and
12 dp vertical padding, 12 dp row gap, 6 dp pill padding, 24 dp icons, tabs with
at least 52 dp height and nominal widths constrained between 96 and 120 dp
within available width (narrow windows may reduce the minimum). Tabs use 4 dp
horizontal padding to leave room for Russian labels and enlarged text.

The active fill is primary at 10% alpha over the navigation surface; active icon
and label use primary, with a filled icon. Inactive item backgrounds are
transparent and foreground is onSurfaceVariant. Keep restrained 160 ms color
selection transitions. A shorter visible pill must not mean smaller touch targets.

The current implementation has selectableGroup, selected state and Role.Tab,
with visible localized labels. Preserve semantics and keyboard focus. Verify
actual TalkBack announcements before claiming accessibility certification.
Stable geometry is confirmed for the inspected English compact roots; larger
fonts, Russian labels and narrow windows still require visual verification.
Do not replace the pill with NavigationBar, Floating Toolbar or a rail by default.
Telegram informs spatial relationships only; no copied assets or implementation.

### Actions, forms and states

Use existing Material3 Button for the one primary confirmation, FilledTonalButton
for lower emphasis, OutlinedButton for retake/reselect and TextButton for tertiary
actions. Destructive actions require explicit wording and confirmation.
Use native OutlinedTextField labels, units, errors and keyboard semantics.

Editor has a 60 dp minimum confirmation; Settings Save uses 56 dp; review actions
use 48 dp minima. These differences are current choices, not proof that every
button needs the same height. Preserve supported content growth.

Define each applicable state: default, pressed, focus, selected, disabled,
loading, empty, error, submitting and success. Busy actions prevent duplicate
submission; errors identify the field/problem and recovery; entered drafts survive
recoverable failure. Do not display missing macro goals as zero. Recognition output
must remain untrusted and reviewable before any future diary integration.

**Implemented for Settings:** brightness, palette and language use selectableGroup
with Role.RadioButton rows and visible native radio indicators. Entire rows are
single targets with 48 dp minimum height. Profile/goals/appearance remain drafts
until Save; nearby helper text states this. Language remains immediate, in a
separate section after Save. Save feedback reserves a stable slot. Settings focus
scrolling subtracts the measured floating-control overlay from available space.
Local AI Lab backend selection is outside Stage 1 and remains unchanged.

Add Food discloses automatic recognition is unavailable before scan selection,
including the source-choice sheet. Package, meal and manual actions remain in
the established order; manual logging remains directly accessible.

### Motion and performance

Existing CalMotion uses 160 ms navigation fades/color changes and a no-bounce,
medium-low-stiffness spring for calorie progress. NavDisplay retains its platform
predictive-back transition. Lab uses a nested NavDisplay. Preserve continuity of
each top-level stack; switching tabs must not reset the user's place.

Motion communicates destination, selection and state. Keep direct manipulation
responsive and cancelable; no decorative bounce, repeated count-up or delays before
input. Use platform animation scaling and verify behavior with animations disabled;
no bespoke reduced-motion preference is implemented. Static screenshots do not
validate animation timing, gesture quality or frame performance.

Keep lists lazy, image decoding bounded and off the main thread, and camera/inference
resources lifecycle-scoped. Do not add blur, animated gradients or custom rendering
without measured benefit. Do not claim jank-free performance without tracing.

### Accessibility and review requirements

Follow [official Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
and [Android window-size guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes).
Interactive regions must be at least 48 × 48 dp without overlapping neighbors.
Name icon-only actions; hide decorative icons from spoken output. Expose headings,
selection, expanded/collapsed state, errors and progress meaningfully. Never rely
only on hue. Keep focus order aligned with reading and task order.

Require text contrast of at least 4.5:1 for ordinary text and 3:1 for large text;
meaningful controls/graphics need 3:1 where applicable. Validate actual foreground,
background and alpha composition in both branded themes and representative dynamic
schemes. Disabled styling is not judged as enabled text.

Major UI changes need device/emulator screenshots with long names, populated/empty
diaries, optional macro goals, errors and IME. Include EN/RU, font scaling up to
200%, compact portrait, short landscape and expanded windows; test TalkBack,
keyboard focus, gesture back and animation-disabled behavior where relevant.
This is the future acceptance matrix, not a claim it passed in this audit.

## Do's and Don'ts

- Do preserve the leaf brand, semantic colors, calorie anchor and floating pill.
- Do use whitespace/alignment before containers; give related values a common axis.
- Do keep dates, units, consumed amounts and per-100-g values unambiguous.
- Do show the real product capability before asking users to invest effort.
- Do apply the same component behavior across palettes and destinations.
- Do use relevant design skills and official platform guidance with product judgment.
- Do not create a generic dashboard of equal-weight metric cards.
- Do not add arbitrary colors, gradients, borders, excessive rounding or glass effects.
- Do not shrink touch targets or text for visual compactness.
- Do not replace Navigation 3, the brand, persistence, or AI boundaries for a visual task.
- Do not treat proposed refinements or this roadmap as permission to implement them.
