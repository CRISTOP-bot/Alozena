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
import com.cristopbot.estadia.data.firebaseSignInMessage
import com.cristopbot.estadia.ui.AccountMessageKind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

private val Coral = Color(0xFF10213D)
private val Ink = Color(0xFF101827)
private val Muted = Color(0xFF707887)
private val Soft = Color(0xFFF4F5F7)
private val BlueForUi = Color(0xFF10213D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Coral, onPrimary = Color.White, background = Color(0xFFF6F6F5), surface = Color.White)) { AlozenaApp() } }
    }
}

@Composable
private fun AlozenaApp() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val guestFavoritePreferences = remember(context) { context.getSharedPreferences("alozena_favorite_listing_ids", Context.MODE_PRIVATE) }
    var category by remember { mutableStateOf("Todo") }
    var maxNightlyPrice by remember { mutableFloatStateOf(10000000f) }
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var guestFavoriteFirestoreIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedStay by remember { mutableStateOf<Stay?>(null) }
    var showBookingRequests by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val categories = listOf("Todo", "Casa", "Departamento", "Cabaña", "Habitación", "Otro")
    val categoryFilters = categories
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("estadia_preferences", Context.MODE_PRIVATE) }
    var hasCompletedWelcome by remember { mutableStateOf(preferences.getBoolean("welcome_completed", false)) }
    var hasAcceptedTerms by remember { mutableStateOf(preferences.getBoolean("terms_accepted_v2", false)) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val localFavoritesDb = remember(context) { GuestFavoritesDb(context) }
    var signInInProgress by remember { mutableStateOf(false) }
    var publishingInProgress by remember { mutableStateOf(false) }
    var publishMessage by remember { mutableStateOf<String?>(null) }
    var hostListings by remember { mutableStateOf<List<Stay>>(emptyList()) }
    var bookingMessage by remember { mutableStateOf<String?>(null) }
    var favoritesLoaded by remember { mutableStateOf(false) }
    val backendState = remember { EstadiaRepository.backendState(context) }
    var remoteStays by remember { mutableStateOf<List<Stay>?>(null) }
    LaunchedEffect(localFavoritesDb, remoteStays) {
        withContext(Dispatchers.IO) { localFavoritesDb.readFavorites() }
        val savedIds = guestFavoritePreferences.getStringSet("firestore_ids", emptySet()).orEmpty()
        guestFavoriteFirestoreIds = savedIds
        if (remoteStays != null || backendState == BackendState.NotConfigured) {
            favorites = remoteStays.orEmpty().filter { it.firestoreId in savedIds }.map { it.id }.toSet()
            favoritesLoaded = true
        }
    }
    var remoteFavoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backendMessage by remember { mutableStateOf<String?>(null) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var accountMessageKind by remember { mutableStateOf(AccountMessageKind.Error) }
    val firebaseAuth = remember(backendState) {
        if (backendState == BackendState.Ready) runCatching { FirebaseAuth.getInstance(FirebaseApp.getInstance()) }.getOrNull() else null
    }
    var userEmail by remember(firebaseAuth) { mutableStateOf(firebaseAuth?.currentUser?.email) }
    LaunchedEffect(userEmail) {
        if (userEmail != null && !hasCompletedWelcome) {
            preferences.edit().putBoolean("welcome_completed", true).apply()
            hasCompletedWelcome = true
        }
    }
    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth -> userEmail = auth.currentUser?.email }
        firebaseAuth?.addAuthStateListener(listener)
        onDispose { firebaseAuth?.removeAuthStateListener(listener) }
    }
    val isFavoritesPage = selectedTab == 1
    val effectiveFavorites = if (backendState == BackendState.Ready && userEmail != null) {
        (remoteStays ?: emptyList()).filter { it.firestoreId in remoteFavoriteIds }.map { it.id }.toSet()
    } else favorites
    val liveListings = remoteStays ?: emptyList()
    val results = StayFilters.filter(liveListings, query, category, isFavoritesPage, effectiveFavorites)
        .filter { it.price <= maxNightlyPrice.toInt() }

    LaunchedEffect(backendState, userEmail, selectedTab) {
        if (backendState == BackendState.Ready && userEmail != null && selectedTab == 2) {
            runCatching { EstadiaRepository.create(context).searchMyListings() }
                .onSuccess { hostListings = it }
                .onFailure { publishMessage = it.localizedMessage ?: "No se pudieron cargar tus publicaciones." }
        }
    }
    LaunchedEffect(backendState, query, category) {
        if (backendState == BackendState.Ready) {
            runCatching { EstadiaRepository.create(context).searchStays(query, category) }
                .onSuccess { remoteStays = it; backendMessage = null }
                .onFailure { remoteStays = emptyList(); backendMessage = "No se pudieron cargar las publicaciones. Revisa tu conexión e inténtalo de nuevo." }
        } else {
            remoteStays = emptyList()
            backendMessage = "Las publicaciones aparecerán cuando haya anuncios enviados en Alozena."
            favoritesLoaded = true
        }
    }
    LaunchedEffect(backendState, isFavoritesPage, userEmail) {
        if (backendState == BackendState.Ready && isFavoritesPage && userEmail != null) {
            favoritesLoaded = false
            runCatching { EstadiaRepository.create(context).getFavoriteStayIds() }
                .onSuccess { remoteFavoriteIds = it; favoritesLoaded = true }
                .onFailure { backendMessage = "No se pudieron cargar tus favoritos."; favoritesLoaded = true }
        } else if (isFavoritesPage && userEmail == null) {
            favoritesLoaded = true
        }
    }
    fun startBookingRequests() {
        if (userEmail == null) {
            accountMessage = "Inicia sesión para ver solicitudes de estancia."
        } else {
            showBookingRequests = true
        }
    }

    fun startGoogleSignIn() {
        if (signInInProgress) return
        scope.launch {
            accountMessage = null
            signInInProgress = true
            try {
                if (backendState != BackendState.Ready) {
                    accountMessageKind = AccountMessageKind.Error
                    accountMessage = "Google no está disponible en esta instalación. Puedes seguir explorando Alozena como invitado."
                    return@launch
                }
                val clientIdResource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (clientIdResource == 0) {
                    accountMessageKind = AccountMessageKind.Error
                    accountMessage = "No pudimos preparar el acceso con Google. Puedes explorar Alozena como invitado e intentar más tarde."
                    return@launch
                }
                val hostActivity = activity
                if (hostActivity == null) {
                    accountMessageKind = AccountMessageKind.Error
                    accountMessage = "No pudimos abrir el selector de cuentas. Inténtalo de nuevo."
                    return@launch
                }
                when (val result = GoogleSignInFlow().requestIdToken(hostActivity, context.getString(clientIdResource))) {
                    is GoogleSignInResult.Token -> {
                        EstadiaRepository.create(context).signInWithGoogleIdToken(result.idToken)
                        userEmail = firebaseAuth?.currentUser?.email
                        preferences.edit().putBoolean("welcome_completed", true).apply()
                        hasCompletedWelcome = true
                        accountMessageKind = AccountMessageKind.Success
                        accountMessage = "Sesión iniciada."
                    }
                    GoogleSignInResult.NoAccounts -> {
                        accountMessageKind = AccountMessageKind.Neutral
                        accountMessage = "No encontramos cuentas de Google disponibles. Puedes seguir explorando Alozena como invitado."
                    }
                    GoogleSignInResult.Cancelled -> accountMessage = null
                    GoogleSignInResult.Failed -> {
                        accountMessageKind = AccountMessageKind.Error
                        accountMessage = "No pudimos completar el acceso con Google. Inténtalo de nuevo o sigue explorando como invitado."
                    }
                }
            } catch (error: FirebaseNetworkException) {
                Log.w("AlozenaAuth", "Network issue during Firebase sign-in", error)
                accountMessageKind = AccountMessageKind.Error
                accountMessage = firebaseSignInMessage("ERROR_NETWORK_REQUEST_FAILED")
            } catch (error: FirebaseAuthException) {
                Log.w("AlozenaAuth", "Firebase sign-in rejected", error)
                accountMessageKind = AccountMessageKind.Error
                accountMessage = firebaseSignInMessage(error.errorCode)
            } catch (error: Exception) {
                Log.e("AlozenaAuth", "Google sign-in could not be completed", error)
                accountMessageKind = AccountMessageKind.Error
                accountMessage = "No se pudo completar el acceso con Google. Inténtalo de nuevo o sigue explorando como invitado."
            } finally {
                signInInProgress = false
            }
        }
    }

    fun continueAsGuest() {
        preferences.edit().putBoolean("welcome_completed", true).apply()
        hasCompletedWelcome = true
        accountMessage = null
    }

    fun clearFavorites() {
        val savedIds = effectiveFavorites.toList()
        val guestSavedFirestoreIds = guestFavoriteFirestoreIds.toList()
        favorites = emptySet()
        guestFavoriteFirestoreIds = emptySet()
        guestFavoritePreferences.edit().putStringSet("firestore_ids", emptySet()).apply()
        scope.launch(Dispatchers.IO) { savedIds.forEach { localFavoritesDb.setFavorite(it, false) } }
        if (backendState == BackendState.Ready && userEmail != null) {
            scope.launch {
                val savedFirestoreIds = if (userEmail == null) guestSavedFirestoreIds else liveListings.filter { it.id in savedIds }.map { it.firestoreId }
                savedFirestoreIds.forEach { id -> runCatching { EstadiaRepository.create(context).setFavorite(id, false) } }
                remoteFavoriteIds = emptySet()
            }
        }
    }

    fun toggleFavorite(stay: Stay) {
        val wasFavorite = stay.id in effectiveFavorites
        favorites = if (wasFavorite) favorites - stay.id else favorites + stay.id
        if (userEmail == null) {
            guestFavoriteFirestoreIds = if (wasFavorite) guestFavoriteFirestoreIds - stay.firestoreId else guestFavoriteFirestoreIds + stay.firestoreId
            guestFavoritePreferences.edit().putStringSet("firestore_ids", guestFavoriteFirestoreIds).apply()
            scope.launch(Dispatchers.IO) { localFavoritesDb.setFavorite(stay.id, !wasFavorite) }
        }
        if (backendState == BackendState.Ready && userEmail != null) {
            scope.launch {
                runCatching { EstadiaRepository.create(context).setFavorite(stay.firestoreId, !wasFavorite) }
                    .onSuccess { remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds - stay.firestoreId else remoteFavoriteIds + stay.firestoreId }
                    .onFailure { remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds + stay.firestoreId else remoteFavoriteIds - stay.firestoreId; backendMessage = "Inicia sesión para guardar favoritos" }
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Términos y privacidad") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Alozena es una plataforma independiente. Los anuncios son declaraciones de sus anfitriones. Alozena verifica la cuenta que los publica, no confirma por sí misma la existencia física o propiedad de los alojamientos.", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Alozena todavía no ofrece reseñas ni calificaciones; no mostramos opiniones inventadas.")
                    Spacer(Modifier.height(10.dp))
                    Text("Cuenta: iniciar sesión con Google usa Firebase Authentication. Alozena no recibe ni almacena tu contraseña de Google. Los favoritos de invitado se guardan localmente; los favoritos de cuenta y publicaciones se procesan en Firebase.")
                    Spacer(Modifier.height(10.dp))
                    Text("Reservas: Alozena registra solicitudes de estancia; no procesa pagos ni confirma disponibilidad automáticamente. La confirmación debe constar en la app.")
                    Spacer(Modifier.height(10.dp))
                    Text("Uso: publica únicamente alojamientos reales y contenido del que tengas autorización. No agregues información privada ni datos de pago en las descripciones.")
                    Spacer(Modifier.height(10.dp))
                    Text("Para una publicación comercial, estos términos deben ser revisados por un adulto responsable y adaptados a las leyes aplicables.", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("Cerrar") } }
        )
    }

    if (!hasCompletedWelcome || !hasAcceptedTerms) {
        FirstRunLoginPage(
            isLoading = signInInProgress,
            backendReady = backendState == BackendState.Ready,
            errorMessage = accountMessage,
            messageKind = accountMessageKind,
            onDismissMessage = { accountMessage = null },
            termsAccepted = hasAcceptedTerms,
            onTermsClick = { showTermsDialog = true },
            onTermsAccepted = {
                preferences.edit().putBoolean("terms_accepted_v2", true).apply()
                hasAcceptedTerms = true
            },
            onGoogleSignIn = ::startGoogleSignIn,
            onContinueAsGuest = ::continueAsGuest
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFFF5F7FB),
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                val navColors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Coral, indicatorColor = Coral, unselectedIconColor = Muted, unselectedTextColor = Muted)
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Inicio", fontSize = 10.sp) }, colors = navColors)
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Buscar", fontSize = 10.sp) }, colors = navColors)
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("Publicar", fontSize = 10.sp) }, colors = navColors)
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("Favoritos", fontSize = 10.sp) }, colors = navColors)
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.PersonOutline, null) }, label = { Text("Perfil", fontSize = 10.sp) }, colors = navColors)
            }
        }
    ) { padding ->
        when (selectedTab) {
            2 -> HostListingPage(
                modifier = Modifier.fillMaxSize().padding(padding),
                isSignedIn = userEmail != null,
                isLoading = publishingInProgress,
                message = publishMessage,
                listings = hostListings,
                onDelete = { listing ->
                    scope.launch {
                        runCatching { EstadiaRepository.create(context).deleteMyListing(listing.firestoreId) }
                            .onSuccess { hostListings = hostListings.filterNot { it.firestoreId == listing.firestoreId }; remoteStays = remoteStays.orEmpty().filterNot { it.firestoreId == listing.firestoreId }; publishMessage = "Publicación eliminada de Alozena." }
                            .onFailure { error -> publishMessage = error.localizedMessage ?: "No se pudo eliminar la publicación." }
                    }
                },
                onPublish = { title, location, type, price, cleaningFee, securityDeposit, guests, beds, description, photoUri ->
                    scope.launch {
                        publishingInProgress = true
                        publishMessage = null
                        runCatching { EstadiaRepository.create(context).publishListing(title, location, type, price, cleaningFee, securityDeposit, guests, beds, description, photoUri) }
                            .onSuccess { id -> publishMessage = "Publicación enviada a Alozena (ID $id). Es una declaración del anfitrión; Alozena no verificó físicamente el alojamiento."; remoteStays = EstadiaRepository.create(context).searchStays(query, category) }
                            .onFailure { error -> publishMessage = error.localizedMessage ?: "No se pudo publicar. Revisa los datos y tu conexión." }
                        publishingInProgress = false
                    }
                },
                onSignIn = ::startGoogleSignIn
            )
            3 -> com.cristopbot.estadia.ui.AccountPage(
                modifier = Modifier.padding(padding).fillMaxSize(),
                userEmail = userEmail,
                listingCount = hostListings.size,
                favoriteCount = effectiveFavorites.size,
                onOpenListings = { selectedTab = 2 },
                backendReady = backendState == BackendState.Ready,
                statusMessage = accountMessage,
                statusKind = accountMessageKind,
                onDismissMessage = { accountMessage = null },
                isLoading = signInInProgress,
                onOpenTerms = { showTermsDialog = true },
                onOpenFavorites = { selectedTab = 1 },
                onOpenTrips = ::startBookingRequests,
                onOpenHelp = { showHelpDialog = true },
                onSignIn = ::startGoogleSignIn,
                onSignOut = {
                    firebaseAuth?.signOut()
                    favorites = emptySet()
                    remoteFavoriteIds = emptySet()
                    userEmail = null
                    accountMessageKind = AccountMessageKind.Neutral
                    accountMessage = "Se cerró la sesión de Alozena."
                }
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF6F6F5)).padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isFavoritesPage) {
                        item {
                            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, tint = Coral, modifier = Modifier.size(24.dp))
                                Text("Favoritos", Modifier.weight(1f).padding(start = 10.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                                if (effectiveFavorites.isNotEmpty()) IconButton(onClick = ::clearFavorites) { Icon(Icons.Default.DeleteOutline, "Quitar todos los favoritos", tint = Muted) }
                            }
                        }
                    } else if (selectedTab == 4) {
                        item {
                            SearchPageHeader(query = query, onQuery = { query = it }, onOpenFilters = { showFilters = true })
                        }
                    } else {
                        item {
                            AlozenaHomeHeader(
                                query = query,
                                selectedCategory = category,
                                onQuery = { query = it },
                                onOpenTerms = { showTermsDialog = true },
                                onCategory = { selected -> category = selected },
                                onDestination = { destination -> query = destination },
                                onPublish = { selectedTab = 2 },
                                destinations = liveListings.map { it.location }.distinct().take(8)
                            )
                        }
                    }
                    backendMessage?.let { message -> item { Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) } }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                                Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)); Text(" Precio y tipo", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) { Text("Tipo · ${if (category == "Todo") "Todos" else category}", fontSize = 12.sp) }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isFavoritesPage) "Tus favoritos" else if (selectedTab == 4) "Resultados de búsqueda" else "Destacados", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                            Text("${results.size} publicaciones", color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (!favoritesLoaded) {
                        item { Box(Modifier.fillMaxWidth().padding(vertical = 52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Coral) } }
                    } else if (results.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (isFavoritesPage) Icons.Default.FavoriteBorder else Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(if (isFavoritesPage) "Aún no tienes favoritos" else "Todavía no hay publicaciones", fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(if (isFavoritesPage) "Guarda un alojamiento publicado para verlo aquí." else "Sé el primero en publicar un alojamiento real en Alozena.", color = Muted, fontSize = 13.sp)
                                if (!isFavoritesPage) TextButton(onClick = { selectedTab = 2 }) { Text("Publicar alojamiento", color = Coral) }
                            }
                        }
                    } else {
                        items(results, key = { it.id }) { stay ->
                            StayCard(
                                stay = stay,
                                isFavorite = stay.id in effectiveFavorites,
                                onFavorite = { toggleFavorite(stay) },
                                onClick = { selectedStay = stay },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    selectedStay?.let { stay -> StayDetails(
        stay = stay,
        isSignedIn = userEmail != null,
        onClose = { selectedStay = null },
        onRequestBooking = { checkIn, checkOut, guestCount ->
            scope.launch {
                bookingMessage = "Enviando solicitud…"
                runCatching { EstadiaRepository.create(context).createBooking(stay.firestoreId, checkIn, checkOut, guestCount) }
                    .onSuccess { bookingMessage = "Solicitud enviada al anfitrión desde Alozena." }
                    .onFailure { error -> bookingMessage = error.localizedMessage ?: "No se pudo enviar la solicitud." }
            }
        },
        bookingMessage = bookingMessage,
        onSignIn = ::startGoogleSignIn
    ) }
    if (showBookingRequests) {
        BookingRequestsDialog(
            context = context,
            onDismiss = { showBookingRequests = false },
            signedIn = userEmail != null,
            onCancel = { id -> EstadiaRepository.create(context).cancelMyBookingRequest(id) },
            onHostResponse = { id, status -> EstadiaRepository.create(context).respondToBooking(id, status) }
        )
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Ayuda de Alozena") },
            text = { Text("Alozena muestra únicamente publicaciones enviadas por anfitriones. Las solicitudes se gestionan dentro de la app y no se procesan pagos.") },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("Entendido") } }
        )
    }
    if (showFilters) {
        CategoryFilterDialog(
            categories = categoryFilters,
            selected = category,
            maxPrice = maxNightlyPrice,
            onSelect = { category = it },
            onPriceChange = { maxNightlyPrice = it },
            onDismiss = { showFilters = false },
            onApply = { showFilters = false }
        )
    }
}

@Composable
private fun FirstRunLoginPage(
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
            Box(Modifier.background(Color(0xFFEAF1FF), CircleShape).padding(24.dp), contentAlignment = Alignment.Center) {
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
private fun AlozenaHomeHeader(
    query: String,
    selectedCategory: String,
    onQuery: (String) -> Unit,
    onOpenTerms: () -> Unit,
    onCategory: (String) -> Unit,
    onDestination: (String) -> Unit,
    onPublish: () -> Unit,
    destinations: List<String>
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F6F5)).padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Home, contentDescription = null, tint = Coral, modifier = Modifier.size(25.dp))
            Text("Alozena", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink)
            IconButton(onClick = onOpenTerms) { Icon(Icons.Default.Settings, "Configuración", tint = Ink) }
        }
        Text("Encuentra tu\\nhogar ideal", Modifier.padding(start = 20.dp, top = 6.dp), fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Renta, busca y vive mejor.", Modifier.padding(start = 20.dp, top = 6.dp, bottom = 14.dp), fontSize = 14.sp, color = Muted)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White).padding(horizontal = 14.dp),
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
                Column(Modifier.width(78.dp).clip(RoundedCornerShape(15.dp)).background(if (selectedCategory == item) Color(0xFFE7EAF0) else Color.White).clickable { onCategory(item) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun SearchPageHeader(query: String, onQuery: (String) -> Unit, onOpenFilters: () -> Unit) {
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
private fun CategoryFilterDialog(
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
private fun StayCard(stay: Stay, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(122.dp, 112.dp)) {
                AsyncImage(model = stay.image, contentDescription = "Foto del alojamiento: ${stay.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)))
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color.White.copy(alpha = .96f), CircleShape)) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos", tint = if (isFavorite) Coral else Ink, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Text(stay.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Muted, modifier = Modifier.size(14.dp)); Text(stay.location, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("${stay.beds} camas · ${stay.guests} huéspedes", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                Text("$${formatPrice(stay.price)} MXN / noche", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Publicado por ${stay.ownerName}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}


@Composable
private fun StayDetails(
    stay: Stay,
    isSignedIn: Boolean,
    onClose: () -> Unit,
    onRequestBooking: (String, String, Int) -> Unit,
    bookingMessage: String?,
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
                Text("Anfitrión: ${stay.ownerName} · no verificado por Alozena", Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .94f)).padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp, color = Muted)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
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
                    Button(onClick = { onRequestBooking(checkIn, checkOut, guests) }, enabled = checkIn.isNotBlank() && checkOut.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                        Text("Solicitar estancia")
                    }
                } else {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Inicia sesión para solicitar") }
                }
                Text("Esta solicitud no es una reserva confirmada ni bloquea automáticamente las fechas. Alozena no procesa pagos.", Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetailStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFFF3F4F5)).padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(19.dp))
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun HostListingPage(
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
private fun BookingRequestsDialog(
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


private class GuestFavoritesDb(context: Context) : SQLiteOpenHelper(context, "estadia-local.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE guest_favorites (stay_id INTEGER PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS guest_favorites (stay_id INTEGER PRIMARY KEY NOT NULL)")
    }

    fun readFavorites(): Set<Int> = readableDatabase.rawQuery("SELECT stay_id FROM guest_favorites", null).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) }
    }

    fun setFavorite(stayId: Int, saved: Boolean) {
        if (saved) writableDatabase.execSQL("INSERT OR IGNORE INTO guest_favorites(stay_id) VALUES (?)", arrayOf(stayId))
        else writableDatabase.delete("guest_favorites", "stay_id = ?", arrayOf(stayId.toString()))
    }
}

private fun bookingDateLabel(value: Any?): String = when (value) {
    is com.google.firebase.Timestamp -> java.text.SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX")).format(value.toDate())
    else -> "—"
}

private fun categoryIcon(category: String) = when (category) {
    "Casa", "Cabaña" -> Icons.Default.Home
    "Departamento" -> Icons.Default.Apartment
    "Habitación" -> Icons.Default.Bed
    "Otro" -> Icons.Default.Explore
    else -> Icons.Default.Explore
}

private fun formatPrice(price: Int): String = NumberFormat.getNumberInstance(Locale("es", "MX")).format(price)
