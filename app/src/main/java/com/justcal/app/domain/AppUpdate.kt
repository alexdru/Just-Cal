package com.justcal.app.domain

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): ReleaseVersion? {
            if (!Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value)) return null
            val parts = value.split('.').map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class AppUpdate(
    val version: ReleaseVersion,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class UpdatePreferences(
    val automatic: Boolean = true,
    val lastAttemptMillis: Long = 0,
    val skippedVersion: String? = null,
) {
    fun checkDue(now: Long): Boolean =
        automatic && (lastAttemptMillis == 0L || now < lastAttemptMillis || now - lastAttemptMillis >= CHECK_INTERVAL_MILLIS)

    companion object { const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L }
}

enum class UpdateError { NETWORK, RATE_LIMIT, INVALID_RELEASE, INVALID_APK, STORAGE, INSTALLER }
class UpdateException(val reason: UpdateError) : Exception(reason.name)

/** Only this boundary accesses GitHub and private update files. No diary data is accepted. */
interface AppUpdateRepository {
    suspend fun preferences(): UpdatePreferences
    suspend fun setAutomatic(enabled: Boolean)
    suspend fun recordAttempt(atMillis: Long)
    suspend fun skip(version: String)
    suspend fun latest(): AppUpdate?
    /** Returns a read-only FileProvider URI only after size, hash and package validation. */
    suspend fun download(update: AppUpdate, progress: (Float) -> Unit): String
}
