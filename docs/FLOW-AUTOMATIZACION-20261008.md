# Flow: ejecución del plan y operación

Actualización aplicada el 2026-10-08 en NEXUS Desktop. No se compiló ni empaquetó APK/EXE; no se lanzaron generaciones ni mejoras de pago.

## Comportamiento final

- Cola persistente en `flow_queue`, junto a la base de trabajos existente. Un consumidor por directorio y un bloqueo de perfil entre procesos/directorios cubren la ejecución completa. Cotizaciones del host y renovación usan el mismo bloqueo; el puente mantiene además su ProfileLease.
- Tras un resultado incierto, la cola pausa nuevos envíos. La recuperación lee el identificador existente. Los bloques futuros no enviados nunca se generan al recuperar.
- Al arrancar, reservas interrumpidas se reconcilian mediante consulta. Las solicitudes pendientes de envío se convierten en «No enviado», sin renovar su autorización de gasto. La recuperación continúa aunque la cola de envíos esté pausada.
- Pausar afecta a pendientes; cancelar solo admite pendientes de envío. Un id cancelado o ya reservado no se reutiliza para una nueva generación.
- Antes de ejecutar, se vuelve a validar precio y límites. Si cambian, no se envía. No se reintenta automáticamente un transporte incierto.
- Quality ofrece 8 s. El contrato fija máximo 120 resultados por solicitud y bloques de hasta cuatro. Los controles del servidor también limitan resultados pendientes totales y solicitudes pendientes.
- «Control Flow» permite ajustar límites, presupuesto pendiente, permiso explícito para importe desconocido, pausa y cancelación. Por defecto: 120 resultados/solicitud, 120 resultados pendientes totales, 120 solicitudes pendientes, 1000 créditos pendientes y coste desconocido desautorizado. El importe desconocido no se considera cero. El presupuesto es un límite de compromisos pendientes/activos/inciertos; no es saldo ni acumulado mensual.
- Modelo solicitado y confirmación del proveedor se conservan en el historial. El visor distingue atribución no confirmada; no promete que el nombre solicitado haya sido confirmado por Google.
- Salud del host y nuevas conexiones MCP usan la comprobación del editor, con fecha, fuente y caché explícitas. La consulta de saldo queda separada y sigue sin funcionar para esta cuenta por la ruta antigua.

## Telemetría

La telemetría general reconoce cotización, recuperación, mejora, acceso, cola, política y cancelación de Flow. «Control Flow» muestra y exporta metadatos sanitizados con espera en cola, cotización inicial, ejecución/recuperación, copia local y total.

Ejecución incluye revalidar el precio, el trabajo en Flow y la descarga del puente. Copia local mide validación de integridad e incorporación a NEXUS, y corresponde a la última consulta con resultados. No se presenta como duración exclusiva del modelo ni latencia de red. No se exportan prompts, claves, cuentas, archivos ni URLs.

## Validación

- 17 pruebas nuevas de automatización: persistencia, concurrencia real de threads, bloqueo entre procesos, dos directorios, pausa/cancelación, reinicio, presupuesto, límites agregados, precios cambiados, recuperación parcial, privacidad y registro/serialización MCP, archivo dañado y excepción después del transporte.
- 44 pruebas offline del puente; 10 de recuperación; 7 del proveedor; 3 de selección; 5 de modalidades; 3 de edición; 5 de bloques; 15 de renovación. Pasan las suites ejecutadas, incluida la expectativa antigua corregida de capacidades del catálogo.
- Pruebas JavaScript de telemetría, resultados Flow, argumentos y encargos: PASS. Regresión de galería/editor a 1360/760/480/1280 px: PASS.
- QA nueva de «Control Flow» a 1360/480 px: límites, pausa, cancelación y atribución; sin errores JavaScript ni operaciones remotas. Datos de interfaz simulados.
- Host real aislado: política guardada/consultada y cantidad inválida rechazada antes de cotizar o generar.
- Host instalado actualizado en el mismo puerto: 98 trabajos preservados, 17 entradas Flow y política de servidor vigente. 13 resultados Flow mantienen sus SHA-256. La cola nativa conserva 9 completados y 2 fallidos: no aumentó por estas pruebas.
- Runtime actualizado con backup de los módulos sustituidos y verificación SHA-256. Conexión MCP nueva: `connected`, `source=flow_editor`. Las conexiones MCP ya abiertas conservan la implementación antigua hasta reconectar; la herramienta de la conversación actual todavía devuelve su diagnóstico upstream 503. No se reinicia Codex durante su propia tarea.

El host se reinició después de verificar ausencia de generaciones Flow activas y disponibilidad del asistente. Cinco trabajos Atlas antiguos con ids remotos se conservaron. No se cerraron navegadores, perfiles ni servicios ajenos.

## Uso

Recargar NEXUS para cargar el JavaScript nuevo. Abrir «Control Flow» para revisar límites y tiempos. Si la cola pausa por incertidumbre, recuperar el resultado existente antes de decidir si se reanudan otras solicitudes; nunca crear otro id para repetir ese envío incierto. Las conexiones de Codex/MCP abiertas antes de esta actualización deben reconectarse para usar el nuevo comprobador.

## Límites de la validación

No se probó una nueva generación completa ni todos los modelos contra Google durante esta intervención. La concurrencia, reinicios y fallos se verificaron con transporte interceptado, además de sondas reales de salud y pruebas del host. Android no se modificó ni probó. La tarifa de Quality se contrastó con la ayuda oficial en la auditoría; 4/6 s se retiraron de forma conservadora, no mediante una generación real.

Referencias: [auditoría](AUDITORIA-FLOW-AUTOMATIZACION-20261008.md), [plan](PLAN-FLOW-AUTOMATIZACION-20261008.md), [Google: créditos y parámetros](https://support.google.com/flow/answer/16526234?hl=en).
