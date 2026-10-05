package com.justcal.app.ai

import java.math.BigDecimal
import kotlinx.serialization.json.*

/** Lab output, never a diary input. JSON validity does not establish factual accuracy. */
data class ExtractedNutrition(
    val productName: String?,
    val caloriesKcal: BigDecimal?,
    val nutritionBasisGrams: BigDecimal?,
    val proteinGrams: BigDecimal?,
    val fatGrams: BigDecimal?,
    val carbohydrateGrams: BigDecimal?,
    val packageWeightGrams: BigDecimal?,
    val formattedJson: String,
)

object NutritionExtraction {
    private val numbers = listOf("caloriesKcal", "nutritionBasisGrams", "proteinGrams",
        "fatGrams", "carbohydrateGrams", "packageWeightGrams")
    private val keys = (listOf("productName") + numbers).toSet()
    private val json = Json { prettyPrint = true }
    val schema: String = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(keys.map(::JsonPrimitive)))
        put("properties", buildJsonObject {
            put("productName", buildJsonObject {
                put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
                put("maxLength", 200)
            })
            numbers.forEach { key ->
                put(key, buildJsonObject {
                    put("type", JsonArray(listOf(JsonPrimitive("number"), JsonPrimitive("null"))))
                    put("minimum", 0)
                    put("maximum", 100000)
                    if (key.endsWith("WeightGrams") || key == "nutritionBasisGrams") put("exclusiveMinimum", 0)
                })
            }
        })
    }.toString()

    /** Accept complete JSON or one fenced JSON block; never guess missing fields or units. */
    fun parse(response: String): ExtractedNutrition {
        val trimmed = response.trim()
        val candidate = if (trimmed.startsWith("```json\n") && trimmed.endsWith("\n```")) {
            trimmed.removePrefix("```json\n").removeSuffix("\n```").trim()
        } else trimmed
        val obj = json.parseToJsonElement(candidate) as? JsonObject ?: error("Expected a JSON object")
        require(obj.keys == keys) { "Expected all seven nutrition fields and no other fields" }
        val name = obj.getValue("productName").let { value ->
            if (value == JsonNull) null else {
                val p = value as? JsonPrimitive ?: error("productName must be a string or null")
                require(p.isString && p.content.length in 1..200) { "Invalid productName" }
                p.content
            }
        }
        fun number(key: String): BigDecimal? {
            val value = obj.getValue(key)
            if (value == JsonNull) return null
            val p = value as? JsonPrimitive ?: error("$key must be a number or null")
            require(!p.isString && p.content.length <= 32) { "$key must be a JSON number" }
            val number = p.content.toBigDecimalOrNull() ?: error("$key must be a finite decimal")
            require(number >= BigDecimal.ZERO && number <= BigDecimal("100000")) { "$key is out of range" }
            if (key == "nutritionBasisGrams" || key == "packageWeightGrams") {
                require(number > BigDecimal.ZERO) { "$key must be positive or null" }
            }
            return number
        }
        return ExtractedNutrition(name, number("caloriesKcal"), number("nutritionBasisGrams"),
            number("proteinGrams"), number("fatGrams"), number("carbohydrateGrams"),
            number("packageWeightGrams"), json.encodeToString(JsonObject.serializer(), obj))
    }
}
