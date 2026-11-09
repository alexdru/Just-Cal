package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justcal.app.domain.*
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ocr.matchEvidence
import com.justcal.app.domain.Retention
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val draft: FoodDraft = FoodDraft(),
    val preview: ConsumedNutrition? = null,
    val errors: Map<FoodField, InputError> = emptyMap(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val missing: Boolean = false,
    val storageError: Boolean = false,
    val dayEpoch: Long? = null,
    val duplicated: Boolean = false,
    val dateChanged: Boolean = false,
    val recognition: RecognitionDraft? = null,
    val matching: FoodMatchingState = FoodMatchingState(),
)

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    private val repository: DiaryRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
    @Assisted("entryId") private val entryId: Long?,
    @Assisted("dayEpoch") private val dayEpoch: Long? = null,
    @Assisted("scan") private val scan: PackageScanPrefill? = null,
    private val diagnostics: com.justcal.app.diagnostics.OcrDiagnosticsRepository? = null,
    @Assisted("recognition") private val recognition: RecognitionDraft? = null,
    private val catalog: FoodCatalogRepository? = null,
) : ViewModel() {
    private var original: DiaryEntry? = null
    private var searchJob: Job? = null
    private var indexingJob: Job? = null
    private var searchGeneration = 0
    private var lastQuery: String? = null
    private val matchJson = Json { ignoreUnknownKeys = true }
    private fun restoredDraft(base: FoodDraft = recognition?.draft() ?: scan?.recognition?.draft() ?: scan?.label?.draft() ?: FoodDraft()) = FoodField.entries.fold(base) { draft, field ->
        draft.with(field, savedState[field.name] ?: draft.value(field))
    }
    private val mutableState = MutableStateFlow(EditorState(draft = restoredDraft(), loading = entryId != null, dayEpoch = savedState.get<Long>("entryDay") ?: dayEpoch,
        recognition = recognition ?: scan?.recognition,
        matching = FoodMatchingState(memory = restoreMatchMemory())))
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    init {
        updatePreview()
        if (entryId != null) load() else startMatching()
    }

    private fun load() {
        mutableState.update { it.copy(loading = true, storageError = false) }
        viewModelScope.launch {
            try {
                original = repository.get(requireNotNull(entryId))
                val draft = if (savedState.get<Boolean>("hasDraft") == true) restoredDraft(original?.let(FoodDraft::from) ?: FoodDraft())
                    else original?.let(FoodDraft::from) ?: FoodDraft()
                mutableState.update { it.copy(draft = draft, loading = false, missing = original == null, dayEpoch = savedState.get<Long>("entryDay") ?: original?.dayEpoch) }
                updatePreview()
                startMatching()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, storageError = true) }
            }
        }
    }

    fun retryLoad() { if (entryId != null && original == null) load() }

    /** Persist the one-shot event independently of composition, navigation and Activity recreation. */
    fun claimInitialFocus(): Boolean {
        if (savedState.get<Boolean>("initialFocusHandled") == true) return false
        savedState["initialFocusHandled"] = true
        return entryId == null && scan == null && savedState.get<Boolean>("hasDraft") != true && state.value.draft.name.isEmpty()
    }

    fun change(field: FoodField, value: String) {
        if (state.value.busy || state.value.loading) return
        savedState[field.name] = value
        savedState["hasDraft"] = true
        if (field != FoodField.AMOUNT) rememberMatch(state.value.matching.memory.copy(dirty = state.value.matching.memory.dirty + field,
            searchName = if (field == FoodField.NAME) value else state.value.matching.memory.searchName))
        mutableState.update { it.copy(draft = it.draft.with(field, value), errors = it.errors - field, storageError = false) }
        updatePreview()
        if (field == FoodField.NAME) scheduleMatching()
    }

    private fun updatePreview() {
        val food = FoodValidator.validate(state.value.draft).food
        mutableState.update { it.copy(preview = food?.let { input -> NutritionCalculator.consumed(input.per100g, input.eatenGramsHundredths) }) }
    }

    fun changeDate(day: Long) {
        if (original == null || state.value.busy || state.value.completed) return
        java.time.LocalDate.ofEpochDay(day)
        savedState["entryDay"] = day
        mutableState.update { it.copy(dayEpoch = day) }
    }

    fun duplicate(day: Long) {
        val id = original?.id ?: return
        if (state.value.busy || state.value.completed || state.value.duplicated) return
        java.time.LocalDate.ofEpochDay(day)
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                repository.duplicate(id, day, clock.millis())
                mutableState.update { it.copy(busy = false, duplicated = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    fun acknowledgeDuplicate() { mutableState.update { it.copy(duplicated = false) } }

    fun save() = saveWithDuplicateChoice(false)

    private fun saveWithDuplicateChoice(keepSeparate: Boolean) {
        if (state.value.busy || state.value.loading || state.value.completed || state.value.missing) return
        if (entryId != null && original == null) return
        val validated = FoodValidator.validate(state.value.draft)
        mutableState.update { it.copy(errors = validated.errors) }
        val food = validated.food ?: return
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                val entry = DiaryEntry(
                    id = original?.id ?: 0,
                    name = food.name,
                    per100g = food.per100g,
                    eatenGramsHundredths = food.eatenGramsHundredths,
                    dayEpoch = state.value.dayEpoch ?: dayEpoch ?: savedState.get<Long>("saveDay") ?: clock.instant().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().also { savedState["saveDay"] = it },
                    createdAtMillis = original?.createdAtMillis ?: creationTime(),
                    source = original?.source ?: if (scan != null) EntrySource.PACKAGE_SCAN else EntrySource.MANUAL,
                )
                val memory = state.value.matching.memory
                val selected = memory.association
                val retention = effectiveRetention()
                if (catalog != null && retention == Retention.NEW_PRODUCT && !keepSeparate) {
                    val duplicates = catalog.duplicates(proposedRevision(entry))
                    if (duplicates.isNotEmpty()) {
                        mutableState.update { it.copy(busy = false, matching = it.matching.copy(duplicates = duplicates)) }
                        return@launch
                    }
                }
                if (catalog != null) {
                    catalog.save(entry, CatalogSave(reservedUuid("entryUuid"), reservedUuid("productUuid"), reservedUuid("revisionUuid"),
                        retention, selected?.id?.takeUnless { memory.fromHistory }, memory.details,
                        if (memory.fromHistory) "HISTORY_PROMOTION" else if (scan != null && selected == null) "SCAN_ACCEPTED" else "USER_SELECTED",
                        preserveExistingAssociation = entryId != null && selected == null && memory.origin == "SCAN_OR_MANUAL",
                        fieldOrigins = fieldOrigins(), revisionSavedAtMillis = creationTime()))
                } else repository.save(entry)
                // Commit succeeded: diagnostics must never cause an insert retry.
                runCatching { scan?.let { diagnostics?.corrections(it, state.value.draft,
                    if (memory.origin.endsWith("SUBSTITUTION")) FoodField.entries.take(5).toSet() - memory.dirty else emptySet(), memory.origin) } }
                mutableState.update { it.copy(busy = false, completed = true,
                    dateChanged = original != null && entry.dayEpoch != original?.dayEpoch) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        if (state.value.busy || state.value.completed) return
        mutableState.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                repository.delete(id)
                mutableState.update { it.copy(busy = false, completed = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    private val originalScan get() = recognition?.draft() ?: scan?.recognition?.draft() ?: scan?.label?.draft()
    private fun restoreMatchMemory(): MatchMemory =
        savedState.get<String>("matchMemory")?.let { runCatching { matchJson.decodeFromString<MatchMemory>(it) }.getOrNull() }
            ?: MatchMemory(retention = if (entryId == null && scan != null) Retention.NEW_PRODUCT else Retention.DIARY_ONLY)
    private fun rememberMatch(memory: MatchMemory) {
        savedState["matchMemory"] = matchJson.encodeToString(MatchMemory.serializer(), memory)
        mutableState.update { it.copy(matching = it.matching.copy(memory = memory)) }
    }
    private fun reservedUuid(key: String): String = savedState.get<String>(key) ?: java.util.UUID.randomUUID().toString().also { savedState[key] = it }
    private fun creationTime(): Long = savedState.get<Long>("creationTime") ?: clock.millis().also { savedState["creationTime"] = it }
    private fun startMatching() {
        if (catalog == null) return
        refreshMatching()
        indexingJob?.cancel()
        indexingJob = viewModelScope.launch {
            try {
                var batch = 0
                do {
                    val more = catalog.buildHistoryBatch()
                    batch++
                    mutableState.update { it.copy(matching = it.matching.copy(indexing = more)) }
                    if (state.value.matching.memory.inspectedKey == null && state.value.matching.memory.association == null) {
                        // Make early history useful, then avoid repeatedly contending with editing for every 100-row batch.
                        if (batch == 1 || !more || batch % 10 == 0) refreshMatching()
                    } else mutableState.update { it.copy(matching = it.matching.copy(stale = true)) }
                    if (more) delay(kotlin.time.Duration.parse("16ms"))
                } while (more)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(matching = it.matching.copy(error = true, indexing = false)) }
            }
        }
    }
    private fun scheduleMatching() {
        val m = state.value.matching
        if (m.memory.inspectedKey != null || m.memory.association != null) {
            mutableState.update { it.copy(matching = it.matching.copy(stale = true)) }; return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(kotlin.time.Duration.parse("400ms")); refreshMatching(false) }
    }
    private fun evidence(): FoodMatchEvidence {
        val d = state.value.matching.memory.details
        fun identity(text: String) = MatchIdentity(text.takeIf { it.isNotBlank() },
            if (text.isBlank()) MatchEvidenceState.MISSING else MatchEvidenceState.KNOWN)
        return (scan?.matchEvidence(recognition ?: scan.recognition) ?: FoodMatchEvidence(state.value.draft.name))
            .copy(name = state.value.matching.memory.searchName ?: originalScan?.name ?: original?.name.orEmpty(), brand = identity(d.brand), manufacturer = identity(d.manufacturer),
                retailer = identity(d.retailer), deliveryService = identity(d.deliveryService))
    }
    fun refreshMatching(force: Boolean = true) {
        val repository = catalog ?: return
        if (state.value.busy) return
        val query = evidence()
        val key = query.toString()
        if (!force && key == lastQuery) return
        lastQuery = key
        val generation = ++searchGeneration
        searchJob?.cancel()
        mutableState.update { it.copy(matching = it.matching.copy(searching = true, error = false, stale = false)) }
        searchJob = viewModelScope.launch {
            try {
                val result = repository.search(query)
                if (generation != searchGeneration) return@launch
                val inspected = state.value.matching.memory.inspectedKey?.let { repository.candidate(it) }
                val associated = state.value.matching.memory.association
                val unavailable = associated != null && !state.value.matching.memory.fromHistory &&
                    repository.candidate("R:" + associated.id) == null
                mutableState.update { it.copy(matching = it.matching.copy(candidates = result.candidates,
                    inspected = inspected, unavailable = unavailable, indexing = result.indexing, truncated = result.truncated, searching = false)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == searchGeneration) mutableState.update { it.copy(matching = it.matching.copy(searching = false, error = true)) }
            }
        }
    }
    fun inspectMatch(key: String) {
        if (state.value.busy) return
        searchGeneration++
        searchJob?.cancel()
        mutableState.update { it.copy(matching = it.matching.copy(searching = false, pending = null, comparisonOpen = true)) }
        rememberMatch(state.value.matching.memory.copy(inspectedKey = key))
        val priorProduct = state.value.matching.inspected?.revision?.productId
        viewModelScope.launch {
            val candidate = try { catalog?.candidate(key) } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            if (state.value.matching.memory.inspectedKey == key)
                mutableState.update { it.copy(matching = it.matching.copy(inspected = candidate, pending = null,
                    alternateOffset = if (priorProduct == candidate?.revision?.productId) it.matching.alternateOffset else 0,
                    alternateVersions = if (priorProduct == candidate?.revision?.productId) it.matching.alternateVersions else emptyList(),
                    moreVersions = if (priorProduct == candidate?.revision?.productId) it.matching.moreVersions else true)) }
        }
    }
    fun selectMatch(key: String) {
        if (state.value.busy) return
        searchGeneration++
        searchJob?.cancel()
        viewModelScope.launch {
            val candidate = try { catalog?.candidate(key) } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            } ?: return@launch
            rememberMatch(state.value.matching.memory.copy(inspectedKey = key))
            mutableState.update { it.copy(matching = it.matching.copy(
                inspected = candidate, pending = null, comparisonOpen = false,
            )) }
            requestReplacement("candidate")
        }
    }
    fun matchingAction(action: String) {
        when (action) {
            "collapse" -> { rememberMatch(state.value.matching.memory.copy(inspectedKey = null))
                mutableState.update { it.copy(matching = it.matching.copy(inspected = null, pending = null, comparisonOpen = false)) } }
            "apply" -> applyMatch(); "scan" -> useCurrentScan(); "undo" -> restorePrevious()
            "confirm" -> confirmReplacement(); "cancel" -> cancelReplacement()
            "refresh" -> refreshMatching(); "more" -> showMoreMatches(); "versions" -> alternateVersions()
            "archive" -> archiveInspected(); "forget" -> forgetInspected()
            "separate" -> keepSeparateProduct(); "diary" -> saveDiaryOnly()
            "cancel_duplicates" -> mutableState.update { it.copy(matching = it.matching.copy(duplicates = emptyList())) }
        }
    }
    fun applyMatch() = requestReplacement("candidate")
    fun useCurrentScan() = requestReplacement("scan")
    fun restorePrevious() = requestReplacement("undo")
    fun cancelReplacement() { mutableState.update { it.copy(matching = it.matching.copy(pending = null)) } }
    fun confirmReplacement() {
        val action = state.value.matching.pending ?: return
        performReplacement(action)
    }
    private fun targetFields(action: String): List<String>? = when (action) {
        "candidate" -> state.value.matching.inspected?.revision?.draft()?.fields()
        "scan" -> originalScan?.fields()
        "undo" -> state.value.matching.memory.undo?.fields
        else -> null
    }
    private fun FoodDraft.fields() = listOf(name, energy, protein, fat, carbs)
    private fun requestReplacement(action: String) {
        if (state.value.busy) return
        val target = targetFields(action) ?: return
        val current = state.value.draft.fields()
        if (state.value.matching.memory.dirty.any { it != FoodField.AMOUNT && current[it.ordinal] != target[it.ordinal] })
            mutableState.update { it.copy(matching = it.matching.copy(pending = action)) }
        else performReplacement(action)
    }
    private fun performReplacement(action: String) {
        if (state.value.busy) return
        val target = targetFields(action) ?: return
        val current = state.value.draft
        val m = state.value.matching.memory
        val previous = MatchUndo(current.fields(), m.dirty, m.association, m.fromHistory, m.retention, m.details, m.origin)
        val next = when (action) {
            "undo" -> {
                val undo = m.undo ?: return
                m.copy(dirty = undo.dirty, association = undo.association, fromHistory = undo.history,
                    retention = undo.retention, details = if (m.detailsDirty) m.details else undo.details, origin = undo.origin, undo = null)
            }
            "scan" -> m.copy(dirty = emptySet(), association = null, fromHistory = false,
                retention = if (scan != null && entryId == null) Retention.NEW_PRODUCT else Retention.DIARY_ONLY,
                origin = "SCAN_OR_MANUAL", details = if (m.detailsDirty) m.details else ProductDetails(), undo = previous)
            else -> {
                val candidate = state.value.matching.inspected ?: return
                val r = candidate.revision
                m.copy(dirty = emptySet(), association = r, fromHistory = candidate.historyEntryUuid != null,
                    retention = if (candidate.historyEntryUuid == null) Retention.REUSE else Retention.DIARY_ONLY,
                    details = if (m.detailsDirty) m.details else ProductDetails(r.brand.orEmpty(), r.manufacturer.orEmpty(), r.retailer.orEmpty(),
                        r.deliveryService.orEmpty(), r.packageMassHundredths?.hundredthsText().orEmpty()),
                    origin = if (candidate.historyEntryUuid == null) "CATALOG_SUBSTITUTION" else "HISTORY_SUBSTITUTION", undo = previous)
            }
        }
        var draft = current
        FoodField.entries.take(5).forEach { field -> draft = draft.with(field, target[field.ordinal]); savedState[field.name] = target[field.ordinal] }
        savedState["hasDraft"] = true
        rememberMatch(next.copy(inspectedKey = null))
        mutableState.update { it.copy(draft = draft, errors = it.errors.filterKeys { key -> key == FoodField.AMOUNT },
            matching = it.matching.copy(pending = null, inspected = null, comparisonOpen = false)) }
        updatePreview()
    }
    fun changeProductDetails(details: ProductDetails) {
        if (state.value.busy) return
        rememberMatch(state.value.matching.memory.copy(details = details, detailsDirty = true))
        scheduleMatching()
    }
    fun setRetention(retention: Retention) {
        if (state.value.busy) return
        val m = state.value.matching.memory
        rememberMatch(if (state.value.matching.unavailable && retention == Retention.NEW_PRODUCT)
            m.copy(retention = retention, association = null, fromHistory = false) else m.copy(retention = retention))
        if (retention == Retention.NEW_PRODUCT) mutableState.update { it.copy(matching = it.matching.copy(unavailable = false)) }
    }
    fun effectiveRetention(): Retention = state.value.matching.memory.effectiveRetention(state.value.draft, state.value.matching.unavailable)
    fun keepSeparateProduct() {
        mutableState.update { it.copy(matching = it.matching.copy(duplicates = emptyList())) }
        saveWithDuplicateChoice(true)
    }
    fun saveDiaryOnly() {
        setRetention(Retention.DIARY_ONLY)
        mutableState.update { it.copy(matching = it.matching.copy(duplicates = emptyList())) }
        saveWithDuplicateChoice(true)
    }
    fun reuseDuplicate(key: String) {
        val c = state.value.matching.duplicates.firstOrNull { it.key == key } ?: return
        rememberMatch(state.value.matching.memory.copy(association = c.revision, fromHistory = false, retention = Retention.REUSE))
        mutableState.update { it.copy(matching = it.matching.copy(duplicates = emptyList())) }
        saveWithDuplicateChoice(true)
    }
    private fun fieldOrigins(): String {
        val memory = state.value.matching.memory
        val evidence = scan?.matchEvidence(recognition ?: scan.recognition)
        return kotlinx.serialization.json.JsonObject(FoodField.entries.take(5).associate { field ->
            val origin = when {
                field in memory.dirty -> "USER_EDITED"
                memory.origin.endsWith("SUBSTITUTION") -> memory.origin
                field != FoodField.NAME && evidence?.nutrients?.get(field.ordinal - 1)?.state == MatchEvidenceState.AI_RECOVERED -> "AI_RECOVERED"
                scan != null -> "SCAN_ACCEPTED"
                else -> "USER_ACCEPTED"
            }
            field.name to kotlinx.serialization.json.JsonPrimitive(origin)
        }).toString()
    }
    private fun proposedRevision(entry: DiaryEntry) = ProductRevision(reservedUuid("revisionUuid"), reservedUuid("productUuid"),
        name = entry.name, energy = entry.per100g.energyKcalHundredths, protein = entry.per100g.proteinGramsHundredths,
        fat = entry.per100g.fatGramsHundredths, carbs = entry.per100g.carbsGramsHundredths, savedAtMillis = entry.createdAtMillis,
        brand = state.value.matching.memory.details.brand.trim().ifBlank { null },
        manufacturer = state.value.matching.memory.details.manufacturer.trim().ifBlank { null },
        retailer = state.value.matching.memory.details.retailer.trim().ifBlank { null },
        deliveryService = state.value.matching.memory.details.deliveryService.trim().ifBlank { null },
        packageMassHundredths = state.value.matching.memory.details.packageGrams.replace(',', '.').toBigDecimalOrNull()?.let {
            runCatching { it.movePointRight(2).longValueExact() }.getOrNull() })
    fun archiveInspected() {
        val r = state.value.matching.inspected?.takeIf { it.historyEntryUuid == null }?.revision ?: return
        manageProduct { catalog?.archive(r.productId) }
    }
    fun forgetInspected() {
        val r = state.value.matching.inspected?.takeIf { it.historyEntryUuid == null }?.revision ?: return
        manageProduct { catalog?.forget(r.productId) }
    }
    private fun manageProduct(operation: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch { try {
            operation()
            rememberMatch(state.value.matching.memory.copy(inspectedKey = null))
            mutableState.update { it.copy(matching = it.matching.copy(inspected = null, comparisonOpen = false)) }
            startMatching()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update { it.copy(matching = it.matching.copy(error = true)) }
        } }
    }
    fun showMoreMatches() { mutableState.update { it.copy(matching = it.matching.copy(showAll = true)) } }
    fun alternateVersions() {
        if (state.value.busy) return
        val r = state.value.matching.inspected?.takeIf { it.historyEntryUuid == null }?.revision ?: return
        viewModelScope.launch {
            val offset = state.value.matching.alternateOffset
            val versions = try { catalog?.revisions(r.productId, offset).orEmpty() } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            mutableState.update { it.copy(matching = it.matching.copy(alternateVersions = versions,
                alternateOffset = offset + versions.size, moreVersions = versions.size == 20)) }
        }
    }

    @AssistedFactory interface Factory { fun create(@Assisted("entryId") entryId: Long?, @Assisted("dayEpoch") dayEpoch: Long?, @Assisted("scan") scan: PackageScanPrefill?, @Assisted("recognition") recognition: RecognitionDraft? = null): EditorViewModel }
}
