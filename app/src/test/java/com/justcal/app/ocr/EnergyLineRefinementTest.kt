package com.justcal.app.ocr

import org.junit.Assert.*
import org.junit.Test

class EnergyLineRefinementTest {
    @Test fun acceptsOnlyObservedUnitRecoveryWithUnchangedNumbers() {
        val original = "Energy 770,6 kJ / 183,2 cal"
        assertTrue(EnergyLineRefinement.accept(original, "Energy 770.6 kJ / 183.2 kcal", .95f))
        assertFalse(EnergyLineRefinement.accept(original, "Energy 770.6 kJ / 183.3 kcal", .99f))
        assertFalse(EnergyLineRefinement.accept(original, "Energy 183.2 kcal", .99f))
        assertFalse(EnergyLineRefinement.accept(original, "Energy 770.6 kJ / 183.2 kcal", .7f))
    }

    @Test fun supportsRussianUnitsInEitherOrderWithoutRetryingKjOnly() {
        assertTrue(EnergyLineRefinement.needed("183,2 кал / 770,6 кДж"))
        assertTrue(EnergyLineRefinement.accept("183,2 кал / 770,6 кДж",
            "183,2 ккал / 770,6 кДж", .9f))
        assertFalse(EnergyLineRefinement.needed("Энергетическая ценность: 414 кДж"))
        assertFalse(EnergyLineRefinement.needed("Энергетическая ценность: 98,5 ккал / 414 кДж"))
    }
}
