# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos y guardar favoritos.

> **Backend:** el código de cliente para Firebase Authentication/Firestore y las reglas están versionados; sin un proyecto Firebase real la app conserva los datos locales de muestra. Solicitar en esta app una reserva sigue siendo una acción de demostración; no confirma disponibilidad, no cobra ni contacta anfitriones.

## Modelo Firestore
- `stays/{stayId}`: catálogo curado; solo anuncios `published=true` se leen públicamente.
- `users/{uid}`: perfil privado.
- `users/{uid}/favorites/{stayId}`: favoritos privados y sincronizables.
- `bookings/{bookingId}`: solicitud del huésped en estado `requested`; solo huésped y anfitrión pueden leerla.

Las reglas restringen perfiles y favoritos al propietario, validan campos/timestamps/capacidad de solicitudes, permiten cancelar solo al huésped y niegan todo lo demás por defecto. No existe proceso de pago ni disponibilidad transaccional; no usar como sistema de reservas real.

## Activar Firebase (proyecto registrado; habilitación remota pendiente)
1. El archivo Firebase recibido identifica el proyecto `estadia-37fbf` y el paquete Android `com.cristopbot.estadia`.
2. En Firebase Console, habilita Authentication → Google y Cloud Firestore. Registra SHA-1 y SHA-256 del certificado de firma. El JSON recibido todavía trae `oauth_client: []`; tras completar Google Sign-In, descarga de nuevo `google-services.json`.
3. Actualiza el secreto de GitHub `FIREBASE_ANDROID_CONFIG` con ese JSON actualizado. El workflow lo escribe solo durante compilaciones `main` y tags `v*`; `app/google-services.json` está ignorado por Git.
4. Desde una terminal autenticada con Firebase CLI como propietaria: `firebase use estadia-37fbf` y `firebase deploy --only firestore:rules,firestore:indexes`. `.firebaserc` se mantiene local/ignorado.
5. No importes ni publiques alojamientos de ejemplo hasta verificar derechos sobre fotos, autorización del anfitrión y precios. Las plantillas permanecen con `published:false`.
6. CI valida las reglas con el emulador y construye el APK, pero no activa Authentication, no crea Firestore ni despliega reglas en producción. Nunca uses claves de cuenta de servicio en la app o en el APK.

El cliente incorpora inicio de sesión Google con Credential Manager, búsqueda Firestore, favoritos sincronizables y consulta/cancelación de solicitudes. La creación de solicitudes desde la interfaz, flujo de anfitrión/publicación de inventario, fechas/disponibilidad concurrente, pagos, notificaciones y moderación siguen pendientes.

## Pruebas
- `gradle testDebugUnitTest assembleDebug` (JDK 17).
- `npm test` desde `firebase-tests/` para probar reglas con Firestore Emulator (requiere Node y Java 21).

GitHub Actions ejecuta las pruebas de reglas y el build Android antes de adjuntar APK a releases. La base remota no queda activa hasta que el propietario cree el proyecto y despliegue configuración/reglas.
