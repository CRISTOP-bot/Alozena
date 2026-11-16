# Acceso con Google

La app usa Credential Manager para obtener un ID token y Firebase Authentication para iniciar sesión. No guarda contraseñas de Google.

## Requisitos de Firebase

1. En Firebase Console, habilita Google en Authentication → Proveedores de acceso.
2. Confirma que la app Android está registrada como `com.cristopbot.estadia`.
3. Registra el SHA-1 y SHA-256 del certificado con el que está firmado el APK que se instalará. Para un build local, obténlos con `gradle signingReport`; para Google Play, usa también el certificado de Play App Signing.
4. Descarga un `google-services.json` actualizado. Debe incluir un cliente OAuth web (tipo 3) y un cliente Android (tipo 1) correspondiente al paquete y certificado.
5. En builds locales, coloca el archivo en `app/google-services.json`. Está excluido de Git.
6. En GitHub Actions, actualiza el secreto de repositorio `FIREBASE_ANDROID_CONFIG` con ese JSON. El workflow valida que existan ambos tipos de cliente sin imprimir sus identificadores.

Para actualizar el secreto sin poner el contenido en el historial del shell:

```powershell
Get-Content .\app\google-services.json -Raw | gh secret set FIREBASE_ANDROID_CONFIG --repo CRISTOP-bot/Alozena
```

## Diagnóstico

- **El cliente OAuth web no está configurado:** falta el recurso generado desde la configuración Firebase; actualiza `google-services.json` y el secreto.
- **Google no está disponible o `ERROR_OPERATION_NOT_ALLOWED`:** revisa que el proveedor Google esté habilitado en Firebase Authentication.
- **Credencial inválida:** confirma que el package name y la huella SHA del APK instalado están registrados y que el JSON corresponde a esa configuración.
- **El selector de cuentas no muestra opciones:** confirma que el dispositivo tiene una cuenta Google y servicios de Google Play actualizados.

La app muestra mensajes sencillos y registra solo el tipo de error de Credential Manager; no registra ID tokens ni el contenido de la configuración.
