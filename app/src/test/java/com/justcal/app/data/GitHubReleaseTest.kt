package com.justcal.app.data

import com.justcal.app.domain.*
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseTest {
    private val hash = "a".repeat(64)
    private fun release(
        tag: String = "v0.16.0", asset: String = "Just-Cal-v0.16.0.apk",
        url: String = "https://github.com/alexdru/Just-Cal/releases/download/v0.16.0/Just-Cal-v0.16.0.apk",
        digest: String = "sha256:$hash", size: Long = 85_000_000, flags: String = "",
    ) = """{"tag_name":"$tag","draft":false,"prerelease":false,"body":"Release notes",
        "assets":[{"name":"unrelated.aab","size":1,"browser_download_url":"unused"},
        {"name":"$asset","size":$size,"browser_download_url":"$url","digest":"$digest"}]$flags}"""

    @Test fun selectsOnlyTheWorkflowApkAndPreservesNotes() {
        val update = requireNotNull(GitHubRelease.decode(release()))
        assertEquals(ReleaseVersion(0, 16, 0), update.version)
        assertEquals(hash, update.sha256)
        assertEquals("Release notes", update.notes)
        assertEquals("https://github.com/alexdru/Just-Cal/releases/tag/v0.16.0", update.pageUrl)
    }

    @Test fun rejectsUntrustedAssetsAndInvalidMetadata() {
        listOf(
            release(asset = "app-debug.apk"),
            release(url = "https://evil.example/app.apk"),
            release(url = "http://github.com/alexdru/Just-Cal/releases/download/v0.16.0/Just-Cal-v0.16.0.apk"),
            release(url = "https://github.com/other/Just-Cal/releases/download/v0.16.0/Just-Cal-v0.16.0.apk"),
            release(digest = ""), release(digest = "sha512:$hash"),
            release(size = 0), release(size = GitHubRelease.MAX_APK_BYTES + 1),
            release(tag = "v0.16.0-beta"), release(tag = "0.16.0"), "not json",
            release().replace("\"digest\":\"sha256:$hash\"", "\"unexpected\":true"),
        ).forEach { body ->
            val error = assertThrows(UpdateException::class.java) { GitHubRelease.decode(body) }
            assertEquals(UpdateError.INVALID_RELEASE, error.reason)
        }
    }

    @Test fun draftAndPrereleaseAreNeverOffered() {
        assertNull(GitHubRelease.decode(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(GitHubRelease.decode(release().replace("\"prerelease\":false", "\"prerelease\":true")))
    }

    @Test fun numericVersionOrderingIsStrictAndDoesNotOverflow() {
        assertTrue(ReleaseVersion.parse("0.10.0")!! > ReleaseVersion.parse("0.9.99")!!)
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("0.99.99")!!)
        assertEquals(0, ReleaseVersion(1, 2, 3).compareTo(ReleaseVersion(1, 2, 3)))
        listOf("1.2", "01.2.3", "-1.2.3", "1.2.3-debug", "999999999999999.0.0", "v1.2.3").forEach {
            assertNull(ReleaseVersion.parse(it))
        }
    }

    @Test fun redirectsCannotLeaveGitHubOrDowngradeToHttp() {
        assertTrue(GitHubRelease.allowedDownloadUrl(URI("https://release-assets.githubusercontent.com/file?token=test")))
        assertTrue(GitHubRelease.allowedDownloadUrl(URI("https://objects.githubusercontent.com/file")))
        listOf("http://github.com/file", "https://github.com.evil.test/file", "https://evil.test/file",
            "https://user@github.com/file", "https://github.com:8080/file", "file:///tmp/update.apk").forEach {
            assertFalse(GitHubRelease.allowedDownloadUrl(URI(it)))
        }
    }

    @Test fun attemptsAreThrottledIncludingClockRollbackAndDisabledPreference() {
        val now = 1_000_000L
        val prefs = UpdatePreferences(lastAttemptMillis = now)
        assertFalse(prefs.checkDue(now + UpdatePreferences.CHECK_INTERVAL_MILLIS - 1))
        assertTrue(prefs.checkDue(now + UpdatePreferences.CHECK_INTERVAL_MILLIS))
        assertTrue(prefs.checkDue(now - 1))
        assertFalse(prefs.copy(automatic = false).checkDue(now + 100_000_000))
        assertTrue(UpdatePreferences().checkDue(now))
    }
}
