# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos, buscar destinos, filtrar categorías, guardar favoritos y revisar una ficha antes de iniciar una reserva de demostración.

> **Prototipo independiente.** No pertenece a Airbnb ni procesa pagos o reservas reales. Los alojamientos y tarifas son datos de muestra; las imágenes requieren conexión a internet.

## Funciones
- Catálogo visual de alojamientos con precio por noche, ubicación y calificación.
- Búsqueda por título o destino, categorías y sección de favoritos.
- Ficha con descripción, capacidad y selector de huéspedes.
- Confirmación de itinerario de demostración, sin cobro ni contacto con anfitriones.
- Secciones de viajes y perfil identificadas como prototipo.

## Compilar
Abre el proyecto con Android Studio (JDK 17) o ejecuta `gradle testDebugUnitTest assembleDebug`. El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## CI y releases
GitHub Actions corre pruebas y compila el APK en cada push a `main` y al crear una etiqueta `v*`. Los pushes a `main` publican una release preliminar versionada automáticamente; las etiquetas publican una release estable. El APK se adjunta a cada release.
