package com.justcal.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.justcal.app.ui.theme.CalLayout
import com.justcal.app.ui.theme.CalSpacing
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.justcal.app.R
import com.justcal.app.domain.ConsumedNutrition
import com.justcal.app.domain.AppSettings
import java.math.BigDecimal

@Composable
fun CalIcon(@DrawableRes resource: Int, description: String? = null) {
    Icon(painterResource(resource), description, modifier = Modifier.size(24.dp))
}

@Composable
fun nutritionText(value: BigDecimal, scale: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return formatNutrition(value, scale, locale)
}

@Composable
fun MacroSummary(nutrition: ConsumedNutrition, modifier: Modifier = Modifier, targets: AppSettings? = null) {
    FlowRow(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(CalSpacing.medium),
    ) {
        MacroValue(stringResource(R.string.protein), macroText(nutrition.proteinGrams, targets?.proteinGoalGramsHundredths))
        MacroValue(stringResource(R.string.fat), macroText(nutrition.fatGrams, targets?.fatGoalGramsHundredths))
        MacroValue(stringResource(R.string.carbs), macroText(nutrition.carbsGrams, targets?.carbsGoalGramsHundredths))
    }
}

@Composable
private fun macroText(consumed: BigDecimal, goalHundredths: Long?): AnnotatedString {
    val amount = nutritionText(consumed, 1)
    val unit = stringResource(R.string.grams)
    val text = if (goalHundredths == null) stringResource(R.string.nutrition_with_unit, amount, unit) else {
        val goal = BigDecimal.valueOf(goalHundredths, 2).stripTrailingZeros()
        stringResource(R.string.nutrition_with_unit,
            stringResource(R.string.macro_progress, amount, nutritionText(goal, goal.scale().coerceAtLeast(0))), unit)
    }
    val contextStyle = MaterialTheme.typography.bodyMedium.toSpanStyle()
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    return buildAnnotatedString {
        append(text)
        if (goalHundredths != null) addStyle(contextStyle, amount.length, text.length)
    }
}

@Composable
private fun MacroValue(label: String, value: AnnotatedString) {
    Column(Modifier.widthIn(min = 84.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
    }
}

/** Center the entire viewport before applying its page margin; short windows still scroll. */
fun Modifier.calContent(maxWidth: Dp = CalLayout.diaryWidth): Modifier =
    fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalTopBar(title: @Composable () -> Unit, onBack: (() -> Unit)? = null,
    maxWidth: Dp = CalLayout.diaryWidth) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        TopAppBar(
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()
                .padding(horizontal = if (onBack == null) CalSpacing.small else 0.dp),
            title = title,
            navigationIcon = {
                onBack?.let { IconButton(onClick = it) {
                    CalIcon(R.drawable.ic_back, stringResource(R.string.close))
                } }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
fun EnergyValue(value: BigDecimal, style: TextStyle = MaterialTheme.typography.titleLarge) {
    Column(horizontalAlignment = Alignment.End) {
        Text(nutritionText(value, 0), style = style.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End)
        Text(stringResource(R.string.kcal), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MacroDetail(nutrition: ConsumedNutrition) {
    Text(stringResource(R.string.macro_detail, nutritionText(nutrition.proteinGrams, 1),
        nutritionText(nutrition.fatGrams, 1), nutritionText(nutrition.carbsGrams, 1)),
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One accessible radio target per row; the indicator itself is decorative. */
@Composable
fun SingleChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = CalSpacing.micro),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalSpacing.related)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
