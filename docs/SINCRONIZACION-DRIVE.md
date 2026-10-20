# Nexus Sync — operación

Windows: Ajustes → Sincronización. Android: Ajustes → Agente → Sincronización.
Conectar la misma cuenta en los tres dispositivos. Los clientes Desktop y
Android deben pertenecer al mismo proyecto Google Cloud para compartir los
archivos visibles con el permiso `drive.file`.

La APK unificada 1.9.63-agent-preview/vc97 conserva también la continuidad directa entre
móviles. Sus controles aparecen separados: **Google Drive** y **Sincronización
entre móviles**. Drive mantiene la copia compartida con Desktop; la continuidad
directa conserva sus categorías y su vinculación existente. Ambos transportes
escriben mediante el bloqueo común del índice; una edición recibida durante una
transferencia de Drive provoca un reintento, conservando la edición reciente.

## Estado del despliegue — 20 de octubre de 2026

S25: vc97 instalada, firma y todos los IDs conservados. Windows: revisión
2026.10.20-device-continuity.2 activa, con Drive y continuidad cifrada. Pixel:
vc96-background conservada; vc97 preparada y pendiente de recuperar ADB. Las
pruebas de archivos reales en ambos sentidos con los tres dispositivos se
completaron antes de esa desconexión; no equivalen a certificar vc97 en Pixel.

Windows y S2597 muestran cero grupos de URI visible duplicada por proyecto;
se conservan los IDs históricos y las versiones concurrentes para revisión.
La APK97 compilada pasa707 pruebas unitarias y tiene4 omitidas.

## Qué se comparte

Proyectos, escenas, planos, personajes, biblioteca y registros de generaciones.
Los medios disponibles se copian como originales verificados por SHA-256.
Un enlace caducado que ya no permite obtener el original aparece como pendiente;
la sincronización no puede recuperar bytes que el proveedor haya eliminado.

Se reutilizan las identidades existentes entre dispositivos. Si dos copias
manuales tienen IDs distintos pero bytes idénticos, queda una entrada visible
por proyecto en la biblioteca. Se conservan las identidades históricas y sus
referencias de montajes e historial; no se borran los originales. No se fusionan
proyectos diferentes solo porque tengan el mismo nombre.

Las conversaciones se importan como historial de texto. Los planes ejecutables,
autorizaciones de herramientas, credenciales y tarjetas de acciones no se
reactivan en el otro dispositivo. Los documentos Corte, incluido su historial
de deshacer, se conservan; Android los transporta y Desktop los abre. No se
integra en esta entrega una aplicación Corte externa de Android.

## Funcionamiento

Windows intenta sincronizar cada 30 segundos cuando el agente está disponible.
Android solicita sincronización al cambiar el índice y al volver a la app;
WorkManager proporciona recuperación periódica a partir de 15 minutos. Android
puede aplazar esos trabajos por ahorro de batería y condiciones de red.
La transferencia usa un servicio de primer plano con notificación para admitir
la primera copia de originales aunque dure más de diez minutos. Al inicializar
se vuelve a programar un trabajo periódico que haya quedado fallido o cancelado.
La opción Solo Wi-Fi usa una conexión no medida. El proyecto activo y los
seleccionados se descargan para uso local. Los demás conservan sus metadatos.

La sincronización manual está disponible aunque se desactive la automática.
Su cola es independiente de las esperas del reintento automático. No cancela
una transferencia en curso. Un intento manual fallido permite pulsar de nuevo;
la cola automática conserva sus reintentos cuando está activada.
Desconectar conserva los originales y los proyectos del dispositivo. No vaciar
ni sustituir el perfil para reparar una autorización: volver a conectar Google.

Cada cambio se registra con identidad de dispositivo y padres inmutables.
Si hay varias versiones concurrentes se conservan y aparecen para revisión.
Elegir una versión crea una nueva revisión que une las anteriores. El índice
anterior a aplicar cambios y los montajes sustituidos tienen copia privada de
recuperación. La sincronización de metadatos y montajes usa reintentos; un corte
entre ambas escrituras se recupera en el ciclo posterior.

## Registro y secretos

Google Drive API v3, clientes OAuth Desktop y Android, permiso exclusivo
`https://www.googleapis.com/auth/drive.file`. Registrar el paquete y SHA-1 del
certificado ya instalado; no cambiar la firma para resolver un error OAuth.
Android usa Google Identity Authorization Client. Desktop usa PKCE y callback
en loopback; almacena sus tokens y secreto de cliente con Windows DPAPI.
Android identifica la cuenta autorizada mediante `about.get` de Drive con el
mismo permiso `drive.file`, sin depender de un perfil de inicio de sesión ni
solicitar permisos adicionales de correo o contactos.
El JSON importado mediante el selector se elimina de la copia temporal de medios
una vez cifrado. No añadir configuraciones OAuth privadas al repositorio.

El proyecto actual sigue en Prueba. Google limita a siete días los refresh
tokens de este estado para Drive; se requiere completar la marca y publicar
OAuth antes de ofrecer funcionamiento desatendido duradero. La publicación es
global al proyecto y también afecta a su cliente web preexistente.

Documentación: [caducidad OAuth](https://developers.google.com/identity/protocols/oauth2#expiration),
[permiso por archivo](https://developers.google.com/workspace/drive/api/guides/api-specific-auth),
[autorización Android](https://developers.google.com/identity/authorization/android/authorize-access),
[subidas reanudables](https://developers.google.com/workspace/drive/api/guides/manage-uploads).
