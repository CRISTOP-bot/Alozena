# Firebase de Estadía

Proyecto identificado por el archivo Firebase recibido: `estadia-37fbf`  
App Android: `com.cristopbot.estadia`

## Habilitación en Firebase Console

1. Confirma que estás en el proyecto `estadia-37fbf`.
2. En Authentication → Proveedores de acceso, habilita Google y completa configuración OAuth requerida.
3. En la configuración de la app Android registra SHA-1 y SHA-256 del certificado debug. El workflow imprime esas huellas mediante `gradle signingReport`.
4. El `google-services.json` recibido registra el package correcto, pero `oauth_client` está vacío. Después de configurar Google/OAuth, descarga un JSON actualizado y reemplaza el secreto de repo `FIREBASE_ANDROID_CONFIG`.
5. Crea Cloud Firestore si aún no existe y selecciona cuidadosamente la región: no puede cambiarse posteriormente.

## Desplegar reglas e índices

Desde una terminal que tenga Firebase CLI y sesión iniciada como propietaria del proyecto:

```sh
firebase login
firebase use estadia-37fbf
firebase deploy --only firestore:rules,firestore:indexes
```

También se puede indicar el proyecto directamente: `firebase deploy --project estadia-37fbf --only firestore:rules,firestore:indexes`.

La selección local `.firebaserc` no se versiona. El despliegue real solo se completa al ejecutar el comando en sesión autorizada y revisar su resultado; la prueba de emulador de CI no cambia el proyecto remoto.

## Inventario y límites

`seed-stays.example.json` contiene ejemplos no publicados y no se carga automáticamente. No publicar esos documentos como anuncios reales sin verificar autorización del anfitrión, tarifas e imágenes. El prototipo todavía no tiene un flujo UI completo para crear/publicar alojamientos ni para enviar solicitudes reales de reserva.

## Protección de credenciales

- `app/google-services.json` se mantiene ignorado por Git.
- GitHub Actions usa `FIREBASE_ANDROID_CONFIG` desde un secreto y lo materializa durante el build conectado.
- Nunca agregues credenciales de servicio/admin al APK, al repositorio ni a secretos de build de Android.
- Las reglas se prueban contra Firestore Emulator; esos tests no verifican el estado o despliegue de Authentication/Firestore en producción.
