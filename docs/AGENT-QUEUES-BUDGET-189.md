# NEXUS Agent 1.8.9: colas y consumo

## Recuperacion de trabajos
Al iniciar, cualquier trabajo activo sin identificador remoto queda Por confirmar, conservando solicitud y metadatos. No se reenvia: una respuesta perdida podria haber sido cobrada. Los trabajos con identificador conservan su consulta.
Flow libera la dependencia cuando el puente ya termino, aunque copiar el resultado falle. Una dependencia no terminal deja de bloquear al siguiente encargo tras diez minutos de espera. Se registra released, no completed, para no inventar el resultado del anterior. Cada bloque conserva su identificador idempotente.
Tras treinta minutos de seguimiento remoto se realiza una ultima consulta: si sigue pendiente, el seguimiento se pausa de forma recuperable. El estado local usa FAILED con __tracking_paused y se presenta como Seguimiento pausado. Recuperar consulta el mismo id y reinicia el plazo; nunca genera de nuevo. Si la consulta falla por red, conserva el marcador y la opcion de recuperacion.

## Consumo del LLM
- Hasta ocho llamadas por mensaje, incluida la lectura inicial de documentos. Las llamadas auxiliares comparten el identificador del turno mediante el contexto de corrutina.
- Backend AWS directo: reserva persistente antes de inferencia, contabilizacion de usage al responder, reservas conservadas ante respuesta perdida o cierre de proceso. Los errores inequívocos de autenticacion/validacion liberan el importe; no reinician el numero de llamadas del turno.
- Valores iniciales editables en Ajustes del agente: 1 USD estimado por mensaje y 5 USD diarios (UTC). Topes adicionales: 150.000 tokens por mensaje y 500.000 diarios, sumando entrada y salida reservada.
- CountTokens usa los mismos mensajes, sistema y herramientas. Si falta para documentos, informa del permiso/compatibilidad sin pagar inferencia; texto usa una estimacion conservadora basada en bytes. Los documentos NO se estiman como tokens base64.
- Modelos conocidos Sonnet 4.6, Opus 4.6 y Haiku 4.5: precios globales de referencia con margen del 20%; sin descuento por cache. El contador es una estimacion local, no una factura ni un limite para toda la cuenta AWS. Nuevos modelos requieren configurar una tarifa.
- Documentos: una primera llamada sin herramientas extrae la informacion necesaria; las rondas posteriores reciben esa sintesis, no el archivo binario. Una lectura truncada no inicia acciones. Los originales se conservan en las referencias. Para consultar detalles no recogidos en la sintesis puede ser necesaria una nueva lectura.
- El backend proxy conserva sus limites del servidor. El presupuesto local nuevo se aplica al backend directo, incluido el adaptador de prompts.

## Fuentes
- https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html
- https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseTokensRequest.html
- https://www-cdn.anthropic.com/files/4zrzovbb/website/3684c2faafb97418665782cea0001f439f74b1d2.pdf

## Validacion
Pruebas de regresion sin llamadas de pago: persistencia y concurrencia de reservas, cambio de dia, respuesta perdida, registro corrupto, lectura unica de documentos, firma CountTokens, recuperacion de envio sin ID y desbloqueo de dependencias Flow. ASUSVIDAL: 23 pruebas pasadas (4 presupuesto, 10 orquestador, 5 Bedrock/CountTokens y 4 colas). APK AgentPreview compilado correctamente en ASUSVIDAL. La ejecucion conjunta inicial de pruebas y ambas variantes agoto Metaspace; se detuvo solo ese Gradle tras pasar las pruebas y se completo assembleAgentPreview en un proceso nuevo (un worker, prioridad nice 15, heap 1 GiB y Metaspace 768 MiB; 7m50s). 484 archivos de la copia aislada comparados con el codigo local: sin diferencias funcionales de contenido.


## Entrega al S25 (2026-10-11)
- Version instalada: 1.8.9-agent-preview, versionCode 33, paquete com.vidadomu.nexus.agent, Samsung SM-S938B.
- Actualizacion mediante adb install --no-streaming -r: Success; sin desinstalar ni borrar datos.
- Inicio de MainActivity: Status ok, proceso activo y 46 skills cargadas. Sin FATAL EXCEPTION en el tramo de arranque inspeccionado.
- SHA256 APK transferido desde ASUS: f14c763142e8043f0a9f86ee5896aec6ac1c4d75f87dbf41c82f6f8344949c49.
- SHA256 APK firmado y base.apk instalado (coinciden): 4b32ead18f5a7bb4f550523249eb276b2b11e884dc05656191e5c5afb48314bd.
- Certificado de la instalacion existente conservado: 8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127.
- Sin inferencias ni generaciones de pago de prueba. Conexion real a proveedores NO VALIDADA: el arranque registra UnknownHostException. El volcado visible corresponde a SystemUI/NotificationShade, por lo que no se afirma revision visual de Ajustes en el telefono.
