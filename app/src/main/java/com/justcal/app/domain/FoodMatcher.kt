package com.justcal.app.domain

import java.math.BigInteger
import java.text.Normalizer
import java.util.Locale

/**
 * Bounded, deterministic review-only matching. Call off-main; checkCancellation must throw
 * when its owning job is cancelled. No inference, persistence or draft mutation occurs here.
 */
object FoodMatcher {
    const val VERSION = 1
    private val generic = setOf("food", "dish", "meal", "salad", "yogurt", "milk", "bread",
        "еда", "блюдо", "салат", "йогурт", "молоко", "хлеб")
    private val recipe = setOf("without", "free", "sugar", "sweetened", "unsweetened", "dressing",
        "без", "сахара", "сахар", "сладкий", "несладкий", "майонез", "масло", "сметана",
        "mayonnaise", "oil", "ranch", "caesar", "цезарь", "vanilla", "chocolate", "strawberry",
        "ванильный", "шоколадный", "клубничный", "ваниль", "шоколад")
    private val homoglyphs = mapOf('a' to 'а', 'c' to 'с', 'e' to 'е', 'o' to 'о',
        'p' to 'р', 'x' to 'х', 'y' to 'у', 'k' to 'к', 'm' to 'м', 't' to 'т')
    data class Name(val tokens: List<String>, val truncated: Boolean)

    fun normalizeName(value: String): Name {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace('ё', 'е')
        val bounded = normalized.take(160)
        val tokens = Regex("[\\p{L}\\p{N}]+(?:[.,][0-9]+)?%?").findAll(bounded).map { it.value }
            .map { token ->
                if (token.any { it in 'а'..'я' } && token.any { it in 'a'..'z' })
                    token.map { homoglyphs[it] ?: it }.joinToString("") else token
            }.toList()
        return Name(tokens.take(32).sorted(), normalized.length > 160 || tokens.size > 32)
    }
    private fun weight(token: String) = if (token.any(Char::isDigit) || token in recipe) 2 else 1

    fun tokenAgreement(a: String, b: String): Int = agreement(a, b)

    private fun agreement(a: String, b: String): Int {
        if (a == b) return 10
        val minLength = minOf(a.length, b.length)
        val limit = when { minLength >= 9 -> 2; minLength >= 5 -> 1; else -> return 0 }
        if (kotlin.math.abs(a.length - b.length) > limit) return 0
        var row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1)
            next[0] = i + 1
            for (j in b.indices) next[j + 1] =
                minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            row = next
        }
        return when (row.last()) { 1 -> 8; 2 -> if (limit == 2) 6 else 0; else -> 0 }
    }

    /** Maximum weight bipartite assignment (Hungarian), O(32³), with lexical tie order. */
    fun nameSimilarity(a: Name, b: Name, checkCancellation: () -> Unit = {}): Double {
        val n = maxOf(a.tokens.size, b.tokens.size)
        if (n == 0) return 0.0
        val u = IntArray(n + 1); val v = IntArray(n + 1)
        val p = IntArray(n + 1); val way = IntArray(n + 1)
        fun cost(i: Int, j: Int): Int {
            if (i >= a.tokens.size || j >= b.tokens.size) return 0
            return -minOf(weight(a.tokens[i]), weight(b.tokens[j])) * agreement(a.tokens[i], b.tokens[j])
        }
        for (i in 1..n) {
            checkCancellation()
            p[0] = i
            var j0 = 0
            val minv = IntArray(n + 1) { Int.MAX_VALUE }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Int.MAX_VALUE; var j1 = 0
                for (j in 1..n) if (!used[j]) {
                    val cur = cost(i0 - 1, j - 1) - u[i0] - v[j]
                    if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                    if (minv[j] < delta) { delta = minv[j]; j1 = j }
                }
                for (j in 0..n) if (used[j]) { u[p[j]] += delta; v[j] -= delta }
                    else minv[j] -= delta
                j0 = j1
            } while (p[j0] != 0)
            do { val j1 = way[j0]; p[j0] = p[j1]; j0 = j1 } while (j0 != 0)
        }
        val contribution = (1..n).sumOf { -cost(p[it] - 1, it - 1) }
        return 2.0 * contribution / (10 * (a.tokens.sumOf(::weight) + b.tokens.sumOf(::weight)))
    }

    private fun identity(value: MatchIdentity) = value.text?.let {
        Normalizer.normalize(it, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}%]+"), " ").trim()
    }?.takeIf { it.isNotBlank() }
    private fun equal(a: MatchIdentity, b: MatchIdentity) =
        a.state == MatchEvidenceState.KNOWN && b.state == MatchEvidenceState.KNOWN &&
            identity(a) != null && identity(a) == identity(b)
    private fun different(a: MatchIdentity, b: MatchIdentity) =
        a.state != MatchEvidenceState.MISSING && b.state != MatchEvidenceState.MISSING &&
            identity(a) != null && identity(b) != null && identity(a) != identity(b)
    private fun within(a: Long, b: Long, absolute: Long, percent: Int): Boolean {
        val x = BigInteger.valueOf(a); val y = BigInteger.valueOf(b)
        return (x - y).abs() * BigInteger.valueOf(100) <=
            maxOf(BigInteger.valueOf(absolute) * BigInteger.valueOf(100), maxOf(x, y) * BigInteger.valueOf(percent.toLong()))
    }

    fun match(candidateId: String, scan: FoodMatchEvidence, saved: FoodMatchEvidence,
        checkCancellation: () -> Unit = {}): FoodMatch {
        checkCancellation()
        val a = normalizeName(scan.name); val b = normalizeName(saved.name)
        val similarity = nameSimilarity(a, b, checkCancellation)
        val reasons = linkedSetOf<FoodMatchReason>()
        if (scan.nameState == MatchEvidenceState.AI_RECOVERED) reasons += FoodMatchReason.AI_EVIDENCE
        if (a.truncated || b.truncated) reasons += FoodMatchReason.NAME_TRUNCATED
        val comparableBasis = scan.basis == MatchNutritionBasis.PER_100_G && saved.basis == MatchNutritionBasis.PER_100_G
        if (!comparableBasis) reasons += FoodMatchReason.NON_COMPARABLE_BASIS
        var count = 0; var informative = 0; var agreements = 0; var nutritionConflict = false
        for (i in 0..3) {
            val x = scan.nutrients[i]; val y = saved.nutrients[i]
            if (x.state == MatchEvidenceState.AI_RECOVERED || y.state == MatchEvidenceState.AI_RECOVERED)
                reasons += FoodMatchReason.AI_EVIDENCE
            if (!comparableBasis || !x.comparable || !y.comparable) {
                reasons += FoodMatchReason.MISSING_NUTRITION
                continue
            }
            val xv = requireNotNull(x.hundredths); val yv = requireNotNull(y.hundredths)
            count++
            if (xv > 0 && yv > 0) informative++
            if (within(xv, yv, if (i == 0) 200 else 30, if (i == 0) 2 else 3)) agreements++
            if (!within(xv, yv, if (i == 0) 1000 else 100, if (i == 0) 10 else 15)) nutritionConflict = true
        }
        val identityConflict = listOf(scan.brand to saved.brand, scan.manufacturer to saved.manufacturer,
            scan.deliveryService to saved.deliveryService).any { different(it.first, it.second) }
        if (identityConflict) reasons += FoodMatchReason.IDENTITY_CONFLICT
        if (different(scan.retailer, saved.retailer)) reasons += FoodMatchReason.RETAILER_DIFFERS
        fun descriptors(tokens: List<String>): List<Set<String>> {
            val words = tokens.toSet()
            fun known(vararg groups: Set<String>) = groups.mapIndexedNotNull { index, group ->
                index.toString().takeIf { group.any(words::contains) }
            }.toSet()
            val sugarFree = words.any { it in setOf("unsweetened", "несладкий") } ||
                ("sugar" in words && words.any { it in setOf("free", "without") }) ||
                ("без" in words && words.any { it in setOf("сахар", "сахара") })
            val sugar = buildSet { if (sugarFree) add("free")
                if (words.any { it in setOf("sweetened", "сладкий") }) add("sweetened") }
            return listOf(sugar,
                known(setOf("vanilla", "ваниль", "ванильный"), setOf("chocolate", "шоколад", "шоколадный"),
                    setOf("strawberry", "клубничный")),
                known(setOf("mayonnaise", "майонез"), setOf("oil", "масло"), setOf("сметана"),
                    setOf("ranch"), setOf("caesar", "цезарь")),
                words.filter { it.endsWith("%") }.toSet())
        }
        val recipeConflict = descriptors(a.tokens).zip(descriptors(b.tokens)).any { (x, y) ->
            x.isNotEmpty() && y.isNotEmpty() && x != y
        }
        if (recipeConflict) reasons += FoodMatchReason.RECIPE_CONFLICT
        if (nutritionConflict) reasons += FoodMatchReason.NUTRITION_CONFLICT
        val exactIdentity = equal(scan.brand, saved.brand) || equal(scan.manufacturer, saved.manufacturer) ||
            equal(scan.deliveryService, saved.deliveryService)
        val distinctive = a.tokens.filter { it !in generic && it !in setOf("without", "free", "без") && !it.any(Char::isDigit) }.distinct()
            .count { token -> b.tokens.any { agreement(token, it) > 0 } }
        val category = when {
            (similarity >= 0.55 || distinctive >= 2) && (identityConflict || recipeConflict || nutritionConflict) -> FoodMatchCategory.CONFLICTING
            similarity >= 0.55 && (informative > 0 || exactIdentity) -> FoodMatchCategory.POSSIBLE
            similarity >= 0.8 && distinctive >= 2 && !a.truncated && !b.truncated &&
                scan.nameState == MatchEvidenceState.KNOWN && saved.nameState == MatchEvidenceState.KNOWN -> FoodMatchCategory.POSSIBLE
            else -> FoodMatchCategory.INSUFFICIENT
        }
        if (category == FoodMatchCategory.INSUFFICIENT) reasons += FoodMatchReason.WEAK_EVIDENCE
        val mass = scan.packageMassHundredths?.let { x -> saved.packageMassHundredths?.let { within(x, it, 0, 2) } } == true
        val score = (40 * similarity + (if (equal(scan.brand, saved.brand)) 15 else 0) +
            (if (equal(scan.manufacturer, saved.manufacturer)) 5 else 0) +
            (if (equal(scan.retailer, saved.retailer)) 5 else 0) + (if (mass) 5 else 0) +
            30.0 * agreements / 4).toInt().coerceAtMost(99)
        return FoodMatch(candidateId, category, score, count, reasons)
    }
}
