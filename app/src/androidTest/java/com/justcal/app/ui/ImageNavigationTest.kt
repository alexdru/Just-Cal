package com.justcal.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.MainActivity
import com.justcal.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(label(id)).performClick()
    private fun fab() = compose.onNodeWithContentDescription(label(R.string.add_food))

    @Test fun launcherModesRestoreAndManualEditorStillOpens() {
        fab().performClick()
        compose.onNodeWithText(label(R.string.scan_package)).assertExists()
        compose.onNodeWithText(label(R.string.scan_meal)).assertExists()
        click(R.string.scan_package)
        compose.onNodeWithText(label(R.string.mode_package)).assertExists()
        compose.onNodeWithText(label(R.string.take_photo)).assertExists()
        compose.onNodeWithText(label(R.string.choose_photo)).assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label(R.string.mode_package)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        click(R.string.scan_meal)
        compose.onNodeWithText(label(R.string.mode_meal)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        click(R.string.add_manually)
        compose.onNode(hasText(label(R.string.food_name)) and hasSetTextAction()).assertExists()
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        click(R.string.settings)
        fab().assertDoesNotExist()
    }
}
