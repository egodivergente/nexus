# Nexus 1.6.2 · Referencias, formatos y lotes

## Resultado comprobado

Build de depuración `1.6.2-references`, código 16, compilada en el Pixel 8 Pro con JDK 17, Gradle 8.9, un worker y sin paralelismo. No se compiló en el EliteBook. La firma final se aplicó en Windows con la clave de depuración existente.

Instalada mediante actualización en el usuario personal 0 del Pixel 8 Pro y del Samsung S25 Ultra. El SHA-256 instalado coincide en ambos con el APK entregado:

`b75ed60d6cc562dc9a5c0b821ca111cbb549ce18e1f5c1cbc5084e11ea2c1a57`

APK: `outputs/references-20261002/NEXUS-1.6.2-references-debug.apk`.

## Problemas corregidos

- La selección del lote mezclaba modelos de texto con modelos de edición: ahora abre con referencias, limita las opciones a contratos compatibles y comprueba el esquema antes de permitir la selección.
- El catálogo ofrecía demasiadas alternativas al mismo nivel: Seedream, GPT Image, FLUX, Midjourney y Nano Banana tienen prioridad; la comparativa presenta una variante reciente por familia y mantiene las otras en búsqueda y «Otros modelos y variantes».
- Los tamaños en píxeles eran difíciles de interpretar: ahora se presentan como `9:16 · Vertical`, `16:9 · Horizontal`, `1:1 · Cuadrado`, etc. Los píxeles son secundarios; las equivalencias aproximadas muestran ≈. El payload conserva el valor nativo exacto.
- El contrato real de FLUX usa `size` como texto con límites numéricos por lado. La validación anterior intentaba validar el texto completo como número. Ahora comprueba cada dimensión.
- La vista previa de laboratorio agrupaba referencias distintas por su función y conservaba solo una: se corrigió la deduplicación para mantener los archivos independientes.

## Pruebas

- 204 pruebas JVM: 0 fallos, 0 errores, 0 omitidas.
- 2 pruebas instrumentadas en Pixel: conservación de tres referencias y preparación de seis solicitudes con cantidades 1/2/3, contratos reales en fixtures, referencias incompatibles bloqueadas y barreras contra envío comprobadas.
- Prueba visual en Nexus Lab del Pixel: tres referencias; formato 9:16; dos solicitudes Seedream, dos GPT y una FLUX; tres entradas listas; ajustes adaptados visibles; scroll completo; CTA fijo accesible; validación sin envío correcta.
- La variante de Youchuan de una sola imagen no pudo seleccionarse con tres referencias. Se verificó por separado el contrato de Blend con mínimo dos y máximo cinco imágenes.
- Inicio de Nexus principal en Pixel y S25: correcto. Pixel sin entradas AndroidRuntime de error del PID comprobado. Catálogo principal verificado visualmente con referencias y las familias prioritarias.
- Integridad SQLite: `ok` en ambos dispositivos. Todas las filas originales se conservaron: Pixel 4 generaciones / 4 salidas; S25 89 generaciones / 87 salidas. Los archivos originales capturados permanecieron idénticos: 8/8 y 23/23, respectivamente.
- Preferencias de Nexus Lab restauradas tras las pruebas. Los medios de prueba se limitaron a Lab.

No se realizaron generaciones ni llamadas a endpoints de generación de pago. Las cotizaciones de las pruebas eran simuladas y estaban señaladas en pantalla; Nexus Lab carece de permiso INTERNET. Los esquemas públicos se consultaron mediante GET, sin claves ni créditos.

## Evidencias

En `outputs/references-20261002/`: `unit-results.json`, `instrumentation-final.txt`, `installation-verification.json`, `artifact-hashes.json`, `build-pixel.log`, `source-final-manifest.json`, capturas `pixel-final-*` y `pixel-main-catalog.png`.

## Archivos principales

- `ui/models/ImageModelChoices.kt`, `CatalogGuide.kt`, `CatalogTreeScreen.kt`, `CatalogWorkflow.kt`.
- `ui/create/ParameterPresentation.kt`, `CreateScreen.kt`, `ComparisonController.kt`, `ComparisonPlan.kt`, `ComparisonScreen.kt`.
- `core/model/FormContract.kt`, `provider/atlas/NativeModelSchema.kt`.
- `CreateViewModel.kt`, `LocalQaFixture.kt`, `MainActivity.kt` para la prueba local de tres referencias.
- Tests `ReferenceBatchTest`, `ParameterPresentationTest`, `ComparisonOfflineTest` y fixtures nativas.

## Límites reales

No se comprobó una generación de pago de extremo a extremo, conforme al límite solicitado. La disponibilidad y cotización remotas siguen dependiendo del proveedor y la conexión; el Pixel mostró el catálogo guardado mientras intentaba recuperar conexión. Una tarifa desconocida sigue bloqueando el envío. La selección reciente utiliza las versiones disponibles en el catálogo, sin afirmar que el catálogo incluya todo lanzamiento del mercado. La comprobación visual de esta entrega se hizo en Pixel; en S25 se verificaron instalación, versión, inicio y conservación de datos.
