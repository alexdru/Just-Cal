package com.justcal.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.R
import com.justcal.app.data.*
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ocr.*
import com.justcal.app.ui.theme.JustCalTheme
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic scan evidence and an isolated real Room graph, never the user's diary. */
@RunWith(AndroidJUnit4::class)
class FoodMatchingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int) = context.getString(id)
    private fun click(id: Int) {
        val node = compose.onNodeWithText(text(id)).performScrollTo()
        val target = node.fetchSemanticsNode().boundsInRoot
        val footerLabel = if (compose.onAllNodesWithText(text(R.string.match_check_values)).fetchSemanticsNodes().isNotEmpty())
            R.string.match_check_values else R.string.save_food
        val footer = compose.onNodeWithText(text(footerLabel)).fetchSemanticsNode().boundsInRoot
        if (target.bottom > footer.top - 100f) {
            compose.onNodeWithTag("food_editor_scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, target.bottom - footer.top + 220f)
            }
        }
        // Activate the accessibility action: automated scroll-to can place content behind the transparent footer.
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = java.io.File(context.getExternalFilesDir(null), "matching-test").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun russianDarkComparisonAtLargeFont() = comparisonVisual(true, "ru", 1.5f)
    @Test fun englishLightComparisonAtNormalFont() = comparisonVisual(false, "en", 1f)
    private fun comparisonVisual(dark: Boolean, language: String, scale: Float) {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(language)); fontScale = scale
        }
        val localized = context.createConfigurationContext(config)
        val revision = ProductRevision("revision", "product", name = "Ванильный йогурт с миндалём",
            energy = 12000, protein = 400, fat = 500, carbs = 1800, savedAtMillis = 1)
        val candidate = FoodCandidate("R:revision", revision,
            match = FoodMatch("R:revision", FoodMatchCategory.POSSIBLE, 0, 2, setOf(FoodMatchReason.MISSING_NUTRITION)))
        val state = EditorState(draft = FoodDraft(revision.name, "120", "4", "", "", "200"),
            matching = FoodMatchingState(memory = MatchMemory(inspectedKey = candidate.key),
            candidates = listOf(candidate), inspected = candidate))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                JustCalTheme(darkTheme = dark) {
                    Box(Modifier.width(320.dp)) {
                        FoodMatchingScreen(state, PackageScanPrefill(ParsedNutritionLabel(
                            name = LabelValue(revision.name, 1f, "fixture"), energyKcal = LabelValue("120", 1f, "fixture"),
                            proteinGrams = LabelValue("4", 1f, "fixture"), basis = LabelBasis.PER_100_G),
                            "", 1f, OcrMetrics(0, 0, 0, 0)), { _, _ -> }, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText(localized.getString(R.string.match_use_saved)).performScrollTo().assertIsDisplayed()
        screenshot("comparison-" + language + "-" + scale)
    }
    @Test fun scanCompareConfirmUndoApplyAndExplicitSaveAtLargeFont() {
        val db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).addCallback(DiaryDatabase.CALLBACK).build()
        try {
            val catalog = RoomFoodCatalogRepository(db)
            val entry = DiaryEntry(name = "Vanilla almond yogurt", per100g = NutritionPer100g(12000, 400, 500, 1800),
                eatenGramsHundredths = 10000, dayEpoch = 123, createdAtMillis = 1)
            runBlocking { catalog.save(entry, CatalogSave(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), Retention.NEW_PRODUCT)) }
            val scan = PackageScanPrefill(ParsedNutritionLabel(name = LabelValue(entry.name, 1f, "synthetic title"),
                energyKcal = LabelValue("120", 1f, "synthetic label"), basis = LabelBasis.PER_100_G),
                "", 1f, OcrMetrics(0, 0, 0, 0))
            lateinit var vm: EditorViewModel
            compose.runOnUiThread { vm = EditorViewModel(RoomDiaryRepository(db.diaryDao(), db), SavedStateHandle(),
                Clock.systemUTC(), null, 1234, scan, catalog = catalog) }
            compose.setContent {
                val state by vm.state.collectAsState()
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    JustCalTheme {
                        Box(Modifier.width(320.dp)) {
                            EditorScreen(state, false, vm::change, vm::save, {}, {}, {}, scan,
                                onMatchAction = vm::matchingAction, onInspectMatch = vm::inspectMatch,
                                onReuseMatch = vm::reuseDuplicate, onRetention = vm::setRetention,
                                onProductDetails = vm::changeProductDetails)
                        }
                    }
                }
            }
            compose.waitUntil(10000) { vm.state.value.matching.candidates.isNotEmpty() && !vm.state.value.matching.searching }
            compose.onNode(hasText(entry.name) and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
            compose.waitUntil(5000) { vm.state.value.matching.inspected != null }
            click(R.string.match_use_saved)
            screenshot("flow-applied-en150")
            compose.runOnIdle {
                assertEquals("", vm.state.value.draft.amount)
                assertEquals("4", vm.state.value.draft.protein)
                assertEquals(1234L, vm.state.value.dayEpoch)
                vm.change(FoodField.FAT, "7")
            }
            click(R.string.match_use_scan)
            compose.onNodeWithText(text(R.string.match_replace_question)).performScrollTo().assertIsDisplayed()
            click(R.string.match_replace)
            screenshot("flow-after-scan-confirm")
            compose.runOnIdle { assertEquals(vm.state.value.toString(), "", vm.state.value.draft.fat) }
            click(R.string.match_undo)
            compose.runOnIdle {
                assertEquals("7", vm.state.value.draft.fat)
                vm.change(FoodField.AMOUNT, "37,51")
            }
            click(R.string.match_use_saved)
            click(R.string.match_replace)
            compose.runOnIdle { assertEquals("37,51", vm.state.value.draft.amount) }
            compose.onNodeWithText(text(R.string.match_check_values)).performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText(text(R.string.match_previous_foods)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.save_food)).performClick()
            compose.waitUntil(10000) { vm.state.value.completed }
            runBlocking {
                val saved = db.diaryDao().getAll().single { it.dayEpoch == 1234L }
                assertEquals(3751L, saved.eatenGramsHundredths)
                assertEquals(400L, saved.proteinGramsHundredths)
                assertEquals(1, db.foodCatalogDao().products().size)
                assertEquals(2, db.foodCatalogDao().links().size)
            }
        } finally { db.close() }
    }
}
