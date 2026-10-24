# Correcciones UX/UI — paquete del 24-10-2026 (Android 1.9.74 / vc108)

Rama `ux/correcciones-capturas-20261024`, creada desde `codex/nexus-master-artbox-20261023` (`83db30a`, 1.9.73/vc107, la versión instalada en el Pixel 8 Pro). Las capturas del paquete y las del Pixel son privadas y **no** están en el repositorio.

## Implementado por pantalla

### ArtBox (capturas 07–10, 12, 14)
- Cabecera compacta: se retiró el inset de barra de estado duplicado (la fila de marca subió ~100 dp). Marca a la izquierda, menú y ajustes a la derecha.
- Pestañas **Vídeos / Imágenes / Herramientas / Favoritos** como texto con indicador fino (`ArtBoxTabs`); con letra ≥125 % se reparten en varias líneas en vez de recortarse. «Más» (Audio/Texto) pasó al menú ⋮; el filtro duplicado de la fila de pestañas desapareció (queda uno, junto a la búsqueda).
- Búsqueda de una sola fila (48 dp) con el único acceso a filtros y orden (`ArtBoxSearchBar`).
- Filtros activos solo como chips discretos y quitables (`ActiveFilterChips`); ya no hay banner «Con referencias» a todo ancho ni «Filtros activos / Quitar filtros activos». El interruptor «Solo modelos con referencias» vive en la hoja de filtros. En Herramientas ya no aparece el filtro de imágenes.
- Logos de proveedor **sin caja**, a 60 dp, sobre lienzo transparente (los PNG ya eran transparentes; la caja era el contenedor). Las marcas de un solo color (blanco/negro) se adaptan al tema midiendo su luminosidad real (`LogoTone`), así Runway, Grok, FLUX, GPT o Midjourney se ven en claro y en oscuro sin cuadrado.
- Fichas de proveedor: logo > nombre > nº de modelos (calculado del catálogo), superficie elevada con contorno fino, sin degradado.
- **Herramientas** con iconos funcionales (tijeras, accesibilidad, varita, avance rápido, ecualizador, pincel, HQ, voz) y no logos de marcas; el recuento sigue saliendo del catálogo.
- Listas de modelos (p. ej. Sincronización labial): una columna salvo pantallas ≥600 dp; se quitó «Fecha no verificada» y el texto de relleno repetido entre fichas; «Ver coste» → «Sin tarifa»; la nota «Precios desde · por …» nombra la unidad solo si todos los modelos visibles comparten modalidad (antes decía «por imagen» en vídeo/audio).

### Lanzadera (capturas 01, 06, 13)
- Contrato verificado en el README oficial de Atlas (`tencent-image-upscaler.md`, GET sin coste): `percent` solo con `mode=percent`; `width/height/long_side/short_side` solo con `aspect|fixed`; `encode_quality` solo en formatos con pérdida; `output_dir` no está documentado. Regla en `core/model/ParameterDependencies.kt`, con su fuente; los modelos sin regla no cambian.
- Los campos no aplicables se ocultan y **no se envían** (`GenerationDispatcher` y `CreationReadiness` usan los valores efectivos), pero se conservan en el borrador: al volver a «Tamaño exacto» reaparecen.
- Hoja y panel de ajustes agrupados en «Motor y calidad / Escala y tamaño / Formato de salida», con nombres en español («Motor de ampliación», «Cómo ajustar el tamaño», «Factor de escala», «Ancho (px)»…) y ancho/alto y lado largo/corto emparejados.
- `Rango: 1 – —` sustituido por el límite publicado («Mínimo 1», «Rango: 1 – 10») o «Sin rango publicado». Campo vacío = «Sin indicar» (distinto de «Automático», que es la elección del proveedor).
- Resumen del panel plegado con lo que se usará realmente: «Ultra · Por factor · ×2 · JPEG».
- La franja «Añade una imagen de entrada» se fundió con el botón principal (que indica lo que falta). El chip BytePlus solo aparece si ese es el proveedor activo (si no, en «Más»).

### Flow (captura 05)
- Resumen **Progreso** arriba (En curso · Listos · Errores) que filtra y salta a la lista; línea viva del trabajo activo: `En curso — progreso porcentual no disponible · tiempo`, con porcentaje solo si el proveedor lo informa (`GenerationJob.progress`).
- Cada trabajo muestra hora, tipo e ID rastreable.
- Aviso «Hay un borrador escrito en Flow…»: acciones **Abrir Flow** (hoja de conexión) y **Preparar de nuevo** (copia el prompt íntegro a un borrador nuevo; no envía nada, así no se duplica el gasto). Un prompt vacío se rellena en su sitio.
- Compositor «¿Qué quieres crear?» plegable; se pliega tras enviar para que el progreso quede a la vista.

### Biblioteca (capturas 02–03)
- Título, recuento y saldo comparten cabecera (se eliminó una fila entera); chips más cerca; columnas iniciales según el ancho (3–6) cuando no hay preferencia guardada.
- Hoja de archivo: las entradas importadas sin MIME ya muestran su **vista previa** (antes «application/octet-stream»); el MIME se resuelve por extensión/tipo (`Asset.resolvedMime`).

### Agente y navegación (captura 11)
- «Continuar en…» recupera el zorro NEXUS (oculto con letra ≥150 %). Proyecto ≠ conversación y la lógica de arranque en frío/reanudación no se han tocado.
- Barra inferior: «Biblioteca» ya no se abrevia a «Biblio» con letra grande; el rótulo deja de crecer a partir de 1,15×.

## Pruebas

| Comando | Resultado |
| --- | --- |
| `:app:testOfflineQaUnitTest` | 659 pruebas, 0 fallos (646 previas + 13 nuevas en `UxCorrectionsTest`) |
| `:agent-core:test` | 313 pruebas, 0 fallos, 4 omitidas |
| `:app:assembleAgentPreview` (firma instalada) | APK `1.9.74-agent-preview` / vc108 |

`UxCorrectionsTest` cubre: campos condicionales del upscaler por modo y formato, valores no aplicables fuera de la petición pero conservados, modelos sin regla intactos, rangos sin «—», nombres en español, agrupación y pares, Flow sin porcentajes inventados, conteo y conflicto de borrador, reutilización de prompt de 6.000 caracteres sin recorte, nota de unidad de precio e iconos funcionales distintos.

## Verificado en el Pixel 8 Pro (instalación `-r`, datos conservados)
Arranque en frío con «Continuar en…»; ArtBox Vídeos/Imágenes/Herramientas en claro, oscuro (Aurora) y letra 140 %; categoría Sincronización labial; ficha y Lanzadera del Tencent Image Upscaler (modo por factor y «Tamaño exacto»); Flow con un trabajo real fallido por borrador, «Preparar de nuevo»; Biblioteca y su hoja de archivo; barra inferior a 140 %.

## No hecho / límites
- **Compositor colapsable en Biblioteca** (capturas 03–04): las referencias parecen ser la propia interfaz de Google Flow; en Nexus solo se aplicó el principio a la pestaña Flow (compositor plegable). Falta decidir si se quiere un compositor dentro del visor de Biblioteca.
- Las dependencias de campos están verificadas solo para el upscaler de imagen de Tencent; otros modelos usan su contrato sin reglas adicionales hasta comprobarlos.
- No se muestran puntuaciones nuevas: no hay datos reales para fichas de la lista; la evaluación técnica 0–10 sigue en la ficha del modelo.
- Sin pruebas instrumentadas nuevas, sin lint y sin revisar 360/412 dp ni Android 17 en emulador; el S25 no se ha usado. Generación nueva en Flow no probada (consumiría saldo).
