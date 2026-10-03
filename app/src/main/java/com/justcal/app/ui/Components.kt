package com.justcal.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.ConsumedNutrition
import com.justcal.app.domain.display
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

@Composable
fun CalIcon(@DrawableRes resource: Int, description: String? = null) {
    Icon(painterResource(resource), description, modifier = Modifier.size(24.dp))
}

@Composable
fun nutritionText(value: BigDecimal, scale: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return value.display(scale).replace('.', DecimalFormatSymbols(locale).decimalSeparator)
}

@Composable
fun MacroSummary(nutrition: ConsumedNutrition, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MacroValue(stringResource(R.string.protein), nutritionText(nutrition.proteinGrams, 1))
        MacroValue(stringResource(R.string.fat), nutritionText(nutrition.fatGrams, 1))
        MacroValue(stringResource(R.string.carbs), nutritionText(nutrition.carbsGrams, 1))
    }
}

@Composable
private fun MacroValue(label: String, value: String) {
    Column(Modifier.widthIn(min = 84.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value ${stringResource(R.string.grams)}", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
