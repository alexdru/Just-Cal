package com.justcal.app.domain

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class FoodMatcherTest {
    private fun evidence(name: String = "Vanilla almond yogurt", vararg values: Long?) =
        FoodMatchEvidence(name, if (values.isEmpty()) List(4) { MatchNumber() } else values.map {
            MatchNumber(it, if (it == null) MatchEvidenceState.MISSING else MatchEvidenceState.KNOWN)
        }, MatchNutritionBasis.PER_100_G)
    private val saved = evidence("Vanilla almond yogurt", 12000, 400, 500, 1800)
    private fun match(scan: FoodMatchEvidence, candidate: FoodMatchEvidence = saved) =
        FoodMatcher.match("stable-id", scan, candidate)

    @Test fun completeEvidenceIsReviewOnly() {
        assertEquals(FoodMatchCategory.POSSIBLE, match(saved).category)
        assertEquals(70, match(saved).rankingScore)
    }
    @Test fun eachMissingNutrientDoesNotVeto() {
        for (missing in 0..3) {
            val scan = saved.copy(nutrients = saved.nutrients.mapIndexed { i, n -> if (i == missing) MatchNumber() else n })
            assertEquals(FoodMatchCategory.POSSIBLE, match(scan).category)
            assertEquals(3, match(scan).comparableFields)
        }
    }
    @Test fun twoRecognizedFieldsRemainUseful() {
        assertEquals(FoodMatchCategory.POSSIBLE, match(evidence("Vanilla almond yogurt", null, 400, 500, null)).category)
    }
    @Test fun unknownBasisNeverMakesNumbersConflict() {
        val result = match(evidence("Vanilla almond yogurt", 1, 1, 1, 1).copy(basis = MatchNutritionBasis.UNKNOWN))
        assertEquals(FoodMatchCategory.POSSIBLE, result.category)
        assertEquals(0, result.comparableFields)
        assertFalse(result.reasons.contains(FoodMatchReason.NUTRITION_CONFLICT))
    }
    @Test fun genericNameOnlyIsInsufficient() {
        assertEquals(FoodMatchCategory.INSUFFICIENT, match(evidence("Yogurt"), evidence("Yogurt")).category)
    }
    @Test fun zeroOnlyAgreementIsNotInformative() {
        assertEquals(FoodMatchCategory.INSUFFICIENT,
            match(evidence("Yogurt", 0, 0, 0, 0), evidence("Yogurt", 0, 0, 0, 0)).category)
    }
    @Test fun recipeVariantsStayConflicting() {
        val scan = saved.copy(name = "Vanilla almond yogurt sugar free")
        val other = saved.copy(name = "Vanilla almond yogurt sweetened")
        assertEquals(FoodMatchCategory.CONFLICTING, match(scan, other).category)
    }
    @Test fun reorderedIdentityWordsAreNotExactIdentityAgreement() {
        val scan = evidence("Yogurt").copy(manufacturer = MatchIdentity("Alpha Foods", MatchEvidenceState.KNOWN))
        val other = evidence("Yogurt").copy(manufacturer = MatchIdentity("Foods Alpha", MatchEvidenceState.KNOWN))
        assertEquals(FoodMatchCategory.CONFLICTING, match(scan, other).category)
    }
    @Test fun manufacturerMismatchOverridesIdenticalNutrition() {
        assertEquals(FoodMatchCategory.CONFLICTING, match(
            saved.copy(manufacturer = MatchIdentity("A", MatchEvidenceState.KNOWN)),
            saved.copy(manufacturer = MatchIdentity("B", MatchEvidenceState.KNOWN))).category)
    }
    @Test fun deliveryMismatchOverridesIdenticalNutrition() {
        assertEquals(FoodMatchCategory.CONFLICTING, match(
            saved.copy(deliveryService = MatchIdentity("A", MatchEvidenceState.KNOWN)),
            saved.copy(deliveryService = MatchIdentity("B", MatchEvidenceState.KNOWN))).category)
    }
    @Test fun retailerMismatchDoesNotProveDifferentProduct() {
        assertEquals(FoodMatchCategory.POSSIBLE, match(
            saved.copy(retailer = MatchIdentity("A", MatchEvidenceState.KNOWN)),
            saved.copy(retailer = MatchIdentity("B", MatchEvidenceState.KNOWN))).category)
    }
    @Test fun majorFatDifferenceOverridesName() {
        assertEquals(FoodMatchCategory.CONFLICTING, match(evidence("Vanilla almond yogurt", 12000, 400, 1500, 1800)).category)
    }
    @Test fun reorderedAndTypoNamesUseOneToOneAlignment() {
        val a = FoodMatcher.normalizeName("almond vanilla yogurt")
        val b = FoodMatcher.normalizeName("yogurt vanila almond")
        assertEquals(2.0 * 28 / 70, FoodMatcher.nameSimilarity(a, b), 0.000001)
        assertTrue(FoodMatcher.nameSimilarity(FoodMatcher.normalizeName("almond almond"),
            FoodMatcher.normalizeName("almond")) < 1.0)
    }
    @Test fun normalizationPreservesRecipeNumbersAndRepairsOnlyMixedTokens() {
        assertEquals(FoodMatcher.normalizeName("ЙОГУРТ 2%").tokens, FoodMatcher.normalizeName("йогурт 2%").tokens)
        assertEquals(listOf("молоко"), FoodMatcher.normalizeName("мoлoко").tokens)
        assertEquals(listOf("milk"), FoodMatcher.normalizeName("milk").tokens)
    }
    @Test fun arithmeticIsSafeAtLongLimits() {
        assertEquals(FoodMatchCategory.CONFLICTING, match(evidence("Vanilla almond yogurt",
            Long.MAX_VALUE, 400, 500, 1800)).category)
    }
    @Test(expected = CancellationException::class) fun cancellationPropagates() {
        FoodMatcher.match("id", saved, saved) { throw CancellationException() }
    }
    @Test fun truncatedNameCannotEstablishNameOnlyMatch() {
        val long = evidence(("vanilla almond ").repeat(30))
        assertEquals(FoodMatchCategory.INSUFFICIENT, match(long, long).category)
        assertTrue(match(long, long).reasons.contains(FoodMatchReason.NAME_TRUNCATED))
    }
}
