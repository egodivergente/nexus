# PR #10: integración previa a generar (27-10-2026)

> Alcance corregido después por Vidal: selección pequeña de Fal que complemente
> Atlas. Véase `FAL-CURATED-20261027.md`. Los avisos por defecto se han retirado;
> este documento conserva la revisión técnica inicial y no es el estado final.

## Estado y alcance

Revisado PR #10, abierto, borrador, sin fusionar, head
`dce80bebd0a3e19d1932bda92edbae3a0a8b786c`.
Esta rama parte de ese head, no de main (main está atrasada respecto al PR).
No modifica las ramas de rescate/UX posteriores, ni instala APKs.

Se conecta `ProviderRoutePolicy` a `PlanBuilder.build` y por tanto a `rebuild`,
antes de devolver el plan a las herramientas y tarjeta de confirmación del agente.
`ToolDeps.costPreflight` permite inyectar la fuente verificada. Android ya muestra
`plan.warnings` en `assistant/OrderCard.kt`; el host Desktop serializa el plan.
Se sincronizan únicamente las clases tocadas y la política a la copia de core Desktop.
No se cambia el ranking de `ModelRouter`, la selección, `SpendGuard`, los tickets,
los endpoints de generación ni el estimador legado.

Por defecto Fal (`fal`, plantilla `fal-extra`) y Atlas muestran evidencia insuficiente.
BytePlus y los demás proveedores quedan fuera de la comparación Fal/Atlas. No se
infiere que un proveedor configurado con otro ID sea Fal: se debe inyectar el ID
exacto tanto en la política como en el conjunto de proveedores del preflight.

Cuando un adaptador aporta datos, se verifica el alcance mediante digest SHA-256
serializado, incluyendo parámetros efectivos, prompt, referencias ordenadas,
rol, MIME y campo de destino. La evidencia de otra petición no sirve. Cada edición
recalcula los avisos. Hay timeout de 2 segundos, aislamiento de las colecciones del
plan, propagación de cancelación y errores sin datos sensibles.

La comparación es histórica, fechada al preparar; no es una reserva de tarifa ni
una autorización de gasto. El usuario conserva el modelo. Para cambiarlo debe
seleccionarlo y preparar/confirmar otro plan. No se usa `complementsAtlas` para enviar.
Los pares muestran totales decimales, diferencia firmada, fuentes y fechas.

## Interfaces comprobadas

### Fal

Fuentes oficiales leídas el 27-10-2026:
- https://fal.ai/docs/platform-apis/v1/models.md
- https://fal.ai/docs/platform-apis/v1/models/pricing.md
- https://fal.ai/docs/platform-apis/v1/models/pricing/estimate.md
- https://fal.ai/docs/api-reference/inference-api/queue/submit.md

Consulta GET real, sin clave ni generación:
`https://api.fal.ai/v1/models?endpoint_id=fal-ai/flux/schnell&expand=openapi-3.0`
respondió 200 y entregó metadata y OpenAPI **3.1.0**, aunque el parámetro de
expansión se llame `openapi-3.0`. Incluye `FluxSchnellInput`, variantes `anyOf`,
`$ref`, cantidad, semilla, formato, comprobador de seguridad y aceleración.
No basta traducir resolution/duration ni equiparar el nombre comercial.
`info.version=1.0.0` del schema no acredita la versión de los pesos del modelo.

`GET https://api.fal.ai/v1/models/pricing?endpoint_id=fal-ai/flux/schnell`
respondió **401 Unauthorized** sin autenticación. La documentación exige clave,
acepta 1–50 endpoint_id y devuelve `prices[].endpoint_id`, `unit_price`, `unit`,
`currency`, además de paginación. Puede aplicar tarifas de cuenta.
El decimal debe leerse del token JSON, nunca pasando por Double.

`POST /v1/models/pricing/estimate` recibe `estimate_type` y `endpoints`:
- `historical_api_price`: `call_quantity`, estimación histórica, no total certificado.
- `unit_price`: `unit_quantity`, requiere conocer unidades facturables y reglas.
No se envió ese POST. Ninguno de estos modos demuestra por sí solo el coste
completo de una configuración, referencias y cantidad determinadas.

En Nexus existe solo `MediaTemplates.kt`, plantilla síncrona `fal-extra` con
`Authorization: Key …`; declara explícitamente que no soporta colas largas.
No se debe presentar como integración de vídeo Fal terminada.

### Atlas

Código inspeccionado:
- `AtlasCloudProvider`: catálogo `/api/v1/models`; generación
  `/api/v1/model/generateImage`, `generateVideo`, `generateAudio`.
- `NativeModelSchema`: descarga schemas de `static.atlascloud.ai`, interpreta
  `components.schemas.Input`; no es un validador completo de OpenAPI Fal.
- `AtlasPriceQuote`: POST público `/api/v1/model/calculate`, sin credenciales ni
  medios reales; descodifica `data.price` como Double. Su flag `isRequestTotal`
  no aporta identidad/versionado, moneda explícita, reglas completas ni recibo
  de equivalencia. También hay fallbacks y tarifas locales.

GET real al catálogo Atlas respondió **403** desde este entorno. No se envió
ningún POST de generación ni se reutilizó una clave de otro dispositivo.
Fuentes públicas consultadas:
- https://atlascloud.ai/docs/en/models/price
- https://www.atlascloud.ai/pricing/models
- https://www.atlascloud.ai/models/explore/seedance-2-lowest-price
La última distingue precios aproximados por segundo y facturación por tokens;
por tanto no sirve multiplicar una cifra promocional por duración y certificar ahorro.
El conector Atlas sí permitió lectura: `atlas_list_models` devolvió una página
con 50 de 510 modelos y `has_more=true` (no acredita catálogo completo).
`atlas_get_model_info` para `bytedance/seedance-2.5/reference-to-video` entregó
schema y estimación real a defaults de 1.514799 USD, separada de `base_price=0.134`.
Son datos observados, NO una cotización de la petición del usuario, y no se
precargan en código ni se comparan con fixtures Fal.

El contrato distingue 1080p nativo, SR y ESR; edición exige duración -1, un vídeo
y ratio adaptive; contiene audio, referencias ordenadas, formato y modo draft.
Esto confirma que igual resolución nominal no acredita calidad equivalente.
Además, el ejemplo generado por el conector propone POST a la URL de polling
`prediction/{request_id}`: contradice el endpoint de envío existente. No copiar
ese ejemplo sin resolver el contrato de operaciones en el OpenAPI original.
No se ha cargado ninguna tarifa real ni equivalencia comercial en esta rama.

## Cambios concretos pendientes para habilitar comparaciones reales

1. Implementar `ComparisonEvidenceSource.load(plan)` en los adaptadores Android
   y Desktop; inyectarlo mediante `ToolDeps.costPreflight` junto a una
   `ProviderRoutePolicy` con reloj y ventanas de vigencia explícitos. Devolver
   null mientras no pueda acreditar todos los campos, sin inventar versiones.
2. Separar en Atlas un DTO de cotización decimal (token JSON → BigDecimal),
   endpoint/digest de contrato, moneda y evidencia de reglas/cargos. Mantener
   el `PriceQuote` legado para su UI hasta migrarla; no certificar su Double ni
   sus fallbacks. Validar respuestas autenticadas y catálogo completo sin filtrar
   ocultos. Un 403 o inventario parcial significa desconocido, no ausente.
3. Crear un cliente Fal de metadata y tarifas autenticadas, sin activar modelos,
   con paginación, timestamp y schema revision/digest. Las unidades y estimaciones
   históricas siguen siendo no comparables hasta verificar reglas y cantidades.
4. Construir `RequestedConfiguration` con defaults y cantidad real del lote,
   referencias con identificadores de contenido inmutables (no solo URI), y
   `inputDigest=GenerationCostPreflight.digest(plan)`. El adaptador debe validar
   combinaciones contra ambos schemas y adjuntar recibos exactos. El digest de
   plan no detecta bytes cambiados detrás de una misma URI: el adaptador debe
   medir/versionar esos contenidos antes de certificar la petición.
5. Acreditar identidad/versionado y equivalencia de ejecución/calidad por par
   y configuración. Misma familia, resolución o nombre no bastan. Sin ello se
   mantienen las carencias, aunque ambas tarifas existan.
6. Implementar aparte la cola Fal (submit/status/result/cancel, credenciales,
   idempotencia y envío incierto) antes de ofrecer vídeo Fal ejecutable. Esta
   rama no simula ese backend ni lo activa.
7. ArtBox usa `ui/create/GenerationDispatcher` y no pasa por `PlanBuilder`:
   conectar el mismo preflight después de construir su petición efectiva y antes
   del diálogo de coste, sin convertir `ComparisonController` (generaciones
   múltiples) en comparación económica ni disparar trabajos de prueba.
8. Tras incorporar en la rama actual de producto: compilar Android y Desktop,
   probar tarjetas en Pixel 8 Pro y UI Desktop; no instalar sobre versiones más
   recientes sin reconciliar. Si se añade selector de alternativa, preparar otro
   plan y obtener su confirmación, nunca reutilizar tickets de la ruta anterior.

## Validación

AXELPC figuraba offline; ASUS no se utilizó. Verificación JVM aislada en el
contenedor de trabajo, fuentes reales de agent-core, Kotlin 2.0.21, Gradle 8.9,
JDK 21 con bytecode objetivo 17. Sin tareas Android, dispositivo ni llamadas de pago.
Pruebas dirigidas: 89 ejecutadas, 0 fallos, 0 errores, 0 omitidas. Incluyen
12 pruebas nuevas, política del PR, router, costes y seguridad de confirmación.
El primer intento del harness omitió recursos de test y produjo 7 NPE; corregido
el classpath de recursos, la ejecución final completa de esa selección pasa.
Suite completa de agent-core: **386 casos, 382 PASS, 4 omitidos, 0 fallos,
0 errores**. El harness necesitó también fijar el directorio de trabajo a
agent-core para las dos pruebas que inspeccionan archivos relativos del repo.
No se modificaron pruebas del proyecto para hacerlas pasar. No se han compilado
la APK ni el host Desktop; se comprobó igualdad de los archivos core sincronizados.

Los nuevos tests cubren conexión PlanBuilder/rebuild, selección intacta, ausencia
 de tickets, ahorro sintético, sin ahorro, caducidad, falta de equivalencia, cambios
 de entrada, orden/roles/campos, mapas, errores, cancelación, timeout y rutas ocultas.
Los fixtures siguen siendo sintéticos. No prueban tarifas reales ni calidad visual.
