# Configuración y datos sensibles

- `app/google-services.json` y `.firebaserc` están excluidos de Git.
- CI recibe la configuración Android mediante el secreto GitHub `FIREBASE_ANDROID_CONFIG`; no copies su contenido a código, documentación ni logs.
- El archivo Firebase Android contiene identificadores y una API key de cliente. No es una credencial de administrador; restringe la API key y protege los datos mediante Firebase Security Rules.
- Nunca incluyas service-account JSON, claves privadas, contraseñas OAuth de cliente ni tokens de usuario en el APK o en el repositorio. Una app Android es inspeccionable; las operaciones administrativas pertenecen a un backend protegido.
- Los IDs de cliente OAuth no son secretos. Los secretos de OAuth de servidor, si se usan, no deben distribuirse en la app Android.
- Alozena no inventa anuncios, reseñas, verificaciones ni disponibilidad. El catálogo de demostración está marcado como ficticio y no reservable.
- La configuración pública de pantallas permite leer únicamente `appConfig/publicScreens`; las escrituras deben realizarse desde Firebase Console o un backend administrativo confiable, nunca desde la app.

Si una credencial administrativa se expuso, revócala y genera una nueva; quitarla del último commit no la elimina del historial.
