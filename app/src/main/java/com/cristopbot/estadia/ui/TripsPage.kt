package com.cristopbot.estadia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cristopbot.estadia.data.EstadiaRepository

@Composable
fun TripsPage(modifier: Modifier = Modifier, repository: EstadiaRepository?, userId: String?) {
    var bookings by remember(userId) { mutableStateOf<List<Pair<String, Map<String, Any?>>>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var loaded by remember(userId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, userId) {
        if (repository != null && userId != null) {
            loading = true
            runCatching { repository.getMyBookingRequests() }
                .onSuccess { bookings = it }
                .onFailure { error = "No se pudieron cargar las solicitudes." }
            loading = false
            loaded = true
        }
    }
    Column(modifier.fillMaxSize().padding(24.dp)) {
        Text("Tus viajes", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.padding(top = 8.dp))
        when {
            repository == null -> Text("Firebase todavía no está configurado; aquí aparecerán tus viajes cuando conectes el backend.", color = Color(0xFF717171))
            userId == null -> Text("Inicia sesión para consultar tus solicitudes.", color = Color(0xFF717171))
            loading || !loaded -> CircularProgressIndicator()
            error != null -> Text(error!!, color = Color(0xFF717171))
            bookings.isEmpty() -> Text("Aún no tienes solicitudes de viaje.", color = Color(0xFF717171))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bookings, key = { it.first }) { (bookingId, booking) ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Alojamiento: ${booking["stayId"] ?: "—"}", fontWeight = FontWeight.SemiBold)
                        val checkIn = (booking["checkIn"] as? com.google.firebase.Timestamp)?.toDate()?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "—"
                        val checkOut = (booking["checkOut"] as? com.google.firebase.Timestamp)?.toDate()?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "—"
                        Text("$checkIn → $checkOut", color = Color(0xFF717171))
                        Text("Estado: ${booking["status"] ?: "—"} · ${booking["guests"] ?: "—"} huéspedes", color = Color(0xFF717171))
                        if (booking["status"] == "requested") androidx.compose.material3.TextButton(onClick = { scope.launch { runCatching { repository.cancelMyBookingRequest(bookingId) }.onSuccess { bookings = repository.getMyBookingRequests() }.onFailure { error = "No se pudo cancelar la solicitud." } } }) { Text("Cancelar solicitud") }
                    }
                }
            }
        }
    }
}
