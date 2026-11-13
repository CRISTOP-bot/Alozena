package com.cristopbot.estadia

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthException
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.cristopbot.estadia.data.BackendState
import com.cristopbot.estadia.data.EstadiaRepository
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

private val Coral = Color(0xFF2463D3)
private val Ink = Color(0xFF152238)
private val Muted = Color(0xFF65758B)
private val Soft = Color(0xFFF1F5FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Coral, onPrimary = Color.White, background = Color(0xFFF5F7FB), surface = Color.White)) { AlozenaApp() } }
    }
}

@Composable
private fun AlozenaApp() {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todo") }
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedStay by remember { mutableStateOf<Stay?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val categories = listOf("Hoteles", "Casas", "Departamentos", "Resorts")
    val categoryFilters = listOf("Todo", "Frente al mar", "Cabañas", "Casas", "Albercas", "Ciudades")
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("estadia_preferences", Context.MODE_PRIVATE) }
    var hasCompletedWelcome by remember { mutableStateOf(preferences.getBoolean("welcome_completed", false)) }
    var hasAcceptedTerms by remember { mutableStateOf(preferences.getBoolean("terms_accepted_v1", false)) }
    var showTermsDialog by remember { mutableStateOf(false) }
    val localFavoritesDb = remember(context) { GuestFavoritesDb(context) }
    var signInInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(localFavoritesDb) { favorites = withContext(Dispatchers.IO) { localFavoritesDb.readFavorites() } }
    val backendState = remember { EstadiaRepository.backendState(context) }
    var remoteStays by remember { mutableStateOf<List<Stay>?>(null) }
    var remoteFavoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backendMessage by remember { mutableStateOf<String?>(null) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
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
        (remoteStays ?: emptyList()).filter { it.id.toString() in remoteFavoriteIds }.map { it.id }.toSet()
    } else favorites
    val results = StayFilters.filter(remoteStays ?: StayCatalog.stays, query, category, isFavoritesPage, effectiveFavorites)

    LaunchedEffect(backendState, query, category) {
        if (backendState == BackendState.Ready) {
            runCatching { EstadiaRepository.create(context).searchStays(query, category) }
                .onSuccess { remoteStays = it; backendMessage = null }
                .onFailure { backendMessage = "No se pudo cargar el catálogo de Firestore" }
        }
    }
    LaunchedEffect(backendState, isFavoritesPage, userEmail) {
        if (backendState == BackendState.Ready && isFavoritesPage && userEmail != null) {
            runCatching { EstadiaRepository.create(context).getFavoriteStayIds() }
                .onSuccess { remoteFavoriteIds = it }
                .onFailure { backendMessage = "Inicia sesión para cargar tus favoritos" }
        }
    }
    fun startGoogleSignIn() {
        if (signInInProgress) return
        scope.launch {
            accountMessage = null
            signInInProgress = true
            try {
                check(backendState == BackendState.Ready) { "Esta instalación no tiene configuración Firebase." }
                val clientIdResource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                check(clientIdResource != 0) { "Falta el cliente OAuth web. Actualiza google-services.json desde Firebase." }
                val clientId = context.getString(clientIdResource)
                val hostActivity = activity ?: error("No se pudo abrir la ventana de acceso de Google.")
                val credentialManager = CredentialManager.create(hostActivity)
                val directSignIn = GetSignInWithGoogleOption.Builder(clientId).build()
                val directRequest = GetCredentialRequest.Builder().addCredentialOption(directSignIn).build()
                val response: GetCredentialResponse = try {
                    credentialManager.getCredential(hostActivity, directRequest)
                } catch (cancelled: GetCredentialCancellationException) {
                    // Some Google Play Services builds close the button-flow after the account is chosen.
                    // Retry once with the standard account chooser before reporting a cancellation.
                    Log.w("EstadiaAuth", "Google button flow closed; retrying with account chooser", cancelled)
                    val accountChooser = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val chooserRequest = GetCredentialRequest.Builder().addCredentialOption(accountChooser).build()
                    credentialManager.getCredential(hostActivity, chooserRequest)
                }
                val credential = response.credential
                val idToken = when {
                    credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    else -> error("Google no devolvió un token de acceso válido.")
                }
                EstadiaRepository.create(context).signInWithGoogleIdToken(idToken)
                userEmail = firebaseAuth?.currentUser?.email
                preferences.edit().putBoolean("welcome_completed", true).apply()
                hasCompletedWelcome = true
                accountMessage = "Sesión iniciada."
            } catch (error: GetCredentialCancellationException) {
                Log.i("EstadiaAuth", "Google account chooser was dismissed", error)
                accountMessage = "El selector de Google se cerró antes de devolver una cuenta. Toca Continuar con Google para volver a intentarlo."
            } catch (error: NoCredentialException) {
                accountMessage = "No se encontró una cuenta de Google disponible en este dispositivo. Revisa que Google Play Services esté actualizado o agrega una cuenta en Ajustes."
            } catch (error: FirebaseAuthInvalidCredentialsException) {
                accountMessage = "Firebase rechazó la credencial de Google. Revisa que el proveedor Google esté activo y que OAuth incluya las huellas de esta app."
            } catch (error: FirebaseAuthException) {
                accountMessage = if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                    "Google todavía no está habilitado como proveedor en Firebase Authentication."
                } else {
                    "Firebase no aceptó el acceso de Google (${error.errorCode}). Revisa proveedor y configuración OAuth."
                }
            } catch (error: Exception) {
                Log.e("EstadiaAuth", "Google sign-in failed: ${error.javaClass.simpleName}", error)
                accountMessage = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "No se pudo completar el acceso con Google (${error.javaClass.simpleName}). Revisa tu conexión y vuelve a intentarlo."
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

    fun toggleFavorite(stay: Stay) {
        val wasFavorite = stay.id in effectiveFavorites
        favorites = if (wasFavorite) favorites - stay.id else favorites + stay.id
        if (userEmail == null) scope.launch(Dispatchers.IO) { localFavoritesDb.setFavorite(stay.id, !wasFavorite) }
        if (backendState == BackendState.Ready && userEmail != null) {
            scope.launch {
                runCatching { EstadiaRepository.create(context).setFavorite(stay.id.toString(), !wasFavorite) }
                    .onSuccess { remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds - stay.id.toString() else remoteFavoriteIds + stay.id.toString() }
                    .onFailure { remoteFavoriteIds = if (wasFavorite) remoteFavoriteIds + stay.id.toString() else remoteFavoriteIds - stay.id.toString(); backendMessage = "Inicia sesión para guardar favoritos" }
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Términos y privacidad") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Alozena es una app independiente para descubrir alojamientos. No está afiliada ni respaldada por Airbnb. No vende ni reserva alojamientos.", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { openExternalUrl(context, "https://www.airbnb.com/terms") }) { Text("Consultar términos de Airbnb") }
                    Spacer(Modifier.height(10.dp))
                    Text("Catálogo: los alojamientos, precios, calificaciones y descripciones mostrados dentro de Alozena son demostrativos y no son ofertas verificadas ni reseñas de huéspedes reales. Para anuncios y opiniones actuales, abre la búsqueda oficial de Airbnb desde la app.")
                    Spacer(Modifier.height(10.dp))
                    Text("Cuenta: si eliges Google, Firebase Authentication recibe los datos necesarios para autenticarte. Los favoritos de invitado se guardan localmente en este dispositivo; al borrar los datos de la app, se eliminan. Los favoritos de una sesión iniciada se sincronizan con Firebase según sus reglas.")
                    Spacer(Modifier.height(10.dp))
                    Text("Privacidad y seguridad: no introduzcas información financiera ni datos sensibles. Alozena no procesa pagos. Puedes explorar sin cuenta; iniciar sesión es opcional.")
                    Spacer(Modifier.height(10.dp))
                    Text("Uso: al continuar aceptas usar la app como demostración y entiendes que la búsqueda externa se abre en Airbnb y queda sujeta a sus propios términos y privacidad.")
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { openExternalUrl(context, "https://www.airbnb.com/terms/privacy_policy") }) { Text("Consultar privacidad de Airbnb") }
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
            termsAccepted = hasAcceptedTerms,
            onTermsClick = { showTermsDialog = true },
            onTermsAccepted = {
                preferences.edit().putBoolean("terms_accepted_v1", true).apply()
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
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("Favoritos") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.BookmarkBorder, null) }, label = { Text("Reservas") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.PersonOutline, null) }, label = { Text("Perfil") })
            }
        }
    ) { padding ->
        when (selectedTab) {
            2 -> PlaceholderPage(
                title = "Mis reservas",
                message = "Tus reservas se consultan de forma segura en Airbnb. Alozena no procesa pagos.",
                icon = Icons.Default.Luggage,
                modifier = Modifier.padding(padding),
                actionLabel = "Abrir Airbnb",
                onAction = { openAirbnbTrips(context) }
            )
            3 -> com.cristopbot.estadia.ui.AccountPage(
                modifier = Modifier.padding(padding).fillMaxSize(),
                userEmail = userEmail,
                backendReady = backendState == BackendState.Ready,
                statusMessage = accountMessage,
                isLoading = signInInProgress,
                onOpenTerms = { showTermsDialog = true },
                onSignIn = ::startGoogleSignIn,
                onSignOut = {
                    firebaseAuth?.signOut()
                    favorites = emptySet()
                    remoteFavoriteIds = emptySet()
                    userEmail = null
                    accountMessage = "Se cerró la sesión de Alozena."
                }
            )
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (isFavoritesPage) {
                        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = Coral, modifier = Modifier.size(24.dp))
                            Text("Favoritos", Modifier.weight(1f).padding(start = 10.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            IconButton(onClick = { showTermsDialog = true }) { Icon(Icons.Default.Settings, "Privacidad y términos", tint = Ink) }
                        }
                    } else {
                        AlozenaHomeHeader(
                            query = query,
                            onQuery = { query = it },
                            onOpenTerms = { showTermsDialog = true },
                            onCategory = { selected -> category = when (selected) { "Hoteles" -> "Ciudades"; "Casas" -> "Casas"; "Departamentos" -> "Ciudades"; "Resorts" -> "Frente al mar"; else -> "Todo" } },
                            onDestination = { destination -> query = destination },
                            onOpenAirbnb = { if (query.isNotBlank()) openAirbnbSearch(context, query) else openAirbnbSearch(context, "México") }
                        )
                    }
                    backendMessage?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    if (query.isNotBlank()) {
                        OutlinedButton(onClick = { openAirbnbSearch(context, query) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) { Text("Buscar alojamientos en Airbnb") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                            Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)); Text(" Filtros", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { category = "Todo" }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) { Text("Categoría · ${if (category == "Todo") "Todas" else category}", fontSize = 12.sp) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isFavoritesPage) "Tus favoritos" else "Lugares para descubrir", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                        Text("${results.size} ejemplos", color = Muted, fontSize = 12.sp)
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
                    } else {
                        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            items(results, key = { it.id }) { stay ->
                                StayCard(stay, isFavorite = stay.id in effectiveFavorites,
                                    onFavorite = { toggleFavorite(stay) },
                                    onClick = { selectedStay = stay })
                            }
                        }
                    }
                }
            }
        }
    }
    selectedStay?.let { stay -> StayDetails(stay = stay, onClose = { selectedStay = null }) }
    if (showFilters) CategoryFilterDialog(categories = categoryFilters, selected = category, onSelect = { category = it; showFilters = false }, onDismiss = { showFilters = false })
}

@Composable
private fun FirstRunLoginPage(
    isLoading: Boolean,
    backendReady: Boolean,
    errorMessage: String?,
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
            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFB3261E), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    onQuery: (String) -> Unit,
    onOpenTerms: () -> Unit,
    onCategory: (String) -> Unit,
    onDestination: (String) -> Unit,
    onOpenAirbnb: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF5F7FB))) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Home, contentDescription = null, tint = Coral, modifier = Modifier.size(27.dp))
            Text("Alozena", Modifier.weight(1f).padding(start = 9.dp), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink)
            IconButton(onClick = onOpenTerms) { Icon(Icons.Default.Settings, "Configuración y términos", tint = Ink) }
        }
        Text("¡Hola!", Modifier.padding(start = 20.dp, top = 4.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("¿A dónde quieres ir?", Modifier.padding(start = 20.dp, top = 2.dp, bottom = 12.dp), fontSize = 15.sp, color = Muted)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Coral, modifier = Modifier.size(21.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner -> Box { if (query.isBlank()) Text("Buscar destinos...", color = Muted, fontSize = 14.sp); inner() } }
            )
            if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Borrar búsqueda", tint = Muted) }
        }
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(148.dp).clickable(onClick = onOpenAirbnb),
            shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Coral)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1000",
                    contentDescription = "Paisaje de destino, imagen ilustrativa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color(0xAA10284D)))
                Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, end = 72.dp)) {
                    Text("Descubre nuevos lugares", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("Inspírate y consulta alojamientos disponibles para tu próxima aventura.", modifier = Modifier.padding(top = 5.dp), fontSize = 12.sp, color = Color.White, lineHeight = 16.sp)
                }
                IconButton(onClick = onOpenAirbnb, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color.White, CircleShape)) {
                    Icon(Icons.Default.ArrowForward, "Buscar en Airbnb", tint = Coral)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Categorías", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            TextButton(onClick = { onCategory("Todo") }) { Text("Ver todas →", color = Coral, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Hoteles", "Casas", "Departamentos", "Resorts").forEach { item ->
                Column(
                    Modifier.width(82.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onCategory(item) }.padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(categoryIcon(item), null, tint = Coral, modifier = Modifier.size(23.dp))
                    Text(item, Modifier.padding(top = 6.dp), color = Ink, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Destinos populares", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            TextButton(onClick = { onDestination("") }) { Text("Ver todos →", color = Coral, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("CDMX" to "Ciudad de México", "Cancún" to "Cancún", "Oaxaca" to "Oaxaca", "Mérida" to "Mérida").forEach { (label, destination) ->
                Column(Modifier.width(126.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onDestination(destination) }) {
                    AsyncImage(
                        model = when (label) {
                            "CDMX" -> "https://images.unsplash.com/photo-1518105779142-d975f22f1b0a?w=500"
                            "Cancún" -> "https://images.unsplash.com/photo-1510097467424-192d713fd8b2?w=500"
                            "Oaxaca" -> "https://images.unsplash.com/photo-1512813195386-6cf811ad3542?w=500"
                            else -> "https://images.unsplash.com/photo-1518509562904-e7ef99cdcc86?w=500"
                        },
                        contentDescription = "Imagen ilustrativa de $label",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(78.dp)
                    )
                    Text(label, Modifier.padding(8.dp), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onFilters: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White)
            .clickable { focusRequester.requestFocus() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = Ink, modifier = Modifier.padding(start = 16.dp).size(23.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("¿A dónde quieres ir?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(3.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) Text("Busca destinos o alojamientos", fontSize = 12.sp, color = Muted, maxLines = 1)
                        innerTextField()
                    }
                }
            )
        }
        IconButton(onClick = onFilters, modifier = Modifier.padding(horizontal = 10.dp).size(38.dp).clip(CircleShape).background(Color.White)) {
            Icon(Icons.Default.Tune, "Abrir filtros", tint = Ink, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}

@Composable
private fun CategoryFilterDialog(categories: List<String>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tipo de alojamiento", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                categories.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(item) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item, modifier = Modifier.weight(1f), color = Ink)
                        RadioButton(selected = selected == item, onClick = { onSelect(item) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = Coral) } },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
private fun StayCard(stay: Stay, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp, 106.dp)) {
                AsyncImage(model = stay.image, contentDescription = stay.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)))
                val heartScale by animateFloatAsState(if (isFavorite) 1.12f else 1f, label = "favorite-heart")
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(35.dp).background(Color.White.copy(alpha = .94f), CircleShape)) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Guardar en favoritos", tint = if (isFavorite) Coral else Ink, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = heartScale; scaleY = heartScale })
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stay.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB020), modifier = Modifier.size(15.dp)); Text(stay.rating, fontSize = 12.sp, color = Ink)
                }
                Text(stay.location, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stay.beds} camas · ${stay.guests} huéspedes", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Text("$${formatPrice(stay.price)} MXN / noche · demo", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("★ ${stay.rating} · calificación ilustrativa", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun StayDetails(stay: Stay, onClose: () -> Unit) {
    val context = LocalContext.current
    var guests by remember { mutableIntStateOf(1) }
    AlertDialog(onDismissRequest = onClose, confirmButton = {
        Button(onClick = { openAirbnbSearch(context, stay.location, guests) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Ver anuncios y reseñas reales en Airbnb") }
    }, dismissButton = {
        TextButton(onClick = onClose) { Text("Cerrar", color = Muted) }
    }, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stay.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
        }
    }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            AsyncImage(model = stay.image, contentDescription = stay.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stay.location, modifier = Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                Icon(Icons.Default.Star, null, tint = Ink, modifier = Modifier.size(16.dp)); Text(" ${stay.rating}", color = Ink, fontSize = 14.sp)
            }
            Text("${stay.guests} huéspedes · ${stay.beds} camas", modifier = Modifier.padding(top = 8.dp), color = Muted, fontSize = 13.sp)
            Text(stay.description, modifier = Modifier.padding(top = 14.dp), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            Text("Descripción de demostración · no es un anuncio real", modifier = Modifier.padding(top = 8.dp), color = Muted, fontSize = 11.sp)
            Text("Calificación ilustrativa: ${stay.rating} · sin reseñas verificadas", modifier = Modifier.padding(top = 5.dp), color = Muted, fontSize = 11.sp)
            Text("Reseñas reales: consulta el anuncio directamente en Airbnb. Alozena no inventa opiniones de huéspedes.", modifier = Modifier.padding(top = 5.dp), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFEEEEEE))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Huéspedes", fontWeight = FontWeight.SemiBold, color = Ink); Text("Hasta ${stay.guests} personas", color = Muted, fontSize = 12.sp) }
                IconButton(onClick = { if (guests > 1) guests-- }, enabled = guests > 1) { Icon(Icons.Default.RemoveCircleOutline, "Quitar huésped") }
                Text("$guests", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { if (guests < stay.guests) guests++ }, enabled = guests < stay.guests) { Icon(Icons.Default.AddCircleOutline, "Agregar huésped") }
            }
            Text("La selección de huéspedes es orientativa. Confirma precio y disponibilidad en Airbnb.", modifier = Modifier.padding(top = 12.dp), color = Muted, fontSize = 12.sp)
        }
    }, shape = RoundedCornerShape(24.dp), containerColor = Color.White)
}

@Composable
private fun PlaceholderPage(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, tint = Coral, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Ink)
            Text(message, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

private class GuestFavoritesDb(context: Context) : SQLiteOpenHelper(context, "estadia-local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE guest_favorites (stay_id INTEGER PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun readFavorites(): Set<Int> = readableDatabase.rawQuery("SELECT stay_id FROM guest_favorites", null).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) }
    }

    fun setFavorite(stayId: Int, saved: Boolean) {
        if (saved) writableDatabase.execSQL("INSERT OR IGNORE INTO guest_favorites(stay_id) VALUES (?)", arrayOf(stayId))
        else writableDatabase.delete("guest_favorites", "stay_id = ?", arrayOf(stayId.toString()))
    }
}

private fun openExternalUrl(context: Context, value: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openAirbnbSearch(context: Context, query: String, guests: Int? = null) {
    val uri = Uri.parse("https://www.airbnb.com/s/${Uri.encode(query)}/homes")
        .buildUpon()
        .apply { if (guests != null) appendQueryParameter("adults", guests.coerceAtLeast(1).toString()) }
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openAirbnbTrips(context: Context) {
    val uri = Uri.parse("https://www.airbnb.com/trips")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun categoryIcon(category: String) = when (category) {
    "Frente al mar", "Resorts" -> Icons.Default.Waves
    "Cabañas", "Casas" -> Icons.Default.Home
    "Albercas" -> Icons.Default.Pool
    "Ciudades", "Hoteles", "Departamentos" -> Icons.Default.LocationCity
    else -> Icons.Default.Explore
}

private fun formatPrice(price: Int): String = NumberFormat.getNumberInstance(Locale("es", "MX")).format(price)
