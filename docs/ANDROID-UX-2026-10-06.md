# NEXUS Android: descarga, asistente y puente local

## Resultado actualizado tras pruebas fisicas

- PASS (entrega posterior): S25 reconectado e identificado como R5CXC3J630Y.
  Instalacion `adb install -r --user 0` completada con Success, sin borrar datos.
  Package Manager confirma vc20 / 1.7.2-agent-preview, actualizada el 2026-10-06.
  Queda resuelto el bloqueo de instalacion S25 descrito mas abajo.

- PASS: APK Agent vc20 / 1.7.2-agent-preview recuperada de la tarea
  20261006T063456Z-f5369c86. SHA-256 remoto y local coinciden:
  ba68811a180af5df7eee15ecfb6407a1be05c76ba89a4deeedb3185377a1326a.
- PASS: firma local mediante selector, trabajo 20261006T075038Z-c1713a25,
  limites verificados de CPU 5% y RAM 512 MB. APK firmada SHA-256
  234229e00aa9e3a5c95dc3c46f3036a97e973599ca6463bb71014cbb6b450375.
  Certificado SHA-256 8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127,
  coincide con la instalacion S25 comprobada previamente.
- PASS: instalada y abierta en Pixel 8 Pro como com.vidadomu.nexus.agent,
  paquete que no existia alli. Otras instalaciones y sus datos intactos.
- PASS: fixture anadido exclusivamente a esta instalacion nueva y sin cuentas;
  tools/qa/seed_export_fixture.py exige historial y outputs vacios. Imagen local
  real visualizada y exportada mediante Descargar archivo -> Guardar.
  SHA-256 del original completo y del archivo guardado coinciden:
  4507a36d9ed0e5c4c9dfe68686f2efaeb7dfb1683cf0876cf07f58477f3b6913.
- PASS: cancelar el selector no crea archivo; segunda descarga no sobrescribe la
  anterior (Android anade sufijo). Cinco destinos visibles, acceso superior a
  Ajustes, pantalla Asistente sin las tres pestanas de modos. Capturas y XML
  conservados fuera de Git en outputs/s25-agent-update. Sin nuevos crashes de
  NEXUS en el buffer de crash consultado.
- PASS: plugin MCP de este chat responde usando la variante Agent del S25;
  consulta real devuelve 227 modelos IMAGE. Ya no requiere recarga en esta sesion.
- FAIL de infraestructura: intentos offlineQa 20261006T072138Z-4bcab20c y
  20261006T074932Z-aca8225d abortados por perdida de telemetria del rele PC.
  La prueba fisica final usa la APK Agent firmada, no una offlineQa sin terminar.
- PENDIENTE: actualizar S25. Install -r no llego a ejecutarse porque ADB quedo
  offline; comprobado tambien desde ASUS, reconexion TLS agota el tiempo.
- NO PROBADO: descarga HTTP de proveedor, video/audio, cancelacion durante una
  transferencia larga, TalkBack y escala de texto elevada. Sin generaciones de
  pago. ChatGPT movil y Flow Android siguen sin integracion operativa comprobada.

Lo siguiente conserva la cronologia anterior; prevalece el resultado de arriba.

## Cambios

- Visor: Descargar archivo usa ACTION_CREATE_DOCUMENT, copia por bloques el archivo
  local o descarga HTTP sin credenciales de proveedor. Muestra progreso, permite
  cancelar y elimina el documento incompleto creado por esta operacion si falla.
- Compartir enlace identifica correctamente la accion existente; no promete
  compartir el archivo binario.
- Un solo asistente visible. Se retiran las tres pestanas de modo y se conserva
  la confirmacion de coste; no se habilitan generaciones de pago automaticas.
- Navegacion inferior de cinco destinos; Ajustes pasa a la barra de saldos.
- Fixture de descarga local exclusivo de offlineQa, activado por qa_export.
  No genera contenido de pago ni modifica la base de datos de produccion.
- Puente Codex: destino com.vidadomu.nexus.agent y nombre completo de MainActivity.
  El plugin anterior consultaba la instalacion separada com.vidadomu.nexus.

## Verificacion

- PASS: 12 pruebas Python del puente, incluidas identidad de telefono, destino,
  actividad real, rechazo de operaciones de pago y validacion de borradores.
- PASS: proceso nuevo del puente consulta el S25, version 1.7.1-agent-preview,
  catalogReady=true; catalogo devuelve 832 modelos. Sin generacion.
- FAIL inicial: trabajo Pixel 20261006T060913Z-d9fbf43f ejecuta 217 pruebas,
  215 correctas. Dos pruebas esperaban la anterior barra de seis destinos.
  Expectativas corregidas conservando las pruebas de inicializacion por destino.
- Segunda compilacion 20261006T062522Z-ae4f8090 aplazada por bloqueo exclusivo
  de otra tarea en Pixel. No se detuvo ni modifico ese trabajo.
- Reintento 20261006T063456Z-f5369c86: supera tests y assembleAgentPreview;
  al compilar offlineQa queda pausado por pantalla encendida en Pixel.
  Tras confirmar el usuario que no estaba usando el Pixel, se apago la pantalla
  una vez y se reanudo. Otra tarea volvio a abrir apps (Corte, AXEL Tasker y Drobe),
  pausando de nuevo la compilacion. Se ha solicitado permiso para coordinar esas
  tareas; no se han detenido sus procesos ni modificado sus trabajos.
- PASS: informes recuperados del reintento, 217 tests Android, cero fallos y
  cero errores en 36 suites. Archivo recuperado y manifiesto SHA-256 verificados.
- FAIL: el reintento termina por proteccion tras cinco minutos de pausa por
  pantalla encendida. No se ha recuperado una APK de ese trabajo ni instalado
  esta version. La validacion visual y de descarga sigue pendiente de Pixel libre.
- NO PROBADO aun: nueva APK instalada, guardado real en selector Android,
  accesibilidad visual y regresiones de navegacion en dispositivo.

## Limites de integracion

El proceso MCP ya abierto conserva codigo anterior: requiere recarga del plugin.
No se han terminado procesos MCP de otras tareas. El puente consulta modelos y
prepara borradores, no adjunta medios ni ejecuta generaciones.

Las apps MCP personalizadas no estan soportadas directamente en ChatGPT movil,
segun https://help.openai.com/en/articles/12584461-developer-mode-and-mcp-apps-in-chatgpt.
La pestana Remote puede dar acceso a chats Codex compatibles, segun
https://help.openai.com/en/articles/20001275-chatgpt-work-and-codex;
esta ruta NO esta probada con NEXUS en el S25 y depende del PC y ADB.

Flow existe en desktop/flow_provider.py, no en Android. El servidor desktop
actual es local con validacion de origen y sesion; no se ha expuesto a la red ni
presentado como una integracion movil funcional.

No se han borrado datos, transferido claves de firma ni ejecutado pagos.
