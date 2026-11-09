package com.justcal.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodMatchingDemoTest {
    @get:Rule val compose = createAndroidComposeRule<FoodMatchingDemoActivity>()
    private lateinit var labels: android.content.res.Resources
    @Before fun captureLabels() { labels = compose.activity.resources }
    private fun label(id: Int) = labels.getString(id)
    private fun activate(id: Int) {
        compose.onNodeWithText(label(id)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    @Test fun chooserOpensUnifiedEditorAndSaveStaysInDisposableSession() {
        activate(R.string.match_demo_partial)
        compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.match_use)).fetchSemanticsNodes().isNotEmpty() }
        val saveBounds = compose.onNodeWithText(label(R.string.save_food)).fetchSemanticsNode().boundsInRoot
        val screenCenter = compose.activity.resources.displayMetrics.heightPixels / 2f
        assertTrue("The pinned Save action must stay below the content viewport center", saveBounds.top > screenCenter)
        compose.onNodeWithText(label(R.string.add_food)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.match_use)).onFirst()
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNode(hasText(label(R.string.amount)) and hasSetTextAction())
            .performScrollTo().performTextReplacement("37.51")
        compose.onNode(hasText(label(R.string.amount)) and hasSetTextAction()).assertTextContains("37.51")
        compose.onNodeWithText(label(R.string.save_food)).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.match_demo_saved)).fetchSemanticsNodes().isNotEmpty() }
        activate(R.string.match_demo_back)
        compose.waitUntil(5000) { compose.onAllNodesWithText(label(R.string.match_demo_conflict)).fetchSemanticsNodes().isNotEmpty() }
        activate(R.string.match_demo_conflict)
        compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.match_use)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(label(R.string.match_conflicting), substring = true)).assertExists()
    }
    @Test fun unifiedEditorAndManualDraftSurviveActivityRecreation() {
        val scenario = androidx.test.core.app.ActivityScenario.launch<FoodMatchingDemoActivity>(
            android.content.Intent(compose.activity, FoodMatchingDemoActivity::class.java).putExtra("scenario", 3))
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.save_food)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(label(R.string.energy)) and hasSetTextAction()).assertTextContains("222")
            compose.onNode(hasText(label(R.string.amount)) and hasSetTextAction()).assertTextContains("37,51")
            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.save_food)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(hasText(label(R.string.energy)) and hasSetTextAction()).assertTextContains("222")
            compose.onNode(hasText(label(R.string.amount)) and hasSetTextAction()).assertTextContains("37,51")
        } finally { scenario.close() }
    }
    @Test fun separateSessionsStartWithOnlyFixtures() = runBlocking {
        val first = FoodMatchingDemoSession(compose.activity.applicationContext, "Vanilla almond yogurt", "Chicken sesame salad")
        val second = FoodMatchingDemoSession(compose.activity.applicationContext, "Vanilla almond yogurt", "Chicken sesame salad")
        val store = androidx.lifecycle.ViewModelStore()
        store.put("first", first); store.put("second", second)
        try {
            first.ready.await(); second.ready.await()
            val entry = com.justcal.app.domain.DiaryEntry(name = "Only in first session",
                per100g = com.justcal.app.domain.NutritionPer100g(1, 1, 1, 1), eatenGramsHundredths = 1,
                dayEpoch = 1, createdAtMillis = 1)
            first.diary.save(entry)
            assertEquals(4, first.diary.observeAll().first().size)
            assertEquals(3, second.diary.observeAll().first().size)
        } finally { store.clear() }
    }
}
