package com.justcal.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.room.withTransaction
import com.justcal.app.R
import com.justcal.app.data.*
import com.justcal.app.domain.*
import com.justcal.app.domain.Retention
import com.justcal.app.ocr.*
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import com.justcal.app.ui.theme.JustCalTheme
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Each editor Activity owns a fresh, isolated Room graph. No production DI or diagnostics. */
class FoodMatchingDemoActivity : AppCompatActivity() {
    private val scenario get() = intent.getIntExtra("scenario", -1)
    private val session by viewModels<FoodMatchingDemoSession> {
        viewModelFactory { initializer {
            FoodMatchingDemoSession(applicationContext, getString(R.string.match_demo_yogurt),
                getString(R.string.match_demo_salad))
        } }
    }
    private val editor by viewModels<EditorViewModel> {
        viewModelFactory { initializer {
            val handle = createSavedStateHandle()
            if (scenario == 3 && handle.get<Boolean>("hasDraft") != true) {
                handle["ENERGY"] = "222"
                handle["AMOUNT"] = "37,51"
                handle["hasDraft"] = true
                handle["matchMemory"] = kotlinx.serialization.json.Json.encodeToString(MatchMemory.serializer(),
                    MatchMemory(dirty = setOf(FoodField.ENERGY), retention = Retention.NEW_PRODUCT))
            }
            EditorViewModel(session.diary, handle, Clock.systemUTC(), null,
                LocalDate.now().toEpochDay(), session.scan(scenario), catalog = session.catalog)
                .also(session::register)
        } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            JustCalTheme {
                if (scenario !in 0..3) DemoChooser(onBack = ::finish) { selected ->
                    startActivity(Intent(this, FoodMatchingDemoActivity::class.java).putExtra("scenario", selected))
                } else {
                    var loaded by remember { mutableStateOf(false) }
                    var failed by remember { mutableStateOf(false) }
                    LaunchedEffect(session) {
                        try { session.ready.await(); loaded = true }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failed = true
                        }
                    }
                    if (!loaded) DemoStatus(if (failed) R.string.match_demo_error else null, ::finish)
                    else {
                        LaunchedEffect(editor) {
                            val readyState = editor.state.first { !it.matching.searching && it.matching.candidates.isNotEmpty() }
                            if (readyState.matching.memory.inspectedKey == null) {
                                val candidates = readyState.matching.candidates
                                val target = if (scenario == 1) candidates.firstOrNull { it.match?.category == FoodMatchCategory.CONFLICTING }
                                    else if (scenario == 2) candidates.firstOrNull { it.historyEntryUuid != null } else candidates.firstOrNull()
                                target?.let { editor.inspectMatch(it.key) }
                            }
                        }
                        DemoEditor(editor, session.scan(scenario), ::finish)
                    }
                }
            }
        }
    }
}

private val demoScenarios = listOf(R.string.match_demo_partial, R.string.match_demo_conflict,
    R.string.match_demo_history, R.string.match_demo_manual)

@Composable
private fun DemoChooser(onBack: () -> Unit, onScenario: (Int) -> Unit) {
    Scaffold(topBar = { CalTopBar(onBack = onBack, title = { Text(stringResource(R.string.match_demo_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().calContent(CalLayout.readingWidth).verticalScroll(rememberScrollState())
            .padding(padding).consumeWindowInsets(padding).padding(CalSpacing.page),
            verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
            Text(stringResource(R.string.match_demo_notice))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                demoScenarios.forEachIndexed { index, title ->
                    CalSettingsRow(stringResource(title), R.drawable.ic_package, index = index, count = demoScenarios.size) {
                        onScenario(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoStatus(message: Int?, onBack: () -> Unit) {
    Scaffold(topBar = { CalTopBar(onBack = onBack, title = { Text(stringResource(R.string.match_demo_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().calContent(CalLayout.readingWidth).verticalScroll(rememberScrollState())
            .padding(padding).consumeWindowInsets(padding).padding(CalSpacing.page)) {
            if (message == null) CalLoadingRow() else Text(stringResource(message))
            TextButton(onClick = onBack) { Text(stringResource(R.string.match_demo_back)) }
        }
    }
}

@Composable
private fun DemoEditor(editor: EditorViewModel, scan: PackageScanPrefill, onBack: () -> Unit) {
    val state by editor.state.collectAsState()
    if (state.completed) DemoStatus(R.string.match_demo_saved, onBack)
    else Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(R.string.match_demo_notice), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f)) {
            EditorScreen(state, false, editor::change, editor::save, {}, onBack, {}, scan = scan,
                onMatchAction = editor::matchingAction, onInspectMatch = editor::inspectMatch,
                onReuseMatch = editor::reuseDuplicate, onRetention = editor::setRetention,
                onProductDetails = editor::changeProductDetails)
        }
    }
}

internal class FoodMatchingDemoSession(context: Context, private val yogurt: String, private val salad: String) : ViewModel() {
    // Intentionally never opened through AppModule: even an accidental Save stays in memory.
    private val database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java)
        .addCallback(DiaryDatabase.CALLBACK).build()
    val diary = RoomDiaryRepository(database.diaryDao(), database)
    val catalog = RoomFoodCatalogRepository(database)
    private val editorJobs = mutableListOf<Job>()
    private val sessionJob = requireNotNull(viewModelScope.coroutineContext[Job])
    val ready = viewModelScope.async(Dispatchers.IO) {
        database.withTransaction {
            suspend fun product(fat: Long) {
                val entry = DiaryEntry(name = yogurt, per100g = NutritionPer100g(12000, 400, fat, 1800),
                    eatenGramsHundredths = 10000, dayEpoch = LocalDate.now().minusDays(1).toEpochDay(), createdAtMillis = 1)
                catalog.save(entry, CatalogSave(UUID(3, fat).toString(), UUID(1, fat).toString(), UUID(2, fat).toString(), Retention.NEW_PRODUCT))
            }
            product(500)
            product(2500)
            diary.save(DiaryEntry(name = salad, per100g = NutritionPer100g(17500, 1300, 950, 800),
                eatenGramsHundredths = 25000, dayEpoch = LocalDate.now().minusDays(2).toEpochDay(), createdAtMillis = 2))
        }
    }
    fun scan(scenario: Int): PackageScanPrefill {
        val history = scenario == 2
        fun value(text: String) = LabelValue(text, 1f, "DEBUG FIXTURE")
        return PackageScanPrefill(ParsedNutritionLabel(name = value(if (history) salad else yogurt),
            energyKcal = value(if (history) "175" else "120"),
            proteinGrams = value(if (history) "13" else "4"),
            fatGrams = if (scenario == 1) value("5") else null,
            carbsGrams = value(if (history) "8" else "18"),
            basis = LabelBasis.PER_100_G), "DEBUG FIXTURE", 1f, OcrMetrics(0, 0, 0, 0))
    }
    fun register(editor: EditorViewModel) { editor.viewModelScope.coroutineContext[Job]?.let(editorJobs::add) }
    override fun onCleared() {
        val jobs = editorJobs.toList() + sessionJob
        CoroutineScope(Dispatchers.IO).launch {
            jobs.joinAll()
            database.close()
        }
    }
}
