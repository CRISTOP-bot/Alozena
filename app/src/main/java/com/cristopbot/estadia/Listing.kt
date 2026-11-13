package com.cristopbot.estadia

import java.text.Normalizer
import java.util.Locale

data class Stay(
    val id: Int,
    val title: String,
    val location: String,
    val category: String,
    val price: Int,
    val cleaningFee: Int,
    val securityDeposit: Int,
    val rating: String,
    val image: String,
    val badge: String,
    val description: String,
    val beds: Int,
    val guests: Int,
    val firestoreId: String = "",
    val hostUid: String = "",
    val reviewCount: Int = 0,
    val ownerName: String = "Anfitrión Alozena"
)

/** Alozena starts with an empty catalog: only listings submitted to its backend appear. */
object StayCatalog {
    val stays: List<Stay> = emptyList()
}

object StayFilters {
    fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    fun filter(stays: List<Stay>, query: String, category: String, favoritesOnly: Boolean, favorites: Set<Int>): List<Stay> = stays.filter { stay ->
        val normalizedQuery = normalize(query)
        val matchesQuery = normalizedQuery.isBlank() || normalize(stay.location).contains(normalizedQuery) || normalize(stay.title).contains(normalizedQuery)
        val matchesCategory = category == "Todo" || stay.category == category
        matchesQuery && matchesCategory && (!favoritesOnly || stay.id in favorites)
    }
}
