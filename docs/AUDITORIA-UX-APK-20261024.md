# Auditoría estricta de UX de Nexus Android — 24 de octubre de 2026

## Dictamen y versión auditada

**No aprobaría esta compilación como una experiencia de uso terminada y coherente.** Hay mejoras reales en importación, miniaturas y organización, pero la navegación se descubre por ensayo, los controles flotantes interfieren con el contenido y varias pantallas presentan el mismo contexto de maneras incompatibles.

La auditoría corresponde al código `237169c4914f4b866e7d1be3eb4bf09b09011bf1`, rama `ux/correcciones-capturas-20261024`, instalado y examinado en un **Pixel 8 Pro físico**, 1344 × 2992, densidad 480 dpi, tema Claro y letra al 100 %. APK: `1.9.74-agent-preview`, versionCode 108.

El artefacto procede del [run de GitHub 40069674534](https://github.com/egodivergente/nexus/actions/runs/40069674534), `NEXUS-agent-preview-apk`. Se aplicó la identidad de firma existente al APK unsigned, comprobando que el contenido de sus entradas ZIP coincidía con el artefacto descargado. SHA-256 instalado: `d35db13fbd0a40411fe172625a684396fa256ed5edad796b2179c858bd142825`.

La primera inspección detectó una APK anterior con **el mismo nombre y número de versión**. Se actualizó en sitio antes de obtener la evidencia de este informe. Se excluyen los hallazgos que solo aparecían en la APK anterior. El run de GitHub no terminó correctamente: falló `:app:compileOfflineQaAndroidTestKotlin`; disponer de APK no demuestra que toda la validación de CI pase.

## Alcance y criterio

Inspección visual, interacción mediante ADB y árboles de accesibilidad, más lectura dirigida del código del mismo commit para contrastar navegación, filtros y contexto del visor. Recorridos de Agente, ArtBox, Lanzadera, Flow, Biblioteca y Ajustes. Seguridad y facturación quedan fuera del análisis.

- **P1:** impide orientarse, comprobar lo preparado o conservar el contexto de una operación habitual.
- **P2:** fricción repetida o incoherencia visual/semántica relevante.
- **P3:** acabado menor.

Las capturas y XML son privados; los identificadores E-* siguientes remiten al inventario conservado con la entrega. No se publican fotografías, conversaciones ni registros privados en este repositorio.

## Hallazgos confirmados

### U01 · P1 · Los controles flotantes invaden el contenido

**Evidencia:** E-06, E-11, E-13 y E-21. Al escribir en Lanzadera con dos referencias, la fila de resultados y el botón principal se superponen a las miniaturas y sus rótulos. En la ficha de modelo y en Flow se distingue texto por debajo del degradado del pie. En Varios modelos, el subtítulo de la cabecera también queda cortado.

**Efecto:** la app permite preparar una entrada que resulta difícil revisar precisamente al editarla. La transparencia produce una apariencia de contenido deshabilitado o mezclado.

**Mejora:** reservar espacio real para el pie y el teclado en la zona desplazable; reducir el pie mientras se escribe; plegar temporalmente las opciones de lote. Mantener una superficie legible detrás de cada control.

**Aceptación:** con teclado abierto y cerrado, referencias y último campo desplazable se pueden ver completos; ningún texto coincide con el pie ni con la cabecera. Repetir con letra grande.

### U02 · P1 · La navegación principal es demasiado difícil de descubrir

**Evidencia:** E-01, E-02, E-20, E-37 y E-38. Las cinco secciones desaparecen por defecto; hay que conocer un gesto inferior para mostrarlas. La barra vuelve a ocultarse a los cinco segundos. El panel de conversaciones también depende de arrastrar hacia la derecha y carece de un acceso visible en el chat.

La regla aparece en `NexusBottomBar.kt:40`, `NexusBottomBar.kt:101` y `MainActivity.kt:96`. Personalización sigue describiendo otra conducta: «Ocultar la barra al bajar por el chat» y «Vuelve en cuanto subes un poco» (`SettingsScreen.kt:500`).

**Mejora:** dejar un acceso mínimo y persistente a navegación —por ejemplo, un pequeño control con el destino actual— y ofrecer los modos Visible, Automática y Gestos en Personalización. Mantener los gestos como atajo. Añadir un acceso discreto al panel lateral del chat.

**Aceptación:** desde cualquier sección se llega a Flow o Biblioteca con una acción visible; el ajuste explica y controla lo que realmente hace la barra.

### U03 · P1 · Flow sigue siendo un formulario remoto con contexto insuficiente

**Evidencia:** E-21, E-22 y E-23. La pantalla llega a «Flow listo», pero no muestra el nombre del proyecto de Google, el modelo/variante elegidos ni el formato efectivo. «Ajustes y coste · Detalles» presenta una explicación de que Flow usará los ajustes de su proyecto; la hoja de mantenimiento tampoco identifica ese proyecto.

**Efecto:** se puede escribir sin poder comprobar en Nexus la configuración que se aplicará. Esto limita la sensación de trabajar dentro de Flow que pidió el usuario.

**Mejora:** una banda compacta con proyecto, modelo y formato reales, con edición contextual; galería del proyecto como zona central; compositor flotante coherente con el resto de Nexus. Cuando el puente no pueda leer algún dato, mostrar ese campo como pendiente de comprobar y ofrecer abrir el contexto exacto.

**Aceptación:** antes de enviar se pueden comprobar proyecto, modelo/variante, proporción y cantidad efectivos. Las etiquetas provienen del estado del puente, no de valores supuestos.

### U04 · P1 · El visor pierde el contexto desde el que se abre

**Evidencia:** E-24 y E-25. Abrir el único resultado mostrado en el filtro Listos de Flow lleva a un visor que indica **17 / 389**. El visor construye su galería con todos los trabajos completados (`OutputViewerScreen.kt:63–79`).

**Mejora:** pasar al visor la colección y los filtros del origen. El cambio a toda la Biblioteca debe ser una opción explícita. Compartir el mismo visor visual entre archivos importados y resultados.

**Aceptación:** abrir un conjunto de un resultado muestra 1/1; anterior/siguiente conserva proyecto, filtro y colección hasta que el usuario cambie el ámbito.

### U05 · P1 · Se perdió la selección combinada de proyectos en Biblioteca

**Evidencia:** E-26, E-27, E-33 y E-34. Las carpetas individuales funcionan. No aparece el filtro para ver conjuntamente varios proyectos. La implementación actual usa un único `openFolder` y crea `LibraryViewFilters` con un conjunto de proyectos vacío (`LibraryScreen.kt:85, 127–138`).

**Mejora:** conservar carpetas para entrar en un proyecto y recuperar «Proyectos» como filtro de selección múltiple, incluyendo Todos y Sin proyecto. Mostrar un resumen compacto y una forma clara de quitarlo.

**Aceptación:** seleccionar A+B muestra la unión de sus archivos, sin duplicados; quitar B conserva A; volver del visor mantiene el filtro.

### U06 · P1 · La identidad del modelo cambia de presentación durante el recorrido

**Evidencia:** E-05, E-06, E-07 y E-18. La tarjeta genérica GPT Image 2.5 lleva el identificador Sunburst en la UI, la ficha resuelve Flare y Lanzadera muestra «GPT Image 2.5 Flare Developer Edit». En MiniMax, una tarjeta representada por `h3-max-turbo` se titula «MiniMax H3 Max». Hay mezcla de nombres de familia, variante, proveedor y función.

**Límite de la comprobación:** confirmado en metadatos de UI y transición entre pantallas; envío al proveedor sin probar.

**Mejora:** una identidad visual estable: familia + versión/variante, función en una línea secundaria y proveedor como distintivo independiente. Si se agrupan endpoints, hacer explícita la variante elegida antes de configurar. Conservar términos distintivos como Mini, Turbo, Max o Developer.

**Aceptación:** la variante reconocible es la misma en tarjeta, ficha, compositor, lote y resultado; el identificador completo queda disponible bajo información.

### U07 · P2 · Las fichas priorizan la plantilla técnica sobre la decisión de uso

**Evidencia:** E-06. La ficha muestra un identificador largo y una lista extensa de resoluciones en una tabla estrecha; vuelve a presentar parte de los mismos datos bajo «En esta variante». Ese bloque desplaza el contenido útil y hace pesada una consulta rápida.

La evaluación técnica aclara correctamente que mide controles publicados. Para decidir calidad visual y adecuación falta evidencia de resultados, fortalezas y fallos propios de cada variante.

**Mejora:** resumen inicial con tarea idónea, diferencia concreta, entradas necesarias y dos o tres límites importantes. Resoluciones completas, contrato y parámetros bajo detalle. Mantener la evaluación técnica y añadir evaluación de resultados con ejemplos, fecha, método y fuente cuando exista evidencia.

**Aceptación:** se distingue la variante y se decide si sirve para la tarea sin recorrer una tabla técnica; toda la información detallada sigue disponible.

### U08 · P2 · Hay incoherencias de contraste y jerarquía tipográfica

**Evidencia:** E-02, E-06, E-07 y E-13. El mismo logo de OpenAI se ve negro en ArtBox y blanco sobre una superficie muy clara en Lanzadera; sucede también con otros logos monocromos del selector de lotes. Descripciones y ayudas tienen tanto peso visual como elementos principales, mientras algunos títulos usan una tipografía mucho más fina.

**Mejora:** un componente común que adapte los logos al fondo real y una escala tipográfica compartida para título, contenido y ayuda. Usar peso, espacio y superficie de forma consistente; evitar bloques explicativos enteros con peso de título.

**Aceptación:** las mismas identidades conservan contraste en todos los módulos y temas; los tres niveles de lectura se distinguen a simple vista.

### U09 · P2 · Biblioteca mezcla densidades y no explica bien el ámbito del recuento

**Evidencia:** E-26 y E-34. La rejilla de cinco columnas también se usa para carpetas, con títulos y recuentos partidos y alturas desiguales. Un nombre de proyecto relativamente corto ya se recorta en la cabecera. La raíz indica 260 archivos mientras una carpeta indica 582: el código confirma que el recuento de la raíz corresponde a los archivos sin proyecto, pero esa condición no se rotula.

**Mejora:** densidad propia para carpetas, separada de la de fotografías; títulos a dos líneas y alturas uniformes. Cabecera con más espacio para el nombre y acciones secundarias agrupadas. Rotular «Sin proyecto» y distinguir ese recuento del total de la Biblioteca.

**Aceptación:** se identifica cada proyecto sin entrar en él; se entiende qué cuenta el número visible y qué ámbito tiene la búsqueda.

### U10 · P2 · Personalización cotidiana conduce a un editor de reglas avanzado

**Evidencia:** E-40, E-41 y E-43. Entrar en Fondo, movimiento y háptica presenta Global, Exportar/Importar, «heredado», restauraciones por fila, campos decimales y botones Guardar repetidos. Los ajustes están disponibles, pero su presentación exige demasiadas decisiones para cambiar una preferencia habitual.

**Mejora:** controles directos para fondo, movimiento reducido e intensidad háptica, con una breve vista previa. Mantener ámbitos, herencia e importación en un nivel Avanzado accesible.

**Aceptación:** activar/desactivar háptica o reducir movimiento requiere como máximo dos acciones desde Personalización y no exige editar valores numéricos técnicos.

### U11 · P2 · El lote tiene dos caminos y cantidades poco coherentes

**Evidencia:** E-13 y E-15. El acceso directo de resultados ofrece ×1, ×2, ×4 y ×8. Varios modelos anuncia 1–20 por modelo y admite uno solo. Para pedir tres resultados hay que descubrir el segundo camino. La pantalla antepone guardar grupos y varios textos explicativos a la elección de modelos. Aparece «1 generaciones».

**Mejora:** un único control de cantidad con presets y valor exacto; en el lote, cada modelo seleccionado lleva su cantidad junto al nombre. Grupos guardados como acción secundaria. Corregir singular/plural.

**Aceptación:** pedir tres resultados de un modelo o cantidades distintas de dos modelos usa el mismo patrón y ofrece un resumen inequívoco antes de ejecutar.

### U12 · P2 · El chat oculta información útil y deja estados ambiguos

**Evidencia:** E-37 y E-38. El nombre del agente/modelo se incorpora al placeholder y queda truncado entre los controles del compositor. Al escribir, esa identidad deja de verse. El panel lateral distingue la conversación activa, pero no ofrece las X solicitadas, búsqueda ni fecha/vista previa para diferenciar títulos iguales. En el historial observado hay respuestas representadas solo por «…», sin explicación visible de su estado.

**Mejora:** identificador breve y consultable del agente fuera del placeholder; adjuntar/cámara agrupados cuando falte espacio; acciones de cerrar presentación o archivar en la fila de conversación, conservando el historial. Convertir «…» en estados diferenciados de espera, respuesta vacía e interrupción, con acción pertinente.

**Límite:** se examinó el historial existente; no se envió un nuevo turno para medir respuesta del agente.

**Aceptación:** se reconoce quién responde mientras se escribe, se accede a proyectos/conversaciones mediante un control visible y ningún estado terminal queda como puntos indefinidos.

### U13 · P2 · El usuario no puede distinguir builds diferentes desde la app

**Evidencia:** la APK anterior y la instalada durante esta auditoría se identifican como 1.9.74 / 108, con contenidos distintos. Esto provocó que inicialmente se inspeccionara una compilación anterior a los cambios de GitHub.

**Mejora:** identidad de compilación visible en Acerca de: versión, canal, fecha y commit corto; incrementar versionCode para cada distribución posterior e identificar una vía única de actualización.

**Aceptación:** el usuario puede comparar su instalación con la distribución vigente sin ADB ni extracción de APK.

## Lo que ya funciona y conviene conservar

| Recorrido | Resultado observado |
| --- | --- |
| Galería desde el compositor | Abre el selector del sistema con una pulsación. |
| Selección de dos imágenes | Ambas quedan seleccionadas en la misma apertura y llegan al compositor. |
| Comprobar y quitar adjuntos | Miniaturas reales y eliminación individual verificadas. |
| Borrar y restaurar prompt | Se recuperó íntegro el texto de prueba. |
| Plantilla Tencent Image Upscaler | Pide una imagen de origen; no expone prompt ni referencias creativas ajenas a esa operación. |
| Nombres Mini | La compilación actual ya muestra GPT Image 1 Mini; se descarta el fallo observado en la APK anterior. |
| Flow | Estado listo observado y apertura de un resultado existente verificada; error previo visible con explicación y recuperación. |
| Biblioteca | Entrada en carpeta de proyecto y listado contextual comprobados. |
| Ajustes | Categorías y búsqueda general ofrecen un punto de entrada comprensible. |

## Observaciones que requieren aislamiento adicional

- Una carpeta pasó de 340 archivos visibles a 0 durante la sesión, mientras el índice general incorporaba más activos. Se conservaron dos capturas posteriores del estado vacío. No se ha aislado si intervino sincronización, reasignación o presentación; **no se concluye pérdida de archivos ni se atribuye una causa**.
- Se observaron salidas inesperadas a Inicio al usar Atrás por ADB con un panel abierto. El cierre del menú de Biblioteca funcionó en la repetición. Hace falta contrastarlo con el gesto físico de Atrás antes de elevarlo a defecto confirmado.
- No se validaron nuevas generaciones, ejecución de lotes, todas las variantes del catálogo, voz en directo, sensación física de la háptica, todos los temas, letra ampliada ni otros dispositivos. Los tiempos de ADB no se presentan como medidas de rendimiento de la app.

## Mejoras propuestas, en orden de entrega

1. **Recuperar una base de uso fiable:** corregir pies/teclado/cabeceras; acceso visible a navegación; identidad estable de modelo; visor limitado al contexto; filtro múltiple de proyectos. Validar esos recorridos juntos, en un mismo APK identificable.
2. **Integrar Flow como espacio de trabajo:** proyecto y ajustes efectivos, galería central del proyecto, un solo conjunto de filtros de estado y compositor compacto compartido. La lectura del estado y su recuperación deben explicar qué falta para seguir.
3. **Cerrar el sistema visual:** mismos componentes para logos, campos, adjuntos, hojas, selección y estados. Fichas orientadas a tareas con datos específicos; controles de lote coherentes; preferencias cotidianas directas y reglas avanzadas aparte.
4. **Aceptación de UX:** repetir selección/revisión de adjuntos con teclado, navegación completa, filtros y retorno del visor, errores y reanudación; incluir letra ampliada y comprobación física de gestos/háptica. Aprobar por recorridos visibles completos, no por número de pruebas unitarias.

## Referencias usadas para orientar las propuestas

- [Material Components: navegación inferior](https://github.com/material-components/material-components-android/blob/master/docs/components/BottomNavigation.md): destinos principales accesibles y representados por icono y etiqueta. La propuesta de un acceso mínimo persistente es un criterio de diseño para Nexus.
- [Android: tamaño de objetivos táctiles](https://support.google.com/accessibility/android/answer/7101858?hl=en): compactar la apariencia manteniendo objetivos de al menos 48 dp. No se propone reducir las zonas táctiles para ahorrar espacio.
- [Google Flow: crear y editar imágenes](https://support.google.com/flow/answer/16729550?hl=en): el flujo estándar identifica proyecto, modelo, proporción, cantidad y referencias. Son los datos operativos que debe reflejar la integración de Nexus.
