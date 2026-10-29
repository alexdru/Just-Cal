package com.justcal.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.R
import com.justcal.app.camera.*
import com.justcal.app.ui.theme.JustCalTheme
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Review states use a generated private photo; no model or ignored photo directory is required. */
@RunWith(AndroidJUnit4::class)
class PhotoReviewInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun photo(images: ImagePreprocessor): PreparedImage {
        val file = images.newCaptureFile()
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }
            listOf("GREEK YOGURT", "Nutrition per 100 g", "Energy 183.2 kcal",
                "Protein 11 g", "Fat 6.4 g", "Carbs 20.4 g", "Net weight 240 g").forEachIndexed { i, text ->
                canvas.drawText(text, 32f, 110f + i * 60, paint)
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        return PreparedImage(file.toUri().toString(), ImageSource.PHOTO_PICKER,
            ScanRequest(ScanMode.PACKAGE, LocalDate.now().withDayOfMonth(1).toEpochDay()), 480, 640, "image/png")
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            java.io.File(context.externalCacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun analysisReplacesActionsKeepsPhotoStillAndRestoresRetryForBothModes() {
        val images = ImagePreprocessor(context)
        val image = photo(images)
        var mode by mutableStateOf(ScanMode.PACKAGE)
        var state by mutableStateOf(PackageReviewState())
        var assistance by mutableStateOf(true)
        var starts = 0
        var backs = 0
        compose.setContent {
            JustCalTheme {
                Surface(Modifier.fillMaxSize()) {
                    PhotoReviewScreen(image.copy(request = image.request.copy(mode = mode)), images,
                        onReplace = {}, onBack = { backs++ }, packageState = state,
                        onRecognize = { starts++; state = PackageReviewState(busy = true) },
                        packageModel = PackageAssistanceModel(false, "Gemma 4 E2B"),
                        packageAssistanceEnabled = assistance, onPackageAssistance = { assistance = it })
                }
            }
        }
        fun text(id: Int) = compose.onNodeWithText(context.getString(id))
        try {
            for (scanMode in ScanMode.entries) {
                compose.runOnIdle { mode = scanMode; state = PackageReviewState() }
                compose.waitUntil(5000) {
                    compose.onAllNodesWithText(context.getString(R.string.use_photo)).fetchSemanticsNodes()
                        .any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
                }
                if (scanMode == ScanMode.PACKAGE) {
                    compose.onNodeWithTag("package_ai_assistance").assertIsOn().performClick().assertIsOff()
                }
                val preview = compose.onNodeWithTag("review_photo_preview").fetchSemanticsNode().boundsInRoot
                screenshot("review-${scanMode.name.lowercase()}-ready.png")
                text(R.string.use_photo).performClick()
                compose.onNodeWithTag("photo_review_processing").assertIsDisplayed()
                for (id in listOf(R.string.use_photo, R.string.choose_another, R.string.ai_use_photo_manual, R.string.ai_models)) {
                    text(id).assertDoesNotExist()
                }
                compose.onNodeWithTag("package_ai_assistance").assertDoesNotExist()
                text(if (scanMode == ScanMode.PACKAGE) R.string.ocr_processing else R.string.ai_processing).assertIsDisplayed()
                assertEquals(preview, compose.onNodeWithTag("review_photo_preview").fetchSemanticsNode().boundsInRoot)
                screenshot("review-${scanMode.name.lowercase()}-processing.png")
                if (scanMode == ScanMode.PACKAGE) {
                    compose.runOnIdle { state = state.copy(processingModel = "Gemma 4 E2B") }
                    text(R.string.ai_package_model_processing).assertIsDisplayed()
                    compose.onNodeWithText("Gemma 4 E2B").assertIsDisplayed()
                    assertEquals(preview, compose.onNodeWithTag("review_photo_preview").fetchSemanticsNode().boundsInRoot)
                    screenshot("review-package-model-processing.png")
                }
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
                compose.runOnIdle { state = PackageReviewState(failed = true) }
                text(R.string.review_failed).assertIsDisplayed()
                text(R.string.retry).assertIsDisplayed().assertIsEnabled()
                text(R.string.ai_use_photo_manual).performScrollTo().assertIsDisplayed()
                if (scanMode == ScanMode.PACKAGE) compose.onNodeWithTag("package_ai_assistance").performScrollTo().assertIsOff()
                text(R.string.retry).performClick()
                compose.onNodeWithTag("photo_review_processing").assertIsDisplayed()
            }
            compose.runOnIdle { assertEquals(4, starts); assertEquals(2, backs) }
        } finally { images.discard(image.uri) }
    }

    @Test fun reviewActionsRemainReadableInEnglishRussianLightDarkAndLargeText() {
        val images = ImagePreprocessor(context)
        val image = photo(images)
        var language by mutableStateOf("ru")
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        var model by mutableStateOf<String?>(null)
        var state by mutableStateOf(PackageReviewState())
        var modelClicks = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                JustCalTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        PhotoReviewScreen(image, images, onReplace = {}, onBack = {}, packageState = state,
                            onModels = { modelClicks++ }, packageModel = PackageAssistanceModel(false, model))
                    }
                }
            }
        }
        try {
            for (lang in listOf("ru", "en")) {
                for (large in listOf(false, true)) {
                    compose.runOnIdle {
                        language = lang; dark = large; fontScale = if (large) 1.5f else 1f
                        model = if (large) "Gemma 4 E2B" else null
                        state = PackageReviewState()
                    }
                    val localized: Context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(lang))
                    })
                    compose.onNodeWithText(localized.getString(R.string.use_photo)).assertIsDisplayed()
                    compose.onNodeWithText(localized.getString(R.string.review_package_guidance)).assertIsDisplayed()
                    screenshot("review-${lang}-${if (large) "dark-150" else "light-100"}.png")
                    val models = compose.onNodeWithText(localized.getString(R.string.ai_models))
                    models.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
                    compose.waitForIdle()
                    assertTrue("Focused action must clear the pinned button",
                        models.fetchSemanticsNode().boundsInRoot.bottom <=
                            compose.onNodeWithTag("photo_review_primary").fetchSemanticsNode().boundsInRoot.top)
                    screenshot("review-${lang}-${if (large) "dark-150" else "light-100"}-focused.png")
                    val previousClicks = modelClicks
                    models.performClick()
                    compose.runOnIdle { assertEquals(previousClicks + 1, modelClicks) }
                    compose.onNodeWithText(localized.getString(R.string.ai_use_photo_manual)).performScrollTo().assertIsDisplayed()
                    compose.runOnIdle { state = PackageReviewState(busy = true, processingModel = "Gemma 4 E2B") }
                    compose.onNodeWithText(localized.getString(R.string.ai_package_model_processing)).assertIsDisplayed()
                    screenshot("review-${lang}-${if (large) "dark-150" else "light-100"}-busy.png")
                }
            }
            compose.runOnIdle { assertEquals(4, modelClicks) }
        } finally { images.discard(image.uri) }
    }
}
