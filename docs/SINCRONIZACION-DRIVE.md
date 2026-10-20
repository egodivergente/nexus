# Nexus Sync — operación

Windows: Ajustes → Sincronización. Android: Ajustes → Agente → Sincronización.
Conectar la misma cuenta en los tres dispositivos. Los clientes Desktop y
Android deben pertenecer al mismo proyecto Google Cloud para compartir los
archivos visibles con el permiso `drive.file`.

## Qué se comparte

Proyectos, escenas, planos, personajes, biblioteca y registros de generaciones.
Los medios disponibles se copian como originales verificados por SHA-256.
Un enlace caducado que ya no permite obtener el original aparece como pendiente;
la sincronización no puede recuperar bytes que el proveedor haya eliminado.

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
La opción Solo Wi-Fi usa una conexión no medida. El proyecto activo y los
seleccionados se descargan para uso local. Los demás conservan sus metadatos.

La sincronización manual está disponible aunque se desactive la automática.
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
