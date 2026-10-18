# Material3 Expressive implementation

Material3 remains explicitly pinned to `androidx.compose.material3:material3:1.5.0-beta01`.
Stable Compose BOM 2026.09.00 resolves UI, foundation and runtime to 1.12.1.
AGP 9.4.1 / built-in Kotlin and compiler plugins 2.4.20 remain unchanged.
Material3/ripple resolve to 1.5.0-beta01, overriding the BOM's Material3 1.4.0.
Core SplashScreen 1.2.0 is the only added library. No alpha BOM or business/data migration.

## Public API decisions

Verified against official beta01 sources/API/release notes, rather than older skill examples.

| Family | Actual beta01 status | Adopted behavior |
| --- | --- | --- |
| Expressive buttons | ButtonShapes, ButtonDefaults sizes/padding/pressed shape; public without Expressive opt-in | CalButton/CalTonalButton preserve action hierarchy with native pressed shapes and growing targets |
| Toggle buttons | ToggleButton variants, ToggleButtonShapes, ToggleButtonSize; public | Connected immediate Settings choices with checked/RadioButton semantics |
| Button groups | ButtonGroup DSL and connected ButtonGroupDefaults; public | FlowRow + connected shapes exposes every choice without overflow |
| Lists | Interactive/selection ListItem, SegmentedListItem and shape/elevation helpers; public | Connected tonal food/day groups with inner dividers and leading tiles; grouped Settings rows; native scan/launcher interactions |
| MotionScheme | expressive/standard and spatial/effects specs; public | Central CalMotion roles, actual pager/child/list/disclosure/value/theme/FAB behavior |
| LoadingIndicator | ExperimentalMaterial3ExpressiveApi | Isolated CalLoading wrapper for preparation/OCR/backup; static indicator when motion disabled |
| MaterialShapes | ExperimentalMaterial3ExpressiveApi | Available, deliberately unused; established brand/media/navigation geometry remains |

None of these API families is absent. The artifact itself is beta; promoted public API
does not make the dependency stable. No generic ButtonSize exists in this artifact.
Experimental opt-in stays inside the loading wrapper, away from domain/persistence.

## Consumer direction

See [DESIGN.md](../DESIGN.md) for actual typography, component, portrait layout and motion
contracts and [ARCHITECTURE.md](ARCHITECTURE.md) for retained Navigation 3 stacks, pager
lifecycle, key-specific immediate persistence and bounded theme reveal rendering.
Only the Settings entry of Local AI Lab shares the redesigned group; the destination is unchanged. OCR calculations, storage, backup compatibility, CameraX and
application signing/identity are preserved. The current corrective UI version is 0.12.11 / code 28.

Roboto Flex uses one official OFL variable binary with verified Russian cmap coverage.
Root MotionScheme is expressive; fast utility effects remain standard. Root swipes and
tap navigation share one pager state; child screens retain Native Navigation 3 Back.
Brightness and Themes share a 600 ms radial reveal from the selected control over a temporary
old frame captured from the root Compose GraphicsLayer at native resolution, with one palette commit. No software View.draw, downsampling or raster scaling; text and icon sharpness stays constant. A single transient image is limited to 24 MiB; larger windows use the surface fade. No per-frame
ColorScheme recomposition; repeated selections replace the visible transition. The Appearance panel uses small native GraphicsLayer recordings in retained palettes with the current selected option. These share the global circle and visible scroll bounds; no separate panel color cutout is exposed. Interrupted color regions are preserved while identical palettes share a layer. Drawing copies have no semantics, focus or preference actions. Capture happens only after a committed palette change, never on pointer down; explicit selection identity also handles unchanged effective colors. Choice feedback retains native connected/capsule geometry and tonal fill. A single 120 ms outline animation is shared across live/old-palette copies, with no nested native shape spring or scale animation; press-to-checked handoff keeps the same animation target.
Valid text settings debounce and persist automatically without global Save.
Native SplashScreen uses the unmasked leaf vector on Android 11+, without a separate
Activity or delay. Locale/layoutDirection/screenLayout updates stay in the same Activity.
Tab ripple is capsule-clipped; FAB exits in 90 ms from the requested tab, without waiting
for pager settling. Three root trees warm after first display to avoid destination composition
during tab movement. The pill stays behind IME. Android 11 removes splash immediately on
readiness; Android 12+ uses an 80 ms exit.
Predictive and regular Back share scene-depth spatial transitions across cancellation.

## Platform limits

minSdk 30, compile/target 37. Variable fonts and adopted Compose controls work on Android 11.
Dynamic Material You colors require API 31; the option is disabled on Android 11.
Existing stored Material You preferences retain the branded rendering fallback there.
Portrait is requested explicitly; Android 16+ large-screen platforms may override orientation
locks, so bounded scrollable layouts remain usable. Font acceptance is limited to 150%;
landscape and 200% checks are outside the requested scope.

## Verification

For 0.12.2, debug APK/instrumentation APK compile and 36 focused domain/UI unit tests pass.
Lint has zero errors and nine existing dependency/resource/debug-preview warnings.
Nineteen consumer instrumentation cases pass on OnePlus 7T (Android 11):
radial old/new-frame pixels and rapid replacement, bounded color recomposition,
early FAB exit, disabled dynamic-color choice, retained tabs/diary dates/drafts,
cancelled predictive Back, anchored IME navigation, language without Activity restart
and 150% narrow typography. The same 19 cases pass on API 37, including Material You
selection and persistence across recreation. Its rapid hardware-key regression exposed
frame-deferred Settings state delivery; Main.immediate collection fixes it without
adding input delays or weakening the test.
The immediate theme/persistence case also passes with system animator scale set to zero;
the original system setting is restored. Tests restore preferences and remove owned entries;
visual fixtures never create diary data.

Rendered Home date/energy, simplified Settings and the radial reveal were inspected on 7T.
Its 8-second warm Home/Diary tap sample has 228 doFrame work slices: mean 7.05 ms,
p95 13.59 ms, maximum 45.28 ms. An 8-second brightness sample has 176 slices:
mean 6.81 ms, p95 21.46 ms, maximum 90.06 ms. These measure debug CPU work, not
presented frames or a zero-jank guarantee. The final cold debug launch measured 1.615 s (earlier samples 1.6–1.8 s);
Android 11 no longer adds a branding exit animation. Release startup/frame benchmarking
and OnePlus 12 remain unverified in this correction. No Lab runtime was inspected or run.

For 0.12.11, 23 component/choice/rendering cases pass on 7T, including frame-by-frame equality of live/draw-only press and release silhouettes, bounded settling after rapid changes and deferring theme capture until the control motion settles. This avoids a readback stall halfway through the outline morph. Debug/instrumentation builds, 12 UI unit tests and lint pass (0 errors / 8 existing warnings). The native connected contours, earlier theme/selection regressions, populated header and navigation-shadow checks remain covered.

For 0.12.10, three pixel regressions first reproduce the transparent scrolled app bar, lost navigation shadow and invisible connected segments against 0.12.9. A 12-entry Home fixture checks scrolling both the calorie summary and final diary rows behind an unchanged opaque header, and retains row actions. Native connected choice shapes/visible tonal fill and content-drawn dropShadow restore the other two regressions. The 20 component/choice/rendering cases pass on 7T, together with 12 UI unit tests, debug/instrumentation builds and lint (0 errors / 8 existing warnings). Populated scroll screenshots and Settings groups were inspected on 7T; user diary data is untouched.

For 0.12.8, native-resolution root GraphicsLayer capture replaces the downsampled software screenshot. A physical one-pixel/detail regression fails against 0.12.7 (73% maximum channel difference), then passes with unscaled layer capture; the same test also checks Cyrillic text retains its old palette and pixel detail outside the circle. All 17 component/choice tests pass on OnePlus 7T and API 37; 12 focused UI unit tests, debug/instrumentation builds and lint pass (0 errors / 9 existing warnings). Recording on 7T confirms visible text throughout the radial transition. The temporary capture uses up to 24 MiB and is discarded at completion; oversized windows use the surface fade.

For 0.12.7, a contour pixel regression first fails against 0.12.6 (43% maximum channel difference), then passes with opaque rectangular replay and native rounded-edge drawing. All 16 component/choice tests pass on OnePlus 7T and API 37; 12 focused UI unit tests, debug/instrumentation builds and lint pass (0 errors / 9 existing warnings). Native-size crops of a recorded 7T light/dark sequence show no temporary panel outline while its colors follow the same reveal circle.

For 0.12.6, debug and instrumentation APKs build, 12 focused UI unit tests pass, and lint reports 0 errors / 9 existing warnings. All 24 component/settings/navigation cases pass on both OnePlus 7T and API 37. The new panel-color pixel regression first fails against 0.12.5's early-color cutout, then passes with palette-layer replay; the earlier checked-state regressions still pass. Rapid three-palette replacement preserves prior color coverage, exposes only one set of semantic targets, invokes each action once and finishes in the latest palette. A recorded 7T light/dark sequence confirms that the partially scrolled Appearance panel follows the global circle without an early rectangular color change. No pointer-down image allocation or per-frame ColorScheme interpolation is added.

Completion retains rounded inverse-surface snackbars with native SnackbarHost MotionScheme/accessibility. The concise Add Food sheet and official Material Symbols Rounded were verified in 0.12.4, with bundled Apache-2.0 attribution. An earlier API 37 rapid hardware-key input test intermittently reordered `alex`; its isolated retry and latest complete Settings runs pass, so this is not claimed fixed. OnePlus 12 / OxygenOS 16 remains unverified.

## Official sources

- [Material3 beta01 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-beta01)
- [Material3 API](https://developer.android.com/reference/kotlin/androidx/compose/material3)
- [MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- [LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/LoadingIndicator.composable)
- [Compose variable fonts](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts)
- [SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen)
