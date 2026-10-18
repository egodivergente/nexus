# Nexus Windows + Corte — preview de integración

**Estado del encargo: PARCIAL. Esta entrega no acredita la paridad completa solicitada.**

Versión de aplicación: `2026.10.18-corte-preview.1`. Referencia Nexus Android:
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

En AXELPC, JDK 17, Gradle 9.1.0, Kotlin 2.3.20, máximo dos workers y sin compilaciones
paralelas entre módulos:

- 226 pruebas del núcleo Nexus, 15 del runtime y 77 del timeline más 7 de almacenamiento.
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
2. Conectar generadores multimedia personalizados al catálogo, cotización, subida,
   envío/seguimiento/cancelación y biblioteca Windows mediante el `MediaEngine` compartido.
3. Completar paridad de encargos/tarjetas, cancelación de turno, agrupación de resultados,
   visores, referencias, personalización y accesibilidad, con pruebas de cada flujo.
4. Implementar paquete portable completo con reimportación y migración de proyectos Corte
   anteriores; completar referencias externas sin copia y conservación de procedencia.
5. Verificar inferencia, visión, documentos, herramientas y razonamiento con cada cuenta
   externa configurada. Listado, activación de modelo e inferencia son resultados distintos.
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
