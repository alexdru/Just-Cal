package com.justcal.app.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.justcal.app.R

@Composable
internal fun FoodMatchingDemoAction() {
    val context = LocalContext.current
    CalSettingsRow(stringResource(R.string.match_demo_title), R.drawable.ic_science) {
        context.startActivity(Intent(context, FoodMatchingDemoActivity::class.java))
    }
}
