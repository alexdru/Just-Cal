package com.justcal.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.R
import com.justcal.app.data.DiaryDatabase
import com.justcal.app.data.backup.DiaryBackupRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupReachabilityTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(fontScale: Float) {
        val base: Context = ApplicationProvider.getApplicationContext()
        val root = File(base.cacheDir, "backup-reach-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        val database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        try {
            val vm = DiaryBackupViewModel(DiaryBackupRepository(database, context), context)
            compose.setContent {
                val config = android.content.res.Configuration(LocalConfiguration.current).apply { this.fontScale = fontScale }
                CompositionLocalProvider(LocalConfiguration provides config,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    MaterialTheme { DiaryBackupScreen(vm) {} }
                }
            }
            val export = compose.onNodeWithText(base.getString(R.string.export_diary))
            val import = compose.onNodeWithText(base.getString(R.string.import_diary))
            import.performScrollTo().assertIsDisplayed()
            export.assertIsDisplayed()
            val viewport = compose.onNodeWithTag("backup_scroll").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("backup_actions").fetchSemanticsNode().boundsInRoot
            assertTrue("Actions should be in the lower half on a normal portrait window", actions.top > viewport.center.y)
            assertTrue("Last action must clear system navigation", import.fetchSemanticsNode().boundsInRoot.bottom < viewport.bottom)
            assertTrue("Viewport must extend behind the app bar",
                viewport.top < compose.onNodeWithTag("cal_top_bar").fetchSemanticsNode().boundsInRoot.bottom)
        } finally { database.close(); root.deleteRecursively() }
    }
    @Test fun actionsAreReachableAndEdgeToEdge() = verify(1f)
    @Test fun enlargedTextKeepsExportAndImportReachable() = verify(1.5f)
}
