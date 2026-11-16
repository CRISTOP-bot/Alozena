# Guía de trabajo para Alozena

- Mantén los archivos Kotlin por debajo de 500 líneas; extrae pantallas o responsabilidades coherentes cuando crezcan.
- Conserva `applicationId` y namespace `com.cristopbot.estadia`, salvo solicitud explícita con migración Firebase.
- Nunca agregues al repositorio `google-services.json`, credenciales de servicio, claves privadas, contraseñas ni tokens. Usa secretos de GitHub Actions y evita imprimir sus valores.
- No presentes datos demo como ofertas reales: no inventes anfitriones, reseñas, propiedades verificadas ni disponibilidad.
- Para cambios en Firestore/Storage, actualiza y ejecuta las pruebas del emulador.
- Antes de finalizar cambios Android, ejecuta `gradle testDebugUnitTest --no-daemon` y `gradle assembleDebug --no-daemon` o confirma el resultado de CI.
- Actualiza `docs/` y `todo.md` cuando cambien los flujos o metas del producto.
