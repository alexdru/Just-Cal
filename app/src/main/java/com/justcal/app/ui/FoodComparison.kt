package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.*
import com.justcal.app.ui.theme.CalSpacing

@Composable
fun FoodComparison(scan: FoodMatchEvidence?, saved: ProductRevision) {
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        Text(stringResource(R.string.match_nutrition_comparison), style = MaterialTheme.typography.headlineSmall)
        val comparable = scan?.basis == MatchNutritionBasis.PER_100_G
        Text(stringResource(if (comparable) R.string.per_100g else R.string.match_basis_unknown),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val table = maxWidth >= 320.dp && LocalConfiguration.current.fontScale <= 1.2f
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.medium)) {
                if (table) Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                    Text(stringResource(R.string.match_parameter), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.match_column_current), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.match_column_saved), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
                val values = listOf(saved.energy, saved.protein, saved.fat, saved.carbs)
                listOf(R.string.energy, R.string.protein, R.string.fat, R.string.carbs).forEachIndexed { index, label ->
                    val original = scan?.nutrients?.get(index)
                    val current = original?.hundredths?.takeIf { comparable }
                    val known = original?.comparable == true && comparable
                    val unit = stringResource(if (index == 0) R.string.kcal else R.string.grams)
                    val currentText = current?.let { nutritionText(it.hundredthsText().toBigDecimal(), 2) + " " + unit }
                        ?: stringResource(R.string.match_missing)
                    val savedText = nutritionText(values[index].hundredthsText().toBigDecimal(), 2) + " " + unit
                    val status = stringResource(if (!known || current == null) R.string.match_missing
                        else if (current == values[index]) R.string.match_same else R.string.match_different)
                    val delta = if (known && current != null && current != values[index]) {
                        val difference = values[index].toBigInteger().subtract(current.toBigInteger()).toBigDecimal().movePointLeft(2)
                        " · " + (if (difference.signum() > 0) "+" else "") + nutritionText(difference, 2) + " " + unit
                    } else ""
                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                        if (table) Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(currentText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(savedText, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        } else {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                            CalValueRow(stringResource(R.string.match_column_current), currentText, stacked = true)
                            CalValueRow(stringResource(R.string.match_column_saved), savedText, stacked = true)
                        }
                        Text(status + delta, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (original?.state == MatchEvidenceState.AI_RECOVERED) Text(stringResource(R.string.match_ai_evidence),
                            style = MaterialTheme.typography.bodySmall)
                        if (original?.state == MatchEvidenceState.UNCERTAIN || original?.state == MatchEvidenceState.CONFLICTING)
                            Text(stringResource(R.string.match_uncertain), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        listOf(R.string.match_brand to (scan?.brand?.text to saved.brand),
            R.string.match_manufacturer to (scan?.manufacturer?.text to saved.manufacturer),
            R.string.match_retailer to (scan?.retailer?.text to saved.retailer),
            R.string.match_delivery to (scan?.deliveryService?.text to saved.deliveryService)).forEach { (label, pair) ->
            if (pair.first != null || pair.second != null) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                CalValueRow(stringResource(R.string.match_column_current), pair.first ?: stringResource(R.string.match_missing))
                CalValueRow(stringResource(R.string.match_column_saved), pair.second ?: stringResource(R.string.match_missing))
            }
        }
        saved.packageMassHundredths?.let { CalValueRow(stringResource(R.string.match_package_mass),
            nutritionText(it.hundredthsText().toBigDecimal(), 2) + " " + stringResource(R.string.grams)) }
    }
}
