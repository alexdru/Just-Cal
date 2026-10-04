package com.justcal.app.ui

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.justcal.app.R

/**
 * Debug-only visual check of the actual vector and platform adaptive drawable.
 * The 108 dp layer is cropped to its central 72 dp viewport, as in a launcher.
 */
@Preview(name = "Launcher masks and monochrome", widthDp = 440, heightDp = 290)
@Composable
private fun LauncherIconPreview() {
    Column {
        LauncherMaskRow(dark = false)
        LauncherMaskRow(dark = true)
    }
}

@Composable
private fun LauncherMaskRow(dark: Boolean) {
    val resources = LocalResources.current
    val icon = remember(resources) { resources.getDrawable(R.mipmap.ic_launcher, null) as AdaptiveIconDrawable }
    val backdrop = if (dark) Color(0xFF121812) else Color(0xFFF5F6F2)
    val label = if (dark) Color.White else Color.Black
    val themedBackground = if (dark) Color(0xFF244D32) else Color(0xFFD9F294)
    val themedForeground = if (dark) Color(0xFFD9F294) else Color(0xFF244D32)
    Row(
        modifier = Modifier.fillMaxWidth().background(backdrop).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MaskSample("Circle", label, CircleShape, icon)
        MaskSample("Rounded", label, RoundedCornerShape(18.dp), icon)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device", color = label)
            Canvas(Modifier.size(72.dp)) {
                icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawIntoCanvas { icon.draw(it.nativeCanvas) }
            }
        }
        MaskSample("Themed", label, CircleShape, icon, themedBackground, themedForeground)
    }
}

@Composable
private fun MaskSample(
    label: String,
    labelColor: Color,
    mask: Shape,
    icon: AdaptiveIconDrawable,
    themedBackground: Color? = null,
    themedForeground: Color? = null,
) {
    val sampleIcon = remember(icon) { icon.constantState!!.newDrawable().mutate() as AdaptiveIconDrawable }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = labelColor)
        Canvas(Modifier.size(72.dp).clip(mask)) {
            drawRect(themedBackground ?: Color(0xFF244D32))
            // Use the explicit monochrome layer on supported preview devices.
            val monochrome = if (Build.VERSION.SDK_INT >= 33) sampleIcon.monochrome else sampleIcon.foreground
            val foreground = if (themedForeground != null) requireNotNull(monochrome) else sampleIcon.foreground
            foreground.clearColorFilter()
            themedForeground?.let { foreground.setTint(it.toArgb()) }
            val overscan = size.width / 4f
            foreground.setBounds(
                -overscan.toInt(),
                -overscan.toInt(),
                (size.width + overscan).toInt(),
                (size.height + overscan).toInt(),
            )
            drawIntoCanvas { foreground.draw(it.nativeCanvas) }
        }
    }
}
