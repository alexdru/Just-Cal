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
class BackupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)

    @Test fun backupDestinationSurvivesRecreationAndUsesSystemDocumentPicker() {
        compose.onNode(hasText(label(R.string.settings)) and hasClickAction()).performClick()
        compose.onNode(hasText(label(R.string.data_backup)) and hasClickAction())
            .performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.export_diary)).assertExists()
        compose.onNodeWithText(label(R.string.import_diary)).assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label(R.string.import_diary)).assertExists().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(label(R.string.import_diary))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithText(label(R.string.import_diary)).assertExists()
    }
}
