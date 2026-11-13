package com.cristopbot.estadia.data

import android.content.Context
import android.net.Uri
import com.cristopbot.estadia.Stay
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    private val appContext: Context,
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

    suspend fun searchMyListings(): List<Stay> {
        val uid = currentUid() ?: error("Inicia sesión para ver tus publicaciones")
        val docs = db.collection("stays").whereEqualTo("hostUid", uid).limit(100).get().await().documents
        return docs.mapNotNull { doc ->
            runCatching {
                Stay(
                    id = doc.id.hashCode(),
                    title = doc.getString("title") ?: return@runCatching null,
                    location = doc.getString("location") ?: return@runCatching null,
                    category = doc.getString("category") ?: "Otro",
                    price = (doc.getLong("pricePerNight") ?: return@runCatching null).toInt(),
                    cleaningFee = (doc.getLong("cleaningFee") ?: 0L).toInt(),
                    securityDeposit = (doc.getLong("securityDeposit") ?: 0L).toInt(),
                    rating = "Sin reseñas",
                    image = doc.getString("coverImageUrl") ?: "",
                    badge = "",
                    description = doc.getString("description") ?: "",
                    beds = (doc.getLong("beds") ?: 0L).toInt(),
                    guests = (doc.getLong("maxGuests") ?: 1L).toInt(),
                    firestoreId = doc.id,
                    hostUid = uid,
                    reviewCount = 0,
                    ownerName = doc.getString("hostDisplayName") ?: "Anfitrión Alozena"
                )
            }.getOrNull()
        }
    }

    suspend fun searchStays(destination: String = "", category: String = "Todo"): List<Stay> {
        val tokens = normalizeSearchTokens(destination)
        var query = db.collection("stays")
            .whereEqualTo("published", true)
            .whereEqualTo("source", "host_submitted")
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
                    cleaningFee = (doc.getLong("cleaningFee") ?: 0L).toInt(),
                    securityDeposit = (doc.getLong("securityDeposit") ?: 0L).toInt(),
                    rating = "Sin reseñas",
                    image = doc.getString("coverImageUrl") ?: "",
                    badge = doc.getString("badge") ?: "",
                    description = doc.getString("description") ?: "",
                    beds = (doc.getLong("beds") ?: 1L).toInt(),
                    guests = (doc.getLong("maxGuests") ?: 2L).toInt(),
                    firestoreId = doc.id,
                    hostUid = doc.getString("hostUid") ?: "",
                    reviewCount = 0,
                    ownerName = doc.getString("hostDisplayName") ?: "Anfitrión Alozena"
                )
            }.getOrNull()
        }
    }

    suspend fun publishListing(
        title: String,
        location: String,
        category: String,
        pricePerNight: Int,
        cleaningFee: Int,
        securityDeposit: Int,
        maxGuests: Int,
        beds: Int,
        description: String,
        photoUri: Uri
    ): String {
        val uid = currentUid() ?: error("Inicia sesión para publicar")
        require(title.trim().length in 5..90) { "El título debe tener entre 5 y 90 caracteres" }
        require(location.trim().length in 3..120) { "Escribe la ciudad o zona del alojamiento" }
        require(category in setOf("Casa", "Departamento", "Cabaña", "Habitación", "Otro"))
        require(pricePerNight in 1..10000000 && cleaningFee in 0..10000000 && securityDeposit in 0..10000000) { "Revisa el precio, limpieza y depósito" }
        require(maxGuests in 1..30 && beds in 0..30) { "Revisa huéspedes y camas" }
        require(description.trim().length in 20..2000) { "La descripción debe tener entre 20 y 2000 caracteres" }
        require(photoUri.scheme == "content") { "Elige una foto desde tu dispositivo" }
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(photoUri) ?: error("No se pudo identificar el formato de la foto")
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Elige una foto JPEG, PNG o WebP" }
        val declaredSize = resolver.openAssetFileDescriptor(photoUri, "r")?.use { it.length }
        require(declaredSize == null || declaredSize < 0L || declaredSize in 1..10_000_000) { "La foto debe medir menos de 10 MB" }
        val tokens = normalizeSearchTokens("$title $location $category")
        val ref = db.collection("stays").document()
        val photoRef = FirebaseStorage.getInstance().reference.child("listing-photos/$uid/${ref.id}/cover")
        try {
            val upload = photoRef.putFile(photoUri).await()
            require((upload.metadata?.sizeBytes ?: 0L) in 1..10_000_000) { "La foto debe medir menos de 10 MB" }
            val photoUrl = photoRef.downloadUrl.await().toString()
            ref.set(mapOf(
            "title" to title.trim(),
            "location" to location.trim(),
            "category" to category,
            "pricePerNight" to pricePerNight,
            "cleaningFee" to cleaningFee,
            "securityDeposit" to securityDeposit,
            "maxGuests" to maxGuests,
            "beds" to beds,
            "description" to description.trim(),
            "coverImageUrl" to photoUrl,
            "source" to "host_submitted",
            "searchTokens" to tokens,
            "hostUid" to uid,
            "hostDisplayName" to (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Anfitrión Alozena"),
            "published" to true,
            "moderationStatus" to "host_attested",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "reviewCount" to 0L
            )).await()
        } catch (error: Exception) {
            runCatching { photoRef.delete().await() }
            throw error
        }
        return ref.id
    }

    suspend fun deleteMyListing(stayId: String) {
        val uid = currentUid() ?: error("Inicia sesión para eliminar tu publicación")
        require(stayId.isNotBlank())
        val stayRef = db.collection("stays").document(stayId)
        val photoRef = FirebaseStorage.getInstance().reference.child("listing-photos/$uid/$stayId/cover")
        db.runTransaction { tx ->
            val stay = tx.get(stayRef)
            check(stay.exists() && stay.getString("hostUid") == uid) { "Solo puedes eliminar tus publicaciones" }
            tx.delete(stayRef)
            null
        }.await()
        runCatching { photoRef.delete().await() }
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
        val guestRows = db.collection("bookings").whereEqualTo("guestUid", uid).limit(100).get().await().documents
        val hostRows = db.collection("bookings").whereEqualTo("hostUid", uid).limit(100).get().await().documents
        return (guestRows + hostRows).distinctBy { it.id }
            .sortedByDescending { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
            .mapNotNull { doc -> doc.data?.plus("bookingId" to doc.id)?.plus("isHost" to (doc.getString("hostUid") == uid)) }
    }

    suspend fun respondToBooking(bookingId: String, status: String) {
        val uid = currentUid() ?: error("Inicia sesión para responder")
        require(status in setOf("accepted", "declined"))
        require(bookingId.isNotBlank())
        val ref = db.collection("bookings").document(bookingId)
        db.runTransaction { tx ->
            val booking = tx.get(ref)
            check(booking.getString("hostUid") == uid && booking.getString("status") == "requested") { "Solo puedes responder solicitudes pendientes de tus alojamientos" }
            tx.update(ref, mapOf("status" to status, "hostRespondedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            null
        }.await()
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
                "guestUid" to uid, "stayId" to stayId,
                "hostUid" to (stay.getString("hostUid") ?: error("Falta el anfitrión de la publicación")),
                "stayTitle" to (stay.getString("title") ?: "Alojamiento Alozena"),
                "nightlyPrice" to (stay.getLong("pricePerNight") ?: 0L),
                "cleaningFee" to (stay.getLong("cleaningFee") ?: 0L),
                "securityDeposit" to (stay.getLong("securityDeposit") ?: 0L),
                "checkIn" to com.google.firebase.Timestamp(java.util.Date.from(java.time.LocalDate.parse(checkIn).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())),
                "checkOut" to com.google.firebase.Timestamp(java.util.Date.from(java.time.LocalDate.parse(checkOut).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())),
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
            return EstadiaRepository(context.applicationContext, FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app))
        }
    }
}
