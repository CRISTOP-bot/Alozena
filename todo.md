# Metas de Alozena

## Acceso y configuración

- [ ] Probar Google sign-in en un dispositivo real con el proveedor habilitado y las huellas SHA del certificado instalado registradas.
- [ ] Validar el flujo de recuperación de cuenta y cierre de sesión en dispositivos sin Google Play Services.
- [ ] Definir firma de producción y registrar el certificado de Play App Signing antes de distribuir en Play Store.

## Publicaciones reales

- [ ] Invitar anfitriones reales y publicar solo alojamientos, fotos y precios con autorización.
- [ ] Definir un flujo de revisión y reporte de publicaciones antes de ampliar el catálogo.
- [ ] Confirmar reglas, índices y configuración de Storage en Firebase de producción.

## Pantallas remotas

- [ ] Desplegar las reglas actualizadas y crear `appConfig/publicScreens` en Firebase de producción para activar la sincronización en tiempo real.

## Solicitudes y confianza

- [ ] Validar disponibilidad y evitar solicitudes solapadas antes de llamar “reserva” al flujo.
- [ ] Diseñar políticas de cancelación y soporte; no habilitar pagos sin revisión adulta y legal.
- [ ] Agregar reseñas solo cuando exista una regla verificable de estancia completada.

## Calidad de la app

- [ ] Probar accesibilidad, tamaños de pantalla y navegación en Android de gama baja.
- [ ] Añadir pruebas de UI para acceso, favoritos y publicación.
- [ ] Mantener archivos Kotlin por debajo de 500 líneas y CI verde en cada cambio.
