package com.cristopbot.estadia


import android.os.Bundle
import android.net.Uri
import android.app.Activity
import android.content.Context
import android.util.Log
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.cristopbot.estadia.data.BackendState
import com.cristopbot.estadia.data.EstadiaRepository
import com.cristopbot.estadia.data.GoogleSignInFlow
import com.cristopbot.estadia.data.GoogleSignInResult
import com.cristopbot.estadia.data.SeedData
import com.cristopbot.estadia.data.firebaseSignInMessage
import com.cristopbot.estadia.ui.AccountMessageKind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale


@Composable
internal fun HostListingPage(
    modifier: Modifier,
    isSignedIn: Boolean,
    isLoading: Boolean,
    message: String?,
    listings: List<Stay>,
    onDelete: (Stay) -> Unit,
    onPublish: (String, String, String, Int, Int, Int, Int, Int, String, Uri) -> Unit,
    onSignIn: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Casa") }
    var price by remember { mutableStateOf("") }
    var cleaningFee by remember { mutableStateOf("0") }
    var securityDeposit by remember { mutableStateOf("0") }
    var guests by remember { mutableStateOf("2") }
    var beds by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { selected -> photoUri = selected }
    var hostAttested by remember { mutableStateOf(false) }
    var publishStep by remember { mutableIntStateOf(0) }
    Column(modifier.background(Color(0xFFF6F6F5)).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Publicar anuncio", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("1  Detalles", "2  Fotos", "3  Precio").forEachIndexed { index, label ->
                Text(label, color = if (publishStep == index) Ink else Muted, fontSize = 12.sp, fontWeight = if (publishStep == index) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (publishStep == index) Color.White else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
        Text("Completa la información del lugar. Al enviar, se publica en Alozena bajo tu responsabilidad; la app no verifica físicamente la propiedad.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        if (isSignedIn) {
            Text("Tus publicaciones", Modifier.padding(top = 8.dp), color = Ink, fontWeight = FontWeight.SemiBold)
            if (listings.isEmpty()) Text("Aún no has publicado alojamientos.", Modifier.padding(top = 4.dp), color = Muted, fontSize = 12.sp)
            listings.forEach { listing ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listing.title, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("${listing.location} · $${formatPrice(listing.price)} MXN / noche · limpieza $${formatPrice(listing.cleaningFee)} · depósito $${formatPrice(listing.securityDeposit)}", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDelete(listing) }) { Text("Eliminar", color = Color(0xFFB3261E)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Nueva publicación", color = Ink, fontWeight = FontWeight.SemiBold)
        }
        if (!isSignedIn) {
            Text("Inicia sesión para crear una publicación vinculada a tu cuenta de anfitrión.", color = Muted, fontSize = 13.sp)
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Iniciar sesión con Google") }
        } else {
            if (publishStep == 0) {
                Text("Tipo de propiedad", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Casa", "Departamento", "Habitación", "Otro").forEach { type ->
                        FilterChip(selected = category == type, onClick = { category = type }, label = { Text(type) }, leadingIcon = { Icon(categoryIcon(type), null, modifier = Modifier.size(17.dp)) })
                    }
                }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Título del anuncio") }, placeholder = { Text("Ej. Casa en zona céntrica") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Ciudad y zona") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp).padding(top = 8.dp), label = { Text("Descripción real del alojamiento") }, placeholder = { Text("Describe tu propiedad...") }, shape = RoundedCornerShape(13.dp))
                Button(onClick = { publishStep = 1 }, enabled = title.isNotBlank() && location.isNotBlank() && description.length >= 20, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Siguiente  →") }
            } else if (publishStep == 1) {
                Text("Fotos del alojamiento", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Selecciona una foto real desde tu dispositivo.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp)); Text(if (photoUri == null) "  Elegir foto" else "  Cambiar foto")
                }
                photoUri?.let { selectedPhoto -> AsyncImage(model = selectedPhoto, contentDescription = "Vista previa de la foto elegida", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 10.dp).clip(RoundedCornerShape(14.dp))) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hostAttested, onCheckedChange = { hostAttested = it })
                    Text("Confirmo que el alojamiento existe y tengo autorización para publicar estos datos y fotos.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Text("Alozena no verifica físicamente la propiedad.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { publishStep = 0 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Atrás") }
                    Button(onClick = { publishStep = 2 }, enabled = photoUri != null && hostAttested, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Siguiente  →") }
                }
            } else {
                Text("Precio y capacidad", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Precio por noche (MXN)") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                OutlinedTextField(cleaningFee, { cleaningFee = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Cargo de limpieza (MXN)") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                OutlinedTextField(securityDeposit, { securityDeposit = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Depósito de seguridad (MXN)") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(guests, { guests = it.filter(Char::isDigit) }, Modifier.weight(1f).padding(top = 8.dp), label = { Text("Huéspedes") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                    OutlinedTextField(beds, { beds = it.filter(Char::isDigit) }, Modifier.weight(1f).padding(top = 8.dp), label = { Text("Camas") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                }
                OutlinedButton(onClick = { publishStep = 1 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(13.dp)) { Text("Atrás") }
            }
            if (publishStep == 2) Button(
                onClick = { photoUri?.let { selectedPhoto -> onPublish(title, location, category, price.toIntOrNull() ?: 0, cleaningFee.toIntOrNull() ?: 0, securityDeposit.toIntOrNull() ?: 0, guests.toIntOrNull() ?: 0, beds.toIntOrNull() ?: 0, description, selectedPhoto) } },
                enabled = publishStep == 2 && !isLoading && hostAttested && photoUri != null && title.isNotBlank() && location.isNotBlank() && price.isNotBlank() && description.length >= 20,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp), shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Publicar anuncio")
            }
        }
        message?.let { Text(it, color = if (it.startsWith("Publicación enviada")) Color(0xFF237A57) else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
internal fun BookingRequestsDialog(
    context: Context,
    onDismiss: () -> Unit,
    signedIn: Boolean,
    onCancel: suspend (String) -> Unit,
    onHostResponse: suspend (String, String) -> Unit
) {
    var rows by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(signedIn) {
        if (signedIn) {
            loading = true
            runCatching { EstadiaRepository.create(context).getMyBookingRequests() }
                .onSuccess { rows = it }
                .onFailure { error -> message = error.localizedMessage ?: "No se pudieron cargar tus solicitudes." }
            loading = false
        } else message = "Inicia sesión para ver tus solicitudes de Alozena."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitudes de Alozena") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (loading) CircularProgressIndicator()
                else if (rows.isEmpty()) Text(message ?: "Aún no tienes solicitudes de estancia.", color = Muted)
                rows.forEach { row ->
                    Text("${row["stayTitle"] ?: "Alojamiento Alozena"}", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text("${bookingDateLabel(row["checkIn"])} → ${bookingDateLabel(row["checkOut"])} · ${row["guests"] ?: 0} huéspedes", color = Muted, fontSize = 12.sp)
                    Text("Estado: ${row["status"] ?: "Pendiente"}", color = Muted, fontSize = 12.sp)
                    if (row["status"] == "requested") {
                        val id = row["bookingId"] as? String
                        if (row["isHost"] == true && id != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { scope.launch {
                                    runCatching { onHostResponse(id, "accepted") }
                                        .onSuccess { rows = rows.map { existing -> if (existing["bookingId"] == id) existing + ("status" to "accepted") else existing }; actionMessage = "El anfitrión aceptó la solicitud en Alozena." }
                                        .onFailure { error -> actionMessage = error.localizedMessage ?: "No se pudo confirmar." }
                                } }) { Text("Aceptar solicitud", color = BlueForUi) }
                                TextButton(onClick = { scope.launch {
                                    runCatching { onHostResponse(id, "declined") }
                                        .onSuccess { rows = rows.map { existing -> if (existing["bookingId"] == id) existing + ("status" to "declined") else existing }; actionMessage = "Solicitud rechazada." }
                                        .onFailure { error -> actionMessage = error.localizedMessage ?: "No se pudo rechazar." }
                                } }) { Text("Rechazar", color = Muted) }
                            }
                        } else if (id != null) TextButton(onClick = {
                            scope.launch {
                                runCatching { onCancel(id) }
                                    .onSuccess { rows = rows.map { existing -> if (existing["bookingId"] == id) existing + ("status" to "cancelled") else existing }; actionMessage = "Solicitud cancelada en Alozena." }
                                    .onFailure { error -> actionMessage = error.localizedMessage ?: "No se pudo cancelar la solicitud." }
                            }
                        }) { Text("Cancelar solicitud", color = BlueForUi) }
                    }
                }
                actionMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}



internal fun bookingDateLabel(value: Any?): String = when (value) {
    is com.google.firebase.Timestamp -> java.text.SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX")).format(value.toDate())
    else -> "—"
}

