package com.justcal.app.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justcal.app.ocr.LabelRegionSelection
import com.justcal.app.ocr.PackageOcrResult
import java.io.File
import java.math.BigDecimal
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Scores saved native runs against separately reviewed print; never runs inference or changes admission. */
@RunWith(AndroidJUnit4::class)
class PackagePhotoBatchQualityDeviceTest {
    @Test fun compareSavedLocalPackagePhotos() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("packagePhotoBatch") == "true")
        val runId = args.getString("batchRun") ?: "2026-10-28"
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val external = requireNotNull(context.getExternalFilesDir(null))
        val root = File(external, "package-batch-results/$runId")
        val reference = read(File(external, "package-batch-reference.json"))
        val references = reference.getValue("cases").jsonArray.map { it.jsonObject }
        require(references.isNotEmpty())
        require(reference.getValue("fieldOrder") == JsonArray(FIELDS.map(::JsonPrimitive)))
        require(references.all { it.getValue("values").jsonArray.size == FIELDS.size })
        val fixtures = File(external, "package-batch-fixtures")
        val supplied = fixtures.walkTopDown().filter {
            it.isFile && (it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp"))
        }.map { it.relativeTo(fixtures).invariantSeparatorsPath }.toSet()
        val reviewed = references.mapTo(mutableSetOf()) { it.getValue("fixture").jsonPrimitive.content }
        require((reviewed.size == references.size) && (reviewed == supplied)) {
            "Reference must cover the exact current photo inbox; do not silently score a subset"
        }
        val variants = (args.getString("batchVariants") ?: "ocr,gemma4-e2b,paddleocr-vl16").split(',')
        val catalog = ModelCatalog(context)
        val summaries = variants.map { variant ->
            require(variant.matches(Regex("[A-Za-z0-9-]+")))
            val macroTask = variant == "gemma4-e2b-macros"
            val sourceVariant = if (macroTask) "gemma4-e2b-crop" else variant
            val cases = references.map { expected ->
                val fixture = expected.getValue("fixture").jsonPrimitive.content
                val baseline = read(File(root, "ocr/$fixture.json"))
                val report = read(File(root, "$sourceVariant/$fixture.json"))
                val hash = expected.getValue("inputSha256")
                require((report["inputSha256"] == hash) && (baseline["inputSha256"] == hash)) { "Source mismatch: $fixture" }
                val engine = report.getValue("engine").jsonPrimitive.content
                require(engine == sourceVariant.removeSuffix("-crop")) { "Engine mismatch: $fixture" }
                val artifact = report["artifactHash"]?.jsonPrimitive?.contentOrNull?.let(catalog::matching)
                val currentTransport = (report["runtimeVersion"]?.jsonPrimitive?.content == AI_RUNTIME_VERSION) &&
                    (report["cropProfileVersion"]?.jsonPrimitive?.content == LabelImageCropper.PROFILE_VERSION)
                val currentPackageContract = if (macroTask) currentTransport &&
                    (artifact?.profile == ModelTaskProfile.GENERAL_VISION) &&
                    (report["taskVersion"]?.jsonPrimitive?.content == "package-photo-crop-json-v2") &&
                    (report["prompt"]?.jsonPrimitive?.content == PackageMacroAssistance.PROMPT) &&
                    (report["structured"]?.jsonPrimitive?.boolean == true)
                else currentTransport && (engine == "paddleocr-vl16") &&
                    (artifact?.profile == ModelTaskProfile.LABEL_TRANSCRIPTION) &&
                    (report["taskVersion"]?.jsonPrimitive?.content == TranscriptionNutrition.PROFILE_VERSION)
                val nutrition = report["nutritionJson"] as? JsonObject
                val hybrid = report["hybridDraft"] as? JsonObject
                val editor = baseline.getValue("editor").jsonObject
                val original = EDITOR_FIELDS.map { number(editor[it]) }
                val crop = report["inputMode"]?.jsonPrimitive?.content == "OCR_NUTRITION_CROP"
                val ocrResult = Json.decodeFromJsonElement(PackageOcrResult.serializer(), baseline.getValue("ocrResult"))
                val ocrLabel = Json.decodeFromJsonElement(com.justcal.app.ocr.ParsedNutritionLabel.serializer(), baseline.getValue("parsedLabel"))
                val wouldEnrich = crop && !LabelRegionSelection.complete(ocrResult) &&
                    (!macroTask || PackageMacroAssistance.shouldRun(ocrLabel))
                val macroDraft = if (macroTask) runCatching {
                    PackageMacroAssistance.merge(ocrLabel, report.getValue("raw").jsonPrimitive.content).draft()
                }.getOrNull() else null
                val candidate = if (macroTask) macroDraft?.let {
                    listOf(it.energy, it.protein, it.fat, it.carbs, it.amount).map(String::toBigDecimalOrNull)
                } ?: original else when (engine) {
                    "ocr" -> original
                    "paddleocr-vl16" -> if (hybrid == null) original else
                        listOf("energy", "protein", "fat", "carbs", "ocrAmount").map { number(hybrid[it]) }
                    else -> GEMMA_FIELDS.map { number(nutrition?.get(it)) }
                }
                val actual = if (((engine == "paddleocr-vl16") || macroTask) && !wouldEnrich) original else candidate
                if (macroTask) {
                    for (i in original.indices) {
                        if (original[i] != null) check(equal(actual[i], original[i])) { "Changed existing OCR: $fixture" }
                    }
                    for (i in listOf(0, 4)) check(actual[i] == original[i]) { "Changed energy/amount: $fixture" }
                    if (macroDraft != null) check(macroDraft.name == editor.getValue("name").jsonPrimitive.content)
                }
                val basisCorrect = if (macroTask) ocrLabel.basis == com.justcal.app.ocr.LabelBasis.PER_100_G else when (engine) {
                    "ocr" -> baseline["parsedLabel"]?.jsonObject?.get("basis")?.jsonPrimitive?.content == "PER_100_G"
                    "paddleocr-vl16" -> if ((hybrid == null) || !wouldEnrich) {
                        baseline["parsedLabel"]?.jsonObject?.get("basis")?.jsonPrimitive?.content == "PER_100_G"
                    } else number(hybrid["basisGrams"])?.compareTo(BigDecimal("100")) == 0
                    else -> number(nutrition?.get("nutritionBasisGrams"))?.compareTo(BigDecimal("100")) == 0
                }
                val targets = expected.getValue("values").jsonArray.map(::number)
                val missing = mutableListOf<String>()
                val wrong = mutableListOf<String>()
                val invented = mutableListOf<String>()
                val recovered = mutableListOf<String>()
                val removed = mutableListOf<String>()
                var correct = 0
                for (i in FIELDS.indices) {
                    val value = actual[i]
                    val target = targets[i]
                    when {
                        (target == null) && (value != null) -> invented += FIELDS[i]
                        target == null -> Unit
                        value == null -> missing += FIELDS[i]
                        equal(value, target) -> correct++
                        else -> wrong += FIELDS[i]
                    }
                    if ((i < 4) && basisCorrect && (original[i] == null) && (target != null) && equal(value, target)) {
                        recovered += FIELDS[i]
                    }
                    if ((target != null) && (original[i] != null) && equal(original[i], target) && !equal(value, target)) {
                        removed += FIELDS[i]
                    }
                }
                val independent = expected.getValue("evidenceGroup") == expected.getValue("fixture")
                buildJsonObject {
                    put("fixture", fixture)
                    put("independent", independent)
                    put("engine", engine)
                    put("sourceVariant", sourceVariant)
                    put("evaluatedTaskVersion", if (macroTask) JsonPrimitive(PackageMacroAssistance.PROFILE_VERSION) else report.getValue("taskVersion"))
                    put("wouldRunPackageFallback", wouldEnrich)
                    put("currentPackageTaskContract", currentPackageContract)
                    put("candidateActual", JsonArray(candidate.map { it?.toPlainString()?.let(::JsonPrimitive) ?: JsonNull }))
                    put("inputMode", report.getValue("inputMode"))
                    put("outcome", report.getValue("outcome"))
                    put("expected", expected.getValue("values"))
                    put("actual", JsonArray(actual.map { it?.toPlainString()?.let(::JsonPrimitive) ?: JsonNull }))
                    put("visible", targets.count { it != null })
                    put("correct", correct)
                    put("wrongFields", JsonArray(wrong.map(::JsonPrimitive)))
                    put("missingFields", JsonArray(missing.map(::JsonPrimitive)))
                    put("inventedAbsentFields", JsonArray(invented.map(::JsonPrimitive)))
                    put("basisCorrect", basisCorrect)
                    put("recoveredMissingNutritionFields", JsonArray(recovered.map(::JsonPrimitive)))
                    put("removedCorrectOcrFields", JsonArray(removed.map(::JsonPrimitive)))
                    put("productionCropRecoveries", if (((engine == "paddleocr-vl16") || macroTask) && wouldEnrich && independent) recovered.size else 0)
                    put("elapsedMs", report.getValue("elapsedMs"))
                }
            }
            val independent = cases.filter { it.getValue("independent").jsonPrimitive.boolean }
            fun totals(rows: List<JsonObject>) = buildJsonObject {
                put("photos", rows.size)
                for (key in listOf("visible", "correct", "productionCropRecoveries")) {
                    put(key, rows.sumOf { it.getValue(key).jsonPrimitive.int })
                }
                for (key in listOf(
                    "wrongFields",
                    "missingFields",
                    "inventedAbsentFields",
                    "recoveredMissingNutritionFields",
                    "removedCorrectOcrFields",
                )) {
                    put(key, rows.sumOf { it.getValue(key).jsonArray.size })
                }
                put(
                    "unsafeNutritionBasisPhotos",
                    rows.count {
                        !it.getValue("basisCorrect").jsonPrimitive.boolean &&
                            it.getValue("actual").jsonArray.subList(0, 4).any { value -> value != JsonNull }
                    },
                )
            }
            val unique = totals(independent)
            val numericalMinimum = ((variant == "paddleocr-vl16") || macroTask) &&
                (unique.getValue("productionCropRecoveries").jsonPrimitive.int >= 2) &&
                (unique.getValue("wrongFields").jsonPrimitive.int == 0) &&
                (unique.getValue("inventedAbsentFields").jsonPrimitive.int == 0) &&
                (unique.getValue("unsafeNutritionBasisPhotos").jsonPrimitive.int == 0)
            val candidateGate = numericalMinimum &&
                (unique.getValue("removedCorrectOcrFields").jsonPrimitive.int == 0) &&
                independent.all { it.getValue("currentPackageTaskContract").jsonPrimitive.boolean }
            buildJsonObject {
                put("variant", variant)
                put("allFiles", totals(cases))
                put("independentViews", unique)
                put("minimumNumericalGatePassed", numericalMinimum)
                put("packageCandidateGatePassed", candidateGate)
                put("cases", JsonArray(cases))
            }
        }
        val report = buildJsonObject {
            put("schemaVersion", 1)
            put("runId", runId)
            put("method", "Exact BigDecimal comparison against visible print. OCR uses its editor; Paddle uses the actual hybrid only where the production router would request a reliable crop; complete OCR, no crop and failures retain baseline; Gemma crop/full rows score standalone JSON. The separate gemma4-e2b-macros row replays those exact native responses through the production missing-macro consumer; energy, name, mass and existing OCR fields cannot change. Correct OCR fields removed by a candidate also prevent recommendation.")
            put("fieldOrder", reference.getValue("fieldOrder"))
            put("variants", JsonArray(summaries))
        }
        File(root, "quality-comparison.json").writeText(report.toString())
        summaries.firstOrNull { it["variant"]?.jsonPrimitive?.content == "gemma4-e2b-macros" }?.let { quality ->
            val native = read(File(root, "gemma4-e2b-crop/" + references.first().getValue("fixture").jsonPrimitive.content + ".json"))
            val gate = buildJsonObject {
                put("taskVersion", PackageMacroAssistance.PROFILE_VERSION)
                put("nativeTransportTaskVersion", "package-photo-crop-json-v2")
                put("artifactHash", native.getValue("artifactHash"))
                put("runtimeVersion", AI_RUNTIME_VERSION)
                put("cropProfileVersion", LabelImageCropper.PROFILE_VERSION)
                put("consumerAppVersion", com.justcal.app.BuildConfig.VERSION_NAME)
                put("prompt", PackageMacroAssistance.PROMPT)
                put("method", "Replay of all 27 saved real native responses through the exact production macro-only consumer. No new inference is claimed for this replay. Separate fresh production-flow tests verify router/editor integration.")
                put("gatePassed", quality.getValue("packageCandidateGatePassed"))
                put("quality", quality)
            }
            File(root, "package-macros-gate.json").writeText(gate.toString())
            if (args.getString("batchRequireQuality") == "true") {
                assertTrue("Gemma missing-macro quality admission", quality.getValue("packageCandidateGatePassed").jsonPrimitive.boolean)
            }
        }
        assertEquals(references.size * variants.size, summaries.sumOf { it.getValue("cases").jsonArray.size })
    }

    private fun read(file: File) = Json.parseToJsonElement(file.readText()).jsonObject

    private fun number(element: JsonElement?): BigDecimal? =
        (element as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toBigDecimalOrNull()

    private fun equal(first: BigDecimal?, second: BigDecimal?) =
        (first != null) && (second != null) && (first.compareTo(second) == 0)

    companion object {
        private val FIELDS = listOf("energyKcal", "proteinGrams", "fatGrams", "carbsGrams", "netGrams")
        private val EDITOR_FIELDS = listOf("energy", "protein", "fat", "carbs", "amount")
        private val GEMMA_FIELDS = listOf("caloriesKcal", "proteinGrams", "fatGrams", "carbohydrateGrams", "packageWeightGrams")
    }
}
