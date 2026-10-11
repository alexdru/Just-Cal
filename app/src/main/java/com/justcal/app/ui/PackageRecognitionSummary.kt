package com.justcal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.justcal.app.R
import com.justcal.app.ocr.LabelBasis
import com.justcal.app.ocr.PackageScanPrefill
import com.justcal.app.ui.theme.CalSpacing
import kotlin.math.roundToInt

@Composable
fun PackageRecognitionSummary(scan: PackageScanPrefill) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stateLabel = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(CalSpacing.small)) {
        Text(stringResource(R.string.ocr_review_values),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge)
        if (scan.label.basis != LabelBasis.PER_100_G) Text(stringResource(R.string.ocr_basis_unknown),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ocr_confidence, (scan.confidence * 100).roundToInt()),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = stateLabel }) {
            Text(stringResource(if (expanded) R.string.ocr_hide_text else R.string.ocr_show_text))
        }
        if (expanded) Text(scan.rawText, style = MaterialTheme.typography.bodyMedium)
    }
}
