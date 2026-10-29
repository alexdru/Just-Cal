package com.justcal.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.MainActivity
import com.justcal.app.R
import com.justcal.app.data.DiaryDatabase
import com.justcal.app.domain.EntrySource
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Platform picker integration without a custom gallery or storage permission. */
@RunWith(AndroidJUnit4::class)
class PhotoPickerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNode(hasText(label(id)) and hasClickAction()).performClick()

    private fun fixture(color: Int, labelText: Boolean = false, imageFile: java.io.File? = null): Uri {
        val resolver = compose.activity.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JustCal-picker-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis() + if (labelText) 1000 else 0)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            val bitmap = imageFile?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                ?: Bitmap.createBitmap(if (labelText) 1200 else 320, if (labelText) 800 else 240, Bitmap.Config.ARGB_8888)
            try {
                if (imageFile == null) bitmap.eraseColor(color)
                if (labelText) {
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK; textSize = 44f }
                    listOf("GREEK YOGURT", "Net weight 240 g", "Nutrition per 100 g",
                        "Protein 11 g", "Fat 6.4 g", "Carbohydrates 20.4 g",
                        "Energy 183.2 kcal").forEachIndexed { index, text ->
                        canvas.drawText(text, 40f, 80f + index * 95, paint)
                    }
                }
                resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    private fun selectPhoto(index: Int) {
        compose.waitUntil(15000) {
            nodes(automation.rootInActiveWindow).count { it.contentDescription?.startsWith("Photo taken") == true } >= 2
        }
        val photo = nodes(automation.rootInActiveWindow).filter { it.contentDescription?.startsWith("Photo taken") == true }[index]
        var clickable: AccessibilityNodeInfo? = photo
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        check(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        // Newer pickers show Done even for a single image; older pickers return immediately.
        compose.waitUntil(15000) {
            val done = nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() == "Done" }
            if (done != null) {
                var button: AccessibilityNodeInfo? = done
                while (button != null && !button.isClickable) button = button.parent
                button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test fun realModelsOpenEditableConfirmationAndSaveOnceToSelectedDay() {
        val photos = listOf(fixture(Color.WHITE, labelText = true), fixture(Color.WHITE, labelText = true))
        val date = java.time.LocalDate.now().withDayOfMonth(1)
        val database = androidx.room.Room.databaseBuilder(compose.activity.applicationContext,
            DiaryDatabase::class.java, "just-cal.db").build()
        fun entries() = kotlinx.coroutines.runBlocking { database.diaryDao().observeDay(date.toEpochDay()).first() }
        val baseline = entries().map { it.id }.toSet()
        val uniqueName = "OCR flow " + UUID.randomUUID()
        try {
            click(R.string.history)
            click(R.string.choose_day)
            val fullDate = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                .withLocale(compose.activity.resources.configuration.locales[0]))
            compose.onNode(hasText(fullDate, substring = true, ignoreCase = true) and hasClickAction()).performClick()
            click(R.string.open_day)
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_package)
            click(R.string.choose_photo)
            selectPhoto(0)
            click(R.string.use_photo)
            compose.waitUntil(20000) {
                check(compose.onAllNodesWithText(label(R.string.ocr_failed)).fetchSemanticsNodes().isEmpty()) { "Real OCR failed on the rendered text fixture" }
                compose.onAllNodesWithText(label(R.string.ocr_confirmation)).fetchSemanticsNodes().isNotEmpty()
            }
            val screenshot = automation.takeScreenshot()
            try {
                val file = java.io.File(compose.activity.getExternalFilesDir(null), "ocr-confirmation.png")
                file.outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { screenshot.recycle() }
            fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())
            field(R.string.food_name).assertTextContains("Greek yogurt")
            field(R.string.energy).performScrollTo().assertTextContains("183.2")
            field(R.string.amount).performScrollTo().assertTextContains("240")
            org.junit.Assert.assertEquals(baseline, entries().map { it.id }.toSet())
            field(R.string.food_name).performScrollTo().performTextReplacement(uniqueName)
            field(R.string.amount).performScrollTo().performTextReplacement("120")
            compose.activityRule.scenario.recreate()
            field(R.string.amount).performScrollTo().assertTextContains("120")
            click(R.string.save_food)
            compose.waitUntil(5000) { entries().any { it.name == uniqueName } }
            val saved = entries().filter { it.name == uniqueName }
            org.junit.Assert.assertEquals(1, saved.size)
            org.junit.Assert.assertEquals(12000L, saved.single().eatenGramsHundredths)
            org.junit.Assert.assertEquals(EntrySource.PACKAGE_SCAN, saved.single().toDomain().source)
        } finally {
            kotlinx.coroutines.runBlocking { entries().filter { it.name == uniqueName }.forEach { database.diaryDao().delete(it.id) } }
            database.close()
            photos.forEach { compose.activity.contentResolver.delete(it, null, null) }
        }
    }

    @Test fun selectedMealModelKeepsPhotoAndDateUntilExplicitEditorSave() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realAiModels") == "true")
        val context = compose.activity.applicationContext
        val catalog = com.justcal.app.ai.ModelCatalog(context)
        val models = com.justcal.app.ai.LocalModelStore(context, com.justcal.app.ai.LiteRtModelInspector(), catalog)
        val preferences = com.justcal.app.data.GoalPreferences(context)
        val previousChoices = kotlinx.coroutines.runBlocking { preferences.aiModels.first() }
        val model = kotlinx.coroutines.runBlocking { models.list().first {
            models.validate(it).catalogId == "gemma4-e2b"
        } }
        // Picker ordering differs across Android providers; both test-owned choices show the same food.
        val extra = fixture(Color.WHITE, imageFile = java.io.File(context.getExternalFilesDir(null), "meal-fixtures/apple.jpg"))
        val resolver = compose.activity.contentResolver
        val photo = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JustCal-meal-${UUID.randomUUID()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis() + 20_000)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        val date = java.time.LocalDate.now().withDayOfMonth(1)
        val database = androidx.room.Room.databaseBuilder(context, DiaryDatabase::class.java, "just-cal.db").build()
        fun entries() = kotlinx.coroutines.runBlocking { database.diaryDao().observeDay(date.toEpochDay()).first() }
        val baseline = entries().map { it.id }.toSet()
        val uniqueName = "Meal flow " + UUID.randomUUID()
        try {
            resolver.openOutputStream(photo)!!.use { output ->
                java.io.File(compose.activity.getExternalFilesDir(null), "meal-fixtures/apple.jpg").inputStream().use { it.copyTo(output) }
            }
            resolver.update(photo, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            kotlinx.coroutines.runBlocking { preferences.selectAiModel("meal", model.id) }
            click(R.string.history)
            click(R.string.choose_day)
            val fullDate = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                .withLocale(compose.activity.resources.configuration.locales[0]))
            compose.onNode(hasText(fullDate, substring = true, ignoreCase = true) and hasClickAction()).performClick()
            click(R.string.open_day)
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_meal)
            click(R.string.choose_photo)
            selectPhoto(0)
            automation.takeScreenshot().let { bitmap ->
                try { java.io.File(context.getExternalFilesDir(null), "meal-flow-review.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            compose.onNodeWithText(label(R.string.ai_models)).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.lab_import_model)).assertExists()
            compose.onNodeWithContentDescription(label(R.string.close)).performClick()
            compose.onNodeWithText(label(R.string.mode_meal)).assertExists()
            // Returning from Models recreates the preview; wait for its asynchronous decode.
            // Semantics performClick on a disabled button would otherwise do nothing.
            val usePhoto = hasText(label(R.string.use_photo)) and hasClickAction()
            compose.onNode(usePhoto).assertIsDisplayed()
            compose.waitUntil(15_000) {
                compose.onAllNodes(usePhoto and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(usePhoto).assertIsEnabled().performClick()
            compose.waitUntil(90_000) {
                check(compose.onAllNodesWithText(label(R.string.ai_runtime_error)).fetchSemanticsNodes().isEmpty()) { "Meal model failed; inspect JustCalAI diagnostics" }
                compose.onAllNodes(hasText(label(R.string.food_name)) and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())
            compose.onNodeWithText(label(R.string.ai_suggestions)).assertExists()
            field(R.string.food_name).performScrollTo()
            field(R.string.food_name).assertTextContains("apple", ignoreCase = true)
            field(R.string.amount).performScrollTo().assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            org.junit.Assert.assertEquals(baseline, entries().map { it.id }.toSet())
            field(R.string.food_name).performScrollTo().performTextReplacement(uniqueName)
            field(R.string.energy).performScrollTo().performTextReplacement("183.2")
            field(R.string.protein).performScrollTo().performTextReplacement("11")
            field(R.string.fat).performScrollTo().performTextReplacement("6.4")
            field(R.string.carbs).performScrollTo().performTextReplacement("20.4")
            field(R.string.amount).performScrollTo().performTextReplacement("120")
            compose.activityRule.scenario.recreate()
            field(R.string.amount).performScrollTo().assertTextContains("120")
            click(R.string.save_food)
            compose.waitUntil(5000) { entries().any { it.name == uniqueName } }
            val saved = entries().filter { it.name == uniqueName }
            org.junit.Assert.assertEquals(1, saved.size)
            org.junit.Assert.assertEquals(12000L, saved.single().eatenGramsHundredths)
            org.junit.Assert.assertEquals(EntrySource.MANUAL, saved.single().toDomain().source)
        } finally {
            kotlinx.coroutines.runBlocking {
                previousChoices.forEach { (task, id) -> preferences.selectAiModel(task, id) }
                entries().filter { it.name == uniqueName }.forEach { database.diaryDao().delete(it.id) }
            }
            database.close()
            resolver.delete(photo, null, null)
            resolver.delete(extra, null, null)
        }
    }

    @Test fun selectedPackageModelOnlyCompletesMissingMacrosUntilExplicitSave() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realPackageMacros") == "true")
        val context = compose.activity.applicationContext
        val catalog = com.justcal.app.ai.ModelCatalog(context)
        val models = com.justcal.app.ai.LocalModelStore(context, com.justcal.app.ai.LiteRtModelInspector(), catalog)
        val preferences = com.justcal.app.data.GoalPreferences(context)
        val previous = kotlinx.coroutines.runBlocking { preferences.aiModels.first()["package"] }
        val model = kotlinx.coroutines.runBlocking { models.list().first {
            models.validate(it).catalogId == "gemma4-e2b"
        } }
        val resolver = compose.activity.contentResolver
        val photos = mutableListOf<Uri>()
        val date = java.time.LocalDate.now().withDayOfMonth(1)
        val database = androidx.room.Room.databaseBuilder(context, DiaryDatabase::class.java, "just-cal.db").build()
        fun entries() = kotlinx.coroutines.runBlocking { database.diaryDao().observeDay(date.toEpochDay()).first() }
        val baseline = entries().map { it.id }.toSet()
        val uniqueName = "Package macros " + UUID.randomUUID()
        try {
            repeat(2) {
                val photo = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "JustCal-package-${UUID.randomUUID()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis() + 20_000)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }))
                photos += photo
                resolver.openOutputStream(photo)!!.use { output ->
                    java.io.File(context.getExternalFilesDir(null), "package-batch-fixtures/1792612058338.jpg")
                        .inputStream().use { it.copyTo(output) }
                }
                resolver.update(photo, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            kotlinx.coroutines.runBlocking { preferences.selectAiModel("package", model.id) }
            click(R.string.history)
            click(R.string.choose_day)
            val fullDate = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                .withLocale(compose.activity.resources.configuration.locales[0]))
            compose.onNode(hasText(fullDate, substring = true, ignoreCase = true) and hasClickAction()).performClick()
            click(R.string.open_day)
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_package)
            click(R.string.choose_photo)
            selectPhoto(0)
            val assistance = compose.onNodeWithTag("package_ai_assistance")
            fun awaitAssistance() {
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasTestTag("package_ai_assistance") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
            }
            awaitAssistance()
            assistance.performScrollTo().assertIsOn().performClick().assertIsOff()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("package_ai_assistance").performScrollTo().assertIsOff()
            compose.onNodeWithText(label(R.string.ai_models)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(label(R.string.close)).performClick()
            compose.onNodeWithTag("package_ai_assistance").performScrollTo().assertIsOff()
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            click(R.string.use_photo)
            fun field(id: Int) = compose.onNode(hasText(label(id)) and hasSetTextAction())
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText(label(R.string.energy)) and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            field(R.string.energy).performScrollTo().assertTextContains("150")
            field(R.string.carbs).performScrollTo().assertTextContains("24.8")
            for (id in listOf(R.string.protein, R.string.fat, R.string.amount)) {
                field(id).performScrollTo().assert(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            }
            compose.onNodeWithText(label(R.string.ai_package_macro_suggestions)).assertDoesNotExist()
            org.junit.Assert.assertEquals(baseline, entries().map { it.id }.toSet())
            org.junit.Assert.assertEquals(model.id, kotlinx.coroutines.runBlocking { preferences.aiModels.first()["package"] })
            compose.onNodeWithContentDescription(label(R.string.close)).performClick()
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_package)
            click(R.string.choose_photo)
            selectPhoto(0)
            awaitAssistance()
            compose.onNodeWithTag("package_ai_assistance").performScrollTo().assertIsOn()
            automation.takeScreenshot().let { bitmap ->
                try { java.io.File(context.getExternalFilesDir(null), "package-assistance-review.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            click(R.string.use_photo)
            compose.waitUntil(90_000) {
                check(compose.onAllNodesWithText(label(R.string.ocr_failed)).fetchSemanticsNodes().isEmpty())
                compose.onAllNodesWithText(label(R.string.ai_package_macro_suggestions)).fetchSemanticsNodes().isNotEmpty()
            }
            field(R.string.energy).performScrollTo().assertTextContains("150")
            field(R.string.protein).performScrollTo().assertTextContains("4.5")
            field(R.string.fat).performScrollTo().assertTextContains("3.6")
            field(R.string.carbs).performScrollTo().assertTextContains("24.8")
            field(R.string.amount).performScrollTo().assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            org.junit.Assert.assertEquals(baseline, entries().map { it.id }.toSet())
            field(R.string.food_name).performScrollTo().performClick().performTextReplacement(uniqueName)
            field(R.string.food_name).assertTextContains(uniqueName)
            field(R.string.amount).performScrollTo().performClick().performTextReplacement("120")
            field(R.string.food_name).assertTextContains(uniqueName)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            field(R.string.food_name).performScrollTo().assertTextContains(uniqueName)
            field(R.string.protein).performScrollTo().assertTextContains("4.5")
            field(R.string.fat).performScrollTo().assertTextContains("3.6")
            field(R.string.energy).performScrollTo().assertTextContains("150")
            field(R.string.amount).performScrollTo().assertTextContains("120")
            automation.takeScreenshot().let { bitmap ->
                try { java.io.File(context.getExternalFilesDir(null), "package-macros-confirmation.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            click(R.string.save_food)
            compose.waitUntil(5000) { entries().any { it.name == uniqueName } }
            val saved = entries().filter { it.name == uniqueName }
            org.junit.Assert.assertEquals(1, saved.size)
            org.junit.Assert.assertEquals(12000L, saved.single().eatenGramsHundredths)
            org.junit.Assert.assertEquals(EntrySource.PACKAGE_SCAN, saved.single().toDomain().source)
        } finally {
            kotlinx.coroutines.runBlocking {
                preferences.selectAiModel("package", previous)
                entries().filter { it.name == uniqueName }.forEach { database.diaryDao().delete(it.id) }
            }
            database.close()
            photos.forEach { resolver.delete(it, null, null) }
        }
    }

    @Test fun cancellingPickerReturnsDirectlyForBothScanModes() {
        listOf(R.string.scan_package, R.string.scan_meal).forEach { mode ->
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(mode)
            click(R.string.choose_photo)
            compose.waitUntil(15000) {
                automation.rootInActiveWindow?.packageName?.toString()?.let { it != compose.activity.packageName } == true
            }
            check(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(label(R.string.add_food))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onAllNodesWithText(label(R.string.choose_photo)).assertCountEquals(0)
            compose.onAllNodesWithText(label(R.string.cancel)).assertCountEquals(0)
        }
    }

    @Test fun systemPickerReviewReselectAndCancel() {
        val cache = java.io.File(compose.activity.cacheDir, "image-input")
        val previousFiles = cache.listFiles().orEmpty().map { it.name }.toSet()
        val photos = listOf(fixture(Color.RED), fixture(Color.BLUE))
        try {
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_package)
            click(R.string.choose_photo)
            selectPhoto(0)
            compose.onNodeWithText(label(R.string.mode_package)).assertExists()
            compose.onNodeWithText(label(R.string.review_package_guidance)).assertExists()
            compose.onNodeWithText(label(R.string.review_package_ocr)).assertExists()
            compose.onNodeWithText(label(R.string.choose_another)).performScrollTo().performClick()
            selectPhoto(1)
            compose.onNodeWithText(label(R.string.mode_package)).assertExists()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            click(R.string.use_photo)
            compose.waitUntil(20000) {
                compose.onAllNodesWithText(label(R.string.ocr_failed)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(label(R.string.retry)).assertIsEnabled()
            compose.onNodeWithText(label(R.string.ai_use_photo_manual)).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction()).onFirst().assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            androidx.test.espresso.Espresso.pressBack()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription(label(R.string.add_food)).assertExists()
            compose.waitUntil(5000) { cache.listFiles().orEmpty().all { it.name in previousFiles } }
        } finally { photos.forEach { compose.activity.contentResolver.delete(it, null, null) } }
    }
}
