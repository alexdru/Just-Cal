package com.justcal.app

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.justcal.app.domain.Appearance
import android.content.res.Configuration
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.justcal.app.ui.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.animation.ValueAnimator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.justcal.app.ui.theme.CalMotion
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.justcal.app.ui.JustCalApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val settingsViewModel by viewModels<SettingsViewModel>()
    override fun onStop() {
        settingsViewModel.flushEdits()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var ready = false
        var splashVisible by mutableStateOf(true)
        splash.setKeepOnScreenCondition { !ready }
        // Readiness must not depend on a draw: SplashScreen itself defers that draw.
        lifecycleScope.launch {
            settingsViewModel.state.first { !it.loading }
            ready = true
        }
        splash.setOnExitAnimationListener { provider ->
            fun finish() {
                splashVisible = false
                provider.remove()
                val light = when (settingsViewModel.state.value.draft.appearance) {
                    Appearance.LIGHT -> true
                    Appearance.DARK -> false
                    Appearance.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            // The Android 11 compatibility splash adds no exit dwell on older hardware.
            if (android.os.Build.VERSION.SDK_INT >= 31 && ValueAnimator.areAnimatorsEnabled()) {
                provider.view.animate().alpha(0f).scaleX(1.03f).scaleY(1.03f)
                    .setDuration(CalMotion.splashExitMillis).withEndAction { finish() }.start()
            } else finish()
        }
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent { JustCalApp(splashVisible = splashVisible) }
    }
}
