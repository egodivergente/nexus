# Lotes: verificación del 2 de octubre de 2026

Versión 1.6.1-batches (15). Compilación ejecutada en el Pixel 8 Pro físico, con fuentes verificadas mediante SHA-256 y un worker. La compilación final terminó correctamente en 3 min 55 s. La firma se realizó con la clave debug existente para permitir actualizaciones sin desinstalar.

## Resultados

- 194 pruebas JVM: 0 fallos, 0 errores, 0 omitidas. Incluyen 16 casos de ComparisonPlanTest y la regresión de inicialización de navegación.
- ComparisonOfflineTest en Pixel: PASS. Cuatro contratos, cantidades 1/2/3/4, diez solicitudes, subtotal y total simulado; ambos caminos de envío bloqueados y el historial sin cambios.
- Interfaz física: selección de un modelo con tres generaciones; selección de dos modelos con cantidades 3 y 2; controles +/−; subtotales y total; cantidades restauradas después de cierre forzado; CTA visible con teclado abierto. Capturas y árboles UI guardados en la carpeta de resultados.
- Arranque de la app normal: PASS, botón Crear lote o comparar visible y sin errores AndroidRuntime en su proceso.
- APK instalado en el perfil personal 0 de Pixel 8 Pro y Samsung S25. Hash del APK instalado coincidente en ambos; sin desinstalación ni borrado de datos.
- Pixel: 4 generaciones y 4 resultados conservados, filas idénticas e integrity_check=ok.
- S25: 85 generaciones y 84 resultados conservados, todos los identificadores presentes e integrity_check=ok. Un resultado existente completó local_uri, MIME image/jpeg y dimensiones 1424×800. Las restantes columnas se conservaron; la comparación byte a byte de esa fila detecta legítimamente esta actualización.
- Preferencias y archivo de prueba del laboratorio restaurados al estado anterior. La copia original del laboratorio tenía una advertencia de tar al principio por ausencia de files; se conservó y se leyó una copia separada sin esa cabecera de diagnóstico.

## Evidencia

`outputs/batches-20261002`: build-pixel.log, source-final-manifest.json, source-checksums.sha256, unit-results.json, instrumentation-pixel.txt, installation-verification.json, artifact-hashes.json, pixel-batch-*.png/xml y pixel-main-runtime.log.

APK final: `outputs/batches-20261002/NEXUS-1.6.1-batches-debug.apk`.
SHA-256: `f102043d9868c10c8f0f9d9d892f52f4673189c606924c3a22bb10e76e44514e`.

## Alcance y límites

No se realizaron generaciones, subidas a proveedores ni llamadas a endpoints de generación de pago. Nexus Lab carece de permiso INTERNET y bloqueó la ejecución. El envío real y la facturación final del proveedor no se probaron. La app normal puede recuperar catálogo y seguir tareas existentes al abrirse.

La cantidad corresponde a solicitudes independientes: 1–20 por modelo y 1–6 modelos, máximo 120. La función actual es para imágenes. No reanuda automáticamente los envíos pendientes tras cerrar el proceso. Las opciones incompatibles se muestran antes de enviar, y la ausencia de coste bloquea el lote.

Se conservó la corrección concurrente de navegación mediante enum Screen, y se agregó una regresión que comprueba la inicialización desde cada destino. También se conservaron los cambios concurrentes del catálogo en la instantánea compilada.
