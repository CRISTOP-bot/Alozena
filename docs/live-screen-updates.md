# Actualizaciones de pantallas en tiempo real

Alozena escucha el documento `appConfig/publicScreens` de Firestore. Cuando un administrador lo cambia, los teléfonos con Alozena abierta reciben los nuevos textos y avisos sin instalar otro APK. Si la app está cerrada o sin conexión, puede usar **Configuración → Actualizar pantallas ahora** cuando vuelva a conectarse.

## Publicar una actualización

1. En Firebase Console, abre **Firestore Database** y crea la colección `appConfig`.
2. Crea el documento `publicScreens` usando el contenido de [`firebase/public-screen-config.example.json`](../firebase/public-screen-config.example.json) como referencia. Respeta los tipos: los textos son cadenas y `schemaVersion`/`revision` son números.
3. Edita sus campos para cambiar los textos o avisos, e incrementa `revision` en cada publicación. Para retirar un aviso, elimina `screens.home.announcement`.
4. Guarda el documento. Los clientes conectados actualizan la pantalla al recibir el cambio.

Los clientes solo pueden leer `appConfig/publicScreens`; no pueden crear, listar ni modificar configuraciones. Publica cambios desde Firebase Console o desde un backend con Firebase Admin SDK y permisos controlados. Nunca pongas credenciales Admin en la app.

## Alcance y seguridad

El documento configura contenido de componentes que ya existen: título y subtítulo de Inicio, un aviso en Inicio y un aviso en Configuración. El cliente valida la versión y limita el tamaño de los textos. No descarga ni ejecuta código, HTML, scripts ni diseños arbitrarios. Añadir un nuevo tipo de componente o cambiar la estructura visual todavía requiere una actualización de la app.

El código de la app no crea documentos ni despliega reglas en el proyecto remoto. Antes de activar la función, despliega `firestore.rules` con Firebase CLI autenticado y crea `appConfig/publicScreens` desde Firebase Console o Admin SDK. Hasta entonces, Alozena conserva el contenido predeterminado. En el repositorio, las pruebas de emulador comprueban que el documento público es legible y que los clientes no pueden escribirlo.

Los ajustes de tamaño de texto se guardan localmente en el teléfono. La opción de actualizaciones en tiempo real pausa o reanuda el listener; la actualización manual sigue disponible si Firebase está configurado.
