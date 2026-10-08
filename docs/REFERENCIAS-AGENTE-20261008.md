# Referencias del agente — 1.8.1-agent

La tarjeta de edición podía contener dos imágenes y, aun así, fallar antes del envío con «Completa: Imágenes de referencia». El adaptador del agente asignaba una clave explícita a entradas cuyo formulario nativo valida por tipo de ranura. Se conserva la clave solo para campos de contratos de proveedor; las referencias nativas pasan a la misma ranura que utiliza el lienzo.

## Comportamiento

- Las tarjetas pendientes muestran los adjuntos y permiten cambiar las referencias o descartar el encargo sin abrir los detalles.
- `update_plan` acepta medios y reconstruye el mismo identificador. Una edición inválida conserva el plan anterior; una edición válida anula la confirmación previa.
- Preparar otra vez el mismo encargo pendiente reutiliza su tarjeta. `new_copy` permite una copia adicional cuando el usuario la pide expresamente. No se reutilizan trabajos enviados, inciertos ni descartados.
- El lote envía únicamente tarjetas marcadas con «Incluir en lote». Las tarjetas antiguas se restauran sin selección; ningún trabajo se envía por restaurar un chat.
- Los errores antiguos de referencias se retiran solo si el adaptador y la validación actual confirman que el encargo está listo. El historial y los archivos originales se conservan.

## Verificación

La compilación se realiza en una copia aislada de las fuentes actuales en ASUSVIDAL, con Java 17, SDK 35 y un solo trabajador. Las pruebas JVM cubren la validación nativa y por contrato, referencias en arrays, fotogramas inicial/final, vídeos, actualización sin duplicar y conservación de planes inválidos. Las pruebas de interfaz se ejecutan en la variante `offlineQa`, que bloquea las generaciones reales.

El APK del agente debe actualizarse con el certificado de la instalación existente. No se requiere desinstalar, borrar datos ni introducir de nuevo las credenciales.

Resultados del 8 de octubre de 2026:

- PASS: 98 pruebas de `agent-core` y 234 pruebas JVM de la app, incluidas 11 nuevas regresiones de referencias y actualización de planes.
- PASS: APK del agente, APK offline y APK de pruebas compilados en ASUSVIDAL. El empaquetado requirió aumentar el heap a 1536 MiB; se mantuvieron un trabajador y prioridad baja.
- PASS: actualización del Samsung SM-S938B a `1.8.1-agent-preview` (código 25) por ADB sobre Tailscale, con el certificado de la instalación anterior.
- PASS: comprobación del chat real: se conservan los ocho encargos y sus entradas; las dos referencias distintas son legibles. Los seis errores antiguos de referencias se retiran tras la revalidación. Las 12 preferencias existentes permanecen idénticas tras instalar.
- PASS: tarjetas con miniaturas y controles de referencias visibles; lote sin selección inicial y botón de generación deshabilitado.
- PASS: las cuatro pruebas Compose de `AssistantReferenceUiTest` ejecutadas en el Samsung SM-S938B mediante `offlineQa`, usuario Android 0. Resultado: `OK (4 tests)`. Comprueban acceso a referencias y descarte, recuento de adjuntos, bloqueo del lote durante una edición y exclusión de tarjetas antiguas sin selección.
- NO EJECUTADO: envío de generaciones reales o pruebas con coste.

SHA-256 del APK firmado del agente: `e8f31b484ece23f4ae5844bcf1ff7069777c866d95c23d889a96b3b8204f137d`.
