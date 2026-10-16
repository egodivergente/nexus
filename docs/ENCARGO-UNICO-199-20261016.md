# 1.9.10-agent — Encargo único, agente consciente de su interfaz y contrato Tencent (2026-10-16)

Rama: `fix/encargo-unico-agente-ui` (desde `main` + 1.9.7, 1.9.8 y la 1.9.9 de Historial,
`codex/android-ux-polish-20261016` @ `4512d93`). Android `1.9.10-agent-preview` / `versionCode 44`.

## Causas raíz encontradas

| Síntoma | Causa en el código (antes) |
|---|---|
| Tarjetas duplicadas y triplicadas | `UiEvent.GenerationSubmitted` añadía SIEMPRE otra `PlanCard`; cada `prepare_generation` con un parámetro distinto creaba plan y tarjeta nuevos; los chats guardados conservaban todas. |
| Botones GENERAR repetidos | Un botón por tarjeta duplicada + barra de lote con otro GENERAR aunque hubiera un solo encargo. |
| El agente dice «listo» y la app muestra «falló» | Los errores de envío solo llegaban a `PlanUi.error` (UI). El agente nunca recibía pulsaciones, rechazos ni el estado pintado. |
| Tencent: `resolution is required (720p, 1080p, 1440p or 4k)` | El schema publicado no declara `resolution`, pero el backend lo exige. Además `short=0` (default) es inválido con plantillas 1080P/2K/4K (`x-depends-on` ignorado). |
| Prompt mezclado con campos de API | `promptRequired` no se leía del schema (siempre `true`) y `AtlasGenerationPayload` enviaba `prompt` aunque el schema no lo tenga. |
| Coste inventado para 4K | Se usaba la tarifa base del catálogo (0,004 $/s, la de 720P) para cualquier plantilla; 4K cuesta 0,036542 $/s. |

## Qué cambia

**Encargos (agent-core `orders/OrderBook`)** — fuente única de verdad compartida por UI, agente y envío.
- Una tarjeta por encargo con `order_id` persistente; los reintentos, correcciones y recargas la actualizan en su sitio.
- Estados: borrador → pendiente de confirmación → enviando → procesando → completado / error / incierto / descartado,
  con transiciones validadas.
- Idempotencia: la clave `encargo:intento:huella` se reserva antes del ticket; una segunda pulsación
  (también concurrente) se rechaza sin enviar. Un envío incierto nunca se reabre ni se reintenta solo;
  solo el estado verificado del trabajo lo resuelve.
- Al reiniciar, un envío a medias pasa a incierto.

**Agente ↔ UI**
- Cada turno recibe «[Estado real de la interfaz…]»: encargos, lo que muestra cada tarjeta (la UI lo declara),
  pulsaciones del usuario, rechazos y fallos con diagnóstico. Herramienta `get_ui_state` para el detalle.
- `update_plan(order_id)` corrige en el mismo encargo, también tras un error, conservando vídeo, referencias y configuración.
- `ClaimVerifier`: si la respuesta afirma «enviado/generando/terminado» sin respaldo, el agente la rehace una vez
  con el estado real; lo no corregido se muestra como «Estado verificado».
- `ErrorDiagnosis`: traduce el error literal del proveedor a causa y corrección aplicable usando solo el schema.

**Validación y payload (app)**
- `NativeModelSchema`: `promptAccepted`/`promptRequired` según el schema; rangos `x-depends-on`;
  `reconcile()` omite defaults incompatibles y añade campos derivados. Formulario, agente y payload usan el mismo contrato.
- `AtlasContractOverrides`: `resolution` deducida del sufijo de `template` (2k = 1440p) para `tencent/video/upscaler`.
- `TieredTariffs`: tarifa por nivel y factor de fps publicados en el README oficial; total con la duración real del
  vídeo (leída al adjuntar) o, si falta, solo la tarifa por segundo, sin inventar total.

**Interfaz**
- Tarjeta compacta: modelo, proveedor que factura, coste, estado y UN botón (Generar · coste / Enviando… /
  Aplicar corrección / Reintentar / Corregir con el agente / Ver resultado / Ver en Historial). Detalles técnicos plegados.
- La barra de lote solo aparece con 2+ encargos pendientes; sin casillas por tarjeta.
- Al abrir un chat antiguo se deduplican las tarjetas y se colapsan encargos abiertos con el mismo modelo y archivos.
- Skill `tencent-video-upscaler`: sin prompt, plantilla por tipo de material.

## Validación

- Compilación en AXELPC: `:app:assembleAgentPreview` correcta. APK refirmada con el `debug.keystore` del ASUS
  (certificado `8b75db0f…`, el mismo de la app instalada) para actualizar sin desinstalar.
- Pruebas: agent-core 153/153 y app (agentPreview) 293/293, sin red de pago. Destacan `TencentUpscaleFlowTest`
  (flujo de chat completo con LLM de guion: el agente afirma un envío falso, se autocorrige, recibe el rechazo real y
  corrige el mismo encargo; doble pulsación concurrente = un envío), `OrderBookTest`, `TencentContractTest`
  (schema público real) y `OrderCardLogicTest` (datos reales del S25).
- S25 Ultra (SM-S938B), con los datos del usuario conservados: el chat real con tres envíos de Tencent fallidos se
  mostró como UNA tarjeta («Intento 3»), con el error literal, el diagnóstico y «Aplicar corrección».
- Envío real hecho por el usuario (no por Nexus ni por el agente): tras «Aplicar corrección» y su pulsación en
  Generar, Atlas aceptó el trabajo con `resolution=4k` y ya no devolvió «resolution is required»; el trabajo falló
  con `fps must be an integer between 1 and 100`. Esta entrega añade `fps` medido del vídeo original
  (comprobado en pruebas; pendiente de un envío real).
- Incidencia corregida: los mensajes al agente quedaban sin respuesta porque `(?U)` en `ClaimVerifier` no existe en
  el motor de regex de Android (ICU) y la app se cerraba al verificar la respuesta. Se quitó y una prueba vigila
  `(?U)` en agent-core y en la app.
- Integración vc44 (1.9.10): incluye la rama de Historial (5 pestañas, «Abrir Historial») y la Biblioteca sin
  etiquetas de origen en las miniaturas. Instalada en el S25 a las 05:56 con datos conservados; el chat abre sin
  cierres y la pestaña Historial funciona.
- Evidencia real en `nexus.db` del S25: cuatro trabajos fallaron sin `resolution`; uno con `resolution=4k` y sin
  `fps` falló por `fps`; uno con `resolution=4k` + `fps=25` y sin prompt se COMPLETÓ (0,33 €).
- Pendiente en el teléfono: una respuesta del agente tras el arreglo de `(?U)` (no se forzó Nexus a primer plano
  mientras el usuario usaba otra app).
- Pixel 8 Pro: offline en Tailscale desde hace 8 días; no probado.

## Pendiente conocido

- Windows (actualizado en 1.9.18): `desktop/agent/core` se sincronizó con `desktop/tools/sync-agent-core.py`
  (148 ficheros) y compila con 165 pruebas en verde, igual que `agent-core`. El host de escritorio
  (`DesktopAgent.kt`) solo pinta el progreso (`UiEvent.Info`): **no muestra tarjetas de encargo ni botones de
  respuesta rápida**. Por eso `suggest_replies` se quita de su caja de herramientas. El agente de escritorio ya usa
  el libro de encargos (idempotencia, sin reenvíos inciertos) y los contratos nuevos, pero no los dibuja; pintarlos
  sigue pendiente y NO se ha probado a mano en el EliteBook (no se instaló nada allí).
- No se ha hecho ninguna generación real de pago: el campo `resolution` que exige Atlas se deriva del error real
  observado; su aceptación por el backend se confirmará en el primer envío del usuario.
