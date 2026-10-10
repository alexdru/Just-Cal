package com.justcal.app.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Debug
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.MainActivity
import com.justcal.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an actual CameraX rear camera, including the emulator's virtual scene camera. */
@RunWith(AndroidJUnit4::class)
class CameraFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performClick()

    @Test fun historicalMealCaptureReviewRetakeAndRecreation() {
        val cache = java.io.File(compose.activity.cacheDir, "image-input")
        val previousFiles = cache.listFiles().orEmpty().map { it.name }.toSet()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        click(R.string.history)
        click(R.string.choose_day)
        val day = LocalDate.now().withDayOfMonth(1)
        val locale = compose.activity.resources.configuration.locales[0]
        val full = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        val medium = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        compose.onNode(hasText(full, substring = true) and hasClickAction()).performClick()
        click(R.string.open_day)
        compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
        click(R.string.scan_meal)
        click(R.string.take_photo)
        repeat(5) { cycle ->
            if (cycle == 1) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            }
            if (cycle == 2) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            }
            compose.waitUntil(15000) {
                compose.onAllNodes(hasContentDescription(label(R.string.capture_photo)) and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(label(R.string.capture_photo)).performClick()
            compose.waitUntil(20000) {
                compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(label(R.string.mode_meal)).assertExists()
            compose.onNodeWithText(medium).assertExists()
            if (cycle == 0) {
                compose.activityRule.scenario.recreate()
                compose.waitUntil(10000) {
                    compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(medium).assertExists()
            }
            Log.i("JustCalCameraTest", "Cycle $cycle: nativeHeapKiB=${Debug.getNativeHeapAllocatedSize() / 1024}")
            if (cycle < 4) click(R.string.retake)
        }
        click(R.string.use_photo)
        compose.onNodeWithText(label(R.string.photo_ready)).assertExists()
        click(R.string.done)
        compose.onNodeWithText(medium).assertExists()
        compose.waitUntil(5000) {
            cache.listFiles().orEmpty().all { it.name in previousFiles }
        }
    }
}
