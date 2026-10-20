# Continuidad S25 ↔ Pixel · Nexus 1.9.59 / vc93

## Uso

En **Ajustes → Agente y modelos → Sincronización entre móviles**, vincular los dos
teléfonos con la misma clave aleatoria y la dirección del otro. Las direcciones
Tailscale permiten continuar fuera de la LAN, sin un proceso de sincronización en
el PC ni el ASUS. Los dos teléfonos necesitan conectividad y Nexus mantiene una
notificación de continuidad mientras el intercambio está activado.

Controles en la APK:

- Activación, dirección del otro teléfono y puerto propio.
- Clave de vinculación, intervalo en segundos y espera de red (0 = sin límite).
- Tamaño máximo por archivo en MB (0 = sin límite).
- Chats, memoria/proyectos/personajes, skills con sus archivos auxiliares,
  galería/generaciones completadas y adjuntos locales, independientemente.
- Claves API/puentes, ajustes/perfiles y autenticación AGY, independientemente.
- Dirección del adaptador AGY local y reanudación tras reiniciar.
- Propagación de eliminaciones; desactivada inicialmente para conservar las
  copias existentes. Afecta a los datos seleccionados, sin borrar una edición
  local concurrente de una skill ni la conversación actualmente abierta.

Las conversaciones recibidas aparecen con ↔ en el historial. Abrirlas permite
continuarlas; recibir cambios no sustituye el chat que el usuario está escribiendo.
Las sesiones y el proyecto abierto, los contadores locales de consumo y las rutas
de los puentes de cada dispositivo se mantienen locales. Los trabajos en curso
siguen perteneciendo al dispositivo que los envió: importar historial nunca envía
otra generación. Las tareas completadas incorporan outputs adicionales sin pisar
los archivos locales existentes.

## Integridad y ediciones simultáneas

El transporte usa tramas AES-256-GCM autenticadas y vinculadas a cada solicitud.
Cada trama lleva orden e identidad de transferencia; una transferencia truncada o
alterada se rechaza antes de confirmar el archivo. Los blobs se verifican por
SHA-256, se escriben primero en temporales y se reutilizan si ya están disponibles.
Esto permite reutilizar la galería migrada anteriormente al Pixel.

La memoria se combina con una base por dispositivo y documento, dentro del mismo
mutex y notificación de cambios que emplea el editor. Dos campos distintos pueden
combinarse; si un mismo campo cambia a la vez, se conserva el valor local y una
revisión recibida en `files/device-sync/conflicts/`. Los chats recibidos se guardan
como réplicas separadas. Las skills conservan su base de edición y los archivos
recibidos sin modificar no se vuelven a exportar como contenido propio.

Las revisiones que contienen claves o autenticación permanecen cifradas en disco,
incluidas las bases de combinación. La clave de emparejamiento se excluye de los
datos exportados. Desactivar una categoría en el teléfono emisor no se interpreta
como eliminar todos sus datos en el receptor.

## AGY en Termux

`tools/nexus-continuity-termux.mjs` es un adaptador local con las API incluidas en
Node; no añade paquetes npm. Escucha en `127.0.0.1:8796`, requiere la misma clave
de vinculación y solo accede a `antigravity-oauth-token` e `installation_id` de la
instalación Debian existente. No sustituye conversaciones ni ajustes de la CLI.
La APK conserva la ruta local de su puente AGY, que puede ser diferente en cada
teléfono. Los cambios de autenticación conservan una revisión anterior cifrada.

Instalación privada del adaptador:

- Debian: `/root/.nexus-continuity/adapter.mjs`, `pairing.key` y `options.json`.
- Termux: `$PREFIX/bin/nexus-continuity-start`, basado en el script versionado.
- Arranque: `~/.termux/boot/95-nexus-continuity`.

El receptor de arranque integrado de la versión de Termux instalada en ambos
teléfonos reconoce `~/.termux/boot`. El lanzador consulta las opciones antes de
arrancar y conserva los demás scripts. Si existe `nexus-agy-start`, también puede
reanudar el puente durante ese arranque. Android puede aplazar actividad durante
ahorro de batería o detener una aplicación forzada; el siguiente arranque de Nexus
reanuda el servicio habilitado y los cambios pendientes se reintentan.

## Verificacion final (2026-10-20)

- Build agentPreview PASS; 617 pruebas superadas y 4 omitidas; 2 Node PASS.
- Firma conservada: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
- APK vc93 SHA256: `a298c6b5b34c2e15d9db38bf9fce2959f1633f092497f805444a60b254cdb2d5`.
- Prueba fisica final sobre 1.9.60-sync-preview / vc94 en ambos moviles: integra
  este mismo codigo de continuidad con Drive. No degradar a vc93.
- Intercambio automatico bidireccional PASS: chat, adjunto con SHA256, memoria,
  skill y clave sintetica. Actualizaciones PASS en ambos sentidos: clave
  existente, memoria y retorno al origen de una skill editada en el receptor.
- AGY: GET autenticado y POST protegido sin cambios PASS en ambos adaptadores.
  No se han rotado tokens reales como prueba. Arranque configurado y sintaxis
  verificada; reinicio fisico no probado.
- Dos audios ya ausentes en origen siguen inaccesibles. Ambos moviles muestran
  el mismo aviso. La preparacion inicial de la galeria tarda varios minutos.
- Fixtures retirados de ambos moviles. Informes y backups privados en entregas/,
  fuera de Git. No se han enviado generaciones ni conversaciones de pago.

La tarea coordinada de Drive prepara una APK posterior unificada desde
`codex/nexus-drive-sync-android-20261020`. Coordinar nuevas instalaciones.
