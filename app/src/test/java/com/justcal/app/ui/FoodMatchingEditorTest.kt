package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ocr.*
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodMatchingEditorTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val a = ProductRevision("a", "product", name = "Almond yogurt", energy = 12000, protein = 400,
        fat = 500, carbs = 1800, savedAtMillis = 1)
    private val b = a.copy(id = "b", name = "Vanilla yogurt", energy = 15000)
    private fun scan() = PackageScanPrefill(ParsedNutritionLabel(
        name = LabelValue("Original scan", 1f, ""), energyKcal = LabelValue("100", 1f, ""),
        basis = LabelBasis.PER_100_G), "", 1f, OcrMetrics(0, 0, 0, 0))
    private class Diary : DiaryRepository {
        override fun observeDay(dayEpoch: Long) = flowOf(emptyList<DiaryEntry>())
        override fun observeAll() = flowOf(emptyList<DiaryEntry>())
        override suspend fun get(id: Long): DiaryEntry? = null
        override suspend fun save(entry: DiaryEntry) = 1L
        override suspend fun duplicate(id: Long, dayEpoch: Long, createdAtMillis: Long) = 1L
        override suspend fun changeDate(id: Long, dayEpoch: Long) {}
        override suspend fun delete(id: Long) {}
    }
    private class Catalog(val rows: MutableMap<String, FoodCandidate>) : FoodCatalogRepository {
        var lastRequest: CatalogSave? = null
        var queries = mutableListOf<FoodMatchEvidence>()
        var failSearch = false
        var slowName: String? = null
        var versionRows = emptyList<FoodCandidate>()
        override suspend fun buildHistoryBatch() = false
        override suspend fun search(evidence: FoodMatchEvidence): FoodSearchResult {
            queries += evidence
            if (evidence.name == slowName) withContext(NonCancellable) { delay(2000) }
            if (failSearch) error("search failure")
            return FoodSearchResult(rows.values.toList(), false, false)
        }
        override suspend fun candidate(key: String) = rows[key]
        override suspend fun revisions(productId: String, offset: Int) = versionRows.filter { it.revision.productId == productId }.drop(offset).take(20)
        override suspend fun save(entry: DiaryEntry, request: CatalogSave): Long { lastRequest = request; return 1 }
        override suspend fun duplicates(revision: ProductRevision) = emptyList<FoodCandidate>()
        override suspend fun archive(productId: String) { rows.clear() }
        override suspend fun forget(productId: String) { rows.clear() }
    }
    private fun catalog() = Catalog(mutableMapOf("R:a" to FoodCandidate("R:a", a), "R:b" to FoodCandidate("R:b", b)))
    private fun vm(catalog: Catalog, handle: SavedStateHandle = SavedStateHandle()) =
        EditorViewModel(Diary(), handle, Clock.systemUTC(), null, 1234, scan(), catalog = catalog)

    @Test fun alternateVersionsPageBothWaysAndResetForAnotherProduct() = runTest(dispatcher) {
        val c = catalog()
        c.rows["R:b"] = FoodCandidate("R:b", b.copy(productId = "different"))
        c.versionRows = (0..44).map { FoodCandidate("R:version$it", a.copy(id = "version$it")) }
        val vm = vm(c); advanceUntilIdle()
        vm.inspectMatch("R:a"); advanceUntilIdle()
        vm.alternateVersions(); advanceUntilIdle()
        assertEquals("version0", vm.state.value.matching.alternateVersions.first().revision.id)
        vm.alternateVersions(); advanceUntilIdle()
        assertEquals("version20", vm.state.value.matching.alternateVersions.first().revision.id)
        vm.alternateVersions(); advanceUntilIdle()
        assertEquals(5, vm.state.value.matching.alternateVersions.size)
        vm.previousVersions(); advanceUntilIdle()
        assertEquals("version20", vm.state.value.matching.alternateVersions.first().revision.id)
        vm.inspectMatch("R:b"); advanceUntilIdle()
        assertEquals(0, vm.state.value.matching.alternateOffset)
        assertTrue(vm.state.value.matching.alternateVersions.isEmpty())
    }
    @Test fun cancelledOlderSearchCannotReplaceNewerResults() = runTest(dispatcher) {
        val c = catalog(); val vm = vm(c); advanceUntilIdle()
        c.slowName = "Old query"
        vm.change(FoodField.NAME, "Old query")
        advanceTimeBy(401); runCurrent()
        vm.change(FoodField.NAME, "Latest query")
        advanceTimeBy(401); runCurrent()
        advanceUntilIdle()
        assertEquals("Latest query", vm.state.value.draft.name)
        assertEquals("Latest query", c.queries.last().name)
        assertFalse(vm.state.value.matching.searching)
    }
    @Test fun unavailableProductKeepsAppliedDraftAndCanSaveDiaryOnly() = runTest(dispatcher) {
        val c = catalog(); val vm = vm(c); advanceUntilIdle()
        vm.inspectMatch("R:a"); advanceUntilIdle(); vm.applyMatch()
        c.rows.clear()
        vm.refreshMatching(); advanceUntilIdle()
        assertTrue(vm.state.value.matching.unavailable)
        assertEquals(a.name, vm.state.value.draft.name)
        assertEquals(Retention.DIARY_ONLY, vm.effectiveRetention())
        vm.change(FoodField.AMOUNT, "1"); vm.save(); advanceUntilIdle()
        assertEquals(Retention.DIARY_ONLY, c.lastRequest!!.retention)
        assertTrue(vm.state.value.completed)
    }
    @Test fun inspectionNeverMutatesAndApplyPreservesInvalidAmountAndDate() = runTest(dispatcher) {
        val c = catalog(); val vm = vm(c); advanceUntilIdle()
        vm.change(FoodField.AMOUNT, " 1,2,invalid ")
        val original = vm.state.value.draft
        vm.inspectMatch("R:a"); advanceUntilIdle()
        assertEquals(original, vm.state.value.draft)
        vm.applyMatch()
        assertEquals(a.name, vm.state.value.draft.name)
        assertEquals(" 1,2,invalid ", vm.state.value.draft.amount)
        assertEquals(1234L, vm.state.value.dayEpoch)
        assertNull(c.lastRequest)
    }
    @Test fun manualOverwriteConfirmationCancelUndoAndScanBlanks() = runTest(dispatcher) {
        val vm = vm(catalog()); advanceUntilIdle()
        vm.change(FoodField.NAME, "My edit")
        vm.inspectMatch("R:a"); advanceUntilIdle(); vm.applyMatch()
        assertEquals("My edit", vm.state.value.draft.name)
        assertNotNull(vm.state.value.matching.pending)
        vm.cancelReplacement()
        assertEquals("My edit", vm.state.value.draft.name)
        vm.applyMatch(); vm.confirmReplacement()
        vm.change(FoodField.FAT, "99")
        vm.inspectMatch("R:b"); advanceUntilIdle()
        assertEquals("99", vm.state.value.draft.fat)
        vm.applyMatch(); vm.confirmReplacement()
        vm.restorePrevious()
        assertEquals("99", vm.state.value.draft.fat)
        assertNull(vm.state.value.matching.memory.undo)
        vm.useCurrentScan(); vm.confirmReplacement()
        assertEquals("", vm.state.value.draft.protein)
        assertEquals("Original scan", vm.state.value.draft.name)
    }
    @Test fun recreationRestoresSingleDraftAssociationUndoAndDropsPrompt() = runTest(dispatcher) {
        val c = catalog(); val handle = SavedStateHandle(); val first = vm(c, handle); advanceUntilIdle()
        first.inspectMatch("R:a"); advanceUntilIdle(); first.applyMatch()
        first.change(FoodField.AMOUNT, "001,00")
        first.change(FoodField.NAME, "My edit")
        first.useCurrentScan()
        val restored = vm(c, handle); advanceUntilIdle()
        assertEquals(first.state.value.draft, restored.state.value.draft)
        assertNull(restored.state.value.matching.pending)
        assertEquals(a, restored.state.value.matching.memory.association)
        restored.restorePrevious(); restored.confirmReplacement()
        assertEquals("001,00", restored.state.value.draft.amount)
        assertEquals("Original scan", restored.state.value.draft.name)
    }
    @Test fun historyApplyRequiresSeparateUncheckedPromotion() = runTest(dispatcher) {
        val c = catalog()
        c.rows["H:a"] = FoodCandidate("H:a", a, "entry", 3, 123)
        val vm = vm(c); advanceUntilIdle()
        assertEquals(Retention.NEW_PRODUCT, vm.state.value.matching.memory.retention)
        vm.inspectMatch("H:a"); advanceUntilIdle(); vm.applyMatch()
        assertEquals(Retention.DIARY_ONLY, vm.state.value.matching.memory.retention)
        assertTrue(vm.state.value.matching.memory.fromHistory)
        vm.setRetention(Retention.NEW_PRODUCT)
        vm.change(FoodField.AMOUNT, "1")
        vm.save(); advanceUntilIdle()
        assertEquals(Retention.NEW_PRODUCT, c.lastRequest!!.retention)
        assertEquals("HISTORY_PROMOTION", c.lastRequest!!.provenance)
    }
    @Test fun copiedNutritionAndAmountNeverTriggerSearch() = runTest(dispatcher) {
        val c = catalog(); val vm = vm(c); advanceUntilIdle()
        val queries = c.queries.size
        vm.inspectMatch("R:a"); advanceUntilIdle(); vm.applyMatch()
        vm.change(FoodField.AMOUNT, "5")
        vm.change(FoodField.PROTEIN, "22")
        advanceUntilIdle()
        assertEquals(queries, c.queries.size)
        vm.refreshMatching(); advanceUntilIdle()
        assertEquals("Original scan", c.queries.last().name)
        assertNull(c.queries.last().nutrients[1].hundredths)
    }
    @Test fun searchFailureNeverBlocksManualSave() = runTest(dispatcher) {
        val c = catalog().apply { failSearch = true }; val vm = vm(c); advanceUntilIdle()
        assertTrue(vm.state.value.matching.error)
        val d = FoodDraft("Manual", "1", "2", "3", "4", "5")
        FoodField.entries.forEach { vm.change(it, d.value(it)) }
        vm.setRetention(Retention.DIARY_ONLY)
        vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.completed)
    }
    @Test fun editingAppliedValuesDefaultsToDiaryOnlyAndAmountDoesNot() = runTest(dispatcher) {
        val vm = vm(catalog()); advanceUntilIdle()
        vm.inspectMatch("R:a"); advanceUntilIdle(); vm.applyMatch()
        vm.change(FoodField.AMOUNT, "5")
        assertEquals(Retention.REUSE, vm.effectiveRetention())
        vm.change(FoodField.FAT, "6")
        assertEquals(Retention.DIARY_ONLY, vm.effectiveRetention())
        vm.setRetention(Retention.NEW_REVISION)
        assertEquals(Retention.NEW_REVISION, vm.effectiveRetention())
        vm.continueWithoutMatching()
        assertEquals("6", vm.state.value.draft.fat)
        assertTrue(vm.state.value.matching.memory.collapsed)
    }
}
