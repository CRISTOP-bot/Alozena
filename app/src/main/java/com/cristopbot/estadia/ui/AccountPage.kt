package com.cristopbot.estadia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Coral = Color(0xFFFF385C)
private val Ink = Color(0xFF222222)
private val Muted = Color(0xFF717171)

@Composable
fun AccountPage(
    modifier: Modifier = Modifier,
    userEmail: String?,
    backendReady: Boolean,
    statusMessage: String? = null,
    isLoading: Boolean = false,
    onOpenTerms: () -> Unit = {},
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.background(Color(0xFFFFF0F2), CircleShape).padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Coral, modifier = Modifier.padding(2.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (userEmail == null) "Inicia sesión" else "Tu cuenta", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !backendReady -> "La conexión con Firebase aún no está disponible. Puedes seguir explorando alojamientos de muestra."
                userEmail != null -> "Tu cuenta de Estadía está conectada con Google."
                else -> "Usa tu cuenta de Google para guardar y sincronizar tus favoritos."
            },
            color = Muted,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7))
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    userEmail != null -> {
                        Text("Sesión activa", color = Color(0xFF237A57), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(userEmail, color = Ink, fontSize = 15.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
                    }
                    backendReady -> {
                        Text("Acceso seguro", color = Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Inicia sesión con Google. Estadía no recibe ni guarda tu contraseña.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onSignIn, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                            else Text("Continuar con Google")
                        }
                    }
                    else -> Text("El inicio de sesión estará disponible cuando Firebase esté configurado en esta instalación.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onOpenTerms) { Text("Términos y privacidad", color = Coral) }
            }
        }
        statusMessage?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = if (it == "Sesión iniciada." || it == "Se cerró la sesión.") Color(0xFF237A57) else Color(0xFFB3261E), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
