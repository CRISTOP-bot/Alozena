# Estadía 🏡

App Android nativa en Kotlin y Jetpack Compose para explorar alojamientos de muestra, buscar destinos, filtrar categorías, guardar favoritos y abrir una ficha de demostración.

> **Prototipo independiente.** No pertenece a Airbnb ni procesa pagos o reservas reales. Los alojamientos y tarifas son datos de muestra; las imágenes requieren conexión a internet.

## Funciones
- Catálogo visual con búsqueda por nombre/destino (sin distinguir mayúsculas ni acentos).
- Categorías desplazables horizontalmente y selector de filtros funcional.
- Favoritos y fichas con descripción, capacidad y selector de huéspedes.
- Solicitud de reserva exclusivamente de demostración; no se contacta a anfitriones.
- Secciones de viajes y perfil identificadas como prototipo.

## Compilar
Android Studio con JDK 17 o `gradle testDebugUnitTest assembleDebug`. El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## CI y releases
GitHub Actions corre pruebas y compila en cada push a `main` y al crear etiquetas `v*`. Los pushes a `main` publican una release preliminar versionada automáticamente; las etiquetas publican una release estable. El APK se adjunta a cada release.
