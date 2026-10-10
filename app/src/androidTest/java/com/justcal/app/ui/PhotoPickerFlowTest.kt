package com.justcal.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.MainActivity
import com.justcal.app.R
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Platform picker integration without a custom gallery or storage permission. */
@RunWith(AndroidJUnit4::class)
class PhotoPickerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performClick()

    private fun fixture(color: Int): Uri {
        val resolver = compose.activity.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JustCal-picker-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    private fun selectPhoto(index: Int) {
        compose.waitUntil(15000) {
            nodes(automation.rootInActiveWindow).count { it.contentDescription?.startsWith("Photo taken") == true } >= 2
        }
        val photo = nodes(automation.rootInActiveWindow).filter { it.contentDescription?.startsWith("Photo taken") == true }[index]
        var clickable: AccessibilityNodeInfo? = photo
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        check(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        // Newer pickers show Done even for a single image; older pickers return immediately.
        compose.waitUntil(15000) {
            val done = nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() == "Done" }
            if (done != null) {
                var button: AccessibilityNodeInfo? = done
                while (button != null && !button.isClickable) button = button.parent
                button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test fun systemPickerReviewReselectAndCancel() {
        val cache = java.io.File(compose.activity.cacheDir, "image-input")
        val previousFiles = cache.listFiles().orEmpty().map { it.name }.toSet()
        val photos = listOf(fixture(Color.RED), fixture(Color.BLUE))
        try {
            compose.onNodeWithContentDescription(label(R.string.add_food)).performClick()
            click(R.string.scan_package)
            click(R.string.choose_photo)
            selectPhoto(0)
            compose.onNodeWithText(label(R.string.mode_package)).assertExists()
            compose.onNodeWithText(label(R.string.review_package_guidance)).assertExists()
            compose.onNodeWithText(label(R.string.review_no_recognition)).assertExists()
            click(R.string.choose_another)
            selectPhoto(1)
            compose.onNodeWithText(label(R.string.mode_package)).assertExists()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodes(hasText(label(R.string.use_photo)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            click(R.string.use_photo)
            compose.onNodeWithText(label(R.string.photo_ready)).assertExists()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription(label(R.string.add_food)).assertExists()
            compose.waitUntil(5000) { cache.listFiles().orEmpty().all { it.name in previousFiles } }
        } finally { photos.forEach { compose.activity.contentResolver.delete(it, null, null) } }
    }
}
