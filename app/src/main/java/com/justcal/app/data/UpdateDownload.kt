package com.justcal.app.data

import com.justcal.app.domain.UpdateError
import com.justcal.app.domain.UpdateException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Bounded streaming shared by network downloads and cached-file revalidation. */
internal fun copyUpdate(
    input: InputStream,
    output: OutputStream,
    sizeBytes: Long,
    sha256: String,
    ensureActive: () -> Unit,
    progress: (Float) -> Unit,
) {
    if (sizeBytes !in 1..GitHubRelease.MAX_APK_BYTES) throw UpdateException(UpdateError.INVALID_APK)
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    var lastPercent = -1
    while (true) {
        ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > sizeBytes) throw UpdateException(UpdateError.INVALID_APK)
        digest.update(buffer, 0, count)
        try { output.write(buffer, 0, count) }
        catch (_: java.io.IOException) { throw UpdateException(UpdateError.STORAGE) }
        val percent = (total * 100 / sizeBytes).toInt()
        if (percent != lastPercent) {
            lastPercent = percent
            progress(total.toFloat() / sizeBytes)
        }
    }
    ensureActive()
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    if (total != sizeBytes || actual != sha256) throw UpdateException(UpdateError.INVALID_APK)
}
