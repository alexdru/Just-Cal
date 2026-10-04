# Just Cal architecture

## Application build identity

The root `version.properties` is the explicit application version source for Gradle and release validation; normal builds require no Git metadata. Settings reads the generated application BuildConfig version. Release signing is supplied through environment variables and kept outside application code. Procedures are in [RELEASING.md](RELEASING.md).

Just Cal is a single-module, offline Android application. Compose renders lifecycle-aware StateFlow screen state owned by Hilt ViewModels. Room implementation details stay behind DiaryRepository; deterministic nutrition calculation and input validation belong to domain code.

## Navigation and the global action

Home, Diary and Settings each have a saved Navigation 3 back stack, including entry-scoped ViewModels and saveable UI state. Switching destinations preserves the other stacks. Back pops the current stack; Back at a secondary root returns to Home. NavDisplay handles transitions and predictive back.

Diary history rows and the direct “Choose a day” calendar both open the same Day Detail destination. That destination uses the selected local calendar date and the same diary presentation and repository as Home. Day Detail is retained when switching tabs.

The separate floating FAB is an action, not a fourth destination. It is visible on Home, Diary root and Day Detail, and hidden in Settings and the food editor. It opens a modal Add Food launcher with Scan package, Scan meal and Add manually. The launcher freezes today's date from Home/Diary root or the viewed date from Day Detail. Manual entry still opens the existing FoodEditor. Scans show a compact camera/photo source choice in the same sheet, then enter the shared image pipeline.

The navigation pill is content-sized and horizontally centered on every top-level screen and Day Detail. The global FAB sits separately above/right; its row remains reserved in Settings so hiding it does not change the pill's geometry. The outer host is transparent, with no full-width bottom surface. Scaffold measures the complete control host, including navigation-bar and horizontal safe insets, and screens reserve that height plus a content gap so the last item remains reachable. Inactive tabs are transparent; a compact capsule and animated semantic icon/label colors indicate selection. Telegram is only a reference for geometry and interaction; no Telegram source or assets are included.

Pinned food-editor actions (Add to diary / Save changes) use a transparent host above the navigation bar or keyboard. Scroll content extends behind the button, with end padding based on the measured host height. Focus scrolling accounts for that overlay so the active field remains above the button when the keyboard opens.

## Local image acquisition

Both PACKAGE and MEAL use a serializable ScanRequest containing mode and target epoch day. ImageAcquisition and PhotoReview are typed Navigation 3 keys in the existing top-level stack. Acquisition is replaced by review; retake/reselect replaces review with acquisition using the same request and original source. Back/cancel returns to the originating diary screen without writing an entry. Launcher choice, request and review reference survive Activity recreation; Navigation 3 saves the keys for process restoration.

CameraCapture owns CameraX Preview, ImageCapture and the stable Compose CameraXViewfinder. It binds only while the navigation entry is resumed, unbinds its own use cases on pause/disposal, and disables the orientation listener with them. Still capture uses the rear camera and private temporary files. The orientation listener updates ImageCapture target rotation, including reverse orientations. CameraX objects never enter saved state or ViewModels.

ImageSourceScreen registers Activity Result RequestPermission and PickVisualMedia(ImageOnly). CAMERA is requested only on Take photo. Denial leaves photo selection available; rationale/retry and an explicit app-settings action handle repeated denial. Permission is checked again on resume and before camera operations. No storage/media permissions, FileProvider, external camera intent, custom gallery or network client are used. AndroidX falls back to the platform document picker when Photo Picker is unavailable.

ImagePreprocessor is the Android image boundary. Provider content URIs are streamed into a private cache snapshot, never resolved into a real path. Camera output is already private and is adopted directly. PreparedImage carries the owned URI string, source, scan request, upright width/height and actual MIME type. No Bitmap, CameraX object or AI-runtime type is stored in a navigation key or ViewModel.

Preparation runs on Dispatchers.IO. ImageDecoder validates actual image pixels, applies EXIF rotation/reflection, and decodes into sRGB software pixels. It limits input to 64 MiB and decode size to 12 million pixels / 4096 px longest edge, preserving aspect ratio without upscaling. These are generic resource limits, not a future model's input resolution. Smaller files retain their original encoded bytes (including EXIF); oversized images are saved upright as JPEG quality 95 or lossless PNG when alpha must be preserved, and the original temporary copy is removed. JPEG, PNG, WebP, HEIF/HEIC and AVIF are accepted when supported by the device decoder.

Photo Review decodes the same prepared URI through that boundary, with a separate display budget of 2 million pixels / 1920 px. The Bitmap lives only in the screen composition; recomposition does not decode it again. A future AI adapter should use ImagePreprocessor.decode for upright pixels and apply model-specific preprocessing in the AI layer. Review provides source-specific retake/reselect, current mode/date and Use photo. Use photo validates the reference and creates VerifiedImageInput; the current endpoint explicitly says recognition is not available and adds nothing to the diary. No inference engine, OCR or fake nutrition exists.

Ownership transfers from the acquisition ViewModel to review on success. Cancelled/failed work removes partial files, including results cancelled during dispatcher handoff. Review exit/replacement releases its private image explicitly, with entry disposal and ViewModel cleanup as safeguards. Camera callbacks after leaving the screen discard their output. Files left by process termination expire after 24 hours and are pruned when the image component starts; the OS may evict cache earlier. A restored missing image shows a recoverable error with retake/reselect. An interrupted picker import/capture is restarted by the user; original gallery images are never modified or deleted.

## Diary and calculations

Room persists individual food entries: original nutrition per 100 g and consumed weight in integer hundredths, source, recorded local epoch day and creation timestamp. Recorded days do not change when the device time zone changes. Home refreshes the active local date on resume and across midnight.

Portions and daily totals are derived with exact BigDecimal arithmetic, rounded only for presentation. There is no persisted Day entity or mutable daily-total cache. Adding, editing or deleting an entry updates Room flows and the affected summaries.

## Settings and local profile

GoalPreferences owns the existing DataStore preferences file. It atomically saves optional local display name, calorie target, independently optional protein/fat/carbohydrate targets, brightness (System/Light/Dark) and color style (Just Cal/Material You). Existing appearance and nutrition keys are retained; the additive color_style key defaults to Just Cal when absent or unrecognized. There are no accounts, authentication or cloud profiles.

The calorie target is a required positive whole number, initially an editable 2000 kcal placeholder. Macro targets are independently nullable positive values in integer hundredths of grams. A blank field removes that target; zero is rejected, never used as an absence sentinel. Existing preferences without macro keys mean no macro targets.

Home, Diary and Day Detail observe the same saved settings reactively. Configured macros display consumed / target; unconfigured macros display consumed grams alone. Unsaved Settings drafts, including both appearance choices, survive tab switches and configuration changes but do not alter displayed targets or appearance until saved.

Just Cal is the default green leaf palette, with complete semantic Material color roles in light and dark. Material You opts into Android dynamic colors on API 31+, with a Just Cal fallback below that. Custom components use MaterialTheme semantic colors; brightness and palette are independent.

## Application language and formatting

English is the unqualified resource fallback; Russian has matching translated resources. AGP generates the locale config from resources.properties and resource qualifiers, filtered to en/ru so dependency translations do not advertise unsupported languages.

The in-app System default/English/Русский selector uses AppCompatDelegate application locales and applies immediately, independently of Save changes. MainActivity extends AppCompatActivity to support the same official API on Android 11/12; AndroidX autoStoreLocales owns storage there. Android 13+ delegates to the framework locale service, synchronizing with system app-language settings. There is no language key in DataStore, custom context wrapping or resource mutation. Empty application locales mean System default; the picker rereads the actual app override on configuration changes and resume.

Locale recreation retains the existing saved Navigation 3 keys/stacks and Settings drafts. Route and enum identifiers stay language-independent. Dates use the resource configuration locale; nutrition numbers use NumberFormat with exact BigDecimal input and HALF_UP presentation rounding, including consumed weight and goals. Editable decimal input continues to accept comma or dot.

The locale implementation follows [Android per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages).
