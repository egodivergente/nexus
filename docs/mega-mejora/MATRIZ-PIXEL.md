# Matriz de pruebas físicas en Pixel · tanda mega-mejora 20-10-2026

Documento de planificación. **Nada de lo que hay aquí está ejecutado.** Todas las filas están en estado `Pendiente`. Esta matriz no se ha ejecutado en ningún dispositivo, no se ha usado adb, no se ha instalado nada y no se ha generado ningún contenido para redactarla. Solo un tester con evidencia (captura, vídeo o registro) puede cambiar el estado de una fila. El código de los tests unitarios e instrumentados no sustituye a estas comprobaciones.

Base: rama `aki/mega-mejora-20261020`, checkpoint `f8a5a6f` + cambios preparados (staged) del 20-10 sin commit. IDs según `estado.json` (V, U, D, W).

## 1. Permisos y precondiciones (bloqueantes)

| # | Precondición | Estado |
|---|---|---|
| P1 | **Permiso explícito de Vidal para instalar el paquete de QA en el Pixel 8 Pro.** Sin ese permiso escrito no se instala nada y la matriz no empieza. El permiso cubre solo el paquete `com.vidadomu.nexus.megaqa` (build `offlineQa` con `-PnexusMegaQa`) y no autoriza a sustituir, desinstalar ni tocar la Nexus de producción ni otras apps QA (`.qa`, `.uiqa`, `.mediaqa`, `.backgroundqa`, `.agent`). | Pendiente |
| P2 | **El S25 Ultra queda fuera.** No se instala, no se conecta y no se prueba en él. Todas las filas son para el Pixel 8 Pro. | Pendiente |
| P3 | Solo se usan fixtures y datos locales del paquete `.megaqa`. No se usan datos de producción, cuentas reales de Drive, proyectos reales ni historial real. | Pendiente |
| P4 | Ninguna generación de pago. En `OFFLINE_QA`, `GenerationDispatcher` bloquea el envío («NEXUS Lab: envío bloqueado. No se ha consumido saldo.»), `LocalQaInterceptor` corta la red y `NexusDriveSync` no sincroniza. Si alguna pantalla mostrara un saldo real o consiguiera enviar algo, se detiene la prueba y se informa. | Pendiente |
| P5 | Antes de instalar, el APK `.megaqa` tiene que estar compilado y sus tests registrados (SHA y origen). A 20-10 el README no declara ninguna compilación de esta tanda. | Pendiente |
| P6 | Abrir los fixtures de lanzamiento (`qa_fixture`, `qa_motion`, `qa_export`) requiere extras de intent. Solo los lanza el operador autorizado en P1. Este documento no ejecuta adb. | Pendiente |
| P7 | Los ajustes del Pixel que cambien (tamaño de visualización, tamaño de fuente, tema, escala de grises, TalkBack) se anotan y se devuelven a su valor original al terminar. Para activar las opciones de desarrollador o instalar Accessibility Scanner hace falta un permiso aparte, porque P1 no lo incluye. | Pendiente |

### Fixtures que faltan (localizados en el código el 20-10)

En `app/src/offlineQa/assets` solo hay `qa-image.json` y `qa-motion.json`. Ninguno tiene `seed` ni tarifa. Los datos de catálogo (`atlas-catalog-2026-09-22.json`, `native/*.json`) y `DriveConflictFixtures.kt` están en `src/test` y no van dentro del APK. Las filas que dependen de ellos siguen en `Pendiente` con la nota «Requiere fixture». Si falta el fixture, **no se sustituye** por Drive real, por catálogo descargado ni por proyectos reales: la fila queda bloqueada hasta que exista un fixture `.megaqa`.

| Fixture necesario | Filas afectadas |
|---|---|
| Catálogo offline con `deepseek-ai/deepseek-v4.1-flash`, familias y variantes | MP-10 a MP-17 |
| Esquema con `seed` y `maximum = 2147483647`, y con `prompt_extend` | MP-25, MP-26 |
| Conflictos de Drive inyectados en local, sin cuenta | MP-50 a MP-54 |

## 2. Ejes de configuración

Dispositivo: **Pixel 8 Pro** (único). Ancho lógico 411 dp y 448 dp, ajustado con Ajustes › Pantalla › Tamaño de visualización. Se anota el ancho real obtenido. Si no coincide exactamente, se registra el más cercano y no se da por equivalente.

| Código | Ancho | Fuente | Tema |
|---|---|---|---|
| C01 | 411 dp | 100 % | Claro |
| C02 | 411 dp | 100 % | Oscuro |
| C03 | 411 dp | 130 % | Claro |
| C04 | 411 dp | 130 % | Oscuro |
| C05 | 411 dp | 200 % | Claro |
| C06 | 411 dp | 200 % | Oscuro |
| C07 | 448 dp | 100 % | Claro |
| C08 | 448 dp | 100 % | Oscuro |
| C09 | 448 dp | 130 % | Claro |
| C10 | 448 dp | 130 % | Oscuro |
| C11 | 448 dp | 200 % | Claro |
| C12 | 448 dp | 200 % | Oscuro |

Juego mínimo por fila (salvo que la fila diga otra cosa): **C01, C06, C07, C12**. Es decir, los dos anchos, los extremos de fuente y los dos temas. «Todas» significa C01–C12.

## 3. Matriz

### 3.0 Paquete aislado

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-01 | Salvaguarda | `build.gradle.kts` → sufijo `.megaqa` | C01 | 1. Con P1 concedido, instalar solo `.megaqa`. 2. Ajustes › Apps: comprobar los paquetes. 3. Abrir la Nexus de producción sin tocar nada. | `com.vidadomu.nexus.megaqa` aparece aparte. La Nexus de producción conserva versión, datos e historial. No se ha pedido sustituir la firma. | Pendiente |
| MP-02 | Salvaguarda | `GenerationDispatcher`, `LocalQaInterceptor` | C01 | 1. Abrir el fixture `qa_fixture=motion`. 2. Escribir un prompt. 3. Pulsar Generar. | Mensaje «NEXUS Lab: envío bloqueado. No se ha consumido saldo.» No sale nada a la red ni se cobra nada. | Pendiente |

### 3.1 Catálogo y marcas (V12, V14, V15, V16, D04): requieren fixture de catálogo

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-10 | V12 | `ProviderBrandRegistry.brandIdentityCandidates` (segmentos del id → familia → organización) | C01, C02 | 1. Catálogo › buscar «deepseek-v4.1-flash». 2. Mirar el logo de la tarjeta y de la ficha. | Logo de DeepSeek, no el de OpenAI, aunque la familia se llame «…OpenAI-Compatible Endpoint». Se conservan los logos existentes de las demás marcas. | Pendiente |
| MP-11 | V14 | `CatalogGuide.catalogCardSummary` (máx. 60 car.) | Todas | 1. Catálogo en vista árbol. 2. Recorrer las familias. | A 411 dp cada familia muestra una función completa, sin cortar a media palabra. Con fuente ≥130 % (`largeFont`) el resumen pasa de línea en vez de truncarse. | Pendiente |
| MP-12 | V14 | `CatalogFilterHint` (`filterButtonLabel`, `activeCatalogFilterLabels`) | C01, C05, C12 | 1. En Vídeo, activar dos filtros. 2. Mirar el chip, el icono de filtros y la línea de filtros. 3. Escribir en la búsqueda. 4. En Imagen, con «Con referencias» activo (estado inicial), mirar el contador y pulsar «Quitar filtros activos». | El chip dice «Filtros (2)» y la línea «Filtros activos: …» los enumera. El icono se resalta si hay algún filtro activo, incluido «Solo favoritos». En Imagen, «Con referencias» cuenta y aparece en la línea; en Vídeo no cuenta. «Quitar filtros activos» también lo desactiva. El campo de búsqueda conserva su espacio y no queda tapado. | Pendiente |
| MP-13 | D04 | `CatalogTreeScreen` (CustomAccessibilityAction Subir/Bajar, `moveCatalogItemBy`) | C04 + TalkBack | 1. TalkBack activo. 2. Foco en un elemento ordenable. 3. Menú de acciones › Subir / Bajar. 4. Pulsación larga › diálogo contextual. 5. Un toque en el asa de arrastre. | Las acciones «Subir» y «Bajar» mueven un puesto y se anuncia el cambio. El diálogo ofrece «Subir un puesto» y «Bajar un puesto». El asa abre el mismo menú. El primero no ofrece Subir y el último no ofrece Bajar, o, si los ofrece, no hacen nada. | Pendiente |
| MP-14 | D04 | Semántica de encabezado en catálogo y ficha | C02 + TalkBack | Navegar por encabezados con TalkBack. | Las secciones se anuncian como encabezados. Ningún texto de contenido se anuncia como encabezado. | Pendiente |
| MP-15 | V15 | `ModelFactSheet` y bloque «Qué admite y cómo se cobra» en `ModelOptionsScreen` | Todas | 1. Abrir la ficha de un modelo con todos los datos. 2. Abrir otra sin tarifa ni algún dato. | En la primera pantalla aparecen la entrada admitida, la salida y la unidad de coste. Los datos desconocidos dicen «Sin dato» y nunca 0 ni «gratis». A 200 % no se corta ningún texto. | Pendiente |
| MP-16 | V16 | Hoja de variantes en `ModelOptionsScreen` | C01, C06, C11 | 1. Ficha › Variantes. 2. Revisar cada variante. | Cada variante muestra su diferencia o «Diferencia no documentada». El ejemplo de 5 s lleva unidad y dice «Ejemplo aritmético… No es una cotización». | Pendiente |
| MP-17 | D04 | Altura mínima táctil (`NexusTokens.minimumTouch`) | C01, C05 | Medir chips, asas y botones del catálogo y la ficha (con Accessibility Scanner o límites de diseño solo si P7 se concede). | Todos los objetivos táctiles miden ≥48 dp. Se registra cómo se midió. | Pendiente |

### 3.2 Crear, selector y parámetros (U01, V05–V11, V13)

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-20 | U01, V13 | `ModelPickerSheet` (`NexusSheetSystemBars`, chip «Admite mis N referencias», sin insignia HOT) | Todas | 1. Abrir el fixture `qa_fixture=references` (3 medios). 2. Abrir el selector de modelo. 3. Desplazarse hasta la **última fila**. 4. Repetir con el teclado abierto en la búsqueda. | El chip dice «Admite mis 3 referencias» y debajo aparece «Si falta un modelo, prueba Todo el catálogo…». No aparece la insignia «HOT». La última fila se ve entera por encima de la barra gestual, con el teclado abierto y cerrado, sin padding doble ni hueco vacío. | Pendiente |
| MP-21 | V13 | `CreateScreen` (`contentWindowInsets`, `compactTyping`) | C03, C06, C12 | 1. Lienzo › tocar el prompt. 2. Escribir 10 líneas con el teclado abierto. 3. Girar a horizontal (alto < 500 dp). | **Inset del teclado:** el campo y el motivo de bloqueo quedan visibles por encima del teclado. Generar sigue siendo accesible. No queda contenido detrás del teclado ni de la barra de navegación. | Pendiente |
| MP-22 | V06 | `CreationReadiness` y `CreationFixTarget` | C01, C02, C06 | 1. Fixture `motion` con el prompt vacío. 2. Leer el motivo junto a Generar. 3. Tocar el motivo o su acción. | Aparece «Escribe un prompt.» junto a Generar y lleva al campo del prompt. El texto del motivo tiene un contraste ≥4,5:1 en los dos temas. No se exige ese umbral al botón deshabilitado. | Pendiente |
| MP-23 | V05 | Cabecera de parámetros | C04 + TalkBack | Tocar la cabecera en el título, en el icono y en el espacio vacío. | Cualquier punto abre y cierra. TalkBack anuncia «expandido» o «contraído». | Pendiente |
| MP-24 | V07 | Barra de herramientas del lienzo | C01, C03, C07, C09 | Revisar la barra a 411 y 448 dp. | Ninguna palabra queda partida. Todas las acciones están a ≤2 toques. | Pendiente |
| MP-25 | V08 | `ParameterBounds` y `NativeParameterControl`. **Requiere fixture con seed.** | C01, C05 | 1. Abrir un modelo con `seed` (máx. 2147483647). 2. Leer el rango. 3. Escribir 2147483647. 4. Escribir 2147483648. | **Semilla:** el rango se lee «… – 2.147.483.647», sin «E9» ni 2.147.483.648. El máximo se conserva tal cual. Un valor fuera de rango se rechaza con un mensaje y no se redondea. | Pendiente |
| MP-26 | V09 | `ParameterLabels`. **Requiere fixture con prompt_extend.** | C03 | Revisar las etiquetas de los parámetros. | `prompt_extend` aparece como «Ampliar instrucciones». Las claves desconocidas muestran una ayuda de no disponibilidad, no una traducción inventada. | Pendiente |
| MP-27 | V10 | Selección de opciones de 3 estados | C01 + escala de grises (P7) | 1. Ajustes › Accesibilidad › Corrección de color › Escala de grises. 2. Revisar las opciones Automático, Sí y No. 3. Repetir con TalkBack. | La selección se distingue sin color. TalkBack distingue las tres opciones. Automático no se convierte en No. | Pendiente |
| MP-28 | V11 | `PriceAvailability` y `PriceEstimate` | C01, C02, C06 | 1. Con la red cortada por `OFFLINE_QA`, abrir el lienzo y el selector. 2. Revisar el precio de los modelos sin tarifa. | **Precio desconocido:** aparece «Precio por confirmar» o «Consulta no disponible», con «No equivale a coste cero.» donde corresponda. Nunca aparece «gratis», 0 ni un importe inventado. No hay un muro de avisos repetidos por modelo. | Pendiente |

### 3.3 Agente y proyectos (V01–V04, U19)

Los proyectos se crean a mano dentro de `.megaqa`, con nombres que empiezan por «QA-», y nunca se importan de producción.

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-30 | U19 | `ProjectPicker` y `ProjectMemoryDraft` (**proyecto en borrador**) | C01, C06, C12 | 1. Crear los proyectos QA-A y QA-B. 2. En QA-A, editar la Sinopsis sin guardar. 3. Cambiar a QA-B. 4. Probar «Conservar borrador». 5. Repetir con «Descartar cambios». | Aparece el diálogo «Cambios sin guardar» con «Descartar cambios» y «Conservar borrador» (≥48 dp). Si se conserva, se ve la insignia «Borrador de memoria sin guardar». QA-B no se sobrescribe. Sin ediciones, cambiar de proyecto cuesta ≤2 toques. | Pendiente |
| MP-31 | U19, V13 | Campos de memoria con el teclado abierto | C05, C12 | 1. Abrir la memoria de QA-A. 2. Ir al último campo, «Modelos preferidos». 3. Escribir con el teclado abierto. | **Inset del teclado y última fila:** el último campo y los botones quedan por encima del teclado y la barra gestual. No hace falta cerrar el teclado para verlos. | Pendiente |
| MP-32 | V03 | `ProjectPicker` | C03, C11 | 1. Crear 6 proyectos con nombres de 40 caracteres. 2. Abrir el selector. 3. Elegir el último. | Se llega a los 6. Al elegir uno se ve su nombre completo, sin recorte opaco. | Pendiente |
| MP-33 | V02 | `AgentComposerControls` | C03, C04 | Localizar los dos selectores del agente. | Ningún texto aparece cortado a 130 %. Los dos selectores son reconocibles (la prueba con 5 usuarios queda fuera de esta matriz). | Pendiente |
| MP-34 | V01 | `AgentErrorNotice` | C01, C06 | Enviar un mensaje al agente con la red cortada por `OFFLINE_QA`. | El aviso muestra la causa y la acción sin abrir el detalle. Los botones miden ≥48 dp. No se reintenta solo. | Pendiente |
| MP-35 | V04 | Memoria del proyecto | C02 | Inspeccionar los marcos de los campos. | Ningún campo queda dentro de tres superficies dibujadas. TalkBack lee los mismos campos y textos que antes. | Pendiente |

### 3.4 Biblioteca, historial y visor (V17, V18, U10, U20)

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-40 | V17 | `GenerationRowLabels` y `GenerationSummaryRow` | C01, C05 | 1. Crear 10 trabajos locales con el mismo prompt, abriendo `qa_export` 10 veces (operador P6). 2. Abrir Generaciones. | Las filas se distinguen sin abrirlas («Intento n de N», fecha o variante). Ninguna fila se titula solo con puntos suspensivos. | Pendiente |
| MP-41 | U10 | `LibraryNoResults` y `LibraryScreen` | C01, C06, C11 | 1. Biblioteca › buscar «zzzqa». 2. Con un filtro aplicado, repetir. 3. Pulsar «Ver toda la biblioteca». 4. Pulsar «Quitar filtros y mantener la búsqueda». | Aparece «No hay resultados para «zzzqa».» (o la variante con filtros). Un toque recupera la biblioteca. No se borra ningún archivo y la densidad y demás preferencias permanentes siguen igual. | Pendiente |
| MP-42 | U10 | `GenerationsScreen` (estado vacío con filtro) | C03 | Generaciones › aplicar un filtro sin resultados. | Aparece «No hay trabajos con este filtro.» con una salida visible. | Pendiente |
| MP-43 | V18 | Cabecera de la biblioteca | C01, C03 | Revisar la cabecera a 411 dp. | El título se ve completo y buscar e importar están a un toque. Se mantiene la densidad elegida. | Pendiente |
| MP-44 | U20 | `ExternalAppFallback` y `OutputViewerScreen` | C01, C06 | 1. Comprobar sin desinstalar nada si Alexia (`com.axel.library`) está en el Pixel. Si está, la fila se salta y no se desinstala. 2. Abrir el visor (`qa_export`) › abrir con Alexia. | Aparece un aviso con la acción «Abrir con otra app». No se descarga, envía ni borra nada automáticamente. | Pendiente |
| MP-45 | V13 | **Última fila** de la biblioteca y del historial | C05, C12 | Desplazarse al final de la Biblioteca y de Generaciones. | El último elemento se ve entero por encima de la barra gestual y de la navegación inferior. | Pendiente |

### 3.5 Conflictos de Drive (W01, W02): requieren fixture de conflictos

En `OFFLINE_QA`, `NexusDriveSync` no sincroniza. **Nunca se conecta una cuenta real de Drive para provocar conflictos.** Los conflictos tienen que quedar **sin resolver hasta que se confirme expresamente**.

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-50 | W01 | `DriveConflictReview` y `DriveConflictPresentation` | C01, C06 | 1. Con N conflictos de fixture, abrir el aviso. 2. Tocar el nombre de una versión. | Primero se ve un resumen que pagina o agrupa. Tocar el nombre solo abre «Ver y comparar versiones» y el conflicto sigue sin resolver. | Pendiente |
| MP-51 | W01 | Confirmación de la resolución | C01, C12 | 1. «Conservar esta versión» › «Cancelar». 2. Cerrar la app y volver a abrirla. 3. Repetir y confirmar con «Sí, conservar esta». | Al cancelar y después de reiniciar, el conflicto **sigue pendiente**. Solo al confirmar se resuelve, una vez. | Pendiente |
| MP-52 | W01 | Retirada con confirmación | C02 | Elegir la versión «Esta versión retira el elemento» › «¿Retirar este elemento?» › primero «Cancelar» y después «Sí, retirar». | Al cancelar no se retira nada. Solo se retira con «Sí, retirar». No se borra ningún original sin esa confirmación. | Pendiente |
| MP-53 | W02 | Comparación | C05, C11 | Abrir el detalle de un conflicto. | Se ve el nombre legible (no el ID) y al menos 3 campos comparados. Los identificadores quedan ocultos detrás de «Mostrar identificadores técnicos». A 200 % no se corta nada. | Pendiente |
| MP-54 | W02 | «Conservar ambas» | C01 | Buscar la opción de conservar las dos versiones. | Hoy el código muestra «Conservar ambas como elementos separados aún no está disponible.» El criterio de W02 sobre conservar ambas **no puede darse por cumplido**: se anota como limitación, no como fallo de la prueba. | Pendiente |

### 3.6 Transversales (D04)

| ID | Mejora | Cambio real | Configs | Pasos | Esperado | Estado |
|---|---|---|---|---|---|---|
| MP-60 | D04 | Toda la tanda | C05, C06, C11, C12 | Recorrer catálogo, ficha, lienzo, selector, agente, proyectos, biblioteca, visor y conflictos. | A 200 % no se corta ningún texto esencial. Se anota cada recorte con captura y pantalla. | Pendiente |
| MP-61 | D04 | Temas | C01/C02, C07/C08 | Repetir MP-11, MP-15, MP-22, MP-28 y MP-41 en claro y en oscuro. | Ningún texto ni icono pierde contraste. Los logos se ven en los dos temas. | Pendiente |
| MP-62 | D04 | TalkBack | C04 | Hacer sin mirar la pantalla: elegir modelo, ordenar el catálogo, abrir la ficha, ver el motivo de bloqueo, cambiar de proyecto con borrador, recuperar una búsqueda vacía y abrir un conflicto. | Todas se completan. Se anota cada tarea que falle (el criterio completo de D04 son 20 tareas). | Pendiente |

## 4. Registro de evidencia

- Captura o vídeo por fila y configuración: `MP-xx_C0y_<descripción>.png`. Se guardan en `docs/mega-mejora/evidencia-pixel/` (la carpeta aún no existe) y sin datos personales.
- Estados que solo puede asignar el tester con evidencia: `Superado`, `Fallido` (con captura y pasos), `Bloqueado` (precondición o fixture ausente) y `No aplica` (con motivo). Una fila sin evidencia sigue en `Pendiente`.
- Al acabar, se trasladan a `estado.json` solo los resultados con evidencia (`device_verified` solo es `true` con evidencia física en el Pixel) y se devuelven los ajustes del dispositivo a su valor original (P7).
