package com.cristopbot.estadia

import com.cristopbot.estadia.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StayFiltersTest {
    private val listings = listOf(
        Stay(1, "Casa del Lago", "Valle de Bravo", "Casa", 1800, 0, 0, "Sin reseñas", "https://photo.invalid/casa", "", "Descripción del anfitrión", 2, 4, "id-casa", "host-1"),
        Stay(2, "Loft Centro", "Oaxaca", "Departamento", 1200, 0, 0, "Sin reseñas", "https://photo.invalid/loft", "", "Descripción del anfitrión", 1, 2, "id-loft", "host-2")
    )

    @Test fun liveCatalogHasNoPreloadedHomes() {
        assertEquals(emptyList<Stay>(), StayCatalog.stays)
    }

    @Test fun fallbackListingsAreClearlyMarkedAsDemoOnly() {
        assertEquals(6, SeedData.stays.size)
        assertTrue(SeedData.stays.all { it.isDemo })
        assertTrue(SeedData.stays.all { it.ownerName == "Anfitrión de ejemplo" })
    }

    @Test fun filtersPublishedItemsByCityAndAccentNormalization() {
        assertEquals(listOf("Casa del Lago"), StayFilters.filter(listings, " valle DE bravo ", "Todo", false, emptySet()).map { it.title })
        assertEquals(listOf("Loft Centro"), StayFilters.filter(listings, "oaxaca", "Todo", false, emptySet()).map { it.title })
    }

    @Test fun filtersByTypeAndFavoriteSet() {
        assertEquals(listOf("Casa del Lago"), StayFilters.filter(listings, "", "Casa", true, setOf(1)).map { it.title })
        assertEquals(emptyList<Stay>(), StayFilters.filter(listings, "", "Todo", true, emptySet()))
    }

    @Test fun sortsResultsByPriceWhenRequested() {
        val sorted = StayFilters.filter(listings, "", "Todo", false, emptySet(), StaySortOption.PRICE_ASC).map { it.title }
        assertEquals(listOf("Loft Centro", "Casa del Lago"), sorted)

        val descending = StayFilters.filter(listings, "", "Todo", false, emptySet(), StaySortOption.PRICE_DESC).map { it.title }
        assertEquals(listOf("Casa del Lago", "Loft Centro"), descending)
    }
}
