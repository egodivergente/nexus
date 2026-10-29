# Nexus Agent — entrega vc164 y checkpoint histórico vc161

Fecha: 29/10/2026. Rama: `codex/nexus-premium-20261029`.

## Suplemento vc164 — altura útil y preview real

**1.9.128-agent-preview / vc164 está instalada como actualización en ambos móviles,
con versión, hash instalado y todas las huellas del borrador antes/después iguales.**
La QA visual propia del Pixel pasa; la del S25 está **BLOQUEADA por Keyguard**.
`am start` devuelve Status ok en S25, pero Nexus no está en primer plano: no se
presenta ese resultado como arranque visual ni como aceptación completa en ambos.

| Artefacto vc164 | Evidencia |
|---|---|
| Fuente compilada y publicada | [`d2616d2cde9f44cf81e48c47e8dbb28a53345dc1`](https://github.com/egodivergente/nexus/commit/d2616d2cde9f44cf81e48c47e8dbb28a53345dc1); push y SHA remoto verificados en la rama propia |
| Build | AXELPC, 8 min 54 s, PASS |
| APK | `C:/Users/User/Documents/Codex/nexus-premium-20261029/Nexus-Agent-1.9.128-v164.apk` |
| SHA-256 | `99dfb4ed53e9ac188674172decc8c3d1f4e6d8d8bfb24a8da7cca81e5fd69f39` |
| Firma / alineación / nativas | Certificado existente `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`; 16 KiB PASS; cinco ARM64 idénticas a vc155 |
| Pruebas de este build | **205 app ejecutadas PASS**, 0 fallos/errores/omitidas; **4 núcleo UP-TO-DATE**, resultado válido reutilizado sin cambios del núcleo. No son 209 ejecuciones nuevas |
| Instalación / borradores | Success, vc164 y hash de APK coincidente en ambos; prompt, negativo, parámetros, IDs, URI y conteos iguales durante la actualización |

La corrección usa la altura sobrante para el viewport editable y previews reales,
manteniendo controles y márgenes. La fuente grande no impone un umbral artificial:
decide la altura mínima medida. Si solo un fotograma del par está poblado y el par
horizontal cabe, su preview ocupa todo el ancho y los vacíos quedan compactos.
Los previews pueden recibir hasta el 60 % del sobrante en una columna, con límite
por ancho; el resto va al editor. No cambian capacidades ni comportamiento de envío.

| QA propia vc164 | Pixel 8 Pro | S25 Ultra |
|---|---|---|
| Instalación y borrador preservado | 72 caracteres, 1 referencia QA activa / 0 conservadas al instalar | 4908 caracteres, 3 activos / 2 conservados; todas las huellas iguales |
| Retrato con foto → retirada → vacío | PASS; textos/parámetros y fingerprint original recuperados, 0 activos / 0 conservados; sin borrar el archivo original | No comprobado visualmente en vc164 |
| Fuente 1,5× / ancho 800 × 533 + IME | PASS; prompt mantiene cursor/foco y hoja Duración sobre IME. Retrato tras IME conserva controles visibles | No comprobado visualmente en vc164 |
| Estabilidad visual | Dos capturas normales vc164 idénticas en geometría | Keyguard `showing=true`, `inputRestricted=true`, foco Bouncer/NotificationShade; Nexus no foreground |
| Navegación propia vc164 | Agente → ArtBox → Biblioteca → Lanzadera completada, con capturas; revisión independiente sin otro hallazgo visual de impacto en retrato/fuente 1,5× | No comprobada en vc164; bloqueo persiste, sin otra tentativa |

| Geometría Pixel, píxeles físicos y mismo tipo de escenario | vc161 | vc164 |
|---|---|---|
| Viewport prompt, sin referencias | 192 px | 1261 px; bounds `[96,444,1248,1705]` |
| Hueco Avanzado → Coste, sin referencias | 1208 px | 115 px |
| Viewport prompt, con una foto | 192 px | 553 px; bounds `[96,444,1248,997]` |
| Hueco Avanzado → Coste, con una foto | 1070 px | 54 px; Avanzado termina en y2606 y Coste empieza en y2660 |
| Hueco con una foto y fuente 1,5× | Sin comparación equivalente atribuida | 48 px |

La retirada de la referencia QA devuelve exactamente el borrador original del Pixel
en textos, parámetros, IDs/URI y conteos. Las huellas de instalación del S25 prueban
conservación durante la actualización, no inspección visual de su formulario.
La última captura S25 con tres referencias corresponde a **vc162**, sin atribuirla
a vc164. vc163 fue solo compilada, no instalada.

### Cierre vc164 y límite del S25

La identidad final vuelve a comprobar vc164/hash coincidente y el borrador original
exacto en ambos, sin ausencias en sus huellas. Pixel: Nexus foreground y Keyguard
false; S25: Nexus no foreground, Keyguard showing/inputRestricted true. La navegación
Pixel está completada. El colector definitivo posterior a navegación no detectó
Fatal/ANR de Nexus en las ventanas disponibles:

| Logs propios vc164 | Pixel | S25 |
|---|---|---|
| Líneas revisadas / Fatal o ANR de Nexus detectados | 14 726 / 0 | 101 957 / 0 |
| Intervalo epoch del dispositivo | 1793253210.081 → 1793253895.315 | 1793253268.022 → 1793253937.221 |

Estos logs no equivalen a QA visual del S25. Fuente/tamaño/rotación
temporales restaurados: Android 1,0×, timeout 300000 ms, autoRot 1/userRotation 0;
Pixel 1344 × 2992 @480, S25 1440 × 3120 con override 560. Se conserva S25 AURORA
y escala de app ≈0,95.

**Pendiente humano: desbloquear el S25 y repetir la QA visual vc164.** Despertar,
dismiss y colapsar Shade no resolvieron el bloqueo; no se intentó PIN ni bypass.
No hay decisión pendiente de pago, cambio de firma o acción destructiva. TalkBack,
háptica física, S Pen y 120 Hz no están certificados. No se declara nivel 10 ni
completitud física de ambos móviles. El detalle arquitectónico está en
[CAPABILITY_UI.md](CAPABILITY_UI.md); instalación y QA propia en los JSON privados
`release-v164.json`, `installation-v164.json`, `qa-v164-layout.json`,
`qa-v164-cleanup.json`, `final-v164-identity.json` y `final-v164-device-checks.json`.

## Checkpoint histórico comprobado vc161

Las secciones siguientes conservan la entrega vc161 y sus datos propios; sus
resultados de navegación, logs y comparación completa de bases no se atribuyen a vc164.

## Resultado comprobado

**vc161 / 1.9.125-agent-preview está compilada en AXELPC e instalada como actualización en Pixel 8 Pro y S25 Ultra.** Ambos tienen el mismo hash de APK, versión y arranque comprobados, con Nexus en primer plano. **209 pruebas PASS: 205 app y 4 núcleo, sin fallos, errores ni omitidas.** Fuente [`2abd8d732fa686aff954bcf95516a67cd76feb8d`](https://github.com/egodivergente/nexus/commit/2abd8d732fa686aff954bcf95516a67cd76feb8d), publicada y verificada en remoto.

La QA final del Pixel confirma retrato, fuente 1,5×, ventana 800 × 533 dp con IME y foco estable, y hoja de Duración sobre el teclado. En S25 se comprobaron las cuatro pestañas y la principal de Lanzadera con todas sus entradas, chips, Avanzado y Generar visibles sin scroll en el formulario observado. Se conserva la distinción entre estas pruebas, los tests y las capacidades físicas no verificadas. No se declara nota 10 ni aceptación global de todos los submenús y combinaciones.

## Última pasada y cierre de QA

El recorrido real Kling 4 I2V → MiniMax H3 Ref preservó el prompt de 72 caracteres y el ID/URI de la imagen, con 1 activo y 0 conservados. El retorno **por selector** a Kling 4 reprodujo exactamente prompt, negativo, parámetros, IDs, URI y conteos. Después se quitó la referencia de QA y se cerró la hoja: 0 activos y 0 conservados, con textos y parámetros originales intactos. Se retiró el vínculo del borrador, sin borrar el archivo original.

La acción «Volver» del Snackbar expiró antes del toque y **no cuenta como PASS físico** en este recorrido; la prueba de retorno anterior corresponde al selector. Undo tiene cobertura automatizada. Las últimas pasadas del alcance tocado no encontraron otro hallazgo bloqueante; esto no equivale a certificar globalmente todos los flujos del producto.

| Revisión final desde la actualización, ventana disponible de logcat | Pixel | S25 |
|---|---|---|
| Líneas revisadas / Fatal o ANR detectados | 12 788 / 0 | 235 950 / 0 |
| Intervalo epoch del dispositivo | 1793248241.347 → 1793249452.138 | 1793248263.057 → 1793249459.393 |

Las configuraciones temporales quedaron restauradas: fuente Android 1,0×, timeout 300000 ms, rotación automática 1 y orientación de usuario 0. Pixel: tamaño físico 1344 × 2992 y densidad 480, tema LIGHT. S25: tamaño físico 1440 × 3120 y densidad override 560, tema AURORA y escala de fuente de la app ≈0,95. Se conservaron las preferencias de apariencia; las ventanas de logs son evidencia acotada, no una garantía global.

## Artefacto y publicación

| Elemento | Evidencia final vc161 |
|---|---|
| Repositorio / rama | `egodivergente/nexus` / `codex/nexus-premium-20261029` |
| Fuente | `2abd8d732fa686aff954bcf95516a67cd76feb8d`, push y comprobación remota realizados |
| Base reconciliada | `dead4032d43f2b47ccc58471b4466d2adf2f313e`, vc155 |
| Build | AXELPC, JDK 17, variante `agentPreview` |
| APK | `C:/Users/User/Documents/Codex/nexus-premium-20261029/Nexus-Agent-1.9.125-v161.apk` |
| SHA-256 APK | `f94b544b2ee05495936342d7698eb7a088ade1d40d8883b43b566f8ef47ad05a` |
| Paquete / versión | `com.vidadomu.nexus.agent` / vc161 / `1.9.125-agent-preview` |
| Instalación | `adb install -r -t --user 0`: Success en ambos; versión y hash instalado iguales al artefacto |
| Certificado existente | SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`, conservado |
| Alineación / nativas | 16 KiB PASS; cinco bibliotecas ARM64 byte a byte idénticas a vc155, sin recompilar C++ |
| Pruebas | **209 PASS: 205 app + 4 núcleo; 0 fallos, 0 errores, 0 omitidas** |

Se usaron commits por bloques y una rama propia, sin force-push ni inclusión indiscriminada del workspace. La fuente y este cierre documental se publican en la misma rama. APK, capturas, bases, logs, firma y credenciales permanecen fuera de Git.

## Cambios y motivo

| Área | Resultado implementado y verificado en código |
|---|---|
| Diseño y accesibilidad | Tokens compartidos de medidas, radios e iconos; objetivos táctiles de 48 dp en controles tocados; pestañas sin recorte artificial de escala; cabeceras, proveedores y selección adaptados a texto grande. |
| Háptica y gestos | Política central por selección, confirmación, error y gesto, respetando Nexus/Android. Retirados bypass del vibrador y señales duplicadas. Cancelar resize/movimiento de logos libera estado en `finally` y no confirma el gesto. |
| Chat y conversaciones | Compositor compacto y drawer con búsqueda, preview y acciones reversibles. En el escenario medido, compositor de unos 116 a 60 dp y primera fila del drawer de unos 226 a 178 dp. |
| Arranque y navegación | Runtime esperado fuera de main antes del ViewModel, error con reintento y avisos históricos restaurados sin nueva alerta. Cambiar de pestaña libera foco y oculta IME sin editar el borrador. |
| Biblioteca y ajustes | Deshacer de eliminación lógica/ocultación, restauración atómica de atributos, estados sin resultados, sliders con nombre y Atrás coherente en personalización avanzada. |
| Catálogo | Warmup cancelable por snapshot completado y caché ajustada al catálogo real de 863 modelos, evitando el barrido de las 768 entradas anteriores; reducción con orden LRU preservado. |
| Principal de Lanzadera | Estructura fija habitual, columnas en ancho, prompt compacto/ampliado y controles principales con editor individual. Entradas resumidas y gestión completa en hojas; Avanzado reúne opciones extensas. Scroll de fallback cuando IME, texto grande o altura lo requieren. El prompt conserva su rama de layout al entrar IME. |
| Transferencia de ajustes | Intención compatible por clave nativa o semántica inequívoca de duración/formato/resolución, sin conversión inventada, redondeo ni truncado. Se conserva el origen, el fallback por destino y las anotaciones de adaptación. |
| Recursos y propietario | Handoff por rol/MIME/capacidad, manteniendo ID/URI y borrador fuente. Máscaras, campos ambiguos y desconocidos conservan owner reversible. Reparto de adjuntos, Comparison y preflight rechazan owner ajeno aunque coincidan clave/MIME. |

## Motor de capacidades y pruebas

La ampliación autorizó separar capacidades, estado, restricciones y presentación. Se reutilizan catálogo, contratos, precios y adaptadores, **sin dependencias nuevas**. Ruta integrada: `ModelCapabilityRegistry → ComposerState/CreateUiState → CapabilityConstraintEngine → formulario → GenerationDispatcher → adaptadores`.

La identidad incluye proveedor, variante, endpoint y modalidad. Se distinguen confirmado, no soportado y desconocido, con procedencia y fecha conocida o desconocida; cargar un snapshot local no acredita una verificación remota nueva. Los límites proceden de contratos y restricciones revisadas del adaptador; un máximo no documentado permanece desconocido. El motor representa condiciones `if/then/else`, requisitos, enumeraciones, cardinalidad, visibilidad y pasos exactos con `BigDecimal`. Los valores ocultos se conservan inactivos; reglas no representadas y entradas estructuradas obligatorias sin editor bloquean con explicación. **No es un intérprete completo de JSON Schema.** Arquitectura: [CAPABILITY_UI.md](CAPABILITY_UI.md).

| Cobertura dentro de las 209 pruebas PASS | Alcance |
|---|---|
| Capacidades | Diez escenarios de texto, I2V, inicio/final, multirreferencia, vídeo, dependencias, resolución por duración, desconocido, cambio incompatible y multimodelo; pasos exactos, reglas anidadas, ocultos y guards de owner |
| Transferencia y persistencia | Compatibilidad conjunta, fallback, restauración al reabrir, edición explícita, cambio encadenado durante carga, aislamiento de configuración y formatos antiguos |
| Handoff de media | Contratos → Native → contratos, excedentes retenidos/reactivados, binding de vuelta, final separado del inicial, máscaras/ambigüedad, colisiones de clave y persistencia de owner |
| Comparison | 17 pruebas, incluido rechazo de owner ajeno antes del remapeo, matching/legacy y conservación del owner |
| Biblioteca, núcleo | 3 pruebas de restauración y 1 de eliminación lógica |
| Otras regresiones | Háptica, movimiento, contraste, avisos históricos, arranque, caché, contratos, borradores y preview |

La transferencia valida contra el contrato resuelto, incluidas dependencias conjuntas. Si no admite todo, mantiene un subconjunto compatible y el fallback del destino para lo restante. Las anotaciones se persisten en `CreationDraft`, configuración y Undo: A=15 → B=5 → reabrir B → A no convierte el fallback 5 en intención nueva; editar B=6 sí lo hace. La restauración prepara parámetros pendientes antes del schema y no reaplica después el borrador original sobre una edición. Los cambios encadenados conservan ediciones de claves nativas declaradas, con validación posterior. Estas regresiones pasan en el build final; el recorrido físico de cambio y retorno por selector se documenta en el cierre de QA.

El owner (`providerId`, `modelId`, `endpoint`) es metadata privada de borrador con default nulo compatible con datos antiguos; no entra en `MediaInput` ni en payloads. El origen se guarda antes de canonizar IDs activos; un binding guardado del destino solo sustituye el canónico cuando su metadata lo reconoce. Estados construidos directamente y Comparison también rechazan propietarios ajenos. El modelo propietario conserva sus entradas válidas, incluidas máscaras.

Se usaron contratos versionados y fixtures sintéticas identificadas para probar el mecanismo, sin atribuir capacidades comerciales. No se pulsó Generar ni se ejecutaron peticiones de pago o LLM como parte de esta QA.

## Comprobación física por dispositivo

| Escenario | Pixel 8 Pro | S25 Ultra |
|---|---|---|
| Identidad | `38271FDJG00264`, Pixel 8 Pro | `R5CXC3J630Y`, SM-S938B |
| vc161 / hash / foreground | Versión y hash final comprobados; Nexus en primer plano | Versión y hash final comprobados; Nexus en primer plano |
| Arranque frío vc161 | 1340 ms, lectura puntual | 1506 ms, lectura puntual |
| Agente, ArtBox, Lanzadera, Biblioteca | Recorridos reales en los bloques auditados | Cuatro pestañas finales comprobadas mediante preferencia de página, imágenes y foco |
| Principal final | Retrato PASS; foto y controles habituales visibles | Formulario observado de Seedance 2.5: entradas, chips, Avanzado y Generar visibles sin scroll |
| Fuente ampliada final | 1,5× PASS en el escenario revisado | Sin PASS global de 1,5×; prueba anterior interrumpida |
| Ventana 800 × 533 dp + IME | **PASS final:** prompt accesible y foco estable; hoja de Duración sobre IME comprobada | Sin prueba equivalente; no acredita un plegable físico |
| Cambio de modelo con una foto y retorno, final | Kling 4 I2V → MiniMax H3 Ref: prompt e imagen preservados. Retorno por selector a Kling 4: todas las huellas exactas. Snackbar «Volver» expiró, sin PASS físico de esa acción | No se atribuye el recorrido del Pixel |
| Retirada de referencia QA y cierre de hoja | PASS: 0 activos / 0 conservados; textos y parámetros originales exactos. Sin borrar el archivo original | Sin modificación del borrador por esta limpieza del Pixel |
| Drawer / importación, vc159 | Abrir, fijar/desfijar, renombrado con CANCELAR, cierre/Deshacer, búsqueda/limpiar e IME PASS. NexusLibrary: selección, miniatura/preview y retirada PASS | Apertura del drawer comprobada; sin atribuirle todas las acciones del Pixel |
| TalkBack / sensación háptica / S Pen / plegable | Sin comprobación física; S Pen no aplica | Sin comprobación física |

Los defectos históricos de prompt oculto en vc160 y pérdida de foco del checkpoint UI `6adf7b8` se corrigieron y se repitió la prueba con la fuente final `2abd8d7`; no se trasladan resultados intermedios como evidencia del final. El recorrido histórico S25 de prompt de 4950 caracteres y 13 referencias conservó su hash/IDs; el estado actual de 4908 caracteres con 3 activos y 2 conservados es otro checkpoint y se preservó al instalar. La referencia de QA del Pixel ya se retiró del borrador, sin borrar su original.

UIAutomator sigue devolviendo `null root` en S25, también por USB. Se descartaron resultados de un arnés que reutilizaba XML de otra app. La navegación se acredita con imágenes del paquete correcto y foco; el guard detiene acciones si otra app ocupa el foreground. No se usan esos XML como evidencia.

## Conservación hasta vc161

Referencias: **Pixel vc156 / 1.9.120-agent-preview** y **S25 vc155 / 1.9.119-agent-preview**. Son checkpoints de datos distintos de la fuente base.

| Referencia → vc161 | Pixel | S25 |
|---|---|---|
| Trabajos / outputs | 466 → 466 / 505 → 505 | 660 → 663 / 529 → 532 |
| IDs y filas originales de trabajos/outputs | Todos presentes; ninguna fila original modificada | Todos presentes; ninguna fila original modificada |
| Integridad SQLite | `ok` antes y después | `ok` antes y después |
| Archivos del asistente | 126 → 128; 0 originales ausentes, 117 hashes iguales y **9 cambiados** | 143 → 147; 0 originales ausentes, 132 hashes iguales y **11 cambiados** |
| IDs de conversaciones | 73 → 75; todos los originales presentes | 72 → 76; todos los originales presentes |
| Preferencias | 27 → 29 archivos; 0 ausentes, 11 cambiados y 2 nuevos | 37 → 37; 0 ausentes y 12 cambiados |
| Hash de preferencias seguras | Cambió | Cambió |
| Proyectos / recursos, solo conteos | 4 → 5 / 2156 → 2611 | 4 → 5 / 2156 → 2357 |
| Borrador antes/después de instalar vc161 | Todas las huellas iguales: prompt de 72 caracteres, negativo, parámetros, IDs, URI y conteos; 1 activo / 0 conservados | Todas las huellas iguales: prompt de 4908 caracteres, negativo, parámetros, IDs, URI y conteos; 3 activos / 2 conservados |

Los tres trabajos/outputs nuevos del S25 constan completados por Atlas y son externos a esta QA; no se revirtieron ni se atribuyen a ella. La ejecución de pago permanece deshabilitada y el almacenamiento de credenciales accesible en ambos checkpoints. **No se demuestra igualdad de secretos:** los hashes de preferencias seguras cambiaron. Los hashes modificados del asistente no prueban diferencias limitadas a timestamps. No se exportaron IDs de proyectos/recursos, por lo que sus conteos no prueban conservación individual. Las huellas iguales del borrador acreditan concretamente el paso de instalación, no todas las operaciones posteriores de QA.

## Rendimiento, descartes y límites

La muestra caliente válida de vc157 fue de **633 frames, 6 janky (0,95 %), p50 8 ms, p95 10 ms y p99 38 ms**, en 20 navegaciones. Simpleperf identificó el calentamiento de búsquedas (18,7 % inclusivo de CPU), origen de la corrección de caché. Las lecturas de arranque final son puntuales; no certifican 120 Hz, cero jank ni equivalencia con ese perfil caliente.

| Decisión / pendiente | Motivo |
|---|---|
| Últimos logs y recorrido específico | CERRADOS: 0 Fatal/ANR detectados en ambas ventanas; cambio con imagen, retorno exacto por selector y limpieza QA comprobados. Sin otro hallazgo bloqueante en el alcance revisado |
| Snackbar «Volver» del último recorrido | NO COMPROBADO físicamente: expiró antes del toque. No se presenta como equivalente al retorno por selector; Undo sí tiene pruebas automatizadas |
| Pagos, Generar, LLM y reenvío de trabajos históricos | Descartados para esta QA; no necesarios para validar el alcance |
| Borrado de originales/datos, desinstalación y cambio de firma | Descartados; actualización en sitio y acciones reversibles |
| Refactor ajeno, dependencias nuevas, motores/pesos nativos | Descartados por alcance; el motor de capacidades sí fue autorizado |
| TalkBack, sensación háptica, S Pen, plegable, todas las combinaciones claro/oscuro y 120 Hz sostenidos | Pendientes de aceptación física/medición específica; sin sustituirlos por tests |
| Log histórico AlexiaBridge | Descarga inválida no fatal observada; no se regeneró el recurso |

Las revisiones corrigieron gestos, avisos históricos, arranque, IME, caché, preflight, ownership y persistencia de intención al reabrir/editar durante carga. La comprobación final no detectó Fatal/ANR en las ventanas indicadas y no encontró otro bloqueo en el alcance revisado. No hay operaciones irreversibles pendientes de decisión humana; los límites físicos anteriores se mantienen porque requieren observación o medición específica.

Se leyeron [CONTINUAR.md](../CONTINUAR.md) y [MIGRATION-STATUS.json](../MIGRATION-STATUS.json) antes de modificar. Seguimiento: [AUDIT_PLAN.md](AUDIT_PLAN.md) y [rama publicada](https://github.com/egodivergente/nexus/tree/codex/nexus-premium-20261029). Evidencias privadas en `C:/Users/User/Documents/Codex/nexus-premium-20261029`: `release-v161.json`, `installation-v161.json`, `qa-v161-layout.json`, `qa-v161-model-roundtrip.json`, `final-v161-device-checks.json` y `preservation-final-v161.json` con su resumen. No se incorporan datos, capturas ni logs privados al repositorio.
