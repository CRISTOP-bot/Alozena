# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos, filtrar categorías y guardar favoritos.

> **Estado del backend:** se añadió integración cliente con Firebase Authentication/Firestore, reglas de seguridad y esquema. La app aún usa datos locales de muestra hasta que se cree el proyecto Firebase, se añada `app/google-services.json`, se activen proveedores y se desplieguen las reglas. No se procesa pago ni se confirma disponibilidad/reserva.

## Estructura de datos Firestore
- `stays/{stayId}`: catálogo de alojamientos publicados (`published=true`).
- `users/{uid}`: perfil del usuario autenticado.
- `users/{uid}/favorites/{stayId}`: favoritos privados por cuenta.
- `bookings/{bookingId}`: solicitud autenticada en estado `requested`; no es una reserva confirmada.

## Protección de datos
`firestore.rules` restringe perfiles/favoritos al propietario, limita lecturas del catálogo a anuncios publicados, comprueba capacidad y formato de fecha en solicitudes y niega acceso por defecto. `firestore.indexes.json` contiene índices para consultas. No guardes credenciales de service account en el APK o en GitHub.

## Activación en Firebase
1. Abre [Firebase Console](https://console.firebase.google.com/) con tu cuenta Google; crea un proyecto nuevo para Estadía.
2. Añade una app Android con el paquete `com.cristopbot.estadia`, descarga `google-services.json` y ponlo en `app/` localmente. Está excluido de Git por `.gitignore`; no lo publiques como archivo del repositorio.
3. Habilita Google en Authentication y crea Cloud Firestore. Configura SHA-1/SHA-256 del certificado de Android para OAuth Google.
4. Desde una máquina con Firebase CLI instalada y sesión autorizada en tu proyecto, ejecuta `firebase deploy --only firestore:rules,firestore:indexes`. Más contexto en `firebase/README.md`.
5. Importa los documentos de ejemplo de `firebase/seed-stays.example.json` con `published:false`. Revisa que tienes derecho a usar cada alojamiento, foto y tarifa antes de publicarlos.
6. Para los builds CI conectados, configura el proyecto Firebase como variables GitHub Actions `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`, `FIREBASE_APPLICATION_ID` y el API key de app Android como secret `FIREBASE_API_KEY`; habilita solo después de revisar el proyecto/paquete. No se suben claves administrativas.

La cuenta Google conectada a Zapia no crea automáticamente un proyecto Firebase ni autoriza cambios en Google Cloud. La base seguirá pendiente de esos pasos y no se afirma que ya esté desplegada.

## Build y CI
Gradle 8.9 + JDK 17. El CI ejecuta unit tests y compila un APK de depuración. Las pruebas Firestore Rules Emulator aún están pendientes de añadir y ejecutar.
