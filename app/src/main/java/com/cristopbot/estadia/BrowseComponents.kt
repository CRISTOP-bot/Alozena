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
internal fun FirstRunLoginPage(
    isLoading: Boolean,
    backendReady: Boolean,
    errorMessage: String?,
    messageKind: AccountMessageKind,
    onDismissMessage: () -> Unit,
    termsAccepted: Boolean,
    onTermsClick: () -> Unit,
    onTermsAccepted: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
    ) {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.background(Color(0xFFEAF3F8), CircleShape).padding(24.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Coral, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Alozena", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(5.dp))
            Text("Tu próxima estancia, más cerca", color = Muted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onGoogleSignIn,
                enabled = backendReady && !isLoading && termsAccepted,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Continuar con Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!backendReady) {
                Spacer(Modifier.height(8.dp))
                Text("Google no está disponible en esta instalación; puedes explorar Alozena como invitado.", color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            errorMessage?.let { message ->
                val color = when (messageKind) {
                    AccountMessageKind.Success -> Color(0xFF237A57)
                    AccountMessageKind.Neutral -> Muted
                    AccountMessageKind.Error -> Color(0xFF9D3933)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message, color = color, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (messageKind == AccountMessageKind.Error && backendReady) {
                            TextButton(onClick = onGoogleSignIn, enabled = !isLoading) { Text("Intentar de nuevo", color = color) }
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = onDismissMessage) { Icon(Icons.Default.Close, contentDescription = "Descartar mensaje", tint = color) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = termsAccepted, onCheckedChange = { if (it) onTermsAccepted() })
                Text("Acepto los términos y la información de privacidad", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onTermsClick))
            }
            TextButton(onClick = onTermsClick) { Text("Leer términos y privacidad", color = Coral) }
            TextButton(onClick = onContinueAsGuest, enabled = !isLoading && termsAccepted) {
                Text("Explorar como invitado", color = Ink, fontSize = 14.sp)
            }
        }
    }
    }
}

@Composable
internal fun AlozenaHomeHeader(
    query: String,
    selectedCategory: String,
    onQuery: (String) -> Unit,
    onOpenTerms: () -> Unit,
    onCategory: (String) -> Unit,
    onDestination: (String) -> Unit,
    onPublish: () -> Unit,
    destinations: List<String>
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF7F9FB)).padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Color(0xFFEAF3F8), RoundedCornerShape(10.dp)).padding(6.dp)) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
            }
            Text("Alozena", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink)
            IconButton(onClick = onOpenTerms) { Icon(Icons.Default.Settings, "Configuración", tint = Ink) }
        }
        Text("Encuentra tu\nhogar ideal", Modifier.padding(start = 20.dp, top = 6.dp), fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Renta, busca y vive mejor.", Modifier.padding(start = 20.dp, top = 6.dp, bottom = 14.dp), fontSize = 14.sp, color = Muted)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White).border(1.dp, BorderSoft, RoundedCornerShape(17.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
            BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f).padding(start = 10.dp), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner -> Box { if (query.isBlank()) Text("Buscar por ciudad, zona o barrio...", color = Muted, fontSize = 13.sp); inner() } })
            if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Borrar búsqueda", tint = Muted) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Categorías", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            TextButton(onClick = { onCategory("Todo") }) { Text("Ver todas →", color = Muted, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Casa", "Departamento", "Habitación", "Cabaña", "Otro").forEach { item ->
                Column(
                    Modifier.width(78.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (selectedCategory == item) Color(0xFFEAF3F8) else Color.White)
                        .border(1.dp, if (selectedCategory == item) Color(0xFFD7E8F1) else BorderSoft, RoundedCornerShape(15.dp))
                        .clickable { onCategory(item) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(categoryIcon(item), null, tint = Coral, modifier = Modifier.size(23.dp))
                    Text(item, Modifier.padding(top = 6.dp), color = Ink, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("¿Tienes un alojamiento?", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Publícalo directamente en Alozena.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = onPublish) { Text("Publicar  +", color = Coral, fontWeight = FontWeight.SemiBold) }
        }
        if (destinations.isNotEmpty()) {
            Text("Zonas con publicaciones", Modifier.padding(start = 20.dp, top = 10.dp, bottom = 3.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                destinations.forEach { destination -> AssistChip(onClick = { onDestination(destination) }, label = { Text(destination, fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }) }
            }
        }
    }
}

@Composable
internal fun SearchPageHeader(query: String, onQuery: (String) -> Unit, onOpenFilters: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buscar alojamientos", Modifier.weight(1f), color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenFilters) { Icon(Icons.Default.Tune, "Filtros", tint = Ink) }
        }
        Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3F4F5)).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Muted)
            BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f).padding(start = 9.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), decorationBox = { inner -> Box { if (query.isBlank()) Text("Ciudad, zona o barrio", color = Muted, fontSize = 13.sp); inner() } })
            if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Limpiar", tint = Muted) }
        }
    }
}


@Composable
internal fun CategoryFilterDialog(
    categories: List<String>,
    selected: String,
    maxPrice: Float,
    onSelect: (String) -> Unit,
    onPriceChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrar alojamientos", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Tipo de lugar", fontWeight = FontWeight.SemiBold, color = Ink)
                categories.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(item) }.padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item, modifier = Modifier.weight(1f), color = Ink)
                        RadioButton(selected = selected == item, onClick = { onSelect(item) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Precio máximo por noche", fontWeight = FontWeight.SemiBold, color = Ink)
                Text("$${formatPrice(maxPrice.toInt())} MXN", color = Muted, fontSize = 13.sp)
                Slider(value = maxPrice, onValueChange = onPriceChange, valueRange = 1000f..10000000f)
                Text("Reseñas", fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 8.dp))
                Text("Las reseñas no están habilitadas todavía.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Aplicar", color = Coral) } },
        dismissButton = { TextButton(onClick = { onSelect("Todo"); onPriceChange(10000000f) }) { Text("Restablecer", color = Muted) } },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
internal fun StayCard(stay: Stay, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSoft)
    ) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(122.dp, 112.dp)) {
                AsyncImage(model = stay.image, contentDescription = "Foto del alojamiento: ${stay.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)))
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color.White.copy(alpha = .96f), CircleShape)) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos", tint = if (isFavorite) Coral else Ink, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                if (stay.isDemo) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFF0F3F6),
                        tonalElevation = 0.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text("EJEMPLO · NO RESERVABLE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                } else if (stay.badge.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFEAF3F8),
                        tonalElevation = 0.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(stay.badge, color = Coral, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(stay.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Muted, modifier = Modifier.size(14.dp)); Text(stay.location, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("${stay.beds} camas · ${stay.guests} huéspedes", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    if (stay.reviewCount > 0) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFB68A2C), modifier = Modifier.size(13.dp))
                        Text(stay.rating, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                    } else {
                        Text("Sin reseñas", color = Muted, fontSize = 10.sp)
                    }
                }
                Text("$${formatPrice(stay.price)} MXN / noche", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("Publicado por ${stay.ownerName}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}


