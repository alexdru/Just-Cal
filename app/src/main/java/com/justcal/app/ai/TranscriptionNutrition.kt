package com.justcal.app.ai

import com.justcal.app.domain.*
import com.justcal.app.ocr.*

object TranscriptionNutrition {
    const val PROFILE_VERSION = "label-transcription-v1"
    fun merge(ocr: ParsedNutritionLabel, raw: String): RecognitionDraft {
        val vlm = NutritionLabelParser.parseUnscoredTranscription(raw)
        val base = ocr.draft()
        val conflicts = mutableSetOf<String>()
        val sameBasis = ocr.basis == LabelBasis.PER_100_G && vlm.basis == LabelBasis.PER_100_G
        fun field(key: String, original: String, suggestion: LabelValue?): String? {
            if (!sameBasis) return original.takeIf(String::isNotBlank)
            val candidate = suggestion?.value ?: return original.takeIf(String::isNotBlank)
            if (original.isNotBlank() && original.toBigDecimalOrNull()?.compareTo(candidate.toBigDecimalOrNull() ?: return null) != 0) {
                conflicts += key
                return null
            }
            return original.ifBlank { candidate }
        }
        val energy = field("ENERGY", base.energy, vlm.energyKcal)
        val protein = field("PROTEIN", base.protein, vlm.proteinGrams)
        val fat = field("FAT", base.fat, vlm.fatGrams)
        val carbs = field("CARBS", base.carbs, vlm.carbsGrams)
        return RecognitionDraft(
            name = base.name.takeIf(String::isNotBlank), energy = energy, protein = protein, fat = fat, carbs = carbs,
            basisGrams = if (ocr.basis == LabelBasis.PER_100_G) "100" else null,
            source = "OCR_WITH_VLM_SUGGESTIONS", evidence = raw.take(16_000), conflicts = conflicts,
            ocrAmount = base.amount.takeIf(String::isNotBlank), suggestedPackageGrams = vlm.netGrams?.value,
        )
    }
}
