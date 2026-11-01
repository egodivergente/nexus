# Selección de generadores de vídeo · 2026-11-01 (vc176, 1.9.140-agent)

Encargo de Vidal: recortar el catálogo de vídeo a una lista cerrada, sin texto→vídeo en ninguna parte, con las herramientas en Herramientas
y enrutado por precio que respeta la regla BytePlus. Se reutilizan catálogo, plantillas, formularios, proveedores, precios y enrutado existentes.

Luma: descartado por el usuario (2026-11-01).

## Cómo funciona

- `data/repository/CuratedVideoSelection.kt` contiene la lista blanca por clave exacta `proveedor:id` (también `providerModelId`
  y sin el prefijo `crun/`). Solo afecta al VÍDEO de `atlas_cloud`, `crun`, `runway` y `usr_byteplus-media`; imagen, audio, chat,
  Fal y plantillas propias no cambian.
- `ModelCatalogRepository.publishCatalog` filtra solo la lista ofrecida (`models`). `allModels` sigue completo: historial, borradores,
  «repetir» y búsquedas por id siguen funcionando con modelos retirados. No se borran trabajos ni preferencias; las ocultaciones
  y rutas ocultas se aplican igual que antes.
- Escape local sin interfaz: `curated_video_v1=false` en las preferencias del catálogo restaura el catálogo de vídeo completo.
- Ningún generador conservado se envía solo con texto: `creationReadiness` (usado por la pantalla, la comparación, el agente
  y `GenerationDispatcher`) exige una imagen o un vídeo de entrada. Cubre las rutas cuya API admite prompt sin imagen
  (Crun `kling-v3-pro` y las `*-r2v` de Crun). La comparación sin referencias ya no ofrece estos generadores.
- Las rutas compatibles de un mismo modelo siguen agrupadas en una sola tarjeta (agrupación existente por familia/versión).

## Generadores conservados (71 claves)

| Modelo | Atlas | Crun | Runway | BytePlus |
|---|---|---|---|---|
| Seedance 2.0 Mini / 2.0 / 2.0 Fast / 2.5 | i2v y referencias | i2v y r2v | i2v | 2.0, 2.0 Fast y 2.5 i2v (sin Mini) |
| Kling 4.0 | i2v y referencias | | | |
| Kling 3.0 Pro | i2v | `kling-v3-pro` (con imagen obligatoria) | | |
| Kling 3.0 Omni (Atlas lo publica como Kling Video O3 Pro/Std/4K) | i2v y referencias (4K solo i2v) | `kling/v3-omni-i2v`, `-r2v` | | |
| Vidu Q4 Preview | i2v y referencias | | | |
| Vidu Q3 Pro | i2v e inicio/fin | i2v | | |
| WAN 3.0 (sin Prime) | i2v y referencias | i2v y r2v | i2v | |
| MiniMax H3 (2K nativo) | i2v y referencias | i2v y r2v | Hailuo3 i2v | |
| MiniMax H3 Developer | solo referencias | | | |
| PixVerse V6 y C1 | i2v, inicio/fin y referencias | V6 i2v y r2v | | |
| PixVerse V6 Max | i2v | | | |
| Grok Imagine Video 1.5 | i2v | | i2v | |
| LTX 2.5 Pro y Fast | i2v | | | |
| HappyHorse 1.1 | solo referencias | r2v | | |
| FLUX 3 | i2v e inicio/fin | | | |
| Runway Gen-4.5 | | | i2v | |
| Agnes Video 2.5 | | i2v y r2v | | |
| NVIDIA Cosmos 3 Super | i2v | | | |

## Herramientas (33 claves, siempre en Herramientas)

- Movimiento y pose: Kling 3.0 Std/Pro Motion Control (Atlas, Crun), WAN 2.2 Animate Move y Mix/Replace (Atlas, Crun), Runway Act-Two.
- Edición de vídeo: WAN 2.7 Video Edit y HappyHorse 1.0 Video Edit (Atlas, Crun), FLUX 3 Edit Video, Crun Face Swap múltiple
  y Watermark Remove, Runway Aleph2.
- Extender vídeo: FLUX 3 Extend, PixVerse V6 Extend.
- Avatares y personajes (nueva): LTX 2.5 Pro/Fast Audio-to-Video, OmniHuman 2.0, HeyGen Avatar 5 Digital Twin.
- Sincronización labial: Sync Lipsync v3.
- Upscale vídeo: BytePlus Video Upscaler (Atlas), Runway Magnific Creative, Enhance Frame Rate y Ruby HDR.
- Publicidad y producto (nueva): Atlas Studio TVC Maker, Food Motion, Try-on Video, UGC Ads y Video Clone (`trend-remix`).

## Eliminados de nuevas generaciones (272 rutas en el catálogo del S25)

Todo texto→vídeo; Seedance 1.x y `draft-complete`; Luma; Kling 2.x, 3.0 Std/4K/Turbo, O1, Kling 4.0 Video Edit, O3 Video Edit,
avatares y plantillas Kling; Vidu Q1, Q2, Q3 Turbo/Mix y 2.0; WAN 2.2 Turbo, 2.5, 2.6, 2.7 y 3.0 Prime; MiniMax Hailuo 02/2.3,
H3 Fast/Max/Max Turbo y H3 Developer i2v; Gemini Omni; Grok Imagine (original, Developer, Lite, referencias, edición y extensión);
LTX 2.3; HappyHorse 1.0 y 1.1 i2v; FLUX 3 Keyframes; OmniHuman 1.5; VEED Lipsync; Tencent y AtlasCloud Upscaler;
AtlasCloud Video Extend, Shot Reframe y Template Video Clone; Crun DreamActor, GIF Face Swap, Face Swap simple, Video Enhance,
Vidu Lip Sync, Runway Gen4/Gen4 Aleph vía Crun y Grok 1.5 Preview; en Runway, Gen4 Turbo, H3 Max, HappyHorse 1.0, WAN 3 Prime
y todas las rutas `t2v`/`v2v`.

## Enrutado y precio

- Automático: elige la ruta compatible más barata con la misma configuración (lógica existente de `AutomaticGenerationRouting`
  y `ExactRouteCompatibility`; no se añaden alias nuevos ni conversiones de créditos).
- Regla BytePlus para Seedance (`routing/SeedanceBytePlusPolicy.kt`): en automático cualquier ruta que llegue a BytePlus va al final
  aunque sea más barata; esto incluye Crun con `direct_byteplus` o `byteplus_fallback` activos, que no pueden saltarse la regla.
  Elegir BytePlus a mano lo pone primero: no se busca alternativa automática.
- Elección manual de proveedor: sigue disponible en el selector de rutas del modelo.
- Sin reenvíos cuando la aceptación es dudosa y sin superar el coste aceptado (`RoutingSubmissionPolicy` existente).
- La auditoría externa de precios (`auditoria-precios-video/`) no se aplica automáticamente.

## Bloqueos y límites reales

- DreamActor M2 sin confirmar: Crun publica `bytedance/dreamactor` sin versión; queda fuera.
- Shot Reframe: el contrato de Atlas devuelve NoSuchKey; queda fuera.
- FLUX 3 Keyframes: necesita un editor de fotogramas clave que la app no tiene; queda fuera.
- Atlas Studio TVC Maker, Food Motion, Try-on y Video Clone piden `product_image` como lista; el formulario actual los muestra como
  no compatibles hasta tener soporte de listas en ese campo. UGC Ads ya tiene ranura de imagen de producto.
- BytePlus: no publica Seedance Mini ni último fotograma, y no tiene cotización automática (`ProviderRouteQuotes` no cotiza proveedores
  propios), así que nunca entra en el automático; solo por elección manual.
- Runway Seedance/WAN/Hailuo i2v no admiten último fotograma.
- Crun no cotiza en automático mientras `routing.creditUsd` sea 0 (valor por defecto): no se inventa la conversión de créditos.
- Crun Grok Imagine 1.5 es «preview»; queda fuera.
