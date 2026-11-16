# Documentación de Alozena

- [Acceso con Google y configuración Firebase](./login.md)
- [Protección de configuración y credenciales](./security.md)

## Desarrollo

El proyecto es una app Android nativa en Kotlin y Jetpack Compose. Conserva el `applicationId` `com.cristopbot.estadia` para mantener compatibilidad con Firebase.

Comandos de validación usados por CI:

```text
gradle testDebugUnitTest --no-daemon
gradle assembleDebug --no-daemon
gradle signingReport --no-daemon
```

Las reglas de Firestore y Storage se prueban en el emulador desde `firebase-tests` con `npm test`.
