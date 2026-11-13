# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos y guardar favoritos.

> **Backend:** el código de cliente para Firebase Authentication/Firestore y las reglas están versionados; sin un proyecto Firebase real la app conserva los datos locales de muestra. Solicitar en esta app una reserva sigue siendo una acción de demostración; no confirma disponibilidad, no cobra ni contacta anfitriones.

## Modelo Firestore
- `stays/{stayId}`: catálogo curado; solo anuncios `published=true` se leen públicamente.
- `users/{uid}`: perfil privado.
- `users/{uid}/favorites/{stayId}`: favoritos privados y sincronizables.
- `bookings/{bookingId}`: solicitud del huésped en estado `requested`; solo huésped y anfitrión pueden leerla.

Las reglas restringen perfiles y favoritos al propietario, validan campos/timestamps/capacidad de solicitudes, permiten cancelar solo al huésped y niegan todo lo demás por defecto. No existe proceso de pago ni disponibilidad transaccional; no usar como sistema de reservas real.

## Activar Firebase (con tu cuenta Google)
1. Entra a [Firebase Console](https://console.firebase.google.com/), crea un proyecto y registra Android con paquete `com.cristopbot.estadia`.
2. Habilita Authentication → Google y Cloud Firestore; configura SHA-1 y SHA-256 del certificado para OAuth.
3. Descarga `google-services.json` y ponlo en `app/` local. `.gitignore` lo excluye; **no lo subas al repositorio**.
4. Publica las reglas/índices desde una terminal autorizada: `firebase deploy --only firestore:rules,firestore:indexes`.
5. Importa `firebase/seed-stays.example.json` solo como datos de muestra, con `published:false`. Verifica licencia de fotos, permiso para cada anuncio y tarifas antes de publicar.
6. Para compilaciones CI conectadas, configura `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`, `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY` y un paso seguro para proveer el archivo descargado de configuración; no uses claves de cuenta de servicio en una APK.

El cliente incorpora inicio de sesión Google con Credential Manager, búsqueda Firestore, favoritos sincronizables y consulta/cancelación de solicitudes. La creación de solicitudes desde la interfaz, flujo de anfitrión/publicación de inventario, fechas/disponibilidad concurrente, pagos, notificaciones y moderación siguen pendientes.

## Pruebas
- `gradle testDebugUnitTest assembleDebug` (JDK 17).
- `npm test` desde `firebase-tests/` para probar reglas con Firestore Emulator (requiere Node y Java 21).

GitHub Actions ejecuta las pruebas de reglas y el build Android antes de adjuntar APK a releases. La base remota no queda activa hasta que el propietario cree el proyecto y despliegue configuración/reglas.
