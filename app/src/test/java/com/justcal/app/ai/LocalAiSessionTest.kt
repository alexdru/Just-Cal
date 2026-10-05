package com.justcal.app.ai

import com.justcal.app.camera.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiSessionTest {
    private val model = LocalModel("test", "Gemma.litertlm", 2500000000)
    private val image = PreparedImage("file:///private/image", ImageSource.PHOTO_PICKER,
        ScanRequest(ScanMode.PACKAGE, 100), 100, 100, "image/jpeg")

    @Test fun repeatsInferenceWithoutReloadAndRejectsConcurrentOperations() = runTest {
        val engine = FakeEngine()
        val session = LocalAiSession(engine, backgroundScope)
        session.refresh(listOf(model))
        session.load(); session.load()
        runCurrent()
        assertEquals(AiLabPhase.LOADING, session.state.value.phase)
        advanceTimeBy(100); runCurrent()
        assertEquals(1, engine.loads)
        assertTrue(session.state.value.loaded)
        repeat(2) {
            session.run(image, "Describe", false)
            session.run(image, "Duplicate", false)
            session.unload()
            session.backend(AiBackend.GPU)
            advanceTimeBy(150); runCurrent()
            assertEquals("Hello world", session.state.value.response)
            assertEquals(AiLabPhase.READY, session.state.value.phase)
            assertEquals(AiBackend.CPU, session.state.value.backend)
        }
        assertEquals(2, engine.runs)
        assertEquals(1, engine.loads)
        session.unload(); advanceTimeBy(100); runCurrent()
        assertFalse(session.state.value.loaded)
        assertEquals(1, engine.unloads)
        assertNotNull(session.state.value.metrics)
        session.backend(AiBackend.GPU)
        assertEquals(AiLabPhase.MODEL_SELECTED, session.state.value.phase)
        assertNull(session.state.value.metrics)
        assertNull(session.state.value.initializationMillis)
        assertEquals("", session.state.value.response)
    }

    @Test fun cancellationKeepsPartialResponseAndLoadedEngineUntilCleanupCompletes() = runTest {
        val engine = FakeEngine()
        val session = LocalAiSession(engine, backgroundScope)
        session.refresh(listOf(model)); session.load()
        advanceTimeBy(100); runCurrent()
        engine.longRun = true
        session.run(image, "Describe", false)
        runCurrent()
        assertEquals("Hello", session.state.value.response)
        session.cancel(); runCurrent()
        assertTrue(session.state.value.busy)
        assertTrue(session.state.value.cancelling)
        session.run(image, "Too early", false)
        advanceTimeBy(50); runCurrent()
        assertEquals(AiLabPhase.CANCELLED, session.state.value.phase)
        assertTrue(session.state.value.loaded)
        assertNull(session.state.value.metrics)
        assertEquals("Hello", session.state.value.response)
        engine.longRun = false
        session.run(image, "Repeat", false)
        advanceTimeBy(150); runCurrent()
        assertEquals(AiLabPhase.READY, session.state.value.phase)
        assertEquals(1, engine.loads)
    }

    @Test fun closingWaitsForInferenceBeforeUnloadAndDisablesFurtherWork() = runTest {
        val engine = FakeEngine()
        val session = LocalAiSession(engine, backgroundScope)
        session.refresh(listOf(model)); session.load()
        advanceTimeBy(100); runCurrent()
        engine.longRun = true
        session.run(image, "Describe", false); runCurrent()
        val close = launch { session.close() }
        runCurrent()
        assertEquals(0, engine.unloads)
        advanceTimeBy(50); runCurrent()
        close.join()
        assertEquals(listOf("conversationClosed", "engineClosed"), engine.cleanup)
        session.load(); session.run(image, "Again", false); runCurrent()
        assertEquals(1, engine.loads)
        assertEquals(1, engine.runs)
    }

    @Test fun gpuFailureIsExplicitAndRetryUsesSelectedBackend() = runTest {
        val engine = FakeEngine().apply { failLoad = true }
        val session = LocalAiSession(engine, backgroundScope)
        session.refresh(listOf(model)); session.backend(AiBackend.GPU); session.load()
        advanceTimeBy(100); runCurrent()
        assertEquals(AiLabPhase.ERROR, session.state.value.phase)
        assertEquals("GPU initialization failed", session.state.value.error)
        assertFalse(session.state.value.loaded)
        assertEquals(AiBackend.GPU, engine.backend)
        session.backend(AiBackend.CPU)
        assertEquals(AiLabPhase.MODEL_SELECTED, session.state.value.phase)
        assertNull(session.state.value.error)
        session.backend(AiBackend.GPU)
        engine.failLoad = false
        session.load(); advanceTimeBy(100); runCurrent()
        assertTrue(session.state.value.loaded)
        assertEquals(AiBackend.GPU, session.state.value.backend)
    }

    @Test fun importedModelCancellationAndInvalidOutputCanBeRetried() = runTest {
        val engine = FakeEngine()
        val session = LocalAiSession(engine, backgroundScope)
        session.importModel { progress -> progress(100); delay(1000); model }
        runCurrent()
        assertEquals(AiLabPhase.IMPORTING, session.state.value.phase)
        session.cancel(); runCurrent()
        assertEquals(AiLabPhase.CANCELLED, session.state.value.phase)
        assertNull(session.state.value.model)
        session.importModel { model }; runCurrent()
        session.load(); advanceTimeBy(100); runCurrent()
        session.run(image, "Nutrition", true); advanceTimeBy(150); runCurrent()
        assertEquals("Hello world", session.state.value.response)
        assertNotNull(session.state.value.parsingError)
        assertNull(session.state.value.parsed)
        assertTrue(session.state.value.loaded)
    }

    @Test fun cancelledLoadNeverClaimsReadyAndAllowsAnotherModel() = runTest {
        val engine = FakeEngine()
        val session = LocalAiSession(engine, backgroundScope)
        session.refresh(listOf(model)); session.load(); runCurrent()
        session.cancel(); runCurrent()
        assertFalse(session.state.value.loaded)
        assertEquals(AiLabPhase.CANCELLED, session.state.value.phase)
        session.backend(AiBackend.GPU)
        session.load(); advanceTimeBy(100); runCurrent()
        assertTrue(session.state.value.loaded)
    }

    private class FakeEngine : LocalAiEngine {
        var loads = 0
        var runs = 0
        var unloads = 0
        var failLoad = false
        var longRun = false
        var backend = AiBackend.CPU
        val cleanup = mutableListOf<String>()
        override suspend fun load(model: LocalModel, backend: AiBackend): Long {
            loads++
            this.backend = backend
            delay(100)
            if (failLoad) error("GPU initialization failed")
            return 100
        }
        override suspend fun generate(image: PreparedImage, prompt: String, structured: Boolean,
            onChunk: suspend (String) -> Unit): AiMetrics {
            runs++
            try {
                onChunk("Hello")
                delay(if (longRun) 10000 else 100)
                onChunk(" world")
                return AiMetrics(backend, 100, 100, 1)
            } finally {
                withContext(NonCancellable) { delay(50); cleanup.add("conversationClosed") }
            }
        }
        override suspend fun unload() { unloads++; cleanup.add("engineClosed") }
    }
}
