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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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

private val Blue = Color(0xFF2463D3)
private val Ink = Color(0xFF152238)
private val Muted = Color(0xFF65758B)

@Composable
fun AccountPage(
    modifier: Modifier = Modifier,
    userEmail: String?,
    backendReady: Boolean,
    statusMessage: String? = null,
    isLoading: Boolean = false,
    onOpenTerms: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenTrips: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFFF5F7FB)).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Perfil", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
            androidx.compose.material3.IconButton(onClick = onOpenTerms) { Icon(Icons.Default.Settings, contentDescription = "Configuración y privacidad", tint = Muted) }
        }
        Card(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).background(Color(0xFFE5EDFA), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Blue, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(if (userEmail == null) "Invitado de Alozena" else "Tu cuenta", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(userEmail ?: "Explorando sin iniciar sesión", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        if (userEmail == null && backendReady) {
            Button(onClick = onSignIn, enabled = !isLoading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp)) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                else Text("Continuar con Google")
            }
        } else if (!backendReady) {
            Text("Puedes explorar Alozena sin cuenta. Firebase no está disponible en esta instalación.", modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Card(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ProfileAction(Icons.Default.BookmarkBorder, "Mis reservas", "Consulta tus solicitudes de Alozena", onOpenTrips)
                ProfileAction(Icons.Default.FavoriteBorder, "Mis favoritos", "Alojamientos que guardaste", onOpenFavorites)
                ProfileAction(Icons.Default.Info, "Términos y privacidad", "Cómo funciona Alozena", onOpenTerms)
                ProfileAction(Icons.Default.HelpOutline, "Ayuda", "Información de la aplicación", onOpenHelp)
                if (userEmail != null) ProfileAction(Icons.Default.Logout, "Cerrar sesión", "Desconectar esta cuenta", onSignOut)
            }
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = if (it.startsWith("Sesión iniciada")) Color(0xFF237A57) else Color(0xFFB3261E), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
    }
}
