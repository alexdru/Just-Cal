package com.justcal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.justcal.app.diagnostics.DiagnosticScan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated UI fixtures never alter the user's diagnostic history. */
@RunWith(AndroidJUnit4::class)
class OcrDiagnosticsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun systemBackClosesDetailsInsideAScopedTabWithoutLeavingHistory() {
        var parentBacks = 0
        val scan = DiagnosticScan("back-test", 20000, status = "SUCCESS", totalMs = 123,
            productName = "Back test")
        compose.setContent {
            val owner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner()
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides owner,
            ) {
                val back = androidx.navigationevent.compose.rememberNavigationEventState(
                    currentInfo = androidx.navigationevent.NavigationEventInfo.None,
                )
                androidx.navigationevent.compose.NavigationBackHandler(back) { parentBacks++ }
                MaterialTheme {
                    OcrDiagnosticsContent(listOf(scan), false, false, false, null,
                        {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {})
                }
            }
        }
        compose.onNodeWithText(label(R.string.diag_history)).performClick()
        compose.onNode(hasText("Back test") and hasClickAction()).performClick()
        compose.onNodeWithText(label(R.string.diag_close)).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText(label(R.string.diag_close)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.diag_history)).assertIsSelected()
        compose.runOnIdle { assertEquals(0, parentBacks) }
    }

    @Test fun swipesAndTabClicksStaySynchronizedWithOneSharedReportActionRow() {
        compose.setContent {
            MaterialTheme {
                OcrDiagnosticsContent(emptyList(), false, false, false, null,
                    {}, {}, { _, _ -> }, { _, _ -> },
                    { _, _, _ -> }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithTag("diagnostics_page_0").performScrollToNode(hasText(label(R.string.diag_capture)))
        compose.onNodeWithText(label(R.string.diag_capture)).assertExists()
        compose.onNodeWithTag("diagnostics_detailed_toggle").assertDoesNotExist()
        compose.onNodeWithTag("diagnostics_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText(label(R.string.diag_history)).assertIsSelected()
        compose.onNodeWithTag("diagnostics_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText(label(R.string.diag_performance)).assertIsSelected()
        compose.onNodeWithTag("diagnostics_pager").performTouchInput { swipeRight() }
        compose.onNodeWithText(label(R.string.diag_history)).assertIsSelected()
        compose.onNodeWithText(label(R.string.diag_brands)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.diag_brands)).assertIsSelected()
        compose.onNodeWithTag("diagnostics_pager").performTouchInput { swipeRight() }
        compose.onNodeWithText(label(R.string.diag_errors)).assertIsSelected()
        compose.onAllNodesWithText(label(R.string.diag_save)).assertCountEquals(1)
        compose.onAllNodesWithText(label(R.string.diag_share)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.diag_save)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.diag_share)).assertIsDisplayed()
    }

    @Test fun reportPreviewIncludesRecordedOcrWithoutAnOptInAndSupportsHistoryNavigation() {
        var raw: Boolean? = null
        var photoCount = -1
        compose.setContent {
            MaterialTheme {
                OcrDiagnosticsContent(emptyList(), false, false, false, null,
                    {}, {}, { _, _ -> }, { _, _ -> },
                    { _, includeRaw, photos -> raw = includeRaw; photoCount = photos.size },
                    { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.diag_history)).performClick()
        compose.onNode(hasText(label(R.string.diag_empty)) and hasAnyAncestor(hasTestTag("diagnostics_page_1"))).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.diag_save)).performClick()
        compose.onNodeWithText(label(R.string.diag_report_title)).assertExists()
        compose.onNodeWithText(label(R.string.diag_report_content)).assertExists()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(isDialog())).assertCountEquals(0)
        compose.onNodeWithText(label(R.string.diag_continue)).performClick()
        compose.runOnIdle { assertEquals(true, raw); assertEquals(0, photoCount) }
    }

    @Test fun bottomActionsFloatOverAnEdgeToEdgeListAndTheLastScanClearsThem() {
        val scans = (0..19).map { index ->
            DiagnosticScan("scan-$index", 20000L - index, status = "SUCCESS", totalMs = 123,
                productName = "Scan$index", detailedConsent = true)
        }
        compose.setContent {
            MaterialTheme {
                OcrDiagnosticsContent(scans, false, false, false, null,
                    {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.diag_history)).performClick()
        compose.onNodeWithTag("diagnostics_page_1").performScrollToNode(hasText("Scan19"))
        val last = compose.onNode(hasText("Scan19") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("diagnostics_actions").fetchSemanticsNode().boundsInRoot
        val pager = compose.onNodeWithTag("diagnostics_pager").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText(label(R.string.diag_save)).fetchSemanticsNode().boundsInRoot
        assertTrue("List viewport must continue beneath the floating actions", pager.bottom > save.bottom)
        assertTrue("The final scan must settle above the action host", last.bottom <= host.top)
        compose.onNodeWithText(label(R.string.diag_share)).assertIsDisplayed()
    }

    @Test fun enlargedTextStacksBottomActionsWithoutClipping() {
        compose.setContent {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { fontScale = 1.5f }
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density, 1.5f),
            ) {
                MaterialTheme {
                    OcrDiagnosticsContent(emptyList(), false, false, false, null,
                        {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {})
                }
            }
        }
        compose.onNodeWithText(label(R.string.diag_save)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.diag_share)).assertIsDisplayed()
        val save = compose.onNodeWithText(label(R.string.diag_save)).fetchSemanticsNode().boundsInRoot
        val share = compose.onNodeWithText(label(R.string.diag_share)).fetchSemanticsNode().boundsInRoot
        assertTrue("Scaled actions must not overlap", share.top >= save.bottom || share.left >= save.right)
    }

    @Test fun scanDetailsLeadWithMeasurementsAndDiscloseTechnicalMetadataOnDemand() {
        val scan = DiagnosticScan("scan", 20000, status = "SUCCESS", totalMs = 123,
            productName = "Test product", modelHashes = mapOf("model" to "HIDDEN_HASH"))
        compose.setContent {
            MaterialTheme {
                OcrDiagnosticsContent(listOf(scan), false, false, false, null,
                    {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.diag_history)).performClick()
        compose.onNode(hasText("Test product") and hasAnyAncestor(hasTestTag("diagnostics_page_1"))).performClick()
        compose.onNodeWithText(label(R.string.diag_metrics)).assertIsDisplayed()
        compose.onNodeWithText("model: HIDDEN_HASH").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.diag_technical)).performScrollTo().performClick()
        compose.onNodeWithText("model: HIDDEN_HASH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.diag_close)).assertIsDisplayed()
    }

    @Test fun clearingHistoryRequiresConfirmation() {
        var clears = 0
        compose.setContent {
            MaterialTheme {
                OcrDiagnosticsContent(emptyList(), false, false, false, null,
                    {}, { clears++ }, { _, _ -> }, { _, _ -> },
                    { _, _, _ -> }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.diag_clear)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.diag_clear_title)).assertExists()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText(label(R.string.diag_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, clears) }
    }
}
