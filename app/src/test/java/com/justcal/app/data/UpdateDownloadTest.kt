package com.justcal.app.data

import com.justcal.app.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class UpdateDownloadTest {
    private val bytes = ByteArray(170_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun exactBytesAreVerifiedWithMonotonicBoundedProgress() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Float>()
        copyUpdate(bytes.inputStream(), output, bytes.size.toLong(), hash, {}, progress::add)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(progress.all { it in 0f..1f })
    }

    @Test fun truncationExcessAndHashMismatchAreRejected() {
        listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(1), bytes.copyOf().also { it[0] = 99 })
            .forEach { corrupt ->
                val failure = assertThrows(UpdateException::class.java) {
                    copyUpdate(corrupt.inputStream(), ByteArrayOutputStream(), bytes.size.toLong(), hash, {}, {})
                }
                assertEquals(UpdateError.INVALID_APK, failure.reason)
            }
    }

    @Test fun cancellationStopsTheStreamBeforeFurtherWrites() {
        var checks = 0
        val output = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            copyUpdate(bytes.inputStream(), output, bytes.size.toLong(), hash,
                { if (++checks == 2) throw CancellationException() }, {})
        }
        assertEquals(64 * 1024, output.size())
    }

    @Test fun writeFailuresAreReportedAsStorageErrors() {
        val broken = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Full disk") }
        }
        val error = assertThrows(UpdateException::class.java) {
            copyUpdate(ByteArrayInputStream(bytes), broken, bytes.size.toLong(), hash, {}, {})
        }
        assertEquals(UpdateError.STORAGE, error.reason)
    }
}
