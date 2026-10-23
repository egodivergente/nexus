# Nexus / ArtBox — ejecución del paquete maestro (23-10-2026)

## Punto de partida comprobado

- Rama de trabajo sobre `919f4e8`, que contiene los cambios de Lienzo y Flow; `origin/main` es antecesor y no hay commits remotos pendientes de incorporar.
- Se conserva `gradlew.bat`, modificado antes de este encargo. Sin limpieza ni desinstalación.
- Host AXELPC. El usuario conectó el Pixel 8 Pro; las instalaciones y pruebas se limitan a ese dispositivo. El S25 no se ha utilizado.
- Referencias privadas consultadas fuera del repositorio. Solo los recursos de identidad autorizados para distribución se adaptan a Android.

## Secuencia

1. Registro tipado de preferencias y capacidades por destino; eliminar máximos ficticios de Flow.
2. Shell con pager físico, identidad Nexus y ArtBox, personalización y cinco destinos.
3. Proyectos con conversaciones, borradores y adjuntos compartidos durables.
4. Familias, nombres inequívocos, herramientas y Lanzadera reutilizando contratos reales.
5. Integración Flow/Biblioteca, compilación, pruebas dirigidas y evidencia física autorizada.

Los contratos externos, los ajustes locales y las observaciones sin verificar se mantienen diferenciados. No se gastan créditos para descubrir límites ni se reenvían trabajos inciertos.

## Evidencia

Versión instalada: **1.9.73-agent-preview / vc107** en Pixel 8 Pro. Código Android: `b588f7c43a109a629586dca071c251b7457fcd31`. Estado: preview; la generación nueva de Flow en el teléfono requiere resolver la conexión privada. No equivale a la validación completa del paquete maestro.

APK: `Nexus-1.9.73-ArtBox-preview.apk`, conservada fuera de Git. SHA-256: `ac2a59f31d373cb97aae82aed46c0e73cf90dd8152eedfdf3434ba381723bc0a`. Certificado SHA-256: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. La copia de entrega coincide con el APK compilado; instalación `-r` verificada, sin borrar datos.

### Implementación

- Cinco destinos con `HorizontalPager`: Agente, ArtBox, Lanzadera, Flow y Biblioteca. Cada pestaña conserva su estado.
- Identidad Nexus con el zorro proporcionado; marca ArtBox solo en el catálogo. Fondo azul suministrado y foto propia, intensidad, desenfoque, movimiento y háptica configurables.
- Proyectos con conversaciones y borradores independientes, respaldo de chats anteriores a la migración, selector al arrancar o tras ausencia configurable.
- Chat sin nombre de proyecto ni pestañas visibles de fábrica. El menú del compositor da acceso a conversaciones, proyectos y ajustes. Las pestañas opcionales son compactas y tienen X; cerrarlas conserva conversación, borrador, archivos y trabajos.
- Copia privada y registro atómico de todos los archivos de un `ACTION_SEND_MULTIPLE` antes de elegir destino. La actividad mantiene su identidad al retirar los datos temporales del intent.
- Notas AAC con grabación, escucha y adjunto local; el MIME de la grabación se declara como `audio/mp4`.
- Familias en cuadrícula adaptable, nombres distintivos de versiones y rutas, herramientas por función y orden configurable de workflows. Galería directa y miniaturas inspeccionables por contrato; los modelos de ampliación reciben su archivo de origen.
- Biblioteca con selección simultánea de proyectos, opción «Sin proyecto» y selección persistente. Las asignaciones actuales del archivo prevalecen sobre el proyecto original de generación.
- Lanzadera con cantidades independientes por modelo, grupos y presets; validación por destino y revisión del coste. El prompt original se conserva; la adaptación automática está desactivada.
- Registro tipado de reglas locales con ámbitos, importación/exportación y restauración individual. Los máximos externos de prompt se identifican por proveedor, versión, endpoint, workflow, unidad, fuente y fecha. Un máximo desconocido permite intentar el envío.

### Datos de modelos

`model_endpoint_evidence.json` contiene **702 destinos**: 522 de Atlas y 180 de Crun. Se descargaron **400 contratos oficiales de Atlas**; las otras 122 entradas de Atlas conservan metadatos del catálogo (contexto, salida, rendimiento o funciones cuando están publicados). Crun utiliza su contrato OpenAPI integrado.

Todas las fichas pueden mostrar evaluación técnica 0–10, controles y rangos de la ruta, procedencia y revisión. Las variantes se comparan por diferencias reales de sus contratos. Las notas de investigación distinguen, entre otras, H3/Max/Fast, Hailuo 2.3/Fast, Grok 1.5, FLUX.2 y Seedream Pro/Flash. El índice de controles y las métricas del fabricante están identificados por su naturaleza; las puntuaciones personales de resultados se guardan aparte. No hay una batería propia de generación y calidad visual para todos los modelos.

Recogedor reproducible, solo GET: `tools/catalog/collect_model_evidence.py`. Las respuestas originales quedan en caché privada, fuera del repositorio.

### Comprobaciones

| Área | Estado y alcance |
| --- | --- |
| Compilación | PASS en AXELPC: APK Agent, APK QA y APK de instrumentación |
| Unidad | 646 pruebas de app PASS; 309 de núcleo PASS y 4 omitidas |
| Pixel, galería y contratos | 6 casos PASS: selector directo, referencias inspeccionables y eliminación, ampliación sin prompt/referencias, sustitución, resultado de picker antiguo descartado y cambio de modo Flow |
| Pixel, nueva aceptación | 8 casos PASS: cinco destinos y prompt Flow largo; aislamiento A1/A2/B1; scroll restaurado; pestañas ocultas/X sin pérdida de borrador; selección de dos proyectos en Biblioteca; copia de cinco archivos y preferencias; nota AAC real, escucha y adjunto; intent Android de cinco imágenes, recreación y consumo único del registro |
| Actualización y datos | Actualización vc96 → vc107; 379 IDs de trabajos, 418 resultados, 1.132 archivos y 4 proyectos anteriores conservados; 50 mensajes anteriores conservados |
| Credenciales | Mismo certificado Android; almacén cifrado disponible y vínculo Flow presente después de actualizar. Los valores secretos no se exportan al informe |
| Flow, pruebas | Suite de 92 casos PASS; 4 casos añadidos para borradores, incluidos Unicode, restauración y conservación de una edición manual posterior, PASS en ejecución dirigida |
| Flow, AXELPC real | `connected=true`, editor listo, máximos desconocidos expresados como null; siete registros originales conservados. Descarga HTTP de un JPEG existente de 1.005.076 bytes y SHA-256 `9fa08c4ad3d34f1c796a9ddaf6393bbec899bc316ea2bd60bba035ca7d331b89` |
| Flow, generación nueva en Pixel | PENDIENTE: la VPN activa de Google impide alcanzar la red privada. Se ha solicitado autorización para sustituirla temporalmente por Tailscale y restaurarla después |

Las evidencias originales, copias previas, registros y capturas permanecen en el almacén privado del encargo. La descarga de una imagen anterior no acredita una generación nueva.

### Flow y recuperación

- Se retiraron el máximo ficticio de 4.000 caracteres y los topes ficticios del cuerpo/referencias del puente autenticado. La interfaz mantiene sus preferencias locales separadas de las restricciones reales de Google.
- Un borrador manual de texto se guarda en `flow-drafts` privado antes de utilizar el compositor. Al terminar se restaura si el usuario no lo ha cambiado. Ante referencias manuales ambiguas se conserva el bloqueo para evitar adjuntar material distinto.
- Se conservaron los siete registros anteriores, incluido el resultado incierto. No se reenvió ningún encargo anterior.
- El instalador de Windows incluye el nuevo módulo de borradores; no se ha utilizado ASUS para compilar.

### Límites de aceptación pendientes

- Generación nueva de Flow desde el Pixel, con su descarga y apertura en Nexus.
- Recorrido específico desde Google Fotos y un gestor de archivos, además de los intents Android y el selector ya comprobados.
- Evaluación visual comparada propia por modelo y validación estética final del usuario. Las especificaciones y las notas editoriales actuales no sustituyen esa prueba.
- Las integraciones utilizan las APIs y puentes existentes; no se ha sustituido un proveedor por una CLI ni inventado operaciones ausentes.

## Fuentes consultadas

- [Pager nativo Compose](https://developer.android.com/develop/ui/compose/layouts/pager): seguimiento del gesto y estado.
- [Referencias de Runway](https://help.runwayml.com/hc/en-us/articles/52963720640275-Using-reference-media-to-guide-your-generations): medios visibles y capacidades contextuales.
- [Contrato oficial Runway](https://github.com/runwayml/sdk-python/blob/main/src/runwayml/resources/image_to_video.py): unidades UTF-16 y límites específicos por modelo/operación.
- [ActivityScenario oficial](https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java): seguimiento por identidad del intent durante la recreación.
- [Modelos de vídeo de xAI](https://docs.x.ai/developers/model-capabilities/video/overview), [MiniMax H3](https://www.minimax.io/news/minimax-h3-open-source), [H3 Max](https://design.minimax.io/tools/minimax-h3-max) y [Hailuo 2.3](https://www.minimax.io/news/minimax-hailuo-23).
- [Seedance 2.5](https://seed.bytedance.com/en/blog/one-take-creation-flexible-referencing-introducing-seedance-2-5), [Seedream 5.0 Pro](https://docs.byteplus.com/en/docs/ModelArk/seedream-5-0-pro) y [variantes FLUX.2](https://help.bfl.ai/articles/6122710168-which-flux-2-model-should-i-choose).
- [Veo](https://ai.google.dev/gemini-api/docs/veo), [PixVerse V6](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0), [Ideogram 4](https://ideogram.ai/blog/ideogram-4.0/) y [Qwen Image 3.0](https://qwen.ai/blog?id=qwen-image-3.0).
