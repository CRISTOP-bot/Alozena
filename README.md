# Alozena 🏡

Alozena es una app Android nativa en Kotlin y Jetpack Compose para publicar alojamientos y solicitar estancias dentro de la propia plataforma.

## Estado real

- El catálogo inicial está vacío: no hay anuncios precargados.
- Solo aparecen publicaciones guardadas en Firestore por un usuario anfitrión autenticado.
- La publicación pide título, ubicación, tipo, precio por noche, cargo de limpieza, depósito de seguridad, capacidad, camas, descripción y una foto elegida del dispositivo; la foto se sube a Firebase Storage con ruta por UID. Firestore guarda la publicación ligada al UID y marcada `source=host_submitted`. El anfitrión declara que la información es cierta; Alozena no verifica físicamente la propiedad.
- Las solicitudes de estancia se guardan en Alozena con fechas, huéspedes, título/precio al momento de pedir y estado pendiente. El anfitrión puede confirmar/rechazar y el huésped cancelar dentro de Alozena; no se procesa ningún pago.
- Alozena aún no tiene flujo de reseñas/calificaciones, así que las calificaciones están desactivadas y no aparecen estrellas.
- Favoritos de invitado: SQLite local. Favoritos de cuenta: Firebase privado por UID.

## Firebase

El cliente usa Google Credential Manager y Firebase Authentication. Firestore aloja `stays`, `users/{uid}/favorites` y `bookings`; Firebase Storage recibe fotos elegidas dentro de la app, en rutas asociadas al UID del anfitrión. Las reglas limitan publicación, edición y solicitudes. La autenticación confirma la cuenta, no la existencia física de un alojamiento. Las solicitudes guardan el título y precio copiados del anuncio en el momento de enviarse y pueden ser canceladas por el huésped o confirmadas/rechazadas por su anfitrión dentro de Alozena.

El `applicationId` y namespace técnico `com.cristopbot.estadia` se conservan para no romper Firebase ni instalaciones existentes. El nombre visible, Gradle, repositorio y launcher son Alozena.

## Seguridad y honestidad de datos

No se importan catálogos de terceros, no se raspan páginas, no se usan anuncios, fotos, precios o reseñas ajenos sin autorización y no se presentan datos inventados como reales. Antes de publicar, el anfitrión declara que tiene derecho a usar las fotos y que los datos son ciertos; esa declaración no es una verificación física de Alozena. Las reseñas/calificaciones están deshabilitadas, así que el cliente no muestra estrellas ficticias. No hay flujo de pagos y las solicitudes no bloquean inventario ni representan confirmación de hospedaje.

## CI

GitHub Actions ejecuta las pruebas de reglas Firestore, tests unitarios, `assembleDebug` y `signingReport`. Los builds de `main` publican el APK generado por ese commit. El CI prueba reglas en emuladores, pero no despliega reglas ni habilita Firebase Storage en la consola remota. Antes del primer uso, verifica que Storage esté habilitado en el proyecto y despliega `firestore.rules`, `firestore.indexes.json` y `storage.rules`. En este cambio no se inspeccionó ni se modificó la configuración Firebase remota.

Creado por un niño de 12 años BTW


**Transparencia:** hoy Alozena no incluye un panel de moderación ni puede verificar por sí sola la existencia física de cada propiedad. La declaración del anfitrión y la autenticación no equivalen a verificación independiente; por eso el README no presenta la oferta como verificada por Alozena.

## Despliegue de reglas

En tu proyecto Firebase, ejecuta `firebase deploy --only firestore:rules,firestore:indexes,storage` con Firebase CLI autenticado. El CI de este repositorio ejecuta emuladores locales y no modifica reglas remotas.
