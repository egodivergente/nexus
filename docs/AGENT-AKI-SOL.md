# GPT-5.6 Sol por AKI — 11 de octubre de 2026

## Resultado comprobado

En el Samsung S25, Nexus `com.vidadomu.nexus.agent`, versión
`1.9.1-agent-preview` (35), usa backend `proxy`, endpoint local
`http://127.0.0.1:8787`, modelo exacto `openai.gpt-5.6-sol`.
La APK conserva la firma original; no se borraron sus datos ni claves.

Se envió desde la interfaz una petición de sólo lectura a `get_nexus_settings`.
El puente registró primero `stop=tool_use tools=get_nexus_settings`, luego
`stop=end_turn`; el chat persistió una respuesta Assistant con esa herramienta
y texto `openai.gpt-5.6-sol\n256`. Los 256 tokens eran el ajuste de diagnóstico,
no un resultado ficticio del puente. Después se comprobó en Ajustes la selección
del usuario de 32000 tokens de salida. No se generó multimedia ni se modificaron
archivos mediante el agente durante esta prueba.

Tras arrancar el proceso independiente se comprobaron dos turnos consecutivos:
`repite` completó la consulta real y devolvió `openai.gpt-5.6-sol\n32000`, y
`de q eres capaz` recibió después una respuesta Assistant `END_TURN`. Los fallos
`ConnectException` durante el traslado del servidor y un turno `Cancelado`
permanecen en el historial; no se borraron para ocultarlos.

También se observaron respuestas reales a mensajes del usuario desde la app,
incluida `Soy GPT-5.6 Sol de OpenAI, integrado como asistente de creación en Nexus.`
La comprobación separada de credenciales confirmó que acceso y secreto AWS siguen
coincidiendo con la pareja original, y `settingsOffline=false`.

## Implementación

- `agent-proxy/aki_proxy.py` adapta Converse a la CLI AKI ya autenticada. Envía
  el sistema, historial, definiciones de herramientas y resultados reales.
- Perfil dedicado `nexus-bridge`: herramientas del host denegadas, sin memoria ni
  skills heredadas. El modelo propone llamadas JSON; Nexus las valida y ejecuta
  mediante su orquestador y controles normales. Es adaptación JSON por prompt,
  no un endpoint nativo de function calling de AKI.
- Se valida estructura, nombre de herramienta ofrecida, IDs únicos y stop reason.
  No hay alias silenciosos de modelos ni fallback a AWS directo.
- Sólo loopback, token de dispositivo cifrado en Nexus y hash en el servidor;
  sin CORS abierto, prompts/credenciales en logs ni stderr reenviado a la app.
- HTTP/1.1 mantiene la conexión de OkHttp entre rondas. Antes de ese cambio se
  observó `SocketException`; la prueba completa de dos rondas pasó después.
- La CLI no recibe claves AWS ni acceso a perfiles de credenciales de AWS; emplea
  la sesión existente de AKI. Su cuota/facturación depende de ese servicio, no
  se afirma que un precio cero haya sido verificado. El proxy registra uso sin
  inventar una tarifa; se habilita explícitamente `allow_unpriced_models`.

## Límite solicitado por el usuario

El consumo local de Nexus llegó a 495723 de 500000 tokens. El usuario pidió
expresamente quitar el límite. Sólo para el puente AKI se configura
`daily_token_budget: null`; los valores por defecto del proxy siguen limitados,
el registro previo se conserva y los límites de gasto AWS no se modifican.
Se mantienen autenticación, modelo permitido, límite por respuesta, tamaño de
petición, rate limit y ejecución serial. No se enviaron nuevas llamadas AWS de
pago para esta integración.

## Despliegue y límites conocidos

Código y configuración privada en `/root/.local/share/nexus-aki/` del Debian del
S25. Proceso separado de la sesión Codex mediante Termux/proot-distro. El acceso
directo `Arrancar Nexus Sol` permite arrancarlo si Android lo detiene. No es un
servicio Android integrado ni se garantiza supervivencia a reinicios del móvil.
Requiere una sesión AKI válida.

Este primer puente admite **texto y herramientas**, pero rechaza explícitamente
adjuntos y bloques de razonamiento firmado; no los descarta ni simula haberlos
leído. Para conversaciones previas firmadas, usar una nueva conversación sin
borrar el historial. No se declara recuperado el soporte completo de PDF/imagen.
La CLI no expone un maxTokens nativo: la brevedad se pide al modelo; la cuota del
servicio AKI sigue siendo externa. No usar este adaptador como límite de gasto
garantizado para un servicio facturado por token.

Pruebas Python: 28 aprobadas (proxy y adaptador); las 396 pruebas Android de la
corrección previa siguen correspondiendo a la APK instalada. Secretos, helpers,
firmas, conversaciones y evidencia privada quedan fuera de Git.
