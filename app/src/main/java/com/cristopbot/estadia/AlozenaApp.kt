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
import com.cristopbot.estadia.ui.SettingsDialog
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import kotlinx.coroutines.*

@Composable
internal fun AlozenaApp(textScale: Float, onTextScaleChange: (Float) -> Unit) {
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
    var showSettingsDialog by remember { mutableStateOf(false) }
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
    var bookingMessageStayId by remember { mutableStateOf<String?>(null) }
    var bookingInProgress by remember { mutableStateOf(false) }
    val bookingSubmissionIds = remember { mutableSetOf<String>() }
    var favoritesLoaded by remember { mutableStateOf(false) }
    val backendState = remember { EstadiaRepository.backendState(context) }
    var remoteStays by remember { mutableStateOf<List<Stay>?>(null) }
    val screenConfig = rememberPublicScreenConfig(context, backendState == BackendState.Ready, preferences, scope)
    var remoteFavoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backendMessage by remember { mutableStateOf<String?>(null) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var accountMessageKind by remember { mutableStateOf(AccountMessageKind.Error) }
    val firebaseAuth = remember(backendState) {
        if (backendState == BackendState.Ready) runCatching { FirebaseAuth.getInstance(FirebaseApp.getInstance()) }.getOrNull() else null
    }
    var userEmail by remember(firebaseAuth) { mutableStateOf(firebaseAuth?.currentUser?.email) }
    LaunchedEffect(localFavoritesDb, remoteStays, userEmail) {
        val localIds = withContext(Dispatchers.IO) { localFavoritesDb.readFavorites() }
        val savedIds = guestFavoritePreferences.getStringSet("firestore_ids", emptySet()).orEmpty()
        guestFavoriteFirestoreIds = savedIds
        if (remoteStays != null || backendState == BackendState.NotConfigured) {
            favorites = StayFilters.resolveGuestFavorites(remoteStays.orEmpty(), localIds, savedIds)
            favoritesLoaded = true
        }
    }
    LaunchedEffect(selectedStay?.firestoreId) {
        bookingMessage = null
        bookingMessageStayId = selectedStay?.firestoreId
    }
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
        if (userEmail == null) accountMessage = "Inicia sesión para ver solicitudes de estancia."
        else showBookingRequests = true
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
        val isSignedIn = userEmail != null
        val firestoreIdsToRemove = liveListings.filter { it.id in savedIds }.map { it.firestoreId }
        favorites = emptySet()
        guestFavoriteFirestoreIds = emptySet()
        guestFavoritePreferences.edit().putStringSet("firestore_ids", emptySet()).apply()
        scope.launch(Dispatchers.IO) { savedIds.forEach { localFavoritesDb.setFavorite(it, false) } }
        if (backendState == BackendState.Ready && isSignedIn) {
            scope.launch {
                val repository = EstadiaRepository.create(context)
                var failedToRemoveFavorite = false
                val removedIds = mutableSetOf<String>()
                firestoreIdsToRemove.forEach { id ->
                    try {
                        repository.setFavorite(id, false)
                        removedIds.add(id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failedToRemoveFavorite = true
                    }
                }
                remoteFavoriteIds = remoteFavoriteIds - removedIds
                if (failedToRemoveFavorite) backendMessage = "No se pudieron quitar algunos favoritos. Inténtalo de nuevo."
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
                        try {
                            val repository = EstadiaRepository.create(context)
                            val id = repository.publishListing(title, location, type, price, cleaningFee, securityDeposit, guests, beds, description, photoUri)
                            publishMessage = "Publicación enviada a Alozena (ID $id). Es una declaración del anfitrión; Alozena no verificó físicamente el alojamiento."
                            try {
                                hostListings = repository.searchMyListings()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                publishMessage = "Publicación enviada a Alozena, pero no se pudo actualizar la lista. Vuelve a abrir Publicar para reintentar."
                            }
                            try {
                                remoteStays = repository.searchStays(query, category)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                backendMessage = "La publicación se guardó, pero no se pudo actualizar el catálogo."
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            publishMessage = error.localizedMessage ?: "No se pudo publicar. Revisa los datos y tu conexión."
                        } finally {
                            publishingInProgress = false
                        }
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
                onOpenSettings = { showSettingsDialog = true },
                onOpenTerms = { showTermsDialog = true },
                onOpenFavorites = { selectedTab = 1 },
                onOpenTrips = ::startBookingRequests,
                onOpenHelp = { showHelpDialog = true },
                onSignIn = ::startGoogleSignIn,
                onSignOut = {
                    firebaseAuth?.signOut()
                    remoteFavoriteIds = emptySet()
                    userEmail = null
                    accountMessageKind = AccountMessageKind.Neutral
                    accountMessage = "Se cerró la sesión de Alozena."
                }
            )
            else -> BrowsePage(
                modifier = Modifier.fillMaxSize().padding(padding),
                model = BrowsePageModel(
                    isFavoritesPage = isFavoritesPage,
                    isSearchPage = selectedTab == 4,
                    query = query,
                    category = category,
                    favorites = effectiveFavorites,
                    sortBy = sortBy,
                    results = results,
                    favoritesLoaded = favoritesLoaded,
                    backendMessage = backendMessage,
                    homeTitle = screenConfig.config?.homeTitle,
                    homeSubtitle = screenConfig.config?.homeSubtitle,
                    homeAnnouncement = screenConfig.config?.homeAnnouncement,
                    destinations = liveListings.map { it.location }.distinct().take(8)
                ),
                actions = BrowsePageActions(
                    onQueryChange = { query = it },
                    onCategoryChange = { category = it },
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenFilters = { showFilters = true },
                    onPublish = { selectedTab = 2 },
                    onClearFavorites = ::clearFavorites,
                    onSortChange = { sortBy = it },
                    onToggleFavorite = ::toggleFavorite,
                    onSelectStay = { selectedStay = it }
                )
            )
        }
    }
    selectedStay?.let { stay -> StayDetails(
        stay = stay,
        isSignedIn = userEmail != null,
        onClose = { selectedStay = null },
        onRequestBooking = { checkIn, checkOut, guestCount ->
            scope.launch {
                if (!bookingSubmissionIds.add(stay.firestoreId)) return@launch
                bookingInProgress = true
                bookingMessageStayId = stay.firestoreId
                bookingMessage = "Enviando solicitud…"
                try {
                    EstadiaRepository.create(context).createBooking(stay.firestoreId, checkIn, checkOut, guestCount)
                    if (selectedStay?.firestoreId == stay.firestoreId) {
                        bookingMessageStayId = stay.firestoreId
                        bookingMessage = "Solicitud enviada al anfitrión desde Alozena."
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (selectedStay?.firestoreId == stay.firestoreId) {
                        bookingMessageStayId = stay.firestoreId
                        bookingMessage = error.localizedMessage ?: "No se pudo enviar la solicitud."
                    }
                } finally {
                    bookingSubmissionIds.remove(stay.firestoreId)
                    bookingInProgress = false
                }
            }
        },
        bookingMessage = bookingMessage.takeIf { bookingMessageStayId == stay.firestoreId },
        isBookingInProgress = bookingInProgress,
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
    if (showSettingsDialog) {
        SettingsDialog(
            backendReady = backendState == BackendState.Ready,
            realtimeUpdatesEnabled = screenConfig.realtimeUpdatesEnabled,
            onRealtimeUpdatesChange = screenConfig.onRealtimeUpdatesChange,
            textScale = textScale,
            onTextScaleChange = onTextScaleChange,
            screenSyncStatus = screenConfig.status,
            isRefreshing = screenConfig.isRefreshing,
            settingsNotice = screenConfig.config?.settingsNotice,
            onRefreshScreens = screenConfig.onRefresh,
            onOpenTerms = {
                showSettingsDialog = false
                showTermsDialog = true
            },
            onDismiss = { showSettingsDialog = false }
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

