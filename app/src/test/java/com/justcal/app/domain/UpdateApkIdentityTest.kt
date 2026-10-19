package com.justcal.app.domain

import org.junit.Assert.*
import org.junit.Test

class UpdateApkIdentityTest {
    private val installed = UpdateApkIdentity("com.justcal.app", "0.15.0", 31, 30, setOf("release-cert"))
    private val release = ReleaseVersion(0, 16, 0)
    private val apk = installed.copy(versionName = "0.16.0", versionCode = 32)

    @Test fun acceptsNewerCompatibleApkWithTheSameSigningIdentity() {
        assertTrue(apk.canUpdate(installed, release, 30))
        assertTrue(apk.canUpdate(installed, release, 37))
    }

    @Test fun rejectsDowngradesMismatchedVersionsPackagesAndCertificates() {
        listOf(
            apk.copy(versionCode = 31), apk.copy(versionCode = 30),
            apk.copy(versionName = "0.17.0"), apk.copy(versionName = null),
            apk.copy(packageName = "com.justcal.app.debug"),
            apk.copy(signers = setOf("debug-cert")), apk.copy(signers = emptySet()),
            apk.copy(signers = setOf("release-cert", "other-cert")),
            apk.copy(minSdk = 31),
        ).forEach { assertFalse(it.toString(), it.canUpdate(installed, release, 30)) }
        assertFalse(apk.canUpdate(installed.copy(signers = emptySet()), release, 30))
    }
}
