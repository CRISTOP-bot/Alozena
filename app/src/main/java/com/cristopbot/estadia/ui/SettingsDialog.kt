package com.cristopbot.estadia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsDialog(
    backendReady: Boolean,
    realtimeUpdatesEnabled: Boolean,
    onRealtimeUpdatesChange: (Boolean) -> Unit,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    screenSyncStatus: String,
    isRefreshing: Boolean,
    settingsNotice: String?,
    onRefreshScreens: () -> Unit,
    onOpenTerms: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Actualizaciones de pantalla", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Recibir cambios del servidor al instante",
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = realtimeUpdatesEnabled,
                        onCheckedChange = onRealtimeUpdatesChange,
                        enabled = backendReady
                    )
                }
                Text(screenSyncStatus, fontSize = 11.sp)
                Button(
                    onClick = onRefreshScreens,
                    enabled = backendReady && !isRefreshing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (isRefreshing) "Actualizando…" else "Actualizar pantallas ahora")
                }
                Spacer(Modifier.height(18.dp))
                Text("Tamaño del texto", fontWeight = FontWeight.SemiBold)
                Text("Se guarda en este dispositivo y se aplica a la app.", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0.9f to "Pequeño", 1f to "Normal", 1.15f to "Grande").forEach { (scale, label) ->
                        FilterChip(
                            selected = textScale == scale,
                            onClick = { onTextScaleChange(scale) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                settingsNotice?.let {
                    Spacer(Modifier.height(16.dp))
                    Text("Aviso de Alozena", fontWeight = FontWeight.SemiBold)
                    Text(it, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
                    Text("Términos y privacidad")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
