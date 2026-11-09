package com.justcal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.domain.FoodDraft
import com.justcal.app.domain.FoodField
import com.justcal.app.domain.FoodMatchEvidence
import com.justcal.app.domain.MatchNutritionBasis
import com.justcal.app.domain.ProductRevision
import com.justcal.app.domain.hundredthsText
import com.justcal.app.ui.theme.CalSpacing

@Composable
fun FoodComparison(
    currentDraft: FoodDraft,
    scan: FoodMatchEvidence?,
    saved: ProductRevision,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        Text(
            stringResource(R.string.match_nutrition_comparison),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.per_100g),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val table = maxWidth >= 320.dp && LocalConfiguration.current.fontScale <= 1.2f
            val fields = listOf(FoodField.ENERGY, FoodField.PROTEIN, FoodField.FAT, FoodField.CARBS)
            val savedValues = listOf(saved.energy, saved.protein, saved.fat, saved.carbs)
            Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
                if (table) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                        Text(
                            stringResource(R.string.match_parameter),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            stringResource(R.string.match_column_current),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            stringResource(R.string.match_column_saved),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                fields.forEachIndexed { index, field ->
                    val label = when (field) {
                        FoodField.ENERGY -> R.string.energy
                        FoodField.PROTEIN -> R.string.protein
                        FoodField.FAT -> R.string.fat
                        FoodField.CARBS -> R.string.carbs
                        else -> error("Unexpected comparison field")
                    }
                    val unit = stringResource(if (field == FoodField.ENERGY) R.string.kcal else R.string.grams)
                    val currentText = currentDraft.value(field).trim().ifBlank {
                        stringResource(R.string.match_missing)
                    }
                    val savedText = nutritionText(
                        savedValues[index].hundredthsText().toBigDecimal(),
                        2,
                    ) + " " + unit
                    val currentNumber = currentDraft.value(field).trim().replace(',', '.').toBigDecimalOrNull()
                    val savedNumber = savedValues[index].hundredthsText().toBigDecimal()
                    val differs = currentNumber != null && currentNumber != savedNumber
                    val missing = currentNumber == null
                    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.micro)) {
                        if (table) {
                            Row(horizontalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
                                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (missing) currentText else "$currentText $unit",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(savedText, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            }
                        } else {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                            CalValueRow(
                                stringResource(R.string.match_column_current),
                                if (missing) currentText else "$currentText $unit",
                                stacked = true,
                            )
                            CalValueRow(
                                stringResource(R.string.match_column_saved),
                                savedText,
                                stacked = true,
                            )
                        }
                        if (missing || differs) {
                            val note = if (missing) {
                                stringResource(R.string.match_fills_missing)
                            } else {
                                val delta = savedNumber - currentNumber
                                stringResource(
                                    R.string.match_difference,
                                    (if (delta.signum() > 0) "+" else "") + nutritionText(delta, 2),
                                    unit,
                                )
                            }
                            Text(
                                note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val comparable = scan?.basis == MatchNutritionBasis.PER_100_G
        if (!comparable && scan != null) {
            Text(
                stringResource(R.string.match_basis_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        listOf(
            R.string.match_brand to (scan?.brand?.text to saved.brand),
            R.string.match_manufacturer to (scan?.manufacturer?.text to saved.manufacturer),
            R.string.match_delivery to (scan?.deliveryService?.text to saved.deliveryService),
        ).forEach { (label, pair) ->
            if (pair.first != null || pair.second != null) {
                CalValueRow(
                    stringResource(label),
                    listOfNotNull(pair.first, pair.second).distinct().joinToString(" → "),
                )
            }
        }
    }
}
