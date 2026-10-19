# Nexus Windows + Corte — preview de integración

**Estado del encargo: PARCIAL. Esta entrega no acredita la paridad completa solicitada.**

Versión de aplicación: `2026.10.19-corte-preview.4`. Referencia Nexus Android:
`8ceb3aa7ab44be162d7fc1c8f82a047d33d71ce6` (1.9.39/vc73). Corte compartido:
`5869228ae887a439fc522c2cbf76c13e29a143b2`. Se volvió a consultar GitHub y se observó
`ffc4d0ffb1311ac8eed1b3ec806089d6a6562d41`, que cambia únicamente la lista de métodos
del ContentProvider Android. No cambia los módulos compartidos usados aquí.

## Implementación disponible

- El runtime Windows compila directamente `agent-core/src`; no compila la copia histórica
  `desktop/agent/core/src`. Los tres módulos Corte se fijan por SHA y huellas individuales
  en `desktop/corte/source-lock.json`; `verify-corte-source.py` comprueba los 44 archivos.
- Bedrock/proxy existentes, Anthropic, Chat compatible, Antigravity y perfiles
  Chat/Responses/REST pasan por el host y runtime. Los perfiles pueden guardar varias
  configuraciones y claves independientes mediante DPAPI CurrentUser. El descubrimiento
  y la prueba de inferencia se solicitan expresamente; no se prueban modelos al arrancar.
- Los generadores multimedia personalizados se configuran en Ajustes. `WindowsMediaCodec`
  utiliza `MediaProviderConfig` y `MediaEngine` del núcleo compartido; el host Windows gestiona
  autenticación, subida DATA_URI/multipart, envío SYNC/ASYNC y seguimiento. Sus claves van en
  `custom-media-credentials.dpapi`, separado de los perfiles JSON. Los resultados incrustados
  se materializan con identificadores locales compatibles con biblioteca y Corte.
- La ventana incorpora medios del proyecto, visor, timeline e inspector con el chat
  acoplado y paneles plegables. El proyecto activo usa el identificador del proyecto Nexus.
  Hay selección, arrastre, zoom, atajos, menú contextual y tamaños de panel guardados.
- Las operaciones manuales y las herramientas `corte_*` utilizan el mismo proceso JVM
  y `AgentOps`/`TimelineEditor`. El foco y la selección proceden de `ChangeSet` y `AgentFocus`.
- El documento Windows guarda atómicamente proyecto, revisión, recibos, historial,
  selección, cabezal y relaciones de medios. Cada lote usa una transacción. El deshacer
  exige identificar la transacción superior; una edición posterior impide deshacer otra.
- La biblioteca se conecta al montaje por `asset_id` con `media_id` propio. Se puede
  colocar al cabezal o después de un clip, conservando el original. No se colocan resultados
  tardíos automáticamente en otro proyecto. La exportación usa un snapshot inmutable.
- FFmpeg decodifica y codifica. Los evaluadores originales Kotlin determinan las capas,
  títulos, ganancias y matriz de color que compone Windows. La previsualización y la
  exportación usan el mismo compositor; el visor reproduce ese vídeo preparado.
- Se adaptaron las herramientas recientes de biblioteca, documentos PDF con imágenes,
  lectura de texto/PDF, métricas de imagen y análisis de audio mediante cola persistente.

## Persistencia y conservación

Los datos reales permanecen en `%LOCALAPPDATA%/NexusDesktop`. El montaje se almacena
en `projects/<nexus_project_id>/edit.json`, con `.bak` validado y formato versionado.
El índice de activos, conversaciones, memoria, credenciales y encargos anteriores no
se reemplazan. Los medios se referencian por archivo local después de importarlos en
Nexus. Borrar o deshacer un clip no borra el archivo. Las referencias ausentes se
diagnostican y se pueden revincular explícitamente.

`corte-jobs` conserva exportaciones y previews. Un reinicio marca el trabajo interrumpido;
no lo reenvía. `corte-cache` guarda picos y metadatos regenerables; sus PCM temporales
se eliminan al acabar el análisis. Los originales y exportaciones no se limpian con la
caché. Falta implantar un presupuesto global y política de expulsión para esa caché.

## Comprobación ejecutada

En AXELPC, JDK 17, Gradle 9.1.0, Kotlin 2.3.20, un trabajador y sin compilaciones
paralelas entre módulos (JVM con SerialGC y dos procesadores lógicos visibles):

- Suites JVM: 228 pruebas del núcleo (4 omitidas), 15 del runtime, 77 del timeline y 7 de
  almacenamiento. Base sin red: 323 ejecutadas sin fallos y 4 omitidas. Las cuatro pruebas
  omitidas de BytePlus se ejecutaron después contra la cuenta real y pasaron: total acumulado 327; las cuatro llamadas reales corresponden a la validación anterior.
- Aceptación Windows con archivos reales y perfil aislado: importar, duplicar, dividir,
  colores, foco/selección, undo/redo, reapertura, lote inválido, doble envío tras reinicio,
  revisión obsoleta, edición manual posterior, exportación de 8 s con audio, cancelación,
  medio ausente, miniatura/onda/silencios y separación de proyectos.
- Se comparan fotogramas decodificados del preview y exportación de una composición
  pequeña con título, color, fades y transición. No equivale a certificar todos los codecs,
  resoluciones ni proyectos grandes.
- Prueba host → DPAPI → perfil Kotlin → servidor HTTP local → herramienta real → segunda
  inferencia. Los resultados del servidor local se identifican como prueba de integración;
  no son inferencia con las cuentas externas del usuario.
- Pruebas de PDF con imagen, lectura, métricas, análisis de audio y conservación del original.
- Ocho pruebas de generadores configurables: credencial DPAPI, servidor HTTP local,
  plantilla Kotlin tipada, subida, SYNC/ASYNC, snapshot de configuración, recibos tras reinicio,
  tarifa vinculada a configuración, referencia local sin base64 de ida y vuelta por el navegador,
  incertidumbre sin reenvío y distinción entre preparación interrumpida y envío sin confirmar.
  Edge prueba configuración → creación manual → inferencia local → tarjeta preparada sin envío
  automático → confirmación → biblioteca del proyecto → colocación en Corte. La reindexación
  importa también las salidas de generaciones ya registradas y conserva su proyecto.
  La indexación se realiza automáticamente cuando el turno queda libre; no requiere una orden
  manual ni interrumpe el callback de confirmación. También se prueba el polling UI asíncrono.
- Pruebas previas de edición local/MCP e integridad del host.
- UI real de Edge con un perfil de prueba: importación de activo, colocación, edición real,
  reproducción del preview, chat plegable, ajustes y navegación conservando el montaje.

Los informes y capturas se guardan en `outputs/windows-corte`, fuera de Git. No se han
realizado generaciones multimedia de pago ni se han enviado medios a proveedores.

## Instalación y bloqueo de activación

La instalación observada sigue ejecutando `1.9.5-94141592`. El perfil real contiene dos
encargos Atlas en PROCESSING; uno no tiene identificador remoto. Se consultó el otro por
GET y Atlas confirmó `processing`, sin resultados. No se termina ese proceso, no se
reenvía el encargo incierto y no se cambia su acceso directo mientras haya trabajo activo.

`package-windows-corte.py --install` prepara otra carpeta de instalación, verifica cada
archivo contra su manifest y conserva la versión anterior. El paquete excluye perfiles,
credenciales, bases de datos, media del usuario y backups. La nueva carpeta se comprueba
con datos de prueba separados. **Instalación preparada y comprobación aislada no significan
activación en el perfil de producción.** Queda pendiente cambiar el acceso en un punto seguro.

## Pendientes que impiden declarar terminado el encargo

La matriz `parity.json` contiene 71 entradas iniciales y todos los campos de referencia,
adaptación y aceptación. **Su exhaustividad no está certificada.** El inventario de fuentes
es `source-reference.json`, con 343 archivos Android y skills identificados por huella.

Pendientes concretos:

1. Reconciliar exhaustivamente todos los comportamientos y correcciones de Android;
   validar cada fila en la instalación activada con el perfil real.
2. Verificar generadores personalizados con cada API externa real y ampliar sus protocolos
   cuando sea necesario. La configuración genérica actual no define cancelación remota: se rechaza
   expresamente. Las tarifas son declaradas por el usuario; solo se interpretan unidades generation
   y second. Protocolos con firmas, WebSocket o subidas en varias etapas requieren otro adaptador.
3. Completar paridad de encargos/tarjetas, agrupación de resultados,
   visores, referencias, personalización y accesibilidad, con pruebas de cada flujo.
4. Completar migración directa de proyectos/paquetes Android anteriores y referencias externas
   sin copia. El portable Windows conserva procedencia y crea copias de los medios.
5. BytePlus pasó inferencia mínima, Chat Completions, Responses con herramienta y continuidad,
   y streaming con dola-seed-2-1-turbo-260628. La herramienta de la prueba API devuelve un
   estado simulado. La aceptación posterior con herramientas reales de Windows también pasó;
   faltan visión, documentos, razonamiento específico y las demás cuentas externas. La clave copiada del S25 permanece en DPAPI.
   Listado, activación de modelo e inferencia son resultados distintos.
6. Certificar multipista, transiciones con handles, VFR, velocidades, keyframes y mezclas
   extensas. La ganancia de audio Windows se evalúa por fotograma; falta equivalencia a nivel
   de muestra y cambio de velocidad con conservación del tono. LUT se rechaza; HDR pendiente.
7. Sustituir o ampliar la preparación de preview para reproducción interactiva de montajes
   grandes; acotar cachés, temporales y recursos a escala de producción.
8. Activar el paquete cuando termine el trabajo real, comprobar el acceso directo, procesos,
   módulos cargados y conservación de datos en el perfil real. La entrega completa sigue pendiente.

## Abrir el flujo cuando se active

Abrir Nexus → Montaje → crear o elegir proyecto → importar medios o revisar los resultados
del proyecto → añadir al cabezal o después de un clip → seleccionar y editar con Inspector
o con el agente → preparar visor → revisar → exportar vídeo. La edición local no necesita
el S25. Cada proveedor conserva sus requisitos de conexión y autenticación.

## Reproducir

```powershell
powershell -NoProfile -File desktop/tools/build-windows-corte.ps1
python -X utf8 desktop/tools/test-project-portable.py
python -X utf8 desktop/tools/test-agent-cancel.py
python -X utf8 desktop/tools/test-project-controls-ui.py
python -X utf8 desktop/tools/test-custom-media.py
python -X utf8 desktop/tools/test-corte-windows.py
python -X utf8 desktop/tools/test-windows-agent-providers.py
python -X utf8 desktop/tools/test-windows-library.py
python -X utf8 desktop/tools/test-native-parity.py
python -X utf8 desktop/tools/test-host-integrity.py
python -X utf8 desktop/tools/test-corte-ui.py
python -X utf8 desktop/tools/package-windows-corte.py --install
```

Los scripts de prueba usan datos aislados y medios sintéticos. El instalador anterior
`deploy-desktop-parity.py` detiene el host; **no es el flujo de esta preview**.

## Cambio concurrente posterior

Durante esta tarea, otro proceso incorporó `8c40c827f98b31f6efda31ab6bee9099691bb604` al checkout original: filtros de duplicados, origen completo, búsqueda visible y cabeceras de Biblioteca Android. También dejó modificaciones locales en `app/build.gradle.kts` y `LibraryScreen.kt`. Se preservan; sus novedades de UX no están todavía portadas y verificadas en Windows. La entrega se aisló desde `8ceb3aa` en `windows-corte-delivery` para no publicar cambios ajenos.

## Proveedores configurables

Ajustes → Generadores personalizados → Añadir proveedor. El JSON tiene el mismo formato que
Android (`MediaProviderConfig`); la clave se introduce en el campo protegido. `healthPath` permite
«Guardar y probar conexión» mediante GET, sin generar. Las referencias locales se suben o incrustan
en el host: los archivos grandes no recorren la interfaz codificados en base64.

Las cotizaciones caducan a los cinco minutos y quedan vinculadas a la configuración usada.
Una unidad de precio desconocida impide el envío; no se convierte implícitamente en segundos.
Cada creación se registra antes de contactar con la API. Un cierre durante la preparación se
identifica como no enviado; un cierre o corte después de iniciar la creación queda sin confirmar,
sin reenvío automático. El seguimiento usa el snapshot del protocolo original. Eliminar un
proveedor con trabajos pendientes o cambiar su destino exige conservar el identificador anterior.
No se afirma que el servidor remoto haya cancelado una tarea por detener su seguimiento local.

Durante QA se observó falta real de memoria nativa JVM y recursos del navegador. Se cerró solo
el daemon Gradle 9.1.0 después de verificarlo IDLE y se repitieron las pruebas secuencialmente.
El canal JVM separa diagnósticos de las respuestas JSON y el listener local admite la carga
concurrente de módulos de interfaz y RPC. Los registros de diagnóstico quedan fuera del paquete.

## Proyectos portables y detener el agente — preview 3

Ajustes → Proyectos portables permite exportar el proyecto activo e importar un ZIP como
copia independiente. Se incluyen activos, instrucciones/memoria del proyecto, relaciones,
procedencia y, si existe, el montaje con revisión, selección, cabezal y deshacer/rehacer.
No se incluyen credenciales, sesiones globales ni trabajos vivos del proveedor. El historial
de generaciones conserva la procedencia original en el paquete; la copia recibe identidades
nuevas y no reenvía ni se apropia de los encargos de producción.

Cada archivo se verifica con SHA-256. Se rechazan rutas externas, enlaces, entradas repetidas,
medios ausentes y relaciones incoherentes. Antes de publicar se valida el índice y el montaje.
La importación guarda un recibo persistente: «Reanudar importación pendiente» reutiliza la
misma operación después de una desconexión; no vuelve a crear el proyecto. Los originales
permanecen intactos. Este formato Windows no certifica importar directamente paquetes Android.

«Detener agente» interrumpe el turno LLM de esa ventana sin reiniciar el motor. Se comprueban
dueño e identidad del turno y se descartan respuestas tardías de herramientas. Las operaciones
que ya alcanzaron al proveedor conservan su seguimiento; detener el turno no afirma su
cancelación remota. Si una herramienta ya se está ejecutando, se espera su terminación antes
de permitir otra operación incompatible.

La aceptación usa el JVM real y Edge, con proveedor HTTP local: roundtrip, montaje/historial,
originales intactos, recuperación sin duplicados, corrupción/rutas/relaciones rechazadas,
cancelación durante inferencia y espera de herramienta, respuesta tardía y continuidad del
proceso. Son pruebas aisladas; la activación en el perfil real continúa pendiente.

## BytePlus con herramientas Windows — aceptación real del 19 de octubre

`test-byteplus-pc-live.py --live --desktop-root <instalación/desktop> --report <ruta.json>`
usa la cuenta configurada en DPAPI, con un perfil temporal independiente. Sin `--live` no
realiza llamadas externas. Nunca imprime ni escribe la clave en texto plano. La prueba
permite únicamente leer sus ajustes y leer/añadir un marcador al montaje aislado.

PASS en el runtime instalado `2026.10.19-corte-preview.3`, commit `cf055cd`, modelo
`dola-seed-2-1-turbo-260628`, Responses y streaming: `get_nexus_settings`,
`corte_read_project`, `corte_apply_ops` y otra lectura devolvieron datos reales del host.
La respuesta identificó el valor aleatorio de ajustes devuelto por la herramienta y la
revisión 1. Hubo 83 eventos de streaming, 3 rondas de herramientas, 88 474 tokens de entrada
y 436 de salida declarados por el proveedor; no se estima un coste sin tarifa verificada.

El marcador quedó guardado, se conservó al reiniciar el proceso Corte y se deshizo usando
su transacción. El deshacer lo ejecuta la aceptación local; no se atribuye al modelo.
Ajustes y credenciales de producción conservaron sus huellas. El host Python de la prueba
coincidía byte a byte con el instalado. No hubo generaciones multimedia ni activación
sobre el perfil habitual. Esto verifica cuatro llamadas de herramientas, no todas las
herramientas del PC, ni visión/documentos/razonamiento específico.

La evidencia con auditoría de llamadas está en `outputs/windows-corte/byteplus-pc-live.json`
(fuera de Git). No se repitió la inferencia real después del resultado satisfactorio.

## PDF visual para Responses — preview 4

El host detecta si un PDF requiere visión (imagen incrustada, gráfico vectorial, escaneo o página sin texto).
En perfiles Responses con visión, PDFium prepara imágenes JPEG de sus primeras ocho páginas,
con lado máximo de 1280 píxeles, en un proceso local limitado a 30 segundos. Se adjuntan al
modelo en orden, con cobertura explícita; más de ocho páginas se identifica como LECTURA
PARCIAL. El original se conserva. La extracción de texto sigue siendo la ruta de los PDF
completamente textuales. Los demás proveedores conservan su ruta previa.

La dependencia `pypdfium2>=5,<6` forma parte de `requirements-media.txt` / `setup-media.ps1`.
Si falta o un PDF no puede prepararse, se comunica el fallo. Un perfil Responses sin visión
no recibe un mensaje que aparente haber leído un escaneo. No se soporta aquí elegir un
intervalo posterior al de las primeras ocho páginas ni se certifica todo tipo de PDF.

`test-byteplus-documents.py` prueba transporte real JVM/host con servidor local, PDF textual,
PDF visual, imagen, límites de páginas y conservación. La prueba `--live` usa BytePlus,
razonamiento enabled/low, fixtures sintéticos y un relé local que solo registra metadatos
seleccionados. No registra claves, imágenes codificadas ni contenido de razonamiento.
La aceptación externa de esta corrección se registrará por separado.
