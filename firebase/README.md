# Firebase de Estadía

Proyecto Firebase indicado por `google-services.json`: `estadia-37fbf`  
App Android: `com.cristopbot.estadia`

## Requisitos de consola (realizar en Firebase Console)

1. En Authentication → Proveedores de acceso, habilita **Google** y configura la pantalla de consentimiento OAuth si Google la solicita.
2. En la app Android de Firebase agrega las huellas SHA-1 y SHA-256 del certificado que firma el APK que vas a instalar. Para builds de GitHub Actions `assembleDebug`, obtén la huella del certificado debug correspondiente; si cambias la clave de firma, registra también sus huellas.
3. Vuelve a descargar `google-services.json` después de configurar Google Sign-In y reemplaza el secreto de repo `FIREBASE_ANDROID_CONFIG` con el contenido actualizado. El JSON actual está registrado para el package correcto, pero contiene `oauth_client: []`, por lo que no permite Google Sign-In todavía.
4. Crea Cloud Firestore en la consola, seleccionando cuidadosamente la región: no se puede cambiar una vez creada la base.
5. Después de crear Firestore, despliega reglas e índices desde una terminal con Firebase CLI autenticada como propietaria:

```sh
firebase login
firebase use estadia-37fbf
firebase deploy --only firestore:rules,firestore:indexes
```

El archivo `.firebaserc` local queda ignorado por Git. También puedes usar `firebase deploy --project estadia-37fbf --only firestore:rules,firestore:indexes`.

## Datos de alojamientos

`seed-stays.example.json` es una plantilla demostrativa y tiene `published:false`. **No se incluye ni se publica automáticamente**: antes de cargar alojamientos reales, confirma derecho de uso de fotos, veracidad de descripción, precio y autorización del anfitrión. Los documentos publicados deben incluir `hostUid` propio para cumplir las reglas de lectura/anfitrión.

## Seguridad y CI

- `app/google-services.json` contiene configuración cliente, no una credencial administrativa; permanece ignorado por Git.
- GitHub Actions lee el secreto de repositorio `FIREBASE_ANDROID_CONFIG` solo en builds de `main` y tags `v*` y genera el archivo durante el build.
- Nunca guardes una service-account key en la app, en Git o en un secreto usado para compilar el APK.
- CI prueba reglas Firestore contra el emulador. Esto no comprueba el estado ni despliega las reglas del proyecto remoto.
- La solicitud de viaje del prototipo no es una reserva confirmada, no valida disponibilidad y no procesa pagos.

## Pendientes de habilitación remota

No se puede afirmar que Authentication o Firestore estén activos sin una comprobación en la consola/proyecto. El JSON compartido confirma el project ID y package Android, pero aún no incluye OAuth clients. Tras activar Google provider, descargar el JSON actualizado y desplegar reglas, instala un APK generado con ese JSON y prueba el acceso con Google y una consulta autorizada.
