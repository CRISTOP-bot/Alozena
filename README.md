# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos, filtrar categorías y guardar favoritos.

> **Estado:** el repositorio incluye cliente Firebase/Firestore y reglas, y el CI prueba las reglas con el emulador y genera un APK. El JSON compartido identifica el proyecto `estadia-37fbf` y la app Android `com.cristopbot.estadia`; por sí solo no demuestra que Authentication o Firestore estén activados/desplegados. Los alojamientos de muestra siguen siendo datos locales. La app no procesa pagos ni confirma reservas.

## Firebase

La integración de cliente incluye lectura del catálogo publicado, favoritos por usuario y código para iniciar sesión con Google. La app no cuenta todavía con un flujo completo para publicar alojamientos o solicitar reservas desde la interfaz. El JSON recibido tiene `oauth_client: []`, así que Google Sign-In aún requiere completar el proveedor Google/OAuth y volver a descargar el archivo.

1. En Firebase Console, abre el proyecto `estadia-37fbf` y confirma/activa Authentication → Google y Cloud Firestore.
2. Registra las huellas SHA-1 y SHA-256 del certificado Android para Google Sign-In; puedes obtenerlas en los logs del job del workflow mediante `gradle signingReport`.
3. Descarga de nuevo `google-services.json` después de configurar OAuth. Actualiza el secreto del repositorio `FIREBASE_ANDROID_CONFIG` con el contenido completo. No lo subas al repositorio.
4. Desde una terminal donde Firebase CLI esté autenticado con la cuenta propietaria, ejecuta `firebase use estadia-37fbf` y `firebase deploy --only firestore:rules,firestore:indexes`. Revisa con cuidado la región al crear Firestore: no se puede cambiar después.
5. No publiques anuncios de demostración como alojamientos reales. Comprueba permisos de fotos, datos y precios antes de importar anuncios autorizados.

Más detalles en `firebase/README.md`.

## Datos y seguridad

- `stays/{stayId}`: alojamientos publicados (`published=true`).
- `users/{uid}/favorites/{stayId}`: favoritos privados por cuenta.
- `bookings/{bookingId}`: reglas contempladas para solicitudes autenticadas; la interfaz aún no ofrece el flujo completo para crearlas.

Las pruebas emuladas no despliegan reglas en producción. Nunca guardes claves de cuenta de servicio en la app, el APK, el repositorio o secretos usados por la compilación Android.

## Build y CI

Android usa Gradle 8.9 y JDK 17. GitHub Actions ejecuta pruebas de reglas Firestore en el emulador, tests unitarios, `assembleDebug` y `signingReport`. Los builds de `main` usan la configuración Firebase del secreto `FIREBASE_ANDROID_CONFIG`; el archivo se materializa solo durante el build y está excluido por `.gitignore`.
