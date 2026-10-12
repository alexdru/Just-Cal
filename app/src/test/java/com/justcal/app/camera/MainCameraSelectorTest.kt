package com.justcal.app.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class MainCameraSelectorTest {
    private val modules = listOf(
        CameraModule("ultrawide", 18.0, 2.0),
        CameraModule("main", 48.0, 5.0),
        CameraModule("telephoto", 16.0, 9.0),
    )
    @Test fun matchesDefaultLogicalSensorDespiteCameraIdOrder() {
        assertEquals("main", mainCameraModule(modules.reversed(), 48.0, 5.0).id)
    }
    @Test fun largestSensorIsFallbackWhenLogicalSizeIsUnavailable() {
        assertEquals("main", mainCameraModule(modules, null, null).id)
    }
    @Test fun focalLengthDisambiguatesMatchingSensorSizes() {
        assertEquals("main", mainCameraModule(modules + CameraModule("other", 48.0, 2.0), 48.0, 5.0).id)
    }
    @Test(expected = IllegalStateException::class)
    fun missingPhysicalSensorMetadataIsNotSilentlyReplacedWithLogicalCamera() {
        mainCameraModule(listOf(CameraModule("unknown", 0.0, null)), null, null)
    }
}
