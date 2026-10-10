# Just Cal architecture

## Application build identity

The root `version.properties` is the explicit application version source for Gradle and release validation; normal builds require no Git metadata. Settings reads the generated application BuildConfig version. Release signing is supplied through environment variables and kept outside application code. Procedures are in [RELEASING.md](RELEASING.md).

Just Cal is a single-module, offline Android application. Compose renders lifecycle-aware StateFlow screen state owned by Hilt ViewModels. Room implementation details stay behind DiaryRepository; deterministic nutrition calculation and input validation belong to domain code.

## Design system

[DESIGN.md](../DESIGN.md) is the visual source of truth, distinguishing existing tokens from proposed refinements. `ui/theme/Theme.kt` owns branded/dynamic color selection, `CalTypography`, `CalShapes`, `CalSpacing` and `CalMotion`; `ui/Components.kt` supplies shared nutrition, heading and icon presentation. `FloatingNavigation.kt` owns the deliberate floating-control geometry. The documentation does not introduce another runtime theme layer or change screen/state architecture. See [DESIGN_AUDIT.md](DESIGN_AUDIT.md) for evidence and the staged improvement roadmap.

## Navigation and the global action

Home, Diary and Settings each have a saved Navigation 3 back stack, including entry-scoped ViewModels and saveable UI state. Switching destinations preserves the other stacks. Back pops the current stack; Back at a secondary root returns to Home. NavDisplay handles transitions and predictive back.

Diary history rows and the direct “Choose a day” calendar both open the same Day Detail destination. That destination uses the selected local calendar date and the same diary presentation and repository as Home. Day Detail is retained when switching tabs.

The separate floating FAB is an action, not a fourth destination. It is visible on Home, Diary root and Day Detail, and hidden in Settings and the food editor. It opens a modal Add Food launcher with Scan package, Scan meal and Add manually. The launcher freezes today's date from Home/Diary root or the viewed date from Day Detail. Manual entry still opens the existing FoodEditor. Scans show a compact camera/photo source choice in the same sheet, then enter the shared image pipeline.

The navigation pill is content-sized and horizontally centered on every top-level screen and Day Detail. The global FAB sits separately above/right; its row remains reserved in Settings so hiding it does not change the pill's geometry. The outer host is transparent, with no full-width bottom surface. Scaffold measures the complete control host, including navigation-bar and horizontal safe insets, and screens reserve that height plus a content gap so the last item remains reachable. Inactive tabs are transparent with outlined icons; the selected tab uses a filled icon, a subtle compact capsule and animated semantic icon/label colors. Telegram is only a reference for geometry and interaction; no Telegram source or assets are included.

Pinned food-editor actions (Add to diary / Save changes) use a transparent host above the navigation bar or keyboard. Scroll content extends behind the button, with end padding based on the measured host height. Focus scrolling accounts for that overlay so the active field remains above the button when the keyboard opens.

## Local image acquisition

Both PACKAGE and MEAL use a serializable ScanRequest containing mode and target epoch day. ImageAcquisition and PhotoReview are typed Navigation 3 keys in the existing top-level stack. Acquisition is replaced by review; retake/reselect replaces review with acquisition using the same request and original source. Back/cancel returns to the originating diary screen without writing an entry. Launcher choice, request and review reference survive Activity recreation; Navigation 3 saves the keys for process restoration.

CameraCapture owns CameraX Preview, ImageCapture and the stable Compose CameraXViewfinder. It binds only while the navigation entry is resumed, unbinds its own use cases on pause/disposal, and disables the orientation listener with them. Still capture uses the rear camera and private temporary files. The orientation listener updates ImageCapture target rotation, including reverse orientations. CameraX objects never enter saved state or ViewModels.

ImageSourceScreen registers Activity Result RequestPermission and PickVisualMedia(ImageOnly). CAMERA is requested only on Take photo. Denial leaves photo selection available; rationale/retry and an explicit app-settings action handle repeated denial. Permission is checked again on resume and before camera operations. No storage/media permissions, FileProvider, external camera intent, custom gallery or network client are used. AndroidX falls back to the platform document picker when Photo Picker is unavailable.

ImagePreprocessor is the Android image boundary. Provider content URIs are streamed into a private cache snapshot, never resolved into a real path. Camera output is already private and is adopted directly. PreparedImage carries the owned URI string, source, scan request, upright width/height and actual MIME type. No Bitmap, CameraX object or AI-runtime type is stored in a navigation key or ViewModel.

Preparation runs on Dispatchers.IO. ImageDecoder validates actual image pixels, applies EXIF rotation/reflection, and decodes into sRGB software pixels. It limits input to 64 MiB and decode size to 12 million pixels / 4096 px longest edge, preserving aspect ratio without upscaling. These are generic resource limits, not a future model's input resolution. Smaller files retain their original encoded bytes (including EXIF); oversized images are saved upright as JPEG quality 95 or lossless PNG when alpha must be preserved, and the original temporary copy is removed. JPEG, PNG, WebP, HEIF/HEIC and AVIF are accepted when supported by the device decoder.

Photo Review decodes the same prepared URI through that boundary, with a separate display budget of 2 million pixels / 1920 px. The Bitmap lives only in the screen composition; recomposition does not decode it again. A future AI adapter should use ImagePreprocessor.decode for upright pixels and apply model-specific preprocessing in the AI layer. Review provides source-specific retake/reselect, current mode/date and Use photo. Use photo validates the reference and creates VerifiedImageInput; the current endpoint explicitly says recognition is not available and adds nothing to the diary. Production food recognition is not implemented; this diary flow does not invoke the experimental Local AI Lab runtime.

Ownership transfers from the acquisition ViewModel to review on success. Cancelled/failed work removes partial files, including results cancelled during dispatcher handoff. Review exit/replacement releases its private image explicitly, with entry disposal and ViewModel cleanup as safeguards. Camera callbacks after leaving the screen discard their output. Files left by process termination expire after 24 hours and are pruned when the image component starts; the OS may evict cache earlier. A restored missing image shows a recoverable error with retake/reselect. An interrupted picker import/capture is restarted by the user; original gallery images are never modified or deleted.

## Experimental Local AI Lab

Settings → Local AI Lab is an isolated developer destination. Its nested Navigation 3 image flow reuses ImageSourceScreen, CameraX / Photo Picker and ImagePreprocessor while retaining the Lab ViewModel and loaded engine. It never calls DiaryRepository or creates food entries. Leaving the Lab cancels work and releases owned images and native resources; configuration recreation retains the entry-scoped ViewModel. Process restoration retains prompt/preset and private model files but requires reloading the engine and selecting an image again.

The `ai` package exposes LocalAiEngine using application-owned LocalModel, AiBackend, PreparedImage and AiMetrics types. LiteRtLocalAiEngine is the only adapter that imports LiteRT-LM types. It uses the Kotlin Android API pinned to 0.17.1, with Gemma 4 E2B vision as the initial target. CPU or GPU is selected explicitly for both language and vision; initialization errors are visible without fallback. Optional Android native-library declarations for libvndksupport.so and libOpenCL.so follow the SDK guidance.

LocalModelStore streams a SAF document into a UUID-named directory under noBackupFilesDir/local-ai-models. Metadata preserves the display name; the provider URI is never passed as a native path. Size checks, available-storage checks, private containment, partial-import cleanup and an atomic directory rename protect failed/cancelled imports. Models are excluded from backups and the APK; runtime caches are private, separated by model/backend, and removed with the model. Importing a model does not load it. Model/backend changes and deletion require unloading first.

LocalAiSession owns explicit import/load/ready/run/cancel/error transitions independently of Compose. Engine initialization, decoding, inference and native cleanup run off the main thread. An engine is retained across repeated runs; every run creates a fresh conversation with a bounded token budget and closes it afterwards. A process-wide permit prevents overlapping engines when a previous navigation entry is still cleaning up. Cancelling calls native cancelProcess and awaits its terminal callback before closing the conversation. Entry removal cancels/joins the current operation before unloading the engine, using a cleanup scope that survives ViewModel disposal.

Inference decodes bounded upright sRGB pixels through the generic image boundary, encodes them as a standard image and passes Content.ImageBytes plus Content.Text to LiteRT-LM. The adapter recycles its Bitmap immediately after encoding; the SDK owns model-specific tensor preprocessing. Preview Bitmaps live only in screen composition. Large encoded image data never enters saved navigation or ViewModel state.

The editable Nutrition extraction preset uses ConversationConfig(enableResponseFormat = true) and ResponseFormat.json with a seven-field JSON Schema, supported by this SDK. Thinking is disabled. The original streamed answer remains visible; NutritionExtraction accepts only a complete JSON object (or one complete JSON fence), exact expected keys, explicit nulls and bounded nonnegative BigDecimal values. The weight basis and package weight remain separate. Syntactic validity does not establish factual accuracy: these results stay untrusted, are never normalized into diary nutrition, and cannot be saved to the diary.

App initialization/inference/first-visible-response times use a monotonic clock. Native time-to-first-token, prefill and decode rates come exclusively from Conversation.getBenchmarkInfo with benchmarking enabled. Unsupported/nonfinite metrics show unavailable; no throughput is estimated. Cancellation retains the partial answer without reporting a completed benchmark. See [LOCAL_AI_LAB.md](LOCAL_AI_LAB.md) for model preparation, verification and remaining device coverage.

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
