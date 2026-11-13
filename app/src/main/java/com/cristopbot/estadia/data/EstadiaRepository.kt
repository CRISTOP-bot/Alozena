package com.cristopbot.estadia.data

import android.content.Context
import com.cristopbot.estadia.Stay
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale

fun normalizeSearchTokens(value: String): List<String> = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .split(Regex("[^a-z0-9]+"))
    .filter { it.length >= 2 }
    .distinct()

sealed interface BackendState {
    data object NotConfigured : BackendState
    data object Ready : BackendState
}

/** Firestore client. Never put service-account credentials or admin keys in this APK. */
class EstadiaRepository private constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    suspend fun signInWithGoogleIdToken(idToken: String) {
        require(idToken.isNotBlank()) { "Google ID token is required" }
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    fun signOut() = auth.signOut()
    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun searchStays(destination: String = "", category: String = "Todo"): List<Stay> {
        val tokens = normalizeSearchTokens(destination)
        var query = db.collection("stays").whereEqualTo("published", true)
        if (category != "Todo") query = query.whereEqualTo("category", category)
        val docs = query.limit(100).get().await().documents
        return docs.filter { doc ->
            tokens.isEmpty() || tokens.all { token -> (doc.get("searchTokens") as? List<*>)?.contains(token) == true }
        }.take(50).mapNotNull { doc ->
            runCatching {
                Stay(
                    id = doc.getLong("id")?.toInt() ?: doc.id.hashCode(),
                    title = doc.getString("title") ?: return@runCatching null,
                    location = doc.getString("location") ?: return@runCatching null,
                    category = doc.getString("category") ?: "Casas",
                    price = (doc.getLong("pricePerNight") ?: return@runCatching null).toInt(),
                    rating = doc.getDouble("rating")?.toString() ?: "Nuevo",
                    image = doc.getString("coverImageUrl") ?: "",
                    badge = doc.getString("badge") ?: "",
                    description = doc.getString("description") ?: "",
                    beds = (doc.getLong("beds") ?: 1L).toInt(),
                    guests = (doc.getLong("maxGuests") ?: 2L).toInt()
                )
            }.getOrNull()
        }
    }

    suspend fun setFavorite(stayId: String, isFavorite: Boolean) {
        val uid = currentUid() ?: error("Inicia sesión para guardar favoritos")
        require(stayId.isNotBlank())
        val ref = db.collection("users").document(uid).collection("favorites").document(stayId)
        if (isFavorite) ref.set(mapOf("stayId" to stayId, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
        else ref.delete().await()
    }

    suspend fun getFavoriteStayIds(): Set<String> {
        val uid = currentUid() ?: error("Inicia sesión para cargar favoritos")
        return db.collection("users").document(uid).collection("favorites").get().await().documents.map { it.id }.toSet()
    }

    suspend fun getMyBookingRequests(): List<Map<String, Any?>> {
        val uid = currentUid() ?: error("Inicia sesión para consultar tus solicitudes")
        return db.collection("bookings").whereEqualTo("guestUid", uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).get().await().documents.map { it.data.orEmpty() }
    }

    suspend fun cancelMyBookingRequest(bookingId: String) {
        val uid = currentUid() ?: error("Inicia sesión para cancelar una solicitud")
        require(bookingId.isNotBlank())
        val ref = db.collection("bookings").document(bookingId)
        db.runTransaction { tx ->
            val booking = tx.get(ref)
            check(booking.getString("guestUid") == uid && booking.getString("status") == "requested") { "Solo puedes cancelar tu propia solicitud pendiente" }
            tx.update(ref, "status", "cancelled")
            null
        }.await()
    }

    /** Writes a request only; does not promise availability, payment, or confirmed booking. */
    suspend fun createBooking(stayId: String, checkIn: String, checkOut: String, guests: Int) {
        val uid = currentUid() ?: error("Inicia sesión para solicitar una reserva")
        require(stayId.isNotBlank() && guests > 0 && validIsoDate(checkIn) && validIsoDate(checkOut) && checkIn < checkOut)
        val stayRef = db.collection("stays").document(stayId)
        val bookingRef = db.collection("bookings").document()
        db.runTransaction { transaction ->
            val stay = transaction.get(stayRef)
            check(stay.exists() && stay.getBoolean("published") == true) { "El alojamiento no está disponible" }
            check(guests <= (stay.getLong("maxGuests")?.toInt() ?: 0)) { "El número de huéspedes supera la capacidad" }
            transaction.set(bookingRef, mapOf(
                "guestUid" to uid, "stayId" to stayId, "checkIn" to checkIn, "checkOut" to checkOut,
                "guests" to guests, "status" to "requested",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
            null
        }.await()
    }

    companion object {
        private val ISO_DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        private fun validIsoDate(value: String): Boolean =
            ISO_DATE.matches(value) && runCatching { java.time.LocalDate.parse(value) }.isSuccess
        fun backendState(context: Context): BackendState =
            if (FirebaseApp.getApps(context).isEmpty()) BackendState.NotConfigured else BackendState.Ready

        fun create(context: Context): EstadiaRepository {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: error("Firebase no está configurado; agrega google-services.json desde Firebase Console")
            return EstadiaRepository(FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app))
        }
    }
}
