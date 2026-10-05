package com.justcal.app.ai

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun importsExactBytesAndReopensWithoutProviderAccess() = runTest {
        val folder = temporary.newFolder()
        val files = ModelFiles(folder)
        val bytes = ByteArray(2 * 1024 * 1024 + 31) { (it % 127).toByte() }
        var progress = 0L
        val model = files.import("../../Gemma.litertlm", bytes.size.toLong(), ByteArrayInputStream(bytes)) { progress = it }
        assertEquals(bytes.size.toLong(), progress)
        assertArrayEquals(bytes, files.path(model).readBytes())
        assertEquals(listOf(model), ModelFiles(folder).list())
        assertEquals(folder.canonicalFile, files.path(model).parentFile?.parentFile)
        files.remove(model)
        assertTrue(files.list().isEmpty())
        assertTrue(folder.listFiles()!!.isEmpty())
    }

    @Test fun invalidEmptyAndTruncatedImportsLeaveNoPrivateFiles() = runTest {
        listOf("wrong.bin" to byteArrayOf(1), "empty.litertlm" to byteArrayOf(),
            "short.litertlm" to byteArrayOf(1)).forEach { (name, bytes) ->
            val folder = temporary.newFolder()
            val result = runCatching { ModelFiles(folder).import(name, 2L, ByteArrayInputStream(bytes)) {} }
            assertTrue(result.isFailure)
            assertTrue(folder.listFiles()!!.isEmpty())
        }
    }

    @Test fun interruptedProviderAndCancelledCopyAreTransactional() = runTest {
        val folder = temporary.newFolder()
        val files = ModelFiles(folder)
        val broken = object : InputStream() { override fun read(): Int = throw IOException("Provider disconnected") }
        assertTrue(runCatching { files.import("bad.litertlm", null, broken) {} }.isFailure)
        val job = launch {
            val copyContext = currentCoroutineContext()
            files.import("cancel.litertlm", null, ByteArrayInputStream(ByteArray(3 * 1024 * 1024))) {
                copyContext.cancel()
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(files.list().isEmpty())
        assertTrue(folder.listFiles()!!.isEmpty())
    }

    @Test fun stalePartialsAreRemovedButCompleteModelsSurvive() = runTest {
        val folder = temporary.newFolder()
        val files = ModelFiles(folder)
        val model = files.import("Gemma.litertlm", null, ByteArrayInputStream(byteArrayOf(1))) {}
        File(folder, ".import-orphan").mkdir()
        files.removePartialImports()
        assertEquals(listOf(model), files.list())
        assertFalse(File(folder, ".import-orphan").exists())
        assertTrue(runCatching { files.path(model.copy(id = "../outside")) }.isFailure)
    }
}
