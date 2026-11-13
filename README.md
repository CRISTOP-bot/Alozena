# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos, filtrar categorías y guardar favoritos.

> **Estado:** el repositorio incluye cliente Firebase/Firestore y reglas, y el CI prueba las reglas con el emulador y genera un APK. El JSON compartido identifica el proyecto `estadia-37fbf` y la app Android `com.cristopbot.estadia`; por sí solo no demuestra que Authentication o Firestore estén activados/desplegados. Los alojamientos de muestra siguen siendo datos locales. La app no procesa pagos ni confirma reservas.

## Firebase

La integración de cliente incluye lectura del catálogo publicado, favoritos por usuario y acceso con Google (Credential Manager → Firebase Authentication, con selector alternativo si Google Play Services cierra el flujo directo). En la primera apertura aparece una bienvenida con opción de iniciar sesión o continuar como invitado; la elección se recuerda en ese dispositivo. Si Google Play Services cierra el flujo directo al seleccionar una cuenta, se vuelve a intentar con el selector estándar; si aun así falla, la pantalla muestra el motivo y el log local registra el tipo de error sin tokens. La sesión permite asociar favoritos a la cuenta. El flujo para publicar alojamientos y las solicitudes reales de reserva aún no están completos en la interfaz; la solicitud que aparece en el detalle sigue siendo una demostración.

1. En Firebase Console, activa Authentication → Google y crea Cloud Firestore en el proyecto `estadia-37fbf`.
2. Registra las huellas SHA-1 y SHA-256 del certificado Android para Google Sign-In; puedes obtenerlas en los logs del job mediante `gradle signingReport`.
3. Descarga `google-services.json` después de configurar OAuth y guarda su contenido como secreto del repositorio `FIREBASE_ANDROID_CONFIG`. No subas el archivo al repositorio.
4. Despliega `firestore.rules` y `firestore.indexes.json` con Firebase CLI autenticado: `firebase use estadia-37fbf` y `firebase deploy --only firestore:rules,firestore:indexes`.
5. No publiques anuncios de demostración como alojamientos reales. Comprueba permisos de fotos, datos y precios antes de importar anuncios autorizados.

El CI comprueba compilación y tests de reglas en el emulador; no verifica un inicio de sesión real ni confirma por sí solo el estado del proyecto Firebase remoto. Para usar Google, Authentication → Google debe estar habilitado y el `google-services.json` usado por el build debe incluir el cliente OAuth web y la huella SHA-1 del certificado con que se firma ese APK.

Más detalles en `firebase/README.md`.

## Datos y seguridad

- `stays/{stayId}`: alojamientos publicados (`published=true`).
- `users/{uid}/favorites/{stayId}`: favoritos privados por cuenta.
- `bookings/{bookingId}`: reglas contempladas para solicitudes autenticadas; la interfaz aún no ofrece el flujo completo para crearlas.

Las pruebas emuladas no despliegan reglas en producción. Nunca guardes claves de cuenta de servicio en la app, el APK, el repositorio o secretos usados por la compilación Android.

## Build y CI

Android usa Gradle 8.9 y JDK 17. GitHub Actions ejecuta pruebas de reglas Firestore en el emulador, tests unitarios, `assembleDebug` y `signingReport`. Los builds de `main` usan la configuración Firebase del secreto `FIREBASE_ANDROID_CONFIG`; el archivo se materializa solo durante el build y está excluido por `.gitignore`.


Creado por un niño de 12 años BTW
