# Firma de Nexus Desktop

Comprobado el 2 de octubre de 2026:

- Smart App Control activo (VerifiedAndReputablePolicyState = 1).
- Nexus.exe: NotSigned.
- Las tres DLL de WebView2: firma Microsoft valida.
- Ningun certificado de firma de codigo en CurrentUser/My ni LocalMachine/My.
- No se ha encontrado Azure CLI ni configuracion local de Azure para usar una suscripcion existente. Esto no demuestra que el usuario no tenga una cuenta remota.

No se han cambiado politicas, instalado certificados raiz ni ejecutado el binario mediante otro cargador.

## Verificacion reproducible

Ejecutar `powershell -NoProfile -File desktop/verify-release.ps1`. Genera dist/signature-report.json y devuelve error mientras cualquier binario no tenga firma valida. Una firma valida por si sola no prueba el arranque ni la funcionalidad.

## Decision externa necesaria

Microsoft documenta dos rutas relevantes:

1. Microsoft Store con MSIX: Microsoft firma el paquete tras la certificacion. Requiere una cuenta de desarrollador, aceptar sus condiciones y remitir la aplicacion a Microsoft. La firma no tiene coste. No se ha creado una cuenta ni enviado la aplicacion. Faltan la identidad de editor y la reserva de nombre para construir un paquete asociado real.
2. Firma para distribucion privada: requiere identidad validada y certificado o servicio contratado. No se ha adquirido ninguno ni se ha comprometido gasto. Artifact Signing Public Trust limita actualmente los desarrolladores individuales a Estados Unidos y Canada; las organizaciones tienen una lista mas amplia. No se ha supuesto el pais ni el tipo de entidad del usuario.

No debe presentarse un certificado autofirmado como solucion equivalente para Smart App Control.

Fuentes oficiales consultadas:
- https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options
- https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart
- https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control
