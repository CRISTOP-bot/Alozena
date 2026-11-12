package com.cristopbot.estadia

import org.junit.Assert.assertEquals
import org.junit.Test

class StayFiltersTest {
    @Test fun filtersByCityWithoutCaseSensitivity() {
        assertEquals(listOf("Loft con vista al mar"), StayFilters.filter(StayCatalog.stays, "PUERTO ESCONDIDO", "Todo", false, emptySet()).map { it.title })
    }
    @Test fun combinesCategoryAndFavorites() {
        assertEquals(listOf("Casa entre árboles"), StayFilters.filter(StayCatalog.stays, "", "Cabañas", true, setOf(1)).map { it.title })
    }
    @Test fun emptyFavoritesShowNoResults() {
        assertEquals(emptyList<Stay>(), StayFilters.filter(StayCatalog.stays, "", "Todo", true, emptySet()))
    }
}
