# Alozena 🏡

App Android nativa Alozena en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos, filtrar categorías y guardar favoritos.

> **Estado:** el repositorio incluye cliente Firebase/Firestore y reglas, y el CI prueba las reglas con el emulador y genera un APK. El JSON compartido identifica el proyecto `estadia-37fbf` y la app Android `com.cristopbot.estadia`; por sí solo no demuestra que Authentication o Firestore estén activados/desplegados. Los alojamientos de muestra siguen siendo datos locales. El nombre visible y la interfaz son Alozena. La app no procesa pagos ni confirma reservas.

## Firebase

La integración de cliente incluye lectura del catálogo publicado, favoritos por usuario y acceso con Google (Credential Manager → Firebase Authentication). La primera apertura presenta bienvenida y consentimiento; permite iniciar sesión o explorar como invitado. Los favoritos de invitado se guardan localmente en SQLite; los usuarios con sesión usan sus favoritos de Firebase. El detalle distingue claramente catálogo de demostración de anuncios reales, y abre Airbnb para revisar disponibilidad y reseñas actuales. Alozena no importa ni replica anuncios de Airbnb.

Airbnb no ofrece acceso irrestricto a su API de alojamientos: la integración oficial está sujeta a un programa de socios, permisos y revisión de seguridad. La búsqueda externa no es una API ni una sincronización de datos. No se inventan reseñas. Alozena es un proyecto independiente, no afiliado a Airbnb.

1. En Firebase Console, activa Authentication → Google y crea Cloud Firestore en el proyecto `estadia-37fbf`.
2. Registra las huellas SHA-1 y SHA-256 del certificado Android para Google Sign-In; puedes obtenerlas en los logs del job mediante `gradle signingReport`.
3. Descarga `google-services.json` después de configurar OAuth y guarda su contenido como secreto del repositorio `FIREBASE_ANDROID_CONFIG`. No subas el archivo al repositorio.
4. Despliega `firestore.rules` y `firestore.indexes.json` con Firebase CLI autenticado: `firebase use estadia-37fbf` y `firebase deploy --only firestore:rules,firestore:indexes`.
5. No publiques anuncios de demostración como alojamientos reales. Comprueba permisos de fotos, datos y precios antes de importar anuncios autorizados.

El CI comprueba compilación y tests de reglas en el emulador; no verifica un inicio de sesión real ni confirma por sí solo el estado del proyecto Firebase remoto. Para usar Google, Authentication → Google debe estar habilitado y el `google-services.json` del build debe incluir el cliente OAuth web y la huella SHA-1 de su certificado. Los términos integrados son un borrador de transparencia para una app de demostración, no asesoría legal; antes de distribución pública/comercial deben revisarlos un adulto responsable y asesoría adecuada.

Más detalles en `firebase/README.md`.

## Datos y seguridad

- `stays/{stayId}`: alojamientos publicados (`published=true`).
- `users/{uid}/favorites/{stayId}`: favoritos privados por cuenta.
- `bookings/{bookingId}`: reglas contempladas para solicitudes autenticadas; la interfaz aún no ofrece el flujo completo para crearlas.

Las pruebas emuladas no despliegan reglas en producción. Nunca guardes claves de cuenta de servicio en la app, el APK, el repositorio o secretos usados por la compilación Android.

## Build y CI

Android usa Gradle 8.9 y JDK 17. GitHub Actions ejecuta pruebas de reglas Firestore en el emulador, tests unitarios, `assembleDebug` y `signingReport`. Los builds de `main` usan la configuración Firebase del secreto `FIREBASE_ANDROID_CONFIG`; el archivo se materializa solo durante el build y está excluido por `.gitignore`.


## Privacidad, catálogo y seguridad

- La bienvenida incluye consentimiento visible para las condiciones de uso y el resumen de privacidad; no es asesoría legal.
- Los favoritos de invitado se guardan en SQLite en el dispositivo. Los favoritos con sesión iniciada se sincronizan con Firebase; se mantiene copia local del uso de invitado.
- El catálogo interno es demostrativo, con descripciones y calificaciones ilustrativas. No presenta reseñas inventadas como auténticas; abre Airbnb para consultar anuncios/reseñas actuales.
- Airbnb limita su API oficial a programas de socios y permisos aprobados. No se extraen páginas ni se clona su inventario.
- El manifiesto Android de la app declara únicamente permiso de Internet; la app no pide SMS, accesibilidad, lectura de notificaciones ni control del dispositivo. No se intenta ocultar ni evadir Play Protect. Si muestra una alerta, conserva el texto exacto, analiza el APK y usa la apelación oficial de Google cuando proceda.

Creado por un niño de 12 años BTW


La marca visible y el nombre del proyecto Gradle son Alozena. El applicationId Android y el namespace técnico se conservan para no romper Firebase, las instalaciones existentes ni el mecanismo de actualización.
