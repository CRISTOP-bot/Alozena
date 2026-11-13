package com.cristopbot.estadia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import coil.compose.AsyncImage
import com.cristopbot.estadia.data.BackendState
import com.cristopbot.estadia.data.EstadiaRepository
import com.cristopbot.estadia.ui.AccountPage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Coral = Color(0xFFFF385C)
private val Ink = Color(0xFF222222)
private val Muted = Color(0xFF717171)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Coral, onPrimary = Color.White, background = Color.White, surface = Color.White)) { EstadiaApp() } }
    }
}

@Composable
private fun EstadiaApp() {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todo") }
    var localFavorites by remember { mutableStateOf(setOf<Int>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedStay by remember { mutableStateOf<Stay?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val categories = listOf("Todo", "Frente al mar", "Cabañas", "Casas", "Albercas", "Ciudades")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backendState = remember { EstadiaRepository.backendState(context) }
    val repository = remember(backendState) { if (backendState == BackendState.Ready) runCatching { EstadiaRepository.create(context) }.getOrNull() else null }
    var authUid by remember(repository) { mutableStateOf(repository?.currentUid()) }
    var signedInLabel by remember(repository) { mutableStateOf(repository?.displayNameOrEmail()) }
    var authGeneration by remember { mutableIntStateOf(0) }
    val googleWebClientId = remember(context) {
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            .takeIf { it != 0 }?.let(context::getString).orEmpty()
    }
    var remoteStays by remember { mutableStateOf<List<Stay>?>(null) }
    var remoteFavoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backendMessage by remember { mutableStateOf<String?>(null) }
    val isFavoritesPage = selectedTab == 1
    val effectiveFavorites = if (backendState == BackendState.Ready) {
        (remoteStays ?: emptyList()).filter { stay -> stay.firestoreId?.let(remoteFavoriteIds::contains) == true }.map { it.id }.toSet()
    } else localFavorites
    val results = StayFilters.filter(remoteStays ?: StayCatalog.stays, query, category, isFavoritesPage, effectiveFavorites)

    LaunchedEffect(backendState, query, category) {
        if (backendState == BackendState.Ready && repository != null) {
            runCatching { repository.searchStays(query, category) }
                .onSuccess { remoteStays = it; backendMessage = null }
                .onFailure { backendMessage = "No se pudo cargar el catálogo de Firestore" }
        }
    }
    LaunchedEffect(backendState, authUid, isFavoritesPage, authGeneration) {
        if (backendState == BackendState.Ready && repository != null && authUid != null && isFavoritesPage) {
            runCatching { repository.getFavoriteStayIds() }
                .onSuccess { remoteFavoriteIds = it }
                .onFailure { backendMessage = "No se pudieron cargar tus favoritos" }
        }
    }

    fun toggleFavorite(stay: Stay) {
        if (backendState == BackendState.Ready) {
            val stableId = stay.firestoreId ?: return
            val wasFavorite = stableId in remoteFavoriteIds
            remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds - stableId else remoteFavoriteIds + stableId
            if (repository == null || authUid == null) {
                remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds + stableId else remoteFavoriteIds - stableId
                backendMessage = "Inicia sesión para sincronizar tus favoritos"
                return
            }
            scope.launch {
                runCatching { repository.setFavorite(stableId, !wasFavorite) }
                    .onFailure {
                        remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds + stableId else remoteFavoriteIds - stableId
                        backendMessage = "No se pudo guardar el favorito"
                    }
            }
        } else {
            localFavorites = if (stay.id in localFavorites) localFavorites - stay.id else localFavorites + stay.id
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Explorar") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("Favoritos") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Luggage, null) }, label = { Text("Viajes") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.PersonOutline, null) }, label = { Text("Perfil") })
            }
        }
    ) { padding ->
        when (selectedTab) {
            2 -> TripsPage(modifier = Modifier.padding(padding), repository = repository, userId = authUid)
            3 -> AccountPage(
                modifier = Modifier.padding(padding),
                userEmail = signedInLabel,
                backendReady = backendState == BackendState.Ready,
                onSignIn = {
                    if (repository == null || googleWebClientId.isBlank()) {
                        backendMessage = "Configura Firebase Authentication y el OAuth Web client ID primero."
                    } else scope.launch {
                        runCatching {
                            val googleOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(googleWebClientId)
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                            val result = CredentialManager.create(context).getCredential(context, request)
                            val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                            repository.signInWithGoogleIdToken(token)
                        }.onSuccess {
                            authUid = repository.currentUid()
                            signedInLabel = repository.displayNameOrEmail()
                            authGeneration++
                            remoteFavoriteIds = runCatching { repository.getFavoriteStayIds() }.getOrDefault(emptySet())
                            backendMessage = null
                        }.onFailure { backendMessage = "No se pudo iniciar sesión. Verifica Firebase y OAuth." }
                    }
                },
                onSignOut = {
                    scope.launch {
                        repository?.signOut()
                        authUid = null
                        signedInLabel = null
                        authGeneration++
                        remoteFavoriteIds = emptySet()
                    }
                }
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                SearchHeader(query = query, onQuery = { query = it }, onFilters = { showFilters = true })
                backendMessage?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    categories.forEach { item ->
                        val active = category == item
                        Column(Modifier.clickable { category = item }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(categoryIcon(item), null, tint = if (active) Ink else Muted, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(item, fontSize = 11.sp, color = if (active) Ink else Muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                            Spacer(Modifier.height(7.dp))
                            if (active) Spacer(Modifier.width(28.dp).height(2.dp).background(Ink)) else Spacer(Modifier.height(2.dp))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (isFavoritesPage) Icons.Default.FavoriteBorder else Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(if (isFavoritesPage) "Aún no tienes favoritos" else "No encontramos alojamientos", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(if (isFavoritesPage) "Toca el corazón de un alojamiento para guardarlo." else "Prueba otra ciudad o categoría.", color = Muted, fontSize = 13.sp)
                        }
                    }
                } else LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    items(results, key = { it.firestoreId ?: it.id.toString() }) { stay ->
                        val isFavorite = if (backendState == BackendState.Ready) stay.firestoreId?.let(remoteFavoriteIds::contains) == true else stay.id in localFavorites
                        StayCard(stay, isFavorite = isFavorite, onFavorite = { toggleFavorite(stay) }, onClick = { selectedStay = stay })
                    }
                }
            }
        }
    }
    selectedStay?.let { stay -> StayDetails(stay = stay, onClose = { selectedStay = null }) }
    if (showFilters) CategoryFilterDialog(categories = categories, selected = category, onSelect = { category = it; showFilters = false }, onDismiss = { showFilters = false })
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onFilters: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(64.dp).clip(RoundedCornerShape(32.dp)).background(Color.White).clickable { focusRequester.requestFocus() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = Ink, modifier = Modifier.padding(start = 16.dp).size(23.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("¿A dónde quieres ir?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(3.dp))
            BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }), decorationBox = { innerTextField ->
                    Box { if (query.isEmpty()) Text("Busca destinos o alojamientos", fontSize = 12.sp, color = Muted, maxLines = 1); innerTextField() }
                })
        }
        IconButton(onClick = onFilters, modifier = Modifier.padding(horizontal = 10.dp).size(38.dp).clip(CircleShape).background(Color.White)) { Icon(Icons.Default.Tune, "Abrir filtros", tint = Ink, modifier = Modifier.size(20.dp)) }
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}

@Composable
private fun CategoryFilterDialog(categories: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tipo de alojamiento", fontWeight = FontWeight.Bold, color = Ink) }, text = {
        Column { categories.forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(item) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item, modifier = Modifier.weight(1f), color = Ink); RadioButton(selected = selected == item, onClick = { onSelect(item) })
            }
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = Coral) } }, shape = RoundedCornerShape(22.dp), containerColor = Color.White)
}

@Composable
private fun StayCard(stay: Stay, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(model = stay.image, contentDescription = stay.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
            Text(stay.badge, Modifier.align(Alignment.TopStart).padding(13.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(42.dp)) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Guardar en favoritos", tint = if (isFavorite) Coral else Color.White, modifier = Modifier.size(27.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stay.location, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.Star, null, tint = Ink, modifier = Modifier.size(15.dp)); Text(stay.rating, fontSize = 13.sp, color = Ink, modifier = Modifier.padding(start = 3.dp))
        }
        Text("Alojamiento · ${stay.beds} ${if (stay.beds == 1) "cama" else "camas"}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Disponible durante todo el año", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("$${formatPrice(stay.price)} MXN", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink); Text(" noche", fontSize = 13.sp, color = Ink) }
    }
}

@Composable
private fun StayDetails(stay: Stay, onClose: () -> Unit) {
    var guests by remember { mutableIntStateOf(1) }
    var booked by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, confirmButton = { Button(onClick = { if (booked) onClose() else booked = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(if (booked) "Listo" else "Solicitar reserva · demo") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cerrar", color = Muted) } }, title = {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stay.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") } }
    }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            AsyncImage(model = stay.image, contentDescription = stay.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(stay.location, modifier = Modifier.weight(1f), color = Muted, fontSize = 14.sp); Icon(Icons.Default.Star, null, tint = Ink, modifier = Modifier.size(16.dp)); Text(" ${stay.rating}", color = Ink, fontSize = 14.sp) }
            Text("${stay.guests} huéspedes · ${stay.beds} camas", modifier = Modifier.padding(top = 8.dp), color = Muted, fontSize = 13.sp)
            Text(stay.description, modifier = Modifier.padding(top = 14.dp), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFEEEEEE))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Huéspedes", fontWeight = FontWeight.SemiBold, color = Ink); Text("Hasta ${stay.guests} personas", color = Muted, fontSize = 12.sp) }
                IconButton(onClick = { if (guests > 1) guests-- }, enabled = guests > 1) { Icon(Icons.Default.RemoveCircleOutline, "Quitar huésped") }
                Text("$guests", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { if (guests < stay.guests) guests++ }, enabled = guests < stay.guests) { Icon(Icons.Default.AddCircleOutline, "Agregar huésped") }
            }
            if (booked) Text("Solicitud solo de demostración: no se creó reserva ni hubo cobro.", modifier = Modifier.padding(top = 12.dp), color = Color(0xFF237A57), fontSize = 13.sp)
        }
    }, shape = RoundedCornerShape(24.dp), containerColor = Color.White)
}

@Composable
private fun PlaceholderPage(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) { Icon(icon, null, tint = Coral, modifier = Modifier.size(46.dp)); Spacer(Modifier.height(14.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink); Text(message, color = Muted, fontSize = 14.sp) } }
}

private fun categoryIcon(category: String) = when (category) {
    "Frente al mar" -> Icons.Default.Waves
    "Cabañas" -> Icons.Default.Cabin
    "Casas" -> Icons.Default.Home
    "Albercas" -> Icons.Default.Pool
    "Ciudades" -> Icons.Default.LocationCity
    else -> Icons.Default.Explore
}

private fun formatPrice(price: Int): String = NumberFormat.getNumberInstance(Locale("es", "MX")).format(price)
