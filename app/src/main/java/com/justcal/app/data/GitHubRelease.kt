package com.justcal.app.data

import com.justcal.app.domain.*
import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val RELEASE_BASE = "https://github.com/alexdru/Just-Cal/releases"

/** Deliberately accepts only the signed production asset named by our release workflow. */
object GitHubRelease {
    const val MAX_APK_BYTES = 300L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(body: String): AppUpdate? {
        try {
            val release = json.decodeFromString<Release>(body)
            if (release.draft || release.prerelease) return null
            val version = ReleaseVersion.parse(release.tag.removePrefix("v"))
                ?.takeIf { release.tag == "v$it" }
                ?: throw UpdateException(UpdateError.INVALID_RELEASE)
            val filename = "Just-Cal-v$version.apk"
            val asset = release.assets.singleOrNull { it.name == filename }
                ?: throw UpdateException(UpdateError.INVALID_RELEASE)
            val expectedUrl = "$RELEASE_BASE/download/v$version/$filename"
            val hash = asset.digest?.removePrefix("sha256:")
            if (asset.url != expectedUrl || asset.size !in 1..MAX_APK_BYTES ||
                asset.digest != "sha256:$hash" || hash == null || !Regex("[0-9a-f]{64}").matches(hash)) {
                throw UpdateException(UpdateError.INVALID_RELEASE)
            }
            return AppUpdate(version, release.body.orEmpty().take(32_000), "$RELEASE_BASE/tag/v$version",
                expectedUrl, asset.size, hash)
        } catch (error: UpdateException) {
            throw error
        } catch (_: Exception) {
            throw UpdateException(UpdateError.INVALID_RELEASE)
        }
    }

    /** Redirects may only stay on GitHub's HTTPS release delivery hosts. */
    fun allowedDownloadUrl(url: URI): Boolean =
        url.scheme == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
            url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    @Serializable private data class Release(
        @SerialName("tag_name") val tag: String,
        val draft: Boolean,
        val prerelease: Boolean,
        val body: String? = null,
        val assets: List<Asset>,
    )
    @Serializable private data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
        val digest: String? = null,
    )
}
