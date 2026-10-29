package com.justcal.app.ai

import com.justcal.app.ocr.*
import java.net.URI
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ModelPolicyTest {
    @Test fun resumeAcceptsOnlyExactRangeAndUnchangedValidator() {
        assertTrue(ModelResumePolicy.append(206, 10, "bytes 10-99/100", 100, "\"a\"", "\"a\""))
        assertFalse(ModelResumePolicy.append(200, 10, null, 100, "\"b\"", "\"a\""))
        listOf(
            { ModelResumePolicy.append(206, 10, "bytes 0-89/100", 100, "\"a\"", "\"a\"") },
            { ModelResumePolicy.append(206, 10, "bytes 10-99/101", 100, "\"a\"", "\"a\"") },
            { ModelResumePolicy.append(206, 10, "bytes 10-99/100", 100, "\"b\"", "\"a\"") },
            { ModelResumePolicy.append(416, 10, "bytes */100", 100, "\"a\"", "\"a\"") },
            { ModelResumePolicy.append(206, 10, null, 100, null, null) },
        ).forEach { assertTrue(runCatching(it).isFailure) }
    }

    @Test fun redirectsNeverAllowCleartextCredentialsOrLookalikeHosts() {
        assertTrue(ModelResumePolicy.trusted(URI("https://huggingface.co/a/b")))
        assertTrue(ModelResumePolicy.trusted(URI("https://us.aws.cdn.hf.co/xet-bridge-us/artifact")))
        listOf("http://huggingface.co/a", "https://huggingface.co.evil.test/a",
            "https://user:password@huggingface.co/a", "https://huggingface.co:444/a",
            "file:///private/model", "https://localhost/a").forEach {
            assertFalse(it, ModelResumePolicy.trusted(URI(it)))
        }
    }

    @Test fun backendIntersectionAndFatalFailuresCannotTriggerFallback() {
        val capabilities = ModelCapabilities(true, 4096, false, null, listOf("CPU", "GPU"), listOf("CPU"))
        assertEquals(listOf(AiBackend.CPU), BackendSelector.candidates(capabilities))
        assertTrue(BackendSelector.mayFallback(IllegalStateException("OpenCL delegate unavailable")))
        listOf(CancellationException(), OutOfMemoryError(), IllegalArgumentException("Invalid model"),
            IllegalStateException("GPU allocation failed"), IllegalStateException("Unknown error")).forEach {
            assertFalse(BackendSelector.mayFallback(it))
        }
        assertTrue(BackendSelector.runtimeSupported("0.18.1"))
        assertFalse(BackendSelector.runtimeSupported("0.19.0"))
        assertFalse(BackendSelector.runtimeSupported("unrecognized"))
    }

    @Test fun coordinateMappingRestoresOriginalPixelsForEveryRotation() {
        val original = OcrPoint(20f, 30f)
        assertEquals(original, OcrCoordinateTransform.toPrepared(original, 100, 200, 0))
        assertEquals(original, OcrCoordinateTransform.toPrepared(OcrPoint(170f, 20f), 100, 200, 90))
        assertEquals(original, OcrCoordinateTransform.toPrepared(OcrPoint(80f, 170f), 100, 200, 180))
        assertEquals(original, OcrCoordinateTransform.toPrepared(OcrPoint(30f, 80f), 100, 200, 270))
    }

    private fun label(text: String) = NutritionLabelParser.parse(text.lines().map { OcrLine(it, .99f, emptyList()) })

    @Test fun transcriptionConflictsAreBlankAndVlmMassNeverBecomesConsumedAmount() {
        val baseline = label("Пищевая ценность на 100 г\nБелки 11 г\nЖиры 6,4 г\nЭнергетическая ценность 183,2 ккал\nМасса нетто 240 г")
        val raw = "Пищевая ценность на 100 г\nБелки 12 г\nЖиры 6,4 г\nУглеводы 20,4 г\nМасса нетто 999 г"
        val merged = TranscriptionNutrition.merge(baseline, raw)
        assertEquals("", merged.draft().protein)
        assertTrue("PROTEIN" in merged.conflicts)
        assertEquals("20.4", merged.draft().carbs)
        assertEquals("240", merged.draft().amount)
        assertEquals("999", merged.suggestedPackageGrams)
        assertFalse(NutritionLabelParser.parseUnscoredTranscription(raw).proteinGrams!!.confident)
    }

    @Test fun qualityAdmissionExpiresWhenArtifactRuntimeTaskOrModelProfileChanges() {
        val admission = TaskQualityAdmission("b".repeat(64), "0.18.1", MealRecognition.PROFILE_VERSION, "recorded-image-gate")
        val model = ModelDescriptor(id = "vision", name = "Vision", repository = "test/vision",
            revision = "a".repeat(40), filename = "vision.litertlm", bytes = 1, sha256 = "b".repeat(64),
            license = "Apache-2.0", licenseUrl = "https://example.test/license",
            profile = ModelTaskProfile.GENERAL_VISION, contextTokens = 4096, outputTokens = 512,
            mealNameAdmission = admission)
        assertTrue(model.admitsMealName())
        assertFalse(model.admitsPackage())
        assertFalse(model.copy(mealNameAdmission = null).admitsMealName())
        assertFalse(model.copy(sha256 = "c".repeat(64)).admitsMealName())
        assertFalse(model.copy(mealNameAdmission = admission.copy(artifactHash = "c".repeat(64))).admitsMealName())
        assertFalse(model.copy(mealNameAdmission = admission.copy(runtimeVersion = "0.17.1")).admitsMealName())
        assertFalse(model.copy(mealNameAdmission = admission.copy(taskVersion = "other-task")).admitsMealName())
        assertFalse(model.copy(mealNameAdmission = admission.copy(evidence = " ")).admitsMealName())
        assertFalse(model.copy(profile = ModelTaskProfile.CUSTOM).admitsMealName())
        assertFalse(model.copy(profile = ModelTaskProfile.LABEL_TRANSCRIPTION).admitsMealName())
        assertFalse(model.copy(packageAdmission = admission, profile = ModelTaskProfile.LABEL_TRANSCRIPTION).admitsPackage())
        val packageModel = model.copy(profile = ModelTaskProfile.LABEL_TRANSCRIPTION,
            packageAdmission = admission.copy(taskVersion = TranscriptionNutrition.PROFILE_VERSION))
        assertTrue(packageModel.admitsPackage())
        assertFalse(packageModel.copy(sha256 = "c".repeat(64)).admitsPackage())
        val macros = model.copy(packageAdmission = admission.copy(taskVersion = PackageMacroAssistance.PROFILE_VERSION))
        assertTrue(macros.admitsPackage())
        assertTrue(macros.admitsMealName())
        assertFalse(macros.copy(profile = ModelTaskProfile.LABEL_TRANSCRIPTION).admitsPackage())
        assertFalse(macros.copy(packageAdmission = admission.copy(taskVersion = "package-macros-v1-nutrition-crop-v1")).admitsPackage())
        assertFalse(macros.copy(sha256 = "c".repeat(64)).admitsPackage())
    }

    @Test fun rejectedMealEvidenceRemainsAvailableWithoutAnyPrefill() {
        val malformed = """{"isFood":true,"singleDish":false,"name":"apple and banana"}"""
        val rejected = MealRecognition.review(malformed)
        assertNull(rejected.draft)
        assertEquals(malformed, rejected.raw)
        assertEquals("MEAL_INVALID_RESPONSE", rejected.failure)
        listOf("[]", "null", "not JSON", """{"isFood":[],"singleDish":true,"name":"Apple"}""").forEach {
            assertEquals("MEAL_INVALID_RESPONSE", MealRecognition.review(it).failure)
        }
        val refusal = MealRecognition.review("""{"isFood":false,"singleDish":false,"name":null}""")
        assertNull(refusal.draft)
        assertEquals("MEAL_NO_SINGLE_FOOD", refusal.failure)
        val accepted = MealRecognition.review("""{"isFood":true,"singleDish":true,"name":"Apple"}""")
        assertEquals("Apple", accepted.draft?.name)
        assertEquals("", accepted.draft?.draft()?.amount)
        assertNull(accepted.failure)
    }

    @Test fun mealNamesMustRemainNonblankAfterTrimming() {
        listOf("  ", "\t", "a", "apple\rpie").forEach { name ->
            val raw = kotlinx.serialization.json.buildJsonObject {
                put("isFood", kotlinx.serialization.json.JsonPrimitive(true))
                put("singleDish", kotlinx.serialization.json.JsonPrimitive(true))
                put("name", kotlinx.serialization.json.JsonPrimitive(name))
            }.toString()
            assertTrue(raw, runCatching { MealRecognition.parse(raw) }.isFailure)
        }
        assertEquals("Apple", MealRecognition.parse("""{"isFood":true,"singleDish":true,"name":"  Apple  "}""").name)
    }

    @Test fun mealSchemaRejectsNutritionAndUncertainNames() {
        val accepted = MealRecognition.parse("""{"isFood":true,"singleDish":true,"name":"Apple"}""")
        assertEquals("Apple", accepted.name)
        assertEquals("", accepted.draft().amount)
        assertEquals("", accepted.draft().energy)
        assertNull(MealRecognition.parse("""{"isFood":true,"singleDish":true,"name":"apple and banana"}""").name)
        assertNull(MealRecognition.parse("""{"isFood":false,"singleDish":false,"name":null}""").name)
        listOf(
            """{"isFood":true,"singleDish":true,"name":"Apple","weight":150}""",
            """{"isFood":false,"singleDish":false,"name":"A bus"}""",
            """{"isFood":"true","singleDish":true,"name":"Apple"}""",
            """{"isFood":true,"singleDish":true,"name":"Apple 100 g"}""",
        ).forEach { assertTrue(runCatching { MealRecognition.parse(it) }.isFailure) }
        assertTrue(AiResponseValidation.degenerate(List(6) { "Repeated line" }.joinToString("\n")))
        assertFalse(AiResponseValidation.degenerate("A valid short answer"))
    }

    @Test fun servingAndAmbiguousTextCannotFillPer100g() {
        val baseline = label("Пищевая ценность на 100 г\nБелки 11 г")
        val merged = TranscriptionNutrition.merge(baseline, "Пищевая ценность на порцию\nЖиры 20 г")
        assertEquals("", merged.draft().fat)
        assertEquals("11", merged.draft().protein)
    }
}
