# Auditoría de Google Flow y automatización

Fecha: 2026-10-08. Alcance: NEXUS Studio en Windows, host loopback en ejecución, puente MCP instalado y fuentes actuales. Solo diagnóstico, comprobaciones sin generación y pruebas offline. No se compila, instala ni modifica la integración. No se consumen créditos de generación.

## Dictamen

Existe una integración real con la cuenta propia de Flow y defensas sólidas contra la repetición de una intención. La automatización necesita reforzar la cola compartida, la recuperación al reiniciar y la coherencia del diagnóstico. No se ha probado una generación completa nueva durante esta auditoría; la disponibilidad para generar ahora no queda demostrada por los archivos anteriores ni por una sesión conectada.

## Evidencias actuales

- Host vivo: `/health` responde `Nexus Studio`; proceso Python del workspace. El host usa los archivos de interfaz del workspace; sus módulos Python cargados no quedan actualizados automáticamente al editar fuentes.
- Runtime instalado: `gflow-cli 0.82.1`. Coinciden por SHA-256 siete módulos con sus fuentes: bridge_tools, flow_session, flow_quote, flow_inputs, flow_capabilities, flow_voices y asset_process.
- NEXUS devuelve `connected` con una comprobación reciente. Al caducar el intervalo se inició `refreshing` y se comprobó después una nueva respuesta `connected`, con salud de aproximadamente 67 segundos de antigüedad. La renovación no envió generaciones.
- La sonda MCP `gflow_auth_status` devuelve `verification_error`, 503. Es un fallo de comprobación, no evidencia suficiente de sesión caducada.
- La consulta MCP de créditos falla por ausencia de access_token en la ruta antigua `/fx/api/auth/session`. No se obtiene saldo; no convertirlo en cero ni usar ese error como motivo para reinstalar o cerrar la sesión.
- Base local: 13 trabajos Flow completados. Se comprobaron 13 archivos originales bajo el almacén permitido y sus SHA-256: cero discrepancias. No se exportan prompts, archivos, cookies, cuentas ni URLs privadas.
- Nueve registros conservan `model_confirmed=false`; el nombre pedido no equivale a atribución independiente del proveedor.

## Hallazgos ordenados

### F1 — Alta: el límite Flow de la interfaz no cubre la generación completa

`desktop/ui/studio-batch.js` incrementa `flowInFlight` alrededor de `dispatch`, pero lo decrementa cuando ese envío devuelve. `desktop/flow_provider.py:submit` inicia un thread y devuelve el estado local sin esperar a que termine el trabajo. Otra solicitud del mismo encargo puede empezar mientras la primera sigue en WORKERS. Otros clientes/ventanas tampoco comparten la variable JavaScript.

Reproducción offline: dos ids distintos, cotización simulada y threads interceptados producen dos reservas y dos inicios de worker. Cero llamadas externas. Esto demuestra concurrencia en el adaptador, no dos generaciones simultáneas reales. La concurrencia uno del runtime y ProfileLease ofrecen protección adicional; no constituyen una cola persistente del encargo en NEXUS y la contención puede introducir esperas o fallos.

Propuesta: cola durable central por proveedor/perfil, con un consumidor Flow. Mantener la reserva y el mismo request_id; liberar el puesto al terminar/reconciliar la operación, no al aceptar el envío. Pausar/cancelar debe afectar solo a tareas no enviadas.

### F2 — Alta: la recuperación tras cierre depende de una acción manual

El arranque de `app.js` marca SENDING como FAILED e incierto. `workspace.js` solo sondea trabajos con remoteId y estado activo. `flow_provider.status` consulta SQLite; no llama a Google. Si desaparece el worker, pasa a reconciliación. `flow-ux.js` sí dispone de recuperación explícita con el mismo id.

Propuesta: al arrancar, reconciliar reservas interrumpidas mediante `flow_get_result`, con límite de frecuencia y sin enviar generaciones. Conservar separados: no enviado, aceptado, resultado incierto y fallo confirmado. Los bloques futuros nunca enviados deben permanecer detenidos; recuperar no debe darles permiso para generar.

### F3 — Media: dos diagnósticos de sesión pueden discrepar

La sonda upstream falla mientras el estado del host informa connected. NEXUS usa un comprobador propio que exige cookies, interfaz de Flow lista y respuesta de myaccount, con caché de cinco minutos. Es una prueba distinta de la sonda MCP, y ninguna implica por sí sola una generación completa.

Propuesta: unificar el contrato de salud para NEXUS y MCP e incluir origen, fecha de comprobación, estado de renovación y causa sanitizada. Mantener los errores de saldo separados del acceso al editor.

### F4 — Media: se pierde la advertencia de atribución del modelo

El puente persiste requested_model/model_confirmed. `flow_provider.status` no los incluye en su respuesta y `app.js:parseResponse` no los conserva. Las fichas muestran el modelo del plan; falta informar cuándo la atribución del proveedor no está confirmada.

Propuesta: propagar ambos campos hasta historial, visor y exportación. Etiqueta visible «Modelo solicitado» cuando no exista confirmación; para bloques, conservar la evidencia por salida.

### F5 — Media: contrato de duración y documentación pendientes de actualizar

El catálogo y normalize permiten 4/6/8 segundos para Veo Quality en texto/fotogramas. La ayuda oficial consultada enumera Quality de 8 segundos. Es una discrepancia de contrato/documentación que debe contrastarse con los controles de la cuenta antes de permitir esas duraciones; no se prueba aquí un envío 4/6s.

La README conserva afirmaciones históricas de dos/cinco modelos y exclusión de Lite aunque actualmente se admite Veo Lite; Nano Banana Lite sigue prohibido. También mezcla estado comprobado el día 6 con funcionalidades posteriores.

Propuesta: matriz única modelo/modalidad/parámetros, validada contra la cuenta, y separar pruebas offline de pruebas completas por modelo.

### F6 — Media: cobertura antigua roja y límites incompletos

`test-flow-provider.py` falla porque exige que todos los modelos con prompt sean texto-a-imagen o texto-a-vídeo; ahora existen edición, fotogramas y referencias. Las otras seis pruebas de ese archivo pasan. `test-flow-integration.py` requiere una sesión de QA privada y espera cinco modelos: no se ejecutó como prueba fiable del catálogo actual.

El contrato de cantidad admite cualquier entero positivo y el catálogo no fija un máximo. El editor de encargos limita solicitudes, pero cantidad nativa por solicitud es otra dimensión. Un encargo puede expandirse a muchos bloques. No hay presupuesto agregado obligatorio en el proveedor.

Propuesta: actualizar pruebas por capacidades y añadir límite de resultados/bloques y presupuesto configurable, validado en host además de la interfaz.

### F7 — Baja: telemetría insuficiente para diagnosticar Flow

La telemetría actual mide RPC local y agrupa operaciones no permitidas como `other`. No distingue flowQuote, flowRecover, flowAssetOptions o flowConnect. `flowSubmit` mide aceptación/cotización local, no la generación completa, porque el worker sigue en segundo plano.

Propuesta: medir espera de cola, renovación, cotización, aceptación, generación, recuperación y descarga por request_id, con eventos sanitizados. Separar latencia local de tiempo total y evitar registrar prompts/URLs.

## Controles que funcionan

- Reservas SQLite antes del transporte, ids estables y rechazo de cambios de parámetros con un id existente.
- Recuperación mediante consulta, sin reenvío automático de generación.
- Bloques secuenciales de hasta cuatro, checkpoint antes del envío y detención ante resultado incierto.
- Cotización por configuración y vigencia de cinco minutos; el host vuelve a consultar antes de aceptar. Precio desconocido no se convierte a cero.
- Referencias locales con ids opacos, congelación en el puente y validación de integridad.
- Resultados restringidos al almacén, extensión admitida y SHA-256 antes de copiar a la biblioteca.
- Host loopback, comprobaciones Origin/token y credenciales locales protegidas. Esto no constituye un pentest completo.

## Validación ejecutada

| Prueba | Resultado |
|---|---|
| Suite offline tools/flow-bridge/test_*.py | 44/44 PASS |
| test-flow-recovery.py | 10/10 PASS |
| test-flow-model-selection.py | 3/3 PASS |
| test-flow-modalities.py | 5/5 PASS |
| test-flow-edit.py | 3/3 PASS |
| test-flow-blocks.py | 5/5 PASS |
| test-flow-session-renewal.py | 15/15 PASS |
| test-flow-provider.py | 6 PASS, 1 FAIL por expectativa antigua |
| test-flow-auto-availability.js | PASS |
| test-flow-agent-result.js | PASS |
| test-flow-model-arguments.js | PASS |
| test-studio-batch.js | PASS; no prueba de exclusión durante generación real |
| 13 archivos existentes | SHA-256 válido |
| Nueva generación completa / todos los modelos / Android | NO PROBADO |

Total Python: 91 PASS, 1 FAIL. Las pruebas usan mocks/temp donde corresponde. No se ejecutaron scripts verify-flow-live ni otros verificadores con generaciones.

## Prioridad recomendada

1. Cola compartida Flow y reconciliación automática sin reenvío.
2. Salud coherente, atribución del modelo y contrato de parámetros vigente.
3. Presupuesto/límites de bloques y telemetría por fases.
4. Actualización de cobertura y matriz de verificación completa, con prueba de cuenta solo cuando esté autorizada.

Fuentes externas consultadas: [Google: créditos y costes](https://support.google.com/flow/answer/16526234?hl=en), [repositorio upstream gflow-cli](https://github.com/ffroliva/gflow-cli). El upstream se identifica como wrapper no oficial y alpha. La consulta web no verifica el comportamiento de esta cuenta.
