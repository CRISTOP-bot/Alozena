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
internal fun StayDetails(
    stay: Stay,
    isSignedIn: Boolean,
    onClose: () -> Unit,
    onRequestBooking: (String, String, Int) -> Unit,
    bookingMessage: String?,
    isBookingInProgress: Boolean,
    onSignIn: () -> Unit
) {
    var guests by remember(stay.firestoreId) { mutableIntStateOf(1) }
    var checkIn by remember(stay.firestoreId) { mutableStateOf("") }
    var checkOut by remember(stay.firestoreId) { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(215.dp)) {
                AsyncImage(model = stay.image, contentDescription = "Foto publicada por el anfitrión", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.White, CircleShape)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Ink)
                }
                Text(
                    if (stay.isDemo) "Publicación de ejemplo · no representa un alojamiento disponible"
                    else "Anfitrión: ${stay.ownerName} · no verificado por Alozena",
                    Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .94f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    color = Muted
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (stay.isDemo) {
                    Text(
                        "Este contenido es solo una demostración. Las características, fotos y precios no corresponden a una oferta real.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F3F6)).padding(12.dp)
                    )
                }
                Text(stay.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(stay.ownerName, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Text(stay.location, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DetailStat(Icons.Default.Bed, stay.beds.toString(), "Camas", Modifier.weight(1f))
                    DetailStat(Icons.Default.People, stay.guests.toString(), "Huéspedes", Modifier.weight(1f))
                    DetailStat(Icons.Default.Home, stay.category, "Tipo", Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFE9EDF3))
                Text("Descripción", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stay.description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 7.dp))
                Text("Cargo de limpieza: $${formatPrice(stay.cleaningFee)} MXN", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Sin reseñas: aún no existe un sistema de valoraciones en Alozena.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFE9EDF3))
                Text("Precio publicado por noche", color = Muted, fontSize = 12.sp)
                Text("$${formatPrice(stay.price)} MXN / noche", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                if (stay.cleaningFee > 0) Text("Cargo de limpieza: $${formatPrice(stay.cleaningFee)} MXN", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (!stay.isDemo) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Huéspedes", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { if (guests > 1) guests-- }, enabled = guests > 1) { Icon(Icons.Default.RemoveCircleOutline, "Quitar huésped") }
                        Text("$guests", fontWeight = FontWeight.Bold, color = Ink)
                        IconButton(onClick = { if (guests < stay.guests) guests++ }, enabled = guests < stay.guests) { Icon(Icons.Default.AddCircleOutline, "Agregar huésped") }
                    }
                    OutlinedTextField(value = checkIn, onValueChange = { checkIn = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Entrada (AAAA-MM-DD)") }, singleLine = true)
                    OutlinedTextField(value = checkOut, onValueChange = { checkOut = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Salida (AAAA-MM-DD)") }, singleLine = true)
                    bookingMessage?.let { Text(it, color = if (it.startsWith("Solicitud enviada")) Color(0xFF237A57) else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(12.dp))
                    if (isSignedIn) {
                        Button(onClick = { onRequestBooking(checkIn, checkOut, guests) }, enabled = !isBookingInProgress && checkIn.isNotBlank() && checkOut.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                            if (isBookingInProgress) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text("Solicitar estancia")
                        }
                    } else {
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Inicia sesión para solicitar") }
                    }
                    Text("Esta solicitud no es una reserva confirmada ni bloquea automáticamente las fechas. Alozena no procesa pagos.", Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun DetailStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFFF3F4F5)).padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(19.dp))
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

