package com.cristopbot.estadia.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PublicScreenConfigState(
    val config: PublicScreenConfig?,
    val realtimeUpdatesEnabled: Boolean,
    val status: String,
    val isRefreshing: Boolean,
    val onRealtimeUpdatesChange: (Boolean) -> Unit,
    val onRefresh: () -> Unit
)

@Composable
internal fun rememberPublicScreenConfig(
    context: Context,
    backendReady: Boolean,
    preferences: SharedPreferences,
    scope: CoroutineScope
): PublicScreenConfigState {
    var config by remember { mutableStateOf<PublicScreenConfig?>(null) }
    var realtimeUpdatesEnabled by remember {
        mutableStateOf(preferences.getBoolean("realtime_screen_updates", true))
    }
    var status by remember(backendReady) {
        mutableStateOf(if (backendReady) "Conectando con la configuración de pantallas…" else "Firebase no está disponible en esta instalación.")
    }
    var isRefreshing by remember { mutableStateOf(false) }

    DisposableEffect(context, backendReady, realtimeUpdatesEnabled) {
        val registration = if (backendReady && realtimeUpdatesEnabled) {
            try {
                EstadiaRepository.create(context).listenPublicScreenConfig(
                    onUpdate = { updatedConfig ->
                        config = updatedConfig
                        status = updatedConfig?.let { "Pantallas sincronizadas · revisión ${it.revision}" }
                            ?: "Conectado · se muestran las pantallas predeterminadas"
                    },
                    onError = { error ->
                        Log.w("AlozenaConfig", "No se pudo sincronizar la configuración de pantallas", error)
                        status = "No se pudo sincronizar. Puedes intentar actualizar de nuevo."
                    }
                )
            } catch (error: Exception) {
                Log.w("AlozenaConfig", "No se pudo iniciar la sincronización de pantallas", error)
                status = "No se pudo iniciar la sincronización de pantallas."
                null
            }
        } else {
            status = if (!backendReady) {
                "Firebase no está disponible en esta instalación."
            } else {
                "Actualizaciones en tiempo real pausadas."
            }
            null
        }
        onDispose { registration?.remove() }
    }

    fun setRealtimeUpdatesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("realtime_screen_updates", enabled).apply()
        realtimeUpdatesEnabled = enabled
    }

    fun refresh() {
        if (!backendReady) {
            status = "Firebase no está disponible en esta instalación."
            return
        }
        scope.launch {
            isRefreshing = true
            try {
                config = EstadiaRepository.create(context).fetchPublicScreenConfig()
                status = config?.let { "Pantallas sincronizadas · revisión ${it.revision}" }
                    ?: "Conectado · se muestran las pantallas predeterminadas"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("AlozenaConfig", "No se pudo actualizar la configuración de pantallas", error)
                status = "No se pudo actualizar. Revisa la conexión e inténtalo de nuevo."
            } finally {
                isRefreshing = false
            }
        }
    }

    return PublicScreenConfigState(
        config = config,
        realtimeUpdatesEnabled = realtimeUpdatesEnabled,
        status = status,
        isRefreshing = isRefreshing,
        onRealtimeUpdatesChange = ::setRealtimeUpdatesEnabled,
        onRefresh = ::refresh
    )
}
