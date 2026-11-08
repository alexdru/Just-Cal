package com.justcal.app.ocr

import com.justcal.app.domain.*

/** Reads existing bounded evidence only. This adapter never invokes OCR or an AI runtime. */
fun PackageScanPrefill.matchEvidence(routed: RecognitionDraft? = recognition): FoodMatchEvidence {
    fun number(value: LabelValue?, field: String, recovered: String?): MatchNumber {
        if (field in routed?.conflicts.orEmpty()) return MatchNumber(state = MatchEvidenceState.CONFLICTING)
        val parsed = exactHundredths(value?.value)
        if (parsed != null) return MatchNumber(parsed,
            if (value?.confident == true) MatchEvidenceState.KNOWN else MatchEvidenceState.UNCERTAIN)
        val ai = exactHundredths(recovered)
        return if (ai != null) MatchNumber(ai, MatchEvidenceState.AI_RECOVERED) else MatchNumber()
    }
    return FoodMatchEvidence(
        name = label.draft().name.ifBlank { routed?.name.orEmpty() },
        nameState = if (label.name?.confident == true) MatchEvidenceState.KNOWN else if (routed?.name != null)
            MatchEvidenceState.AI_RECOVERED else MatchEvidenceState.UNCERTAIN,
        nutrients = listOf(number(label.energyKcal, "ENERGY", routed?.energy),
            number(label.proteinGrams, "PROTEIN", routed?.protein), number(label.fatGrams, "FAT", routed?.fat),
            number(label.carbsGrams, "CARBS", routed?.carbs)),
        basis = when (label.basis) {
            LabelBasis.PER_100_G -> MatchNutritionBasis.PER_100_G
            LabelBasis.UNKNOWN, LabelBasis.AMBIGUOUS -> MatchNutritionBasis.UNKNOWN
            LabelBasis.PER_SERVING -> MatchNutritionBasis.UNSUPPORTED
        },
        // Net mass is independent; never use ocrAmount or a VLM mass here.
        packageMassHundredths = label.netGrams?.takeIf { it.confident }?.value?.let(::exactHundredths)?.takeIf { it > 0 },
    )
}
private fun exactHundredths(value: String?): Long? = value?.let {
    runCatching { it.replace(',', '.').toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()?.takeIf { n -> n >= 0 }
}
