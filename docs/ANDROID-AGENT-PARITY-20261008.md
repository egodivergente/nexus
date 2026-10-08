# NEXUS Agent: auditoría y ejecución Android

## Alcance y evidencia

Fuente de partida: rama `codex/s25-agent-update`, commit e756224. La raíz antigua del escritorio contiene Android 1.6.3; no sirve para actualizar Agent. Fuente Agent 1.7.5; instalado en S25 `com.vidadomu.nexus.agent`, 1.7.4-agent-preview (22), verificado por ADB el 08-10.

Firma instalada SHA-256: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Coincide con la clave existente, conservada en Windows. Backup privado por transporte binario `adb exec-out`: SQLite `integrity_check=ok`, 93 generations y 91 outputs. Chat instalado: 38 elementos, 2 planes y 5 adjuntos de sesión. Los backups y prompts quedan fuera de Git.

## Plan de principio a fin

| Área | Hallazgo en la fuente | Implementación y comprobación |
|---|---|---|
| Procedencia | Dos fuentes Android diferentes | Rama aislada sobre Agent; conservar paquete, datos y firma de actualización |
| Modos | Carga fija de Copiloto | Recuperar modo persistido; selector Asistente/Copiloto/Agente |
| Generación | Tarjeta extensa + segundo diálogo | Resumen junto al editor; una acción para todos los planes elegidos; detalles opcionales |
| Bloques | Cada plan se confirma por separado | Hasta 20 encargos, concurrencia limitada, errores aislados y estados por encargo |
| Prompts | Validación sin adaptación compartida | Núcleo compartido con adaptación semántica al límite real y original conservado |
| Costes | Salvaguarda antigua | Coste desconocido visible, sin bloqueo artificial de otros encargos válidos |
| Formato | Defaults del proveedor | 16:9 cuando el schema lo admite; edición explícita conservada |
| Scroll | Scroll incondicional por cantidad de mensajes | Seguir al estar al final; preservar lectura; volver al final al enviar; acceso al último mensaje |
| Editor | Deshabilitado durante respuesta | Borrador editable mientras trabaja; envío bloqueado hasta terminar; teclado y foco estables |
| Modelos | No hay sugerencias al escribir | Catálogo real, coincidencias parciales, inserción en el cursor y nombre exacto |
| Proyectos | Memoria pero sin importador visible | Importar fotos/vídeos una vez, nombre lógico editable, vista y reutilización por asset_id |
| Historial | Chat solo en ViewModel | Persistencia privada y atómica; migración del chat instalado sin tocar el original; archivo y reapertura de conversaciones; contexto sin repetir herramientas |
| Resultados | Solo botón Historial | Medios en el chat, orden de respuesta y estado del trabajo, apertura de visor |
| Temas | Ya hay cuatro temas globales | Conservarlos y ampliar alternativas; no presentar como inexistente lo ya implementado |
| Telemetría | Ausente del chat | Latencia real del último turno, estado de agente y trabajos, detalles desplegables |
| Flow | Pantalla separada, solo texto, puente antiguo | Integrar acceso existente sin pasos duplicados; registrar capacidades verificadas del puente antes de ampliar parámetros/medios |
| Robustez | Riesgo de duplicar envíos inciertos | Idempotencia y seguimiento; no reenviar automáticamente un envío ambiguo |
| Accesibilidad | Controles móviles y landscape sin QA reciente | Etiquetas, toque 48dp, IME, ancho adaptable y pruebas físicas sin gasto |
| Entrega | S25 conectado por ADB | Compilación directa, tests JVM/Android, APK verificada, certificado compatible, actualización sin borrar datos, comprobación S25 |

## Criterio de cierre

La entrega se distingue en código implementado, pruebas ejecutadas, APK compilada y actualización instalada. El cierre del S25 requiere conexión real, comparación de certificado y prueba en el dispositivo. Una APK transferida no cuenta como instalada. Ninguna prueba ejecuta generaciones de pago.


## Diferencias adicionales y dependencias reales

| Área | Estado y límite |
|---|---|
| Flow: modelo/formato/resolución/referencias | El puente ASUS que usa la APK recibe texto y utiliza el proyecto activo. No hay evidencia de que acepte esos parámetros. No inventar un selector ni afirmar que 16:9 se ha aplicado en Flow. Requiere ampliar el contrato y la automatización del puente. |
| Flow: cantidad | La nueva cola reúne resultados mediante bloques con identificadores estables. El puente decide cuántos produce por bloque; puede entregar más de lo solicitado. No se descartan resultados para ocultarlo. |
| Flow: lote con prompts distintos | Hasta 20 por pedido, enviados al puente por cola. Envíos inciertos conservan su seguimiento; no se repiten a ciegas. |
| Catálogo y medios Runway | Contratos con un único campo de imagen/vídeo se mapean por su clave real; máscaras/audio y contratos ambiguos siguen declarados incompatibles. Falta ampliar roles y pruebas de esos medios. |
| Encargos desde pantalla manual | La pantalla de varios modelos admite imágenes. Los vídeos se preparan desde el agente; falta extender la pantalla manual a vídeo y a sus ranuras específicas. |
| Respuesta de LLM en streaming | El contrato móvil actual usa `complete`; scroll de respuestas finales y cambios de estado, sin inventar streaming de tokens. Implementar streaming exige ampliar el proveedor y la recuperación de turnos. |
| Visor | Imágenes en conversación y apertura del visor existente; vídeo mediante visor. No se ha implementado reproducción inline ni drag-and-drop móvil. |
| Sesiones antiguas | Se conserva el archivo legado y se migra el chat actual; `assistant/index.json` es el índice de proyectos y assets, se conserva y reutiliza. No se encontró un archivo de sesiones múltiples instalado. |
| Proveedor y facturación reales | Las pruebas usan fakes o Nexus Lab sin Internet. Ninguna prueba cobra ni genera contenido en las cuentas reales. |

## Validación y entrega (2026-10-08)

- Núcleo JVM: 93 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Suite JVM Android: 227 pruebas, 0 fallos, 0 errores, 0 omitidas.
- APK final corregida compilada directamente en Pixel 8 Pro, 404 archivos de fuente verificados; recompilación de arranque completada en 7 min 48 s.
- APK firmada con el certificado de la instalación existente; SHA-256 `9769e0ccbcf0b92513968f2d309de99d08362d392368b1c64b557978afeeebe7`.
- S25 físico SM-S938B: actualización `adb install -r` completada, versión 24 / `1.8.0-agent-preview` verificada. Apertura real de la actividad y navegación al Asistente realizadas; conversación y resultados antiguos visibles. Proceso vivo sin excepciones fatales nuevas.
- Se detectó y corrigió un crash real de la primera actualización: la emisión síncrona de StateFlow en init ejecutaba onJobs antes de inicializar synced. synced, completedSeen y msgSeq se inicializan ahora antes de registrar colectores.
- Datos finales: 93 trabajos, 91 resultados, SQLite `integrity_check=ok`; 38 elementos de chat, dos planes y cinco referencias de sesión recuperados. Archivo legado conservado. Las 18 entradas del almacenamiento cifrado siguen presentes; algunos valores cambian tras la consulta de proveedores. No se afirma identidad byte a byte después del arranque. Copias privadas fuera de Git.
- Diagnóstico: almacenamiento de credenciales disponible, Flow enlazado, 34 skills sin errores, un proyecto y 97 assets.
- Instrumentación Pixel: prueba de migración del historial superada. Las dos pruebas Compose no obtuvieron una jerarquía de UI con el Pixel bloqueado y pantalla apagada; no se cuentan como superadas. Su ejecución sigue pendiente en un entorno visible.
- No se ejecutaron generaciones reales ni se borraron datos.
- Selector/router: retirado de la instalación activa y del repositorio; compilación directa.
