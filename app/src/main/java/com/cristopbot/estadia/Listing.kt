package com.cristopbot.estadia

import java.text.Normalizer
import java.util.Locale

data class Stay(
    val id: Int,
    val title: String,
    val location: String,
    val category: String,
    val price: Int,
    val rating: String,
    val image: String,
    val badge: String,
    val description: String,
    val beds: Int,
    val guests: Int
)

object StayCatalog {
    val stays = listOf(
        Stay(1, "Casa entre árboles", "Valle de Bravo, México", "Cabañas", 1840, "4.96", "https://images.unsplash.com/photo-1449158743715-0a90ebb6d2d8?w=1000", "Favorito", "Despierta con el sonido del bosque en esta casa luminosa, rodeada de naturaleza y a minutos del lago.", 2, 4),
        Stay(2, "Loft con vista al mar", "Puerto Escondido, México", "Frente al mar", 2260, "4.91", "https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?w=1000", "Increíble", "Un refugio relajado frente al Pacífico, con terraza privada y atardeceres que se quedan contigo.", 1, 2),
        Stay(3, "Casa colorida en el centro", "Oaxaca, México", "Casas", 1320, "4.88", "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=1000", "Favorito", "Patio lleno de plantas, cocina equipada y calles llenas de sabor a unos pasos de la puerta.", 2, 3),
        Stay(4, "Cabaña bajo las estrellas", "Mazamitla, México", "Cabañas", 1580, "4.98", "https://images.unsplash.com/photo-1449158743715-0a90ebb6d2d8?w=1000", "Raro de encontrar", "Escápate a la montaña: chimenea, terraza y noches tranquilas para desconectar de todo.", 2, 4),
        Stay(5, "Departamento luminoso", "Ciudad de México, México", "Ciudades", 1150, "4.85", "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=1000", "Favorito", "Base perfecta para explorar la ciudad, cerca de cafés, parques y transporte público.", 1, 2),
        Stay(6, "Villa privada con alberca", "Mérida, México", "Albercas", 2980, "4.94", "https://images.unsplash.com/photo-1613490493576-7fde63acd811?w=1000", "Superanfitrión", "Una villa serena con alberca privada, espacios frescos y el encanto del centro histórico.", 3, 6)
    )
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
