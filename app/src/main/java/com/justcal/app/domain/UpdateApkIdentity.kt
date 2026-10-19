package com.justcal.app.domain

/** Android archive values converted to plain data for deterministic update validation. */
data class UpdateApkIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val signers: Set<String>,
) {
    fun canUpdate(installed: UpdateApkIdentity, release: ReleaseVersion, deviceSdk: Int): Boolean =
        packageName == installed.packageName && versionName == release.toString() &&
            versionCode > installed.versionCode && minSdk <= deviceSdk &&
            signers.isNotEmpty() && signers == installed.signers
}
