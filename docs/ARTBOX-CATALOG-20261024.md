# ArtBox: catálogo, fullscreen y evaluación local — 2026-10-24

## Procedencia y alcance

Trabajo aislado en `codex/artbox-catalog-fixes-20261024`, worktree
`C:/Users/User/.codex/worktrees/artbox-catalog-fixes`.
Base: `origin/codex/nexus-master-artbox-20261023` en `d21cfe1`, verificada con fetch.
`origin/main` estaba en `631aa29`: no contiene la continuidad ArtBox documentada
en CONTINUAR.md y MIGRATION-STATUS.json. No se fusionan ramas de otras tareas.
El checkout principal conserva muchos cambios ajenos; el worktree ArtBox original
conserva su modificación de gradlew.bat. No se operan dispositivos.

La petición de esta tarea prohíbe publicar, instalar y gastar créditos y prevalece
sobre la instrucción general de push. La entrega queda local.

## Correcciones

- Ocultar una categoría de Herramientas usaba `family:` aunque la clasificación de
  herramientas no es una familia de modelos. Ahora usa `tool:` y se respetan las
  reglas antiguas para esas categorías.
- Las reglas de ocultación capturan las rutas concretas de cada regla en las mismas
  preferencias privadas. Cada publicación del catálogo compara el anterior y el
  nuevo; un cambio de etiqueta/familia no resucita una ruta ya ocultada. Se conserva
  el catálogo completo en caché y la restauración explícita retira solo esa regla;
  las reglas hijas siguen vigentes. Las familias siguen cubriendo variantes futuras.
- La identidad de familia reconoce `providerModelId` cuando existe y la agrupación
  usa Locale.ROOT. Seedance mantiene familia → versión → variantes.
- Ordenar por precio, nombre, uso o lanzamiento conserva el nivel de familias y las
  categorías de Herramientas. Su orden personalizado tiene un ámbito independiente.
- Fullscreen se aplica al crear/reanudar la Activity, recuperar foco, cambiar el tema,
  abrir hojas compatibles y los dos diálogos de credenciales de Ajustes. Las barras
  del sistema se pueden revelar transitoriamente por gesto. No se oculta el teclado.
- Las tarjetas reservan líneas para título, descripción, fecha y nota; logos con
  contenedor constante, columna de precio uniforme y botón Crear de mínimo 52 dp.
  Crear abre opciones, no envía una generación. Con fuente ampliada se usa lista y
  se permite crecer al texto en vez de imponer alturas que lo recorten.

## Validación

PASS: `gradlew.bat :app:testOfflineQaUnitTest --tests '*Catalog*Test'
--max-workers=2 --no-parallel --console=plain`: 56 pruebas, cero fallos/errores/omitidas,
incluidas las 6 nuevas de `CatalogRefreshRegressionTest`. Compilación Android/Kotlin
incluida; ejecución en AXELPC, daemon propio con prioridad BelowNormal.
Regresiones: renombrado, refresco de Seedance, categorías nuevas y antiguas,
ordenación, restauración con reglas hijas e identidad independiente de locale.
Las pruebas usan fixtures locales, no solicitudes de generación.

PASS: `gradlew.bat :app:testOfflineQaUnitTest --max-workers=2 --no-parallel
--console=plain` sobre el parche final: 656 pruebas, cero fallos, errores u
omitidas. Recompilación incremental incluida. Los 56 casos anteriores forman
parte de estos 656; no se suman como casos distintos. `git diff --check`: PASS.

No se ha instalado una APK ni verificado visualmente en teléfono fullscreen,
teclado, diálogos o medidas de tarjetas. La comprobación física queda pendiente.

## AtlasCloud CLI: verificado y límites

Se encontró `atlascloud-cli` npm 0.1.36 ya instalado. El wrapper falla con
`spawn UNKNOWN`; invocar el binario existente para consultar su versión devolvió
«Una directiva de Control de aplicaciones bloqueó este archivo». No se cambia
esa política, no se reinstala y no se accede a claves. No hay JSON real de catálogo
o precios obtenido mediante CLI en esta tarea.

La [documentación oficial actual](https://www.atlascloud.ai/docs/cli) documenta
`atlas models list --type video --json`, `atlas models get MODEL_ID --json` y
`atlas generate cost video MODEL_ID ... --json`. Descubrimiento y estimación no
envían generaciones. La versión y el esquema reales deben verificarse antes de
automatizar. Desactivar actualizaciones automáticas con `ATLAS_AUTO_UPDATE=0` y
telemetría con `ATLAS_TELEMETRY=0` en una futura comprobación autorizada.

El [repositorio público](https://github.com/AtlasCloudAI/cli) distribuye binarios y
wrappers, no el núcleo Go. Su README consultado tenía diferencias respecto a la
guía actual en audio/3D: no se deducen capacidades de una lista promocional.

Decisión: conservar el adaptador Android HTTP existente. No introducir un proceso
CLI de escritorio en Android ni un parser de JSON sin muestra real. Una futura
integración auxiliar debe separar listado, esquema y coste configurado; importar
por proveedor + ID, con fecha/origen/unidad; conservar las preferencias de usuario;
y dejar el coste desconocido cuando falten datos. No se añade una tarifa fija ni
una capacidad basada en nombres comerciales. CLI pendiente de validación ejecutable.

## Inpainting local con máscara: evaluación

Inventario de esta sesión: Windows `AXELPC`, Intel UHD Graphics 620, unos 15,3 GiB
de RAM total. En la caché Hugging Face revisada aparecieron SmolVLM-500M y
faster-whisper-base, no pesos de inpainting. Esto no es un inventario de todos los
discos. No se descargó ningún modelo ni se midieron latencia o calidad.

La app ofrece edición multimedia/Whisper y rutas remotas con máscara; la búsqueda
en el código Android y agent-core no encontró un motor generativo local de
inpainting. No se declara disponible por existir un campo `mask` en un contrato.

| Operación | Ruta local evaluada | Límite |
| --- | --- | --- |
| Borrar defectos/objetos pequeños | OpenCV Telea / Navier-Stokes | Rellena desde vecinos; no entiende instrucciones ni crea objetos nuevos. |
| Añadir o cambiar objetos | Checkpoint dedicado de inpainting mediante Diffusers | Requiere pesos y runtime; viabilidad de rendimiento en UHD 620 no probada. |
| Borrar objetos grandes | Inpainting generativo con descripción del fondo | Calidad y coherencia requieren prueba visual; no garantizadas. |

[OpenCV](https://docs.opencv.org/4.x/df/d3d/tutorial_py_inpainting.html) requiere máscara
del mismo tamaño y usa los píxeles no nulos para delimitar el relleno. Es candidato
para borrado sencillo en CPU, no una solución completa a las tres operaciones.
[Diffusers](https://huggingface.co/docs/diffusers/en/using-diffusers/inpaint) recibe
imagen, máscara y prompt; blanco edita y negro conserva. Un checkpoint dedicado es
preferible a asumir que cualquier modelo de imagen soporta inpainting. El
[offload](https://huggingface.co/docs/diffusers/main/en/optimization/memory) reduce
memoria de GPU pero no demuestra aceleración compatible en esta GPU Intel.

Propuesta para otra fase: editor de máscara con pincel/borrador/deshacer, coordenadas
de la imagen original tras orientar EXIF, máscara PNG de un canal, rechazo de máscara
vacía o dimensiones distintas y límite de píxeles. Preservar originales y guardar
una salida nueva; recomponer fuera de la máscara desde el original si se exige
identidad exacta de esos píxeles. Motor opcional y explícito; nunca fallback remoto
silencioso. Validar tres escenas (añadir/cambiar/borrar), bordes, transparencia,
cancelación, memoria y tiempos antes de habilitarlo. Evaluado, no implementado.
