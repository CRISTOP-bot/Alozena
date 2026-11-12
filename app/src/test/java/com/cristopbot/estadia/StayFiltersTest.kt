package com.cristopbot.estadia

import org.junit.Assert.assertEquals
import org.junit.Test

class StayFiltersTest {
    @Test fun filtersByCityWithoutCaseSensitivity() {
        assertEquals(listOf("Loft con vista al mar"), StayFilters.filter(StayCatalog.stays, "PUERTO ESCONDIDO", "Todo", false, emptySet()).map { it.title })
    }

    @Test fun searchIgnoresAccentsAndSurroundingWhitespace() {
        assertEquals(listOf("Departamento luminoso"), StayFilters.filter(StayCatalog.stays, "  ciudad de mexico  ", "Todo", false, emptySet()).map { it.title })
    }

    @Test fun searchesTitlesAndDestinations() {
        assertEquals(listOf("Casa colorida en el centro"), StayFilters.filter(StayCatalog.stays, "colorida", "Todo", false, emptySet()).map { it.title })
    }

    @Test fun combinesCategoryAndFavorites() {
        assertEquals(listOf("Casa entre árboles"), StayFilters.filter(StayCatalog.stays, "", "Cabañas", true, setOf(1)).map { it.title })
    }

    @Test fun emptyFavoritesShowNoResults() {
        assertEquals(emptyList<Stay>(), StayFilters.filter(StayCatalog.stays, "", "Todo", true, emptySet()))
    }

    @Test fun noMatchReturnsEmptyList() {
        assertEquals(emptyList<Stay>(), StayFilters.filter(StayCatalog.stays, "Tokio", "Todo", false, emptySet()))
    }
}
