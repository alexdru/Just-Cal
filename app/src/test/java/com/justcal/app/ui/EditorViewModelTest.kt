package com.justcal.app.ui

import androidx.lifecycle.SavedStateHandle
import com.justcal.app.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun fill(vm: EditorViewModel) {
        val draft = FoodDraft("Yogurt", "200", "10", "5", "20", "150")
        FoodField.entries.forEach { vm.change(it, draft.value(it)) }
    }
    @Test fun repeatedSaveDuringWriteCreatesOneEntry() = runTest(dispatcher) {
        val repository = FakeDiary()
        val vm = EditorViewModel(repository, SavedStateHandle(), clock, null)
        fill(vm)
        vm.save(); vm.save(); vm.save()
        advanceUntilIdle()
        assertEquals(1, repository.saves)
        assertTrue(vm.state.value.completed)
        assertEquals("300", vm.state.value.preview!!.energyKcal.display(0))
    }
    @Test fun failureKeepsDraftAndAllowsRetry() = runTest(dispatcher) {
        val repository = FakeDiary().apply { fail = true }
        val vm = EditorViewModel(repository, SavedStateHandle(), clock, null)
        fill(vm); vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.storageError)
        assertFalse(vm.state.value.completed)
        assertEquals("Yogurt", vm.state.value.draft.name)
        repository.fail = false
        vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertEquals(1, repository.rows.size)
    }
    @Test fun editPreservesOriginalDateTimestampAndSource() = runTest(dispatcher) {
        val repository = FakeDiary()
        repository.rows[7] = DiaryEntry(7, "Old", NutritionPer100g(100, 0, 0, 0), 10000, 12, 99, EntrySource.PACKAGE_SCAN)
        val vm = EditorViewModel(repository, SavedStateHandle(), clock, 7)
        advanceUntilIdle()
        fill(vm); vm.save(); advanceUntilIdle()
        val saved = repository.rows.getValue(7)
        assertEquals(12L, saved.dayEpoch)
        assertEquals(99L, saved.createdAtMillis)
        assertEquals(EntrySource.PACKAGE_SCAN, saved.source)
        assertEquals("Yogurt", saved.name)
    }
    @Test fun savedStateRestoresDraftWithoutWritingIt() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repository = FakeDiary()
        val first = EditorViewModel(repository, handle, clock, null)
        fill(first)
        val restored = EditorViewModel(repository, handle, clock, null)
        assertEquals(first.state.value.draft, restored.state.value.draft)
        assertEquals(0, repository.saves)
    }
    @Test fun missingEntryCannotBeRecreatedBySave() = runTest(dispatcher) {
        val repository = FakeDiary()
        val vm = EditorViewModel(repository, SavedStateHandle(), clock, 7)
        advanceUntilIdle(); fill(vm); vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.missing)
        assertEquals(0, repository.saves)
    }
    @Test fun deletingAnEntryCompletesOnlyAfterSuccessfulWrite() = runTest(dispatcher) {
        val repository = FakeDiary()
        repository.rows[7] = DiaryEntry(7, "Food", NutritionPer100g(100, 0, 0, 0), 10000, 12, 99)
        val vm = EditorViewModel(repository, SavedStateHandle(), clock, 7)
        advanceUntilIdle()
        repository.fail = true
        vm.delete(); advanceUntilIdle()
        assertFalse(vm.state.value.completed)
        assertTrue(repository.rows.containsKey(7))
        repository.fail = false
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertFalse(repository.rows.containsKey(7))
    }
    private class FakeDiary : DiaryRepository {
        val rows = mutableMapOf<Long, DiaryEntry>()
        var fail = false
        var saves = 0
        override fun observeDay(dayEpoch: Long) = flowOf(rows.values.filter { it.dayEpoch == dayEpoch })
        override suspend fun get(id: Long) = rows[id]
        override suspend fun save(entry: DiaryEntry): Long {
            delay(10)
            if (fail) error("Storage unavailable")
            saves++
            val id = entry.id.takeIf { it != 0L } ?: (rows.size + 1L)
            rows[id] = entry.copy(id = id)
            return id
        }
        override suspend fun delete(id: Long) {
            if (fail) error("Storage unavailable")
            rows.remove(id)
        }
    }
}
