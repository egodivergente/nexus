# Nexus 1.6.3-results · Resultados múltiples

## Causa confirmada en el S25

Seis trabajos tenían resultados con URL distinta asociados al mismo archivo local. `AlexiaBridge` construía el nombre del archivo con el identificador del trabajo, sin distinguir cada salida: después de descargar la primera imagen, reutilizaba su archivo para las siguientes.

El visor reiniciaba el indicador de carga al cambiar de URL, pero conservaba el componente Coil si la URI local era la misma. Al no comenzar una nueva petición, podía seguir mostrando la primera imagen con el indicador de carga indefinido. Los reproductores de audio y vídeo también conservaban la instancia del resultado anterior.

## Cambios

- Nombre independiente y determinista por trabajo, salida y URL, sin sobrescribir las copias antiguas.
- Detección de asociaciones locales ambiguas tanto al mostrar como al recuperar resultados existentes.
- Recuperación de los archivos originales del proveedor mediante GET; ninguna generación nueva.
- Estado del visor y de los reproductores asociado al resultado seleccionado, con reinicio de zoom y carga.
- Clasificación serializada para evitar dos descargas concurrentes sobre el mismo destino temporal.
- Cierre de respuestas HTTP y mensajes de descarga sin URLs firmadas ni rutas con el prompt.

Archivos principales: `core/model/OutputFiles.kt`, `core/alexia/AlexiaBridge.kt`, `ui/output/OutputViewerScreen.kt`, `OutputFilesTest.kt`, `MultiOutputTest.kt`, `app/build.gradle.kts`.

## Comprobación

- Compilación en Pixel 8 Pro, un worker y sin paralelismo; no se compiló en el EliteBook. ASUS comprobado, con otro proceso Gradle y poca memoria disponible.
- 207 pruebas JVM correctas.
- Tres pruebas instrumentadas correctas en Pixel: píxeles diferentes al alternar tres imágenes, ausencia de carga infinita con una URI local compartida y descarga independiente de tres archivos mediante HTTP interceptado, sin red real.
- La primera ejecución del instrumental falló por `InputManager.getInstance` de Espresso 3.6.1. Se actualizó exclusivamente la dependencia de pruebas a Espresso 3.7.0 / AndroidX Test JUnit 1.3.0 y se repitieron las pruebas satisfactoriamente. [Corrección documentada por Android](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0-alpha04).
- Instalado en usuario personal 0 del S25 y Pixel, versión 1.6.3-results, código 17. SHA-256 del APK instalado verificado en ambos: `d9fc3b46fcc90d205acc06dd14794721c26592526ed906b0c7a1e85a477863e1`.
- Los 292 archivos del manifiesto de compilación coinciden con el proyecto actual del PC.

## Recuperación real del S25

El teléfono devolvía `UnknownHostException` al descargar. Se recuperaron desde el PC 18 imágenes válidas de sus URLs originales, se verificó que eran imágenes y se copiaron a los destinos independientes que espera Nexus. Al abrir de nuevo la app, esta las registró con sus metadatos.

| Trabajo | Resultados locales verificados |
|---|---:|
| Youchuan, primer trabajo afectado | 4 de 4 |
| Youchuan, segundo trabajo afectado | 4 de 4 |
| Qwen Image 3 Pro Edit | 4 de 4 |
| Seedream, descomposición de siete capas | 7 de 7 |
| Seedream, descomposición antigua de tres capas | 1 de 3 |
| GPT Image mediante Runway, trabajo antiguo | 0 de 2 |

Los cuatro resultados pendientes pertenecen a dos trabajos antiguos: sus enlaces responden HTTP 403 o 401 también desde el PC mediante GET. Las copias locales antiguas permanecen en Alexia, pero no se presentan como copias verificadas de resultados distintos cuando su asociación es ambigua. No se regeneraron ni se borraron archivos para intentar resolverlo.

Integridad SQLite `ok`; todos los trabajos, identificadores de salida y URLs originales preservados. Las 73 copias locales originales conservan su SHA-256 y todos los assets originales del índice de Alexia siguen presentes. Cuatro trabajos recuperados completos y ningún resultado mantiene una asociación local conflictiva. Sin errores AndroidRuntime del proceso del S25 comprobado.

La prueba visual automatizada se hizo en Pixel. El S25 estaba bloqueado para interacción; allí se verificaron instalación, inicio, registros, lectura de archivos, hashes y reparación de datos. La resolución DNS del teléfono no se modificó y queda como limitación de conectividad.

## Entrega y evidencias

APK: `outputs/multi-output-20261002/NEXUS-1.6.3-results-debug.apk`.

En la misma carpeta: `unit-results.json`, `instrumentation.txt`, `repair-verification.json`, `final-verification.json`, `recovered-media.json`, `source-final-manifest.json` y copias de seguridad previas. Los respaldos contienen información personal y no deben publicarse.

No se realizaron generaciones ni se consumió saldo. Las consultas externas fueron lecturas de archivos ya generados; las pruebas de descarga usan respuestas interceptadas y Nexus Lab no tiene permiso INTERNET.
