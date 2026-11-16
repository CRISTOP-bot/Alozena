package com.cristopbot.estadia

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cristopbot.estadia.data.*
import com.cristopbot.estadia.ui.AccountMessageKind
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import kotlinx.coroutines.*

@Composable
internal fun AlozenaApp() {
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
    var sortBy by remember { mutableStateOf(StaySortOption.RELEVANCE) }
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
    val results = StayFilters.filter(liveListings, query, category, isFavoritesPage, effectiveFavorites, sortBy)
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
            remoteStays = SeedData.stays.filter { stay ->
                val matchesQuery = query.isBlank() || stay.location.contains(query, ignoreCase = true) || stay.title.contains(query, ignoreCase = true)
                val matchesCategory = category == "Todo" || stay.category == category
                matchesQuery && matchesCategory
            }
            backendMessage = "Catálogo demo local: conectando con Firebase para publicar más alojamientos reales."
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
                val serverClientId = context.googleWebClientId()
                if (serverClientId == null) {
                    accountMessageKind = AccountMessageKind.Error
                    accountMessage = "Falta configurar el cliente OAuth web de Firebase. Actualiza la configuración de la app e inténtalo de nuevo."
                    return@launch
                }
                val hostActivity = activity
                if (hostActivity == null) {
                    accountMessageKind = AccountMessageKind.Error
                    accountMessage = "No pudimos abrir el selector de cuentas. Inténtalo de nuevo."
                    return@launch
                }
                when (val result = GoogleSignInFlow().requestIdToken(hostActivity, serverClientId)) {
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
                        accountMessage = "No pudimos validar el acceso con Google. Revisa la configuración de Firebase o continúa como invitado."
                    }
                }
            } catch (error: FirebaseNetworkException) {
                Log.w("AlozenaAuth", "Firebase sign-in failed because of a network error")
                accountMessageKind = AccountMessageKind.Error
                accountMessage = firebaseSignInMessage("ERROR_NETWORK_REQUEST_FAILED")
            } catch (error: FirebaseAuthException) {
                Log.w("AlozenaAuth", "Firebase sign-in rejected (${error.errorCode})")
                accountMessageKind = AccountMessageKind.Error
                accountMessage = firebaseSignInMessage(error.errorCode)
            } catch (error: Exception) {
                Log.e("AlozenaAuth", "Google sign-in failed (${error.javaClass.simpleName})")
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
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Coral,
                        selectedTextColor = Coral,
                        indicatorColor = Color(0xFFEAF2F7),
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted
                    )
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
                    backendMessage?.let { message ->
                        item {
                            Surface(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0F3F6)
                            ) {
                                Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)); Text(" Precio y tipo", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) { Text("Tipo · ${if (category == "Todo") "Todos" else category}", fontSize = 12.sp) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    StaySortOption.RELEVANCE to "Relevancia",
                                    StaySortOption.PRICE_ASC to "Precio ↓",
                                    StaySortOption.PRICE_DESC to "Precio ↑",
                                    StaySortOption.NEWEST to "Nuevos"
                                ).forEach { (option, label) ->
                                    FilterChip(
                                        selected = sortBy == option,
                                        onClick = { sortBy = option },
                                        label = { Text(label, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Coral.copy(alpha = 0.12f),
                                            containerColor = Color.White,
                                            selectedLabelColor = Coral,
                                            labelColor = Ink
                                        )
                                    )
                                }
                            }
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

