# Fal: solo lo que complementa Atlas

## Selección incorporada al código

La plantilla «Fal · selección» en Añadir proveedor API contiene únicamente:

| Entrada | Endpoint Fal | Motivo |
|---|---|---|
| MiniMax H3 Max | `minimax/h3-max/image-to-video` | Promoción Fal: 8 s / 768P = 0,416 USD frente a cotización Atlas 0,608 USD. |
| Topaz · vídeo | `topaz/upscale/video/generative` | Starlight para restauración/ampliación de metraje; Topaz no figura en el catálogo Atlas revisado. |
| Topaz · imagen | `topaz/upscale/image/generative` | Wonder 3.5 para ampliar imágenes; Topaz no figura en Atlas. |
| Recraft 4.1 · SVG | `fal-ai/recraft/v4.1/text-to-vector` | Vectores editables para logos/ilustración; Recraft no figura en Atlas. |

Es una selección editorial pequeña por utilidad, no un ranking de calidad ni una
lista importada de Fal. Solo Starlight Precise; se excluyen las variantes rápidas descartadas por el usuario.
Wonder 3.5 queda explícito y la reconstrucción de caras desactivada por defecto.
MiniMax Max se incluye por su promoción vigente. No se añaden otros duplicados sin ahorro comprobado.
No se altera Atlas, modelos elegidos ni configuraciones Fal guardadas anteriormente.
La plantilla sustituye solo el ejemplo visible de FLUX schnell, que ya tiene
presencia en Atlas y no estaba justificado como complemento.

## Evidencia y precios

El 27-10-2026 se recorrieron seis páginas del conector `atlas_list_models` sin
filtros: offsets 0,100,200,300,400,500; límite 100. Total 510, 510 IDs únicos,
última página `has_more=false`. Ningún nombre/ID contiene Topaz o Recraft.
Snapshot revisable: `FAL-CURATED-ATLAS-SNAPSHOT-20261027.json`.
La ausencia se refiere a ese catálogo publicado y fecha, no a capacidades
inexistentes en Atlas: Atlas sí ofrece otros upscalers.

Para los cuatro endpoints se consultó la API pública de Fal:
`GET https://api.fal.ai/v1/models?endpoint_id=...&expand=openapi-3.0`.
Todos devolvieron metadata y schema de entrada/salida. Fuentes:
- https://fal.ai/topaz
- https://fal.ai/models/topaz/upscale/video/generative/api
- https://fal.ai/models/topaz/upscale/image/generative/api
- https://fal.ai/models/fal-ai/recraft/v4.1/text-to-vector
- https://fal.ai/docs/api-reference/inference-api/queue/status.md

Recraft publica 0,08 USD/imagen en su página consultada. No se incorpora como
precio garantizado de cuenta ni como ahorro frente a Atlas. Topaz factura según
las características de entrada/salida. Todos los `priceUsd` quedan null hasta
conectar cotización apropiada; no se usa una cifra sintética ni se simula gratuidad.
La API autenticada de tarifas Fal sigue sin acceso en esta sesión (401).

### Selección del usuario prevalece sobre el ahorro

Turbo queda excluido expresamente por Vidal, aunque tenga descuento. También
se retira Starlight Fast de las opciones nuevas. No se importan catálogos ni se
restablecen modelos ocultos. Las cifras Turbo de la evidencia son investigación,
no una recomendación ni una entrada visible.

### MiniMax: promoción comprobada, no tarifa permanente

Fuentes Fal consultadas el 27/10/2026:
- https://fal.ai/models/minimax/h3-max/image-to-video : 0,052 USD/s en 768P.
- https://fal.ai/models/minimax/h3-max-turbo/image-to-video : 0,026 USD/s en 768P.

Fal publica un 35% de descuento hasta el 31/10/2026. Los totales anteriores
multiplican por 8 segundos. Frente a las cotizaciones de Atlas a sus parámetros
por defecto (8 s, 768P), la reducción publicada es 31,58%. No se comparan los
base_price Atlas de 0,048/0,024 como si fueran totales de una solicitud.
Después de la promoción, Fal anuncia 0,08/0,04 USD/s: 0,64/0,32 USD para 8 s,
por encima de esas cotizaciones Atlas. Revalidar antes de recomendar por precio.
La descripción visible lleva fecha y caducidad; no se anuncia ahorro permanente.
Las configuraciones guardadas no se eliminan ni cambian de proveedor al vencer.

Es evidencia para una selección editorial, no garantía de factura ni equivalencia
bit a bit de inferencia. No se conecta como cotización dinámica a la política del
PR #10: faltan tarifas autenticadas Fal y evidencia de equivalencia versionada.
H3 normal a 2K/8 s cuesta 1,04 USD en ambos datos consultados; no se añade.

El adaptador MiniMax expone primer fotograma, duración 0,92–15 s, resolución
480P/768P/1080P, semilla y expansión del prompt (desactivada por defecto).
Valida prompt obligatorio de hasta 50000 caracteres. 1080P es refinamiento de
768P; no se compara con otro nivel Atlas. Último fotograma, audio de referencia
y reference-to-video requieren ampliar los roles de MediaInput y el mapeo del
adaptador antes de anunciarlos; no se descartan adjuntos extra silenciosamente.

## Integración

- `FalCuratedModels`: cuatro entradas con contratos revisados, campos tipados y sin
  precio inventado. Plantilla disponible a través del editor existente.
- `FalQueue`: conserva request ID + status_url + response_url en token persistente;
  admite solo HTTPS en queue.fal.run y valida relación URL/ID. No adivina rutas
  de polling a partir de aliases (el schema y la metadata de Topaz usan prefijos
  distintos). No acepta URLs de terceros con la clave Fal.
- `MediaEngine`: primero consulta estado; tras COMPLETED, obtiene resultado con
  GET separado. Un error COMPLETED no es éxito. Sin POST adicional ni reenvíos.
- `ConfigurableMediaProvider`: consume el GET de resultado, clasifica SVG, expone
  límite Recraft de 10000 caracteres y conserva el flujo existente de confirmación.
- Cola opt-in (`falQueue=false` por defecto) para no cambiar proveedores existentes.
  Núcleo y selección sincronizados a Desktop; el ejecutor concreto editado es Android.
- La comparación por plan queda silenciosa cuando no se ha configurado una fuente;
  se retiran los avisos de «evidencia insuficiente» que no pidió el usuario.

## Estado de entrega

Código preparado para configurar Fal con la clave del usuario en el editor.
No se ha guardado ninguna clave, activado servicio, instalado APK ni generado un
archivo multimedia. La selección no aparece mágicamente en la APK instalada:
requiere incorporar esta rama, compilar y configurar el proveedor.
No se ha probado una petición pagada, ni la vista previa SVG, ni subida de vídeos
largos (siguen sujetos a límites configurados del adaptador). La cancelación remota
sigue sin estar configurada y se comunica como no disponible, no como cancelada.

Verificación JVM en contenedor: suite agent-core **390 PASS, 4 omitidas,
0 fallos** (394 casos). Copia Desktop: compila y pasa **31 pruebas dirigidas,
0 fallos**. Kotlin 2.0.21, Gradle 8.9, JDK 21, target 17.
No compiladas APK ni UI Desktop; los cambios del editor/adaptador Android
requieren esa validación antes de instalar. Ocho tests nuevos: selección de cuatro,
contratos de payload, estado/resultado separados, persistencia, URLs hostiles,
errores del proveedor, rangos, y compatibilidad con proveedores síncronos.
