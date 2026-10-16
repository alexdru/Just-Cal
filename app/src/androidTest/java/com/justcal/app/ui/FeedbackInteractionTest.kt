package com.justcal.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.R
import com.justcal.app.camera.ImageSource
import com.justcal.app.camera.ScanMode
import com.justcal.app.camera.ScanRequest
import com.justcal.app.ui.theme.JustCalTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun snackbarRetainsNativeAnnouncementsQueueActionAndDismissal() {
        var first: SnackbarResult? = null
        var second: SnackbarResult? = null
        compose.setContent {
            JustCalTheme {
                val host = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    first = host.showSnackbar("Food saved", "Undo", duration = SnackbarDuration.Indefinite)
                    second = host.showSnackbar("Food deleted", withDismissAction = true, duration = SnackbarDuration.Indefinite)
                }
                CalSnackbarHost(host)
            }
        }
        compose.onNodeWithText("Food saved").assert(
            hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)))
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Food deleted").assertIsDisplayed()
        compose.runOnIdle { assertEquals(SnackbarResult.ActionPerformed, first) }
        compose.onNodeWithContentDescription(label(R.string.close)).performClick()
        compose.onNodeWithText("Food deleted").assertDoesNotExist()
        compose.runOnIdle { assertEquals(SnackbarResult.Dismissed, second) }
    }

    @Test fun conciseLauncherKeepsScanDateModeAndManualActions() {
        var request: ScanRequest? = null
        var source: ImageSource? = null
        var manual = 0
        compose.setContent {
            JustCalTheme {
                var mode by remember { mutableStateOf<ScanMode?>(null) }
                AddFoodLauncher(123, mode, onDismiss = {}, onMode = { mode = it },
                    onManual = { manual++ }, onSource = { scan, imageSource -> request = scan; source = imageSource })
            }
        }
        compose.onNodeWithText(label(R.string.scan_package)).assertIsDisplayed().performClick()
        compose.onNodeWithText(label(R.string.take_photo)).performClick()
        compose.runOnIdle {
            assertEquals(ScanRequest(ScanMode.PACKAGE, 123), request)
            assertEquals(ImageSource.CAMERA, source)
        }
        // The blank trailing part of the whole header also returns to the launcher.
        compose.onNodeWithText(label(R.string.mode_package)).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width - 2f, centerY))
        }
        compose.onNodeWithText(label(R.string.add_manually)).performClick()
        compose.runOnIdle { assertEquals(1, manual) }
        compose.onNodeWithText(label(R.string.scan_meal)).performClick()
        compose.onNodeWithText(label(R.string.choose_photo)).performClick()
        compose.runOnIdle {
            assertEquals(ScanRequest(ScanMode.MEAL, 123), request)
            assertEquals(ImageSource.PHOTO_PICKER, source)
        }
        compose.onNodeWithText(label(R.string.mode_meal)).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width - 2f, centerY))
        }
        compose.onNodeWithText(label(R.string.scan_package)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.scan_meal)).assertIsDisplayed()
    }
}
