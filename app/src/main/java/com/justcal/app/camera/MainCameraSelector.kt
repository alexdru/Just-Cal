package com.justcal.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.abs

internal data class CameraModule(val id: String, val sensorArea: Double, val focalLength: Double?)

/** Match the logical camera's default sensor; largest sensor is the metadata fallback. */
internal fun mainCameraModule(
    modules: List<CameraModule>, defaultArea: Double?, defaultFocalLength: Double?,
): CameraModule = modules.asSequence()
    .filter { (_, sensorArea, _) -> sensorArea.isFinite() && (sensorArea > 0) }
    .minWithOrNull(
        compareBy<CameraModule> { (_, sensorArea, _) ->
            if (defaultArea != null && (defaultArea > 0)) abs(sensorArea - defaultArea) else -sensorArea
        }.thenBy { (_, _, focalLength) ->
            if (defaultFocalLength != null) abs((focalLength ?: Double.POSITIVE_INFINITY) - defaultFocalLength) else 0.0
        }.thenBy(CameraModule::id),
    ) ?: error("Physical main camera metadata unavailable")

/** Both Preview and ImageCapture stay on one physical sensor, including during AF. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal fun mainCameraSelector(context: Context, provider: ProcessCameraProvider): CameraSelector {
    val logical = Camera2CameraInfo.from(provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA))
    val manager = context.getSystemService(CameraManager::class.java)
    val characteristics = manager.getCameraCharacteristics(logical.cameraId)
    val multi = characteristics[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
        ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
    if (!multi) return CameraSelector.DEFAULT_BACK_CAMERA
    fun area(value: CameraCharacteristics): Double? =
        value[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let { it.width.toDouble() * it.height }
    fun focal(value: CameraCharacteristics): Double? =
        value[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull()?.toDouble()
    val modules = characteristics.physicalCameraIds.map { id ->
        val value = manager.getCameraCharacteristics(id)
        CameraModule(id, area(value) ?: 0.0, focal(value))
    }
    val main = mainCameraModule(modules, area(characteristics), focal(characteristics))
    return CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == logical.cameraId } }
        .setPhysicalCameraId(main.id).build()
}
