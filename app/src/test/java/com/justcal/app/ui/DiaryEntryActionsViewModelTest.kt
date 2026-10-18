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
class DiaryEntryActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T23:59:59Z"), ZoneOffset.UTC)
    private val original = DiaryEntry(7, "Food", NutritionPer100g(20550, 1234, 567, 2001),
        3751, 123, 99, EntrySource.PACKAGE_SCAN)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun cancellationOfEveryActionNeverWrites() = runTest(dispatcher) {
        val repository = FakeDiary(original)
        val vm = DiaryEntryActionsViewModel(repository, clock, SavedStateHandle())
        EntryAction.entries.forEach {
            vm.request(original, it)
            vm.dismiss()
            vm.confirm()
        }
        advanceUntilIdle()
        assertEquals(0, repository.writes)
        assertEquals(listOf(original), repository.rows.values.toList())
    }

    @Test fun moveRestoresPendingDayAndPreservesLatestPersistedValues() = runTest(dispatcher) {
        val repository = FakeDiary(original)
        val saved = SavedStateHandle()
        DiaryEntryActionsViewModel(repository, clock, saved).request(original, EntryAction.CHANGE_DATE)
        val restored = DiaryEntryActionsViewModel(repository, clock, saved)
        assertEquals(123L, restored.state.value.dayEpoch)
        val updated = original.copy(name = "Changed elsewhere")
        repository.rows[7] = updated
        restored.confirm(456); restored.confirm(456)
        advanceUntilIdle()
        assertEquals(updated.copy(dayEpoch = 456), repository.rows[7])
        assertEquals(1, repository.writes)
        assertEquals(EntryAction.CHANGE_DATE, restored.state.value.result)
        assertNull(DiaryEntryActionsViewModel(repository, clock, saved).state.value.action)
    }

    @Test fun duplicateDefaultsToLocalTodayAndRepeatedConfirmationCopiesOnce() = runTest(dispatcher) {
        val repository = FakeDiary(original)
        val vm = DiaryEntryActionsViewModel(repository, clock, SavedStateHandle())
        vm.request(original, EntryAction.DUPLICATE)
        val today = clock.instant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        assertEquals(today, vm.state.value.dayEpoch)
        vm.confirm(); vm.confirm()
        advanceUntilIdle()
        vm.confirm()
        assertEquals(1, repository.writes)
        val copy = repository.rows.getValue(8)
        assertEquals(original.copy(id = 8, dayEpoch = today, createdAtMillis = clock.millis()), copy)
        assertEquals(original, repository.rows[7])
        vm.acknowledgeResult()
        vm.request(original, EntryAction.DUPLICATE)
        vm.confirm(today + 10)
        advanceUntilIdle()
        assertEquals(3, repository.rows.size)
    }

    @Test fun failureRetainsTargetForRetryAndDeleteIsGuarded() = runTest(dispatcher) {
        val repository = FakeDiary(original).apply { fail = true }
        val vm = DiaryEntryActionsViewModel(repository, clock, SavedStateHandle())
        vm.request(original, EntryAction.CHANGE_DATE)
        vm.confirm(999); advanceUntilIdle()
        assertTrue(vm.state.value.error)
        assertEquals(999L, vm.state.value.dayEpoch)
        assertEquals(original, repository.rows[7])
        repository.fail = false
        vm.confirm(); advanceUntilIdle()
        assertEquals(999L, repository.rows.getValue(7).dayEpoch)
        vm.acknowledgeResult()
        vm.request(repository.rows.getValue(7), EntryAction.DELETE)
        vm.confirm(); vm.confirm(); vm.dismiss()
        advanceUntilIdle()
        assertTrue(repository.rows.isEmpty())
        assertEquals(2, repository.writes)
        assertEquals(EntryAction.DELETE, vm.state.value.result)
    }

    private class FakeDiary(entry: DiaryEntry) : DiaryRepository {
        val rows = mutableMapOf(entry.id to entry)
        var writes = 0
        var fail = false
        override fun observeDay(dayEpoch: Long) = flowOf(rows.values.filter { it.dayEpoch == dayEpoch })
        override fun observeAll() = flowOf(rows.values.toList())
        override suspend fun get(id: Long) = rows[id]
        override suspend fun save(entry: DiaryEntry): Long = error("Actions must not replace a whole entry")
        private suspend fun beforeWrite() { delay(10); check(!fail); writes++ }
        override suspend fun duplicate(id: Long, dayEpoch: Long, createdAtMillis: Long): Long {
            beforeWrite()
            val next = rows.keys.max() + 1
            rows[next] = rows.getValue(id).copy(id = next, dayEpoch = dayEpoch, createdAtMillis = createdAtMillis)
            return next
        }
        override suspend fun changeDate(id: Long, dayEpoch: Long) {
            beforeWrite()
            rows[id] = rows.getValue(id).copy(dayEpoch = dayEpoch)
        }
        override suspend fun delete(id: Long) { beforeWrite(); rows.remove(id) }
    }
}
