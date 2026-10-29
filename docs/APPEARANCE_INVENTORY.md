# Inventario de apariencia — 29/10/2026

Rama de este bloque: `codex/nexus-axel-appearance-20261029`. Base de continuidad:
vc169, fuente `712ae1c86def5594ae20aaf5137b934de612ac81`; su QA previa fue parcial.
Leídos AGENTS.md, CONTINUAR.md y MIGRATION-STATUS.json. Este inventario procede de
lectura del NavHost, sus callbacks, ramas de pantalla y composables secundarios.
**Revisión estática no equivale a compilación ni aceptación en dispositivo.**

Las cinco identidades nuevas son Aurora Borealis, Obsidian Liquid, Champagne
Silk, Emerald Atmosphere y Nocturne Nebula. `premiumThemes` las presenta; las
identidades anteriores siguen en el enum por compatibilidad de preferencias.
Los propietarios de tema, selector, ambiente y layout integran un mismo
`NexusThemeSpec`/`NexusVisualTokens`; aquí se registra la superficie que deben cubrir.

Checkpoint de este bloque: **vc171**, fuente `4961c5a7…`, build en AXELPC PASS,
303 pruebas app ejecutadas y 4 de núcleo UP-TO-DATE válidas reutilizadas. Firma,
hash y bibliotecas nativas verificados; APK actualizada en Pixel y S25 con hash
y versión comprobados. Pixel foreground PASS. **S25 sin foreground de Nexus por
Keyguard: instalación verificada no equivale a aceptación visual.** La cobertura
física confirmada y sus límites figuran al final; no se heredan de vc169/vc170.

## Mapa de rutas montadas y entradas

| Entrada real | Pantalla / estados secundarios | Fuentes principales |
|---|---|---|
| `workspace`, página 0; alias `assistant` | Agente: conversación, compositor, adjuntos, razonamiento, propuestas, planes, resultados, estados de espera/error y selección/copiar. | `MainActivity.kt`, `assistant/AssistantScreen.kt`, `AgentComposerControls.kt`, `ChatMarkdownView.kt`, `AssistantMediaInput.kt` |
| Drawer y proyecto del agente | Lista/búsqueda de conversaciones; fijar, renombrar, cerrar/Deshacer; proyectos, memoria, borrador/conflictos y diálogo de salida. | `AssistantScreen.kt`, `ProjectPicker.kt`, `ProjectMemoryConflictReview.kt`, componentes de conversaciones |
| Adjuntos/artefactos del agente | Biblioteca reciente, picker del proyecto, vista de enlaces, artefactos HTML y pizarra; paginado y vista ampliada de medios. | `RecentLibraryPanel.kt`, `ProjectMediaPicker.kt`, `ChatLinkSheet.kt`, `ChatVisualArtifact.kt`, `ChatBoardCard.kt`, `FullscreenDialog.kt` |
| Menú del agente | Encargos, ajustes del asistente, proveedores/modelos de razonamiento, credenciales, editor de cerebro propio, skills y sus confirmaciones. | `AssistantScreen.kt`, `BrainEditor.kt`, `SkillsEditor.kt`, `CustomProvidersSettings.kt` |
| `workspace`, página 1; alias `models` | ArtBox por familia, variante, flujo y proveedor; búsqueda, favoritos, filtros, catálogo vacío/cargando/error, recursos activos/conservados. | `models/CatalogTreeScreen.kt`, `FamilyModelOptionsScreen.kt`, `ModelOptionsScreen.kt`, `CatalogVariantSelector.kt` |
| Menús de ArtBox | Personalizar nombre/icono/tamaño, ordenar/arrastrar, mover sección, quitar/recuperar ocultos, modelos anteriores y selector de columnas. | `CatalogTreeScreen.kt`, `CatalogLabels.kt`, `ArtBoxFamilyCard.kt` |
| Ficha de modelo / datos publicados | Contrato, capacidades, precio, proveedor y diagnóstico de una variante, sin sustituir su identidad. | `ModelOptionsScreen.kt`, `ModelFactSheet.kt`, componentes de evidencia de modelos |
| `workspace`, página 2; alias `create` | Lanzadera: cabecera, familia/variante, prompt, entradas declaradas, parámetros comunes, coste y acción; carga/configuración/retención/error. | `create/CreateScreen.kt`, `FlagshipStudio.kt`, `FixedLauncherWorkspace.kt`, `CapabilityFormRenderer.kt` |
| Hojas de Lanzadera | Selector completo de modelos, Prompt ampliado, Avanzado, gestión/reordenación de recursos y Conservados, parámetros, presets, inspiración, coste e inspector de solicitud. | `CreateScreen.kt`, `ModelPickerSheet.kt`, `CreationPresetsSheet.kt`, `CanvasAttachmentEditor.kt`, `NativeParameterControl.kt` |
| Lotes/comparación y BytePlus | Prompt por lotes, selección comparada, confirmación económica, progreso y resultados comparados; hoja de opciones BytePlus. | `PromptBatchSheet.kt`, `ComparisonScreen.kt`, `BytePlusCanvasSheet.kt`, `CreateScreen.kt` |
| `workspace`, página 3; alias `library` | Biblioteca: proyectos/carpetas, filtros/origen, búsqueda, cuadrícula, selección múltiple, vacíos/carga/error y feedback reversible. | `library/LibraryScreen.kt`, `LibraryProjectFilter.kt`, `LibrarySelectionBar.kt`, `LibraryNoResults.kt` |
| Menús y hojas de Biblioteca | Ficha/acciones de archivo, renombrar, mover/nueva carpeta, eliminación de carpeta, importar Flow, ediciones locales, crop y compartir/exportar. | `LibraryActions.kt`, `FlowImportSheet.kt`, `ImageCropDialog.kt`, `assistant/LocalMediaEditsSheet.kt` |
| Visor de Biblioteca | Imagen/vídeo/audio/documento a pantalla completa; acciones y barra superpuesta al medio. | `library/LibraryViewer.kt`, `LibraryThumbnails.kt` |
| `generations` | Historial con estados, filtros, menú de trabajo, adjuntar resultado al chat, preparar de nuevo, seguimiento, archivo, diagnóstico, eliminación y comparación. | `generations/GenerationsScreen.kt`, `ComparisonScreen.kt` |
| `output/{jobId}?index={index}` | Resultado, paginado, vídeo/audio/imagen/texto, ampliación inmersiva, detalles técnicos/coste, remezcla y reutilización. | `output/OutputViewerScreen.kt` |
| `local-inpaint?uri={uri}` | Editor local: lienzo, máscara, herramientas y confirmación/errores de edición. | `inpaint/LocalInpaintScreen.kt` y sus componentes |
| `settings` | Hub, búsqueda/vacío, páginas internas y diálogos de credenciales. Desglose completo debajo. | `settings/SettingsScreen.kt` |
| `byteplus/{page}` | Entrada directa en la página interna solicitada de Ajustes; vuelve a la pantalla anterior. | `MainActivity.kt`, `SettingsScreen.kt`, `BytePlusConsumptionSettings.kt` |
| Saldo global | Hoja «Saldo y gasto», proveedores, detalles de conversión, libro mayor, filtros y recibo/coste por trabajo. | `components/NexusChrome.kt`, `billing/BillingLedgerScreen.kt` |
| Arranque/compartición externa | Gate de inicio, error/reintento, elegir/crear proyecto, importación en progreso, borrador propuesto por agente y errores de compartir. | `MainActivity.kt`, `components/NexusStartupState.kt`, `assistant/ContinueProjectChooser.kt` |

Los alias de las cuatro pestañas redirigen al pager; no crean otra pantalla con
otro tema. `ModelsScreen.kt` contiene utilidades de pluralización: la pantalla
`ModelsScreen` real está declarada en `CatalogTreeScreen.kt`.

## Todas las ramas de Ajustes

| Rama `SettingsPage` | Contenido/entradas secundarias |
|---|---|
| APPEARANCE | Selector de identidad, tipografía/escala, moneda y acceso a fondo/movimiento/háptica. La hoja global de apariencia debe usar el mismo estado. |
| VISUAL | Fondo propio, intensidad/ambiente, movimiento reducido, háptica y editor avanzado de personalización. |
| PROJECTS | Editor de reglas de proyectos/conversaciones, inicio y destino al compartir. |
| AGENT | Ajustes del asistente, memoria, cerebro/modelos/skills y consumo. |
| PROVIDERS | Estado/credenciales de proveedores de generación; enlaces a BytePlus y generadores propios. |
| BYTEPLUS | Consumo, tokens, facturación/paquetes y estados de consulta. También accesible por `byteplus/{page}`. |
| CUSTOM | Cerebros y generadores propios; editores y pruebas de conexión existentes. |
| AUTOMATION | Editor de reglas de concurrencia y ejecución. |
| SECURITY | Permisos Android y explicación de conservación privada de credenciales. |
| SYSTEM | Versión/build, reglas, catálogo/workflows, edición local, diagnóstico y proveedor simulado según build. |
| RULES | Búsqueda de todas las reglas; ámbito Global/Módulo/Proveedor/Modelo-workflow, edición, importación/exportación y selección de foto. |
| CATALOG | Editor de reglas de densidad, antigüedad y orden de workflows. |
| CONNECTIONS | Rama conservada en `when(page)`; no se encontró una entrada desde el hub actual. No se acredita navegación física a esa rama. |

`RuntimeSettingsEditor` genera sus campos a partir de todas las definiciones de
`agent-core/.../config/RuntimeRules.kt`; sus secciones y ámbitos son dinámicos.
No existe una pantalla distinta por cada regla. Sus campos, selectores, switches,
estados inválidos y diálogos deben heredar Material y los componentes compartidos.

## Rutas latentes y superficies externas

- `Screen.Flow` existe y varios callbacks solicitan `flow`. La rama del pager
  llama `FlowCreateScreen → FlowStudioScreen`, pero `bottomNavItems` contiene las
  cuatro pestañas anteriores y no se encontró un `composable(Screen.Flow.route)`
  independiente. Es un hueco estático de navegación, comunicado a integración;
  este bloque de apariencia no cambia routing ni lo declara probado.
- `FlowStudioScreen` conserva interfaz de crear/editar/descargar, menús, ajustes
  y confirmaciones; se inventaría para su coherencia de apariencia aunque su
  acceso anterior esté pendiente. Los DOM de servicios externos no se repintan
  como si fueran controles nativos de Nexus.
- `LiveVoiceDialog` existe en `assistant/LiveVoice.kt`, sin invocación encontrada
  en el árbol actual. Se revisa su paleta por cobertura de código, sin afirmar
  que es una ruta accesible o probada.
- Selector SAF/Photo Picker, permisos/ajustes de Android, compartir, teclado,
  reproductores del sistema y otras apps son superficies externas. Nexus debe
  aplicar su identidad a la entrada y al retorno, no sustituir la interfaz ajena.

## Colores y componentes determinantes

El barrido incluye `ui`, `assistant`, `inpaint` y `settings`, con literales
`Color(0x...)`, colores nominales y usos de blanco/negro. Un literal no demuestra
por sí solo un defecto: se clasifica por función y por quién dibuja el contenido.
Los puntos siguientes son hallazgos de la lectura inicial; otros propietarios
los están integrando. No se presentan como todos corregidos por este archivo.

| Fuente / caso | Clasificación y tratamiento |
|---|---|
| `theme/NexusThemeSpec.kt`, `Theme.kt`, `Color.kt`, `Type.kt` | Fuente central de paleta/semántica/identidad tipográfica. Los literales aquí son definición de tokens; los alias antiguos deben resolver al tema seleccionado. Propietario: integración de tema. |
| `components/NexusChrome.kt`, `NexusSheet.kt`, barras, menús, campos, chips, avisos y estados vacíos | Cobertura transversal: fondos de hoja/diálogo, separadores, disabled, warning/success/processing, placeholder, foco y superficies deben tener roles comunes. No bastan los colores de la pantalla raíz. |
| `create/StudioCanvasComponents.kt` | Prompt/toolbar migrados a `promptSurface`, `promptBorder`, `chromeSurface`, `placeholder`, `disabledText`; foco por primary. Progreso usa processing/onProcessing y no un brillo blanco fijo. Solo presentación. |
| `settings/SettingsScreen.kt`, enum SettingsPage | Trece tintes de categorías fijos encontrados inicialmente. Selector y categorías deben integrarse con la identidad; propietario: agente de ajustes. |
| `settings/ProviderCard.kt` | Verde fijo para conectado y tint genérico: estado funcional, necesita success/tint semánticos. Propietario: integración. |
| `assistant/ContinueProjectChooser.kt` | Cuatro colores fijos por hash de proyecto: decoración funcional de selección, revisar con la identidad. Propietario: integración. |
| `assistant/LiveVoice.kt` | Overlay casi negro y textos blancos fijos; no es el propio vídeo ni una marca. Integrar con superficie/foreground semánticos si se conserva. Propietario: integración. |
| `ProviderBrandRegistry.kt`, `AlexiaBrandAssets.kt` | Paletas/logos de proveedor: conservar la marca reconocible. Los defaults/fallbacks de texto y fondo también requieren contraste; no atribuir automáticamente toda la tarjeta al branding. |
| `assistant/ChatBoardCard.kt` | Rojo de tinta de anotación: contenido de herramienta, excepción conservada; no debe convertirse en color de error de la app. |
| `LibraryViewer.kt`, `OutputViewerScreen.kt`, miniaturas | Negro/blanco superpuesto al medio puede ser una protección de legibilidad del visor. Separar el lienzo del medio de las hojas, acciones y estado de Nexus. |
| Crop/inpaint, máscara, tablero y composición de bitmaps | Colores de herramienta, selección o píxeles exportados: conservar semántica/archivo. Por ejemplo el blanco al aplanar una imagen en Flow no es el fondo de una pantalla. |
| Markdown/artefactos HTML y medios embebidos | Chrome y texto nativo heredan el tema. Mantener colores del contenido original y revisar CSS/WebView separadamente; un barrido de `Color(...)` no acredita su contraste. |

## Prompt compacto y espacio útil

La petición actual revoca el crecimiento automático grande de vc162–169.
`StudioPromptField` vuelve a 2–3 líneas en la principal; el botón Expandir conserva
la hoja existente y todo el texto. Pegar, borrar/Restaurar, contador, límites,
foco y callbacks siguen siendo los mismos. El editor ampliado conserva su tamaño
propio. El prompt vacío no recibe el sobrante del workspace.

En vc171, el cuerpo funcional usa `FontFamily.SansSerif`, peso Medium 500,
manteniendo la escala de tamaños e interlineados. Se respeta el ajuste de peso
del sistema; el Pixel mantiene `font_weight_adjustment=300`. Manrope ya no se
usa para el texto funcional. El recorte y el borde del prompt comparten
`MaterialTheme.shapes.medium`, incluido el fondo de su toolbar dentro del recorte.
Las hojas toman `treatment.sheet`; no se afirma una sombra variable de hoja:
el `shadowElevation` de tratamiento se usa en las tarjetas compartidas.

`FixedLauncherWorkspace` entrega el sobrante a previews reales hasta su capacidad
medida por ancho/conteo; no estira campos vacíos. En ancho suficiente mantiene dos
columnas. La composición del campo conserva su ancestro al aparecer IME; el scroll
se activa por teclado o desbordamiento medido, también con texto grande. No se
cambian contratos, generación, parámetros, archivos ni asignaciones de entradas.

La aritmética de reparto es local a ese layout; un test que repitiese el mínimo
de dos enteros no probaría las medidas, el foco ni la accesibilidad reales.
La aceptación requiere capturas/medidas del build y QA física de retrato, ancho,
fuente grande, prompt vacío/largo, foto/vídeo y expansión sobre IME.

## Estado de cobertura y verificación

| Nivel | Estado de este inventario |
|---|---|
| Navegación/subpantallas | Inspección estática de las entradas montadas, ramas latentes, menús y hojas enumeradas arriba. |
| Integración de apariencia | Implementada y compilada en vc171: cinco identidades, roles semánticos, ambiente y selector reversible. El inventario estático no certifica cada combinación de ruta/tema. |
| Prompt/layout | Implementado y compilado. vc170 observó en Pixel prompt compacto con medios y fuente predeterminada; es evidencia histórica, no prueba del cambio tipográfico y de formas de vc171. |
| Build/tests | Root verificó vc171 en AXELPC: 303 app ejecutadas PASS y 4 núcleo UP-TO-DATE reutilizadas. Firma/hash/nativas verificados; no son 307 ejecuciones nuevas. Este agente no ejecutó el build. |
| Instalación | Pixel y S25: actualización, versión y hash comprobados. Pixel foreground PASS; S25 Keyguard bloquea foreground y QA visual. |
| Selector Pixel vc171 | Cinco live previews y Cancelar: preferencias Sand originales exactas según XML. Aplicar Champagne / Sutil / 55 % / reducir movimiento false y reabrir: PASS físico. Elección conservadora de integración por afinidad cálida con Sand. |
| Conservación Pixel vc171 | Prompt Seedance de 72 caracteres, dos referencias y parámetros: fingerprints sin cambios durante estas comprobaciones. No se han borrado originales. |
| Fuente grande / IME Pixel vc171 | Fuente 1,5×, abrir/cerrar teclado, foco y retorno a tamaño normal capturados; revisión de imágenes aún pendiente de confirmación. Sin PASS atribuido por la sola captura. |
| Navegación / ancho Pixel vc171 | Cuatro pestañas y paisaje en curso de QA; todavía sin resultado atribuido. |
| TalkBack, háptica física, FPS/120 Hz y batería | No medidos en este bloque. Tests de políticas y reloj decorativo acotado no equivalen a aceptación sensorial o medición de rendimiento/consumo. |
| Pagos/generación/proveedores | Fuera de esta auditoría de apariencia; no se ejecutan llamadas por mostrar una vista previa. |

La entrega no se califica globalmente 10/10 por inventariar rutas o compilar.
La aceptación pendiente de fuente grande/IME, navegación/ancho y S25 debe cerrarse
con evidencia propia del dispositivo o conservar su motivo de pendiente. Los
hallazgos funcionales ajenos se registran sin modificar su comportamiento dentro
del trabajo visual.


## Cierre físico acotado vc171

Pixel: fuente150% eIME revisados sin recortes de impacto, cuatro pestañas y paisaje/retrato comprobados; ajustes temporales restaurados y huella completa del borrador igual. Capturas propias y revisión independiente. Muestra de previewSutil cambia4469píxeles en3s; Estático0, sin atribuirFPS/consumo. S25 permanece bloqueado por Keyguard. Informe definitivo en APPEARANCE_REPORT.md; esa tabla sustituye los estados ENQA anteriores sin convertirlos en cobertura física exhaustiva.

El control final de paisaje alcanzó Avanzado mediante scroll y abrió/cerró su hoja. Retrato y ajustes restaurados; hashes instalados y borradores completos de ambos móviles confirmados de nuevo al cierre. No se hereda este PASS al S25 bloqueado.
