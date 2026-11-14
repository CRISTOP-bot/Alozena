package com.cristopbot.estadia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF10213D)
private val Ink = Color(0xFF101827)
private val Muted = Color(0xFF707887)
private val Page = Color(0xFFF6F6F5)

enum class AccountMessageKind { Success, Neutral, Error }

@Composable
fun AccountPage(
    modifier: Modifier = Modifier,
    userEmail: String?,
    backendReady: Boolean,
    statusMessage: String? = null,
    statusKind: AccountMessageKind = AccountMessageKind.Error,
    onDismissMessage: () -> Unit = {},
    isLoading: Boolean = false,
    accountName: String? = null,
    listingCount: Int = 0,
    favoriteCount: Int = 0,
    onOpenTerms: () -> Unit = {},
    onOpenListings: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenTrips: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Perfil", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
            IconButton(onClick = onOpenTerms) { Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Ink) }
        }
        Card(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(Color(0xFFE9EDF3), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Blue, modifier = Modifier.size(34.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text(accountName?.takeIf { it.isNotBlank() } ?: if (userEmail == null) "Explora Alozena" else "Tu cuenta", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(userEmail ?: "Explorando como invitado", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileStat(listingCount.toString(), "Publicaciones")
                    ProfileStat(favoriteCount.toString(), "Favoritos")
                    ProfileStat(if (userEmail == null) "Invitado" else "Anfitrión", "Cuenta")
                }
                if (userEmail == null && backendReady) {
                    Button(onClick = onSignIn, enabled = !isLoading, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Text("Continuar con Google", fontWeight = FontWeight.SemiBold)
                    }
                } else if (userEmail == null) {
                    Text("Puedes explorar Alozena sin iniciar sesión.", modifier = Modifier.fillMaxWidth().padding(top = 13.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                ProfileAction(Icons.Default.BookmarkBorder, "Mis publicaciones", "Administra tus alojamientos", onOpenTrips)
                ProfileAction(Icons.Default.FavoriteBorder, "Mis favoritos", "Alojamientos que guardaste", onOpenFavorites)
                ProfileAction(Icons.Default.Settings, "Configuración", "Términos y privacidad", onOpenTerms)
                ProfileAction(Icons.Default.HelpOutline, "Ayuda y soporte", "Información de Alozena", onOpenHelp)
                if (userEmail != null) ProfileAction(Icons.Default.Logout, "Cerrar sesión", "Desconectar esta cuenta", onSignOut)
            }
        }
        statusMessage?.let { message ->
            val (background, foreground, icon) = when (statusKind) {
                AccountMessageKind.Success -> Triple(Color(0xFFEAF6EF), Color(0xFF237A57), Icons.Default.CheckCircle)
                AccountMessageKind.Neutral -> Triple(Color(0xFFEDF1F5), Color(0xFF40546D), Icons.Default.PersonOutline)
                AccountMessageKind.Error -> Triple(Color(0xFFFFF1EF), Color(0xFF9D3933), Icons.Default.ErrorOutline)
            }
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = background), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 11.dp, end = 3.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = foreground, modifier = Modifier.size(19.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                        Text(message, color = foreground, fontSize = 12.sp, lineHeight = 17.sp)
                        if (statusKind == AccountMessageKind.Error && userEmail == null && backendReady) TextButton(onClick = onSignIn) { Text("Intentar de nuevo", color = foreground) }
                    }
                    IconButton(onClick = onDismissMessage) { Icon(Icons.Default.Close, contentDescription = "Descartar mensaje", tint = foreground) }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ProfileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
    }
}
