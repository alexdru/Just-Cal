package com.justcal.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justcal.app.R
import com.justcal.app.data.DiaryDatabase
import com.justcal.app.data.backup.DiaryBackup
import com.justcal.app.data.backup.DiaryBackupRepository
import com.justcal.app.data.backup.DiaryBackupSerializer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupReplacementConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replaceRequiresSeparateWarningAndAcknowledgementBeforeWritingRecovery() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "backup-ui-test-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        val repository = DiaryBackupRepository(database, context)
        val vm = DiaryBackupViewModel(repository, context)
        val file = File(directory, "import.json")
        file.outputStream().use { DiaryBackupSerializer.write(
            DiaryBackup(exportedAtMillis = 1, appVersion = "test", entries = emptyList()), it) }
        fun label(id: Int) = base.getString(id)
        try {
            compose.setContent { MaterialTheme { DiaryBackupScreen(vm) {} } }
            compose.runOnIdle { vm.preview(Uri.fromFile(file)) }
            compose.waitUntil(5000) { vm.state.value.preview != null && !vm.state.value.busy }
            compose.onNodeWithText(label(R.string.replace_all)).performClick()
            compose.onNodeWithText(label(R.string.replace_acknowledge)).assertExists()
            val confirm = compose.onNode(hasText(label(R.string.replace_all)) and hasClickAction())
            confirm.assertIsNotEnabled()
            runBlocking { assertFalse(repository.recoveryAvailable()) }
            compose.onNode(isToggleable()).performClick()
            confirm.assertIsEnabled().performClick()
            compose.waitUntil(5000) { vm.state.value.result != null && !vm.state.value.busy }
            compose.onNodeWithText(label(R.string.export_recovery)).assertExists()
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }
}
