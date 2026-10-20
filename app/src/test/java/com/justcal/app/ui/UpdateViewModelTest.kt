package com.justcal.app.ui

import com.justcal.app.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)
    private val update = AppUpdate(ReleaseVersion(99, 0, 0), "Notes", "page", "apk", 3, "hash")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun automaticCheckIsDailyAndManualBypassesTheIntervalAndSkip() = runTest(dispatcher) {
        val repo = FakeUpdates(update).apply { prefs = UpdatePreferences(skippedVersion = "99.0.0") }
        val vm = UpdateViewModel(repo, clock)
        vm.check(automatic = true); vm.check(automatic = true)
        advanceUntilIdle()
        assertEquals(1, repo.requests)
        assertEquals(update, vm.state.value.update)
        assertFalse(vm.state.value.showSheet)
        vm.check(automatic = true); advanceUntilIdle()
        assertEquals(1, repo.requests)
        vm.check(); advanceUntilIdle()
        assertEquals(2, repo.requests)
        assertTrue(vm.state.value.showSheet)
        assertFalse(vm.state.value.automaticPrompt)
    }

    @Test fun disabledChecksMakeNoRequestButManualStillWorks() = runTest(dispatcher) {
        val repo = FakeUpdates(update).apply { prefs = UpdatePreferences(automatic = false) }
        val vm = UpdateViewModel(repo, clock)
        vm.check(automatic = true); advanceUntilIdle()
        assertEquals(0, repo.requests)
        vm.check(); advanceUntilIdle()
        assertEquals(1, repo.requests)
        assertTrue(vm.state.value.showSheet)
    }

    @Test fun manualNoUpdateNeverOpensSheetAndEmitsSnackbarForCurrentOrOlderReleases() = runTest(dispatcher) {
        val repo = FakeUpdates(null)
        val vm = UpdateViewModel(repo, clock)
        val feedback = mutableListOf<UpdateFeedback>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback.add(it) } }
        vm.check()
        assertFalse(vm.state.value.showSheet)
        assertTrue(vm.state.value.checking)
        vm.check(); runCurrent()
        assertFalse(vm.state.value.showSheet)
        assertTrue(vm.state.value.checking)
        assertEquals(1, repo.requests)
        advanceUntilIdle()
        assertFalse(vm.state.value.showSheet)
        assertTrue(vm.state.value.checked)
        assertFalse(vm.state.value.checking)
        assertNull(vm.state.value.update)
        assertEquals(listOf(UpdateFeedback.CurrentVersion), feedback)
        repo.release = update.copy(version = ReleaseVersion(0, 0, 1))
        vm.check(); advanceUntilIdle()
        assertNull(vm.state.value.update)
        assertFalse(vm.state.value.showSheet)
        assertEquals(listOf(UpdateFeedback.CurrentVersion, UpdateFeedback.CurrentVersion), feedback)
    }

    @Test fun offlineAutomaticFailureIsQuietButManualErrorAndRetryStayInSheet() = runTest(dispatcher) {
        val repo = FakeUpdates(update).apply { fail = true }
        val vm = UpdateViewModel(repo, clock)
        vm.check(automatic = true); advanceUntilIdle()
        assertEquals(UpdateError.NETWORK, vm.state.value.error)
        assertFalse(vm.state.value.showSheet)
        vm.check(automatic = true); advanceUntilIdle()
        assertEquals(1, repo.requests)
        vm.check(); advanceUntilIdle()
        assertTrue(vm.state.value.showSheet)
        assertFalse(vm.state.value.checked)
        assertFalse(vm.state.value.checking)
        repo.fail = false
        vm.check()
        assertFalse(vm.state.value.showSheet)
        assertTrue(vm.state.value.checking)
        advanceUntilIdle()
        assertTrue(vm.state.value.showSheet)
        assertTrue(vm.state.value.checked)
        assertEquals(update, vm.state.value.update)
        assertNull(vm.state.value.error)
    }

    @Test fun dismissingPendingChecksDoesNotReopenTheSheetOnSuccessOrFailure() = runTest(dispatcher) {
        listOf(false, true).forEach { fails ->
            val repo = FakeUpdates(update).apply { fail = fails }
            val vm = UpdateViewModel(repo, clock)
            vm.check(); runCurrent()
            assertTrue(vm.state.value.checking)
            vm.dismiss(); advanceUntilIdle()
            assertFalse(vm.state.value.showSheet)
            assertFalse(vm.state.value.checking)
            assertEquals(1, repo.requests)
        }
    }

    @Test fun manualTapJoinsRunningAutomaticRequestAndOnlyOpensSheetForAvailableRelease() = runTest(dispatcher) {
        val repo = FakeUpdates(update)
        val vm = UpdateViewModel(repo, clock)
        vm.check(automatic = true); runCurrent()
        assertTrue(vm.state.value.checking)
        assertFalse(vm.state.value.showSheet)
        vm.check()
        assertFalse(vm.state.value.showSheet)
        assertFalse(vm.state.value.automaticPrompt)
        advanceUntilIdle()
        assertEquals(1, repo.requests)
        assertTrue(vm.state.value.showSheet)
        vm.dismiss()
        vm.check(automatic = true); advanceUntilIdle()
        assertFalse(vm.state.value.showSheet)
        assertEquals(1, repo.requests)
    }

    @Test fun dismissedManualAndAutomaticCurrentResultsDoNotEmitSnackbar() = runTest(dispatcher) {
        val vm = UpdateViewModel(FakeUpdates(null), clock)
        val feedback = mutableListOf<UpdateFeedback>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback.add(it) } }
        vm.check(automatic = true); advanceUntilIdle()
        assertTrue(feedback.isEmpty())
        vm.check(); runCurrent()
        vm.dismiss(); advanceUntilIdle()
        assertFalse(vm.state.value.showSheet)
        assertTrue(feedback.isEmpty())
    }

    @Test fun manualJoinBeforeAutomaticJobStartsStillGetsOneCurrentSnackbar() = runTest(dispatcher) {
        val repo = FakeUpdates(null).apply { prefs = UpdatePreferences(automatic = false) }
        val vm = UpdateViewModel(repo, clock)
        val feedback = mutableListOf<UpdateFeedback>()
        val sheets = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.feedback.collect { feedback.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { sheets.add(it.showSheet) }
        }
        vm.check(automatic = true)
        vm.check()
        vm.check()
        advanceUntilIdle()
        assertEquals(1, repo.requests)
        assertFalse(sheets.any { it })
        assertEquals(listOf(UpdateFeedback.CurrentVersion), feedback)
    }

    @Test fun manualAvailableReleaseStaysInButtonUntilResponse() = runTest(dispatcher) {
        val repo = FakeUpdates(update)
        val vm = UpdateViewModel(repo, clock)
        vm.check(); runCurrent()
        assertTrue(vm.state.value.checking)
        assertFalse(vm.state.value.showSheet)
        advanceUntilIdle()
        assertFalse(vm.state.value.checking)
        assertTrue(vm.state.value.showSheet)
        assertEquals(update, vm.state.value.update)
    }

    @Test fun automaticAvailableReleaseOpensSheetButNoUpdateDoesNot() = runTest(dispatcher) {
        val available = UpdateViewModel(FakeUpdates(update), clock)
        available.check(automatic = true); advanceUntilIdle()
        assertTrue(available.state.value.showSheet)
        assertTrue(available.state.value.automaticPrompt)
        val current = UpdateViewModel(FakeUpdates(null), clock)
        current.check(automatic = true); advanceUntilIdle()
        assertFalse(current.state.value.showSheet)
    }

    @Test fun downloadDoubleTapCancellationAndRetryRetainRelease() = runTest(dispatcher) {
        val repo = FakeUpdates(update).apply { blockDownload = true }
        val vm = UpdateViewModel(repo, clock)
        vm.check(); advanceUntilIdle()
        vm.download(); vm.download(); runCurrent()
        assertEquals(1, repo.downloads)
        assertTrue(vm.state.value.downloading)
        vm.dismiss()
        assertTrue(vm.state.value.downloading)
        vm.cancelDownload(); advanceUntilIdle()
        assertFalse(vm.state.value.downloading)
        assertNull(vm.state.value.apkUri)
        assertEquals(update, vm.state.value.update)
        repo.blockDownload = false
        vm.showUpdate(); vm.download(); advanceUntilIdle()
        assertEquals(2, repo.downloads)
        assertEquals("content://verified.apk", vm.state.value.apkUri)
    }

    @Test fun skippingPersistsAndDoesNotDiscardTheManualUpdateEntry() = runTest(dispatcher) {
        val repo = FakeUpdates(update)
        val vm = UpdateViewModel(repo, clock)
        vm.check(); advanceUntilIdle()
        vm.skip(); advanceUntilIdle()
        assertEquals("99.0.0", repo.prefs.skippedVersion)
        assertFalse(vm.state.value.showSheet)
        vm.showUpdate()
        assertTrue(vm.state.value.showSheet)
        assertEquals(update, vm.state.value.update)
    }

    @Test fun preferenceFailureRetainsSavedChoiceAndNotifiesOutsideTheSheet() = runTest(dispatcher) {
        val repo = FakeUpdates(update)
        val vm = UpdateViewModel(repo, clock)
        val feedback = mutableListOf<UpdateFeedback>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback.add(it) } }
        advanceUntilIdle()
        repo.failPreference = true
        vm.setAutomatic(false); advanceUntilIdle()
        assertTrue(vm.state.value.automatic)
        assertEquals(listOf(UpdateFeedback.Failure(UpdateError.STORAGE)), feedback)
        assertFalse(vm.state.value.showSheet)
        assertFalse(vm.state.value.savingPreference)
        repo.failPreference = false
        vm.setAutomatic(false); advanceUntilIdle()
        assertFalse(vm.state.value.automatic)
        assertFalse(repo.prefs.automatic)
    }

    @Test fun manualCheckReopensAnActiveDownloadAndReadyApkWithoutAnotherRequest() = runTest(dispatcher) {
        val repo = FakeUpdates(update).apply { blockDownload = true }
        val vm = UpdateViewModel(repo, clock)
        vm.check(); advanceUntilIdle()
        vm.download(); runCurrent()
        vm.dismiss()
        vm.check()
        assertTrue(vm.state.value.showSheet)
        assertTrue(vm.state.value.downloading)
        assertFalse(vm.state.value.checking)
        assertEquals(1, repo.requests)
        vm.cancelDownload(); advanceUntilIdle()
        repo.blockDownload = false
        vm.download(); advanceUntilIdle()
        val verifiedUri = vm.state.value.apkUri
        vm.dismiss()
        repo.fail = true
        vm.check(); advanceUntilIdle()
        assertTrue(vm.state.value.showSheet)
        assertTrue(vm.state.value.checked)
        assertEquals(verifiedUri, vm.state.value.apkUri)
        assertNull(vm.state.value.error)
        assertEquals(1, repo.requests)
    }

    @Test fun disablingAutomaticDuringCheckIsSavedAndSuppressesItsPrompt() = runTest(dispatcher) {
        val repo = FakeUpdates(update)
        val vm = UpdateViewModel(repo, clock)
        vm.check(automatic = true); runCurrent()
        assertTrue(vm.state.value.checking)
        vm.setAutomatic(false); runCurrent()
        assertFalse(vm.state.value.automatic)
        advanceUntilIdle()
        assertFalse(repo.prefs.automatic)
        assertFalse(vm.state.value.showSheet)
        assertEquals(update, vm.state.value.update)
    }

    private class FakeUpdates(var release: AppUpdate?) : AppUpdateRepository {
        var prefs = UpdatePreferences()
        var requests = 0
        var downloads = 0
        var fail = false
        var failPreference = false
        var blockDownload = false
        override suspend fun preferences() = prefs
        override suspend fun setAutomatic(enabled: Boolean) {
            if (failPreference) error("Storage")
            prefs = prefs.copy(automatic = enabled)
        }
        override suspend fun recordAttempt(atMillis: Long) { prefs = prefs.copy(lastAttemptMillis = atMillis) }
        override suspend fun skip(version: String) { prefs = prefs.copy(skippedVersion = version) }
        override suspend fun latest(): AppUpdate? {
            requests++
            delay(10)
            if (fail) throw UpdateException(UpdateError.NETWORK)
            return release
        }
        override suspend fun download(update: AppUpdate, progress: (Float) -> Unit): String {
            downloads++
            progress(0.5f)
            if (blockDownload) awaitCancellation()
            return "content://verified.apk"
        }
    }
}
