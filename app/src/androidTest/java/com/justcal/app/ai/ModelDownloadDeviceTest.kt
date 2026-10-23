package com.justcal.app.ai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Private transfer state tests; no network request and no user model files. */
@RunWith(AndroidJUnit4::class)
class ModelDownloadDeviceTest {
    @Test fun queuedPauseCancelAndRestartDoNotResurrectStoppedWork() = runBlocking {
        val original = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(original.cacheDir, "model-transfer-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(original) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val catalog = ModelCatalog(context)
        val store = LocalModelStore(context, LiteRtModelInspector(), catalog)
        val repository = ModelDownloadRepository(context, catalog, store)
        val id = catalog.models.first().id
        try {
            repository.queue(id)
            repository.pause()
            repository.transfer(id) // A late service/job callback must not connect.
            assertEquals(TransferPhase.PAUSED, repository.state.value[id]?.phase)
            repository.cancel(id)
            repository.transfer(id)
            assertFalse(repository.state.value.containsKey(id))
            assertFalse(File(directory, "local-ai-models/.download-$id").exists())

            repository.queue(id)
            val partial = File(directory, "local-ai-models/.download-$id/model.litertlm")
            partial.writeBytes(byteArrayOf(1, 2, 3))
            val restored = ModelDownloadRepository(context, catalog, store)
            assertEquals(TransferPhase.PAUSED, restored.state.value[id]?.phase)
            assertEquals(3L, restored.state.value[id]?.bytes)
            restored.cancel(id)
            assertTrue(store.list().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
