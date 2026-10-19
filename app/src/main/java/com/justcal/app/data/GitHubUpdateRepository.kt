package com.justcal.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.justcal.app.BuildConfig
import com.justcal.app.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.minutes

private val Context.updateStore by preferencesDataStore("app_updates")

@Singleton
class GitHubUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppUpdateRepository {
    private val autoKey = booleanPreferencesKey("automatic")
    private val attemptKey = longPreferencesKey("last_attempt_millis")
    private val skipKey = stringPreferencesKey("skipped_version")

    override suspend fun preferences(): UpdatePreferences {
        val values = context.updateStore.data.first()
        return UpdatePreferences(values[autoKey] ?: true, values[attemptKey] ?: 0, values[skipKey])
    }

    override suspend fun setAutomatic(enabled: Boolean) { context.updateStore.edit { it[autoKey] = enabled } }
    override suspend fun recordAttempt(atMillis: Long) { context.updateStore.edit { it[attemptKey] = atMillis } }
    override suspend fun skip(version: String) { context.updateStore.edit { it[skipKey] = version } }

    override suspend fun latest(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = try {
            open(URI("https://api.github.com/repos/alexdru/Just-Cal/releases/latest"), api = true)
        } catch (_: IOException) {
            throw UpdateException(UpdateError.NETWORK)
        }
        try {
            if (connection.responseCode == 404) return@withContext null
            requireSuccess(connection)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1024 * 1024) throw UpdateException(UpdateError.INVALID_RELEASE)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            currentCoroutineContext().ensureActive()
            if (bytes.size > 1024 * 1024) throw UpdateException(UpdateError.INVALID_RELEASE)
            GitHubRelease.decode(bytes.toString(Charsets.UTF_8))
        } catch (_: IOException) {
            throw UpdateException(UpdateError.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(update: AppUpdate, progress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            // Debug/ocrCheck have a separate package and certificate, and can never self-update from production.
            if (context.packageName != "com.justcal.app" || "arm64-v8a" !in Build.SUPPORTED_ABIS) {
                throw UpdateException(UpdateError.INVALID_APK)
            }
            val directory = File(context.cacheDir, "app-updates")
            if (!directory.isDirectory && !directory.mkdirs()) throw UpdateException(UpdateError.STORAGE)
            val destination = File(directory, "Just-Cal-v${update.version}.apk")
            val partial = File(directory, "download.part")
            val job = currentCoroutineContext()
            val active = { job.ensureActive() }
            // Files are owned exclusively by this updater; retain at most the selected APK.
            directory.listFiles()?.filter { it != destination }?.forEach { it.delete() }
            try {
                if (destination.isFile) {
                    try {
                        destination.inputStream().use { input ->
                            copyUpdate(input, discardOutput, update.sizeBytes, update.sha256, active, progress)
                        }
                        verifyPackage(destination, update)
                        return@withContext apkUri(destination)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        destination.delete()
                    }
                }
                val connection = open(URI(update.apkUrl))
                try {
                    requireSuccess(connection)
                    if (connection.contentLengthLong > 0 && connection.contentLengthLong != update.sizeBytes) {
                        throw UpdateException(UpdateError.INVALID_APK)
                    }
                    try {
                        withTimeout(15.minutes) {
                            val transferContext = currentCoroutineContext()
                            connection.inputStream.use { input ->
                                val output = try { partial.outputStream() }
                                    catch (_: IOException) { throw UpdateException(UpdateError.STORAGE) }
                                output.use {
                                    copyUpdate(input, output, update.sizeBytes, update.sha256,
                                        { transferContext.ensureActive() }, progress)
                                }
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        throw UpdateException(UpdateError.NETWORK)
                    }
                } finally {
                    connection.disconnect()
                }
                active()
                verifyPackage(partial, update)
                if (!partial.renameTo(destination)) throw UpdateException(UpdateError.STORAGE)
                apkUri(destination)
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateException) {
                throw error
            } catch (_: IOException) {
                throw UpdateException(UpdateError.NETWORK)
            } catch (_: Exception) {
                throw UpdateException(UpdateError.INVALID_APK)
            } finally {
                partial.delete()
            }
        }

    private fun apkUri(file: File): String =
        FileProvider.getUriForFile(context, "${context.packageName}.updates.fileprovider", file).toString()

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, update: AppUpdate) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateException(UpdateError.INVALID_APK)
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        fun PackageInfo.identity() = UpdateApkIdentity(
            packageName, versionName, longVersionCode, applicationInfo?.minSdkVersion ?: Int.MAX_VALUE,
            signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty(),
        )
        if (!archive.identity().canUpdate(installed.identity(), update.version, Build.VERSION.SDK_INT)) {
            throw UpdateException(UpdateError.INVALID_APK)
        }
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status == 403 || status == 429) throw UpdateException(UpdateError.RATE_LIMIT)
        if (status != 200) throw UpdateException(UpdateError.NETWORK)
    }

    private fun open(uri: URI, api: Boolean = false): HttpURLConnection {
        var current = uri
        repeat(6) {
            if (api) {
                if (current != URI("https://api.github.com/repos/alexdru/Just-Cal/releases/latest")) {
                    throw UpdateException(UpdateError.INVALID_RELEASE)
                }
            } else if (!GitHubRelease.allowedDownloadUrl(current)) {
                throw UpdateException(UpdateError.INVALID_RELEASE)
            }
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Just-Cal/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (api) {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location")
                    ?: throw UpdateException(UpdateError.NETWORK)
                current = current.resolve(location)
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw UpdateException(UpdateError.NETWORK)
    }

    private val discardOutput = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, count: Int) = Unit
    }
}
