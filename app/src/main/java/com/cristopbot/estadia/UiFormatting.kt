package com.cristopbot.estadia

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.text.NumberFormat
import java.util.Locale

internal fun categoryIcon(category: String) = when (category) {
    "Casa", "Cabaña" -> Icons.Default.Home
    "Departamento" -> Icons.Default.Apartment
    "Habitación" -> Icons.Default.Bed
    "Otro" -> Icons.Default.Explore
    else -> Icons.Default.Explore
}

internal fun formatPrice(price: Int): String = NumberFormat.getNumberInstance(Locale("es", "MX")).format(price)
