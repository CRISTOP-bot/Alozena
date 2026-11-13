package com.cristopbot.estadia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountPage(
    modifier: Modifier = Modifier,
    userEmail: String?,
    backendReady: Boolean,
    statusMessage: String? = null,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tu espacio", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !backendReady -> "El backend Firebase todavía no está configurado. La app usa datos de muestra."
                userEmail != null -> userEmail
                else -> "Inicia sesión para sincronizar tus favoritos."
            },
            color = Color(0xFF717171)
        )
        Spacer(Modifier.height(18.dp))
        statusMessage?.let {
            Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }
        if (backendReady) {
            if (userEmail == null) Button(onClick = onSignIn) { Text("Continuar con Google") }
            else OutlinedButton(onClick = onSignOut) { Text("Cerrar sesión") }
        }
    }
}
