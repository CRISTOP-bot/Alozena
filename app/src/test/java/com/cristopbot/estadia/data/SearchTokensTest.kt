package com.cristopbot.estadia.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTokensTest {
    @Test fun normalizesSpanishAccentsAndPunctuation() {
        assertEquals(listOf("ciudad", "de", "mexico"), normalizeSearchTokens("Ciudad de México!"))
    }

    @Test fun removesRepeatedTokensAndSingleCharacterNoise() {
        assertEquals(listOf("casa", "mar"), normalizeSearchTokens("Casa casa a mar"))
    }
}
