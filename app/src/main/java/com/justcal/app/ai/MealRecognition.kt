package com.justcal.app.ai

import com.justcal.app.domain.RecognitionDraft
import kotlinx.serialization.json.*

/** Name-only aid. No photo-derived weights, calories, or per-100-g estimates are accepted. */
object MealRecognition {
    const val PROFILE_VERSION = "meal-name-v1"
    const val PROMPT = "Identify whether this image shows a single food or dish. Return only JSON with exactly these keys: isFood (boolean), singleDish (boolean), name (short string or null). For non-food, unclear images or multiple separate foods, use false and name null. An apple beside a banana is multiple foods: singleDish must be false and name null. A complete prepared dish such as pizza counts as one dish. Name the food only; do not provide weight or nutrition."
    data class Review(val draft: RecognitionDraft?, val raw: String, val failure: String?)

    /** Preserve rejected evidence without allowing it to become an editor prefill. */
    fun review(raw: String): Review = try {
        val draft = parse(raw)
        if (draft.name.isNullOrBlank()) Review(null, raw.take(2048), "MEAL_NO_SINGLE_FOOD")
        else Review(draft, raw.take(2048), null)
    } catch (_: Exception) {
        Review(null, raw.take(2048), "MEAL_INVALID_RESPONSE")
    }

    fun parse(raw: String): RecognitionDraft {
        val trimmed = raw.trim()
        val text = if (trimmed.startsWith("```json\n") && trimmed.endsWith("\n```"))
            trimmed.removePrefix("```json\n").removeSuffix("\n```") else trimmed
        require(text.length <= 2048)
        val obj = Json.parseToJsonElement(text).jsonObject
        require(obj.keys == setOf("isFood", "singleDish", "name")) { "Unexpected meal fields" }
        fun flag(key: String): Boolean {
            val value = obj.getValue(key).jsonPrimitive
            require(!value.isString)
            return requireNotNull(value.booleanOrNull)
        }
        val isFood = flag("isFood")
        val single = flag("singleDish")
        val name = obj.getValue("name").let {
            if (it == JsonNull) null else it.jsonPrimitive.let { value ->
                require(value.isString)
                val name = value.content.trim()
                require(name.length in 2..120 && name.none(Char::isDigit) && name.none(Char::isISOControl))
                name
            }
        }
        require((isFood && single) || name == null) { "Uncertain meal must not supply a name" }
        // A model boolean cannot override explicit evidence of separate foods in its name.
        val compound = name?.let { Regex("""\b(?:and|with)\b|\sи\s|[,;+&/]""", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true
        return RecognitionDraft(name = name.takeUnless { compound }, source = "MEAL_NAME_SUGGESTION", evidence = raw)
    }
}
