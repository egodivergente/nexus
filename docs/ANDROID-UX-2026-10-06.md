# NEXUS Android: descarga, asistente y puente local

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
