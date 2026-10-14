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
application signing/identity are preserved. The corrective UI version is 0.12.2 / code 19.

Roboto Flex uses one official OFL variable binary with verified Russian cmap coverage.
Root MotionScheme is expressive; fast utility effects remain standard. Root swipes and
tap navigation share one pager state; child screens retain Native Navigation 3 Back.
Brightness and Themes share a 600 ms radial reveal from the selected control over a temporary
old frame bounded to 0.6 MP / 1080 px longest edge, with one palette commit. No per-frame
ColorScheme recomposition; repeated selections replace the visible transition.
valid text settings debounce and persist automatically without global Save.
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

## Official sources

- [Material3 beta01 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-beta01)
- [Material3 API](https://developer.android.com/reference/kotlin/androidx/compose/material3)
- [MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- [LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/LoadingIndicator.composable)
- [Compose variable fonts](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts)
- [SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen)
