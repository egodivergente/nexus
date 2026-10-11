# Corrección del presupuesto previo a Bedrock (1.9.1)

## Causa comprobada

El chat podía mostrar `Limite diario del agente alcanzado` antes de enviar
Converse aunque una prueba aislada del proveedor respondiera correctamente.
La capa de presupuesto llama a CountTokens con el mismo `LlmRequest` del agente.
El proveedor utilizaba el perfil `us.anthropic.claude-sonnet-4-6` en ambas rutas.

Desde el S25, CountTokens con ese perfil devuelve `ValidationException: The
provided model doesn't support counting tokens.` La misma entrada con
`anthropic.claude-sonnet-4-6` devuelve un conteo válido. Al faltar el conteo,
el presupuesto reserva conservadoramente el tamaño del JSON en bytes más un
margen, y puede rechazar un turno que sí cabe con el conteo real.

## Cambio limitado

- CountTokens resuelve los perfiles geográficos/globales de Anthropic al ID
  base; Converse conserva el perfil seleccionado y la región.
- No se borran consumos ni reservas, ni se elevan los topes existentes.
- Los documentos siguen bloqueados cuando no se puede obtener su conteo.
- Se añaden regresiones de resolución de IDs y del recorrido
  `BudgetedLlmProvider -> CountTokens -> Converse -> liquidación del consumo`.
- Versión Android: `1.9.1-agent-preview`, versionCode 35.

La inspección privada de credenciales confirmó la pareja original antes y
después de guardar ajustes/cambiar modelo y en un proceso nuevo. El formulario
con secreto vacío no lo sustituye. No se reprodujo la alteración histórica del
secreto; no se atribuye a concurrencia sin evidencia ni se cambia su almacenamiento.

## Verificación

Compilación y pruebas en ASUS desde una copia aislada del commit
`cffcfccf66653d6f058c91c48b57776a13567e1d` más los cambios de esta tarea.
La captura privada del primer request real del orquestador, sin inferencia,
incluye las 50 herramientas y permite contar el contexto completo.
Compilación terminada en ASUS: 396 pruebas aprobadas (120 core y 276 app).
La APK se volvió a firmar con el certificado original y se instaló en el S25
con `install -r -t`, conservando los datos. Se verificaron versionCode 35 y
`1.9.1-agent-preview` en el dispositivo. La respuesta final desde la interfaz
queda pendiente: una captura de request no demuestra una respuesta del agente.

El usuario cambió después la elección a GPT-5.6 Sol mediante su acceso existente
de AKI. Una llamada CLI a `openai.gpt-5.6-sol`, sin variables de credenciales AWS,
respondió `OK` (2991 tokens de entrada, 5 de salida, ninguna herramienta).
Esto no demuestra integración con Nexus: `BudgetedLlmProvider.rates` todavía
rechaza ese identificador por falta de tarifa, y el protocolo del puente AKI
existente no implementa el intercambio de herramientas de Converse. No se ha
sustituido el modelo real por un alias Claude ni desactivado el presupuesto.

Por petición expresa posterior del usuario, se repitió una única llamada mínima
Converse por AWS CLI desde el S25, con las credenciales existentes, región
`us-east-1`, perfil `us.anthropic.claude-sonnet-5-5` y máximo 16 tokens de salida.
AWS respondió `AccessDeniedException: anthropic.claude-sonnet-5-5 is not available
for this account`, remitiendo a AWS Sales. Confirma la restricción descrita en
[AGENT-CLAUDE-55-20261011.md](AGENT-CLAUDE-55-20261011.md); no es una consulta de
disponibilidad. No se contactó con terceros ni se repitió la activación de acuerdos.

## Referencias oficiales consultadas el 11 de octubre de 2026

- [CountTokens: modelId de modelo base](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html).
- [Sonnet 4.6: IDs, perfiles, región y soporte de conteo](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-anthropic-claude-sonnet-4-6.html).
- [Sonnet 5.5: perfiles y limitaciones de CountTokens por endpoint](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-anthropic-claude-sonnet-5-5.html).

Credenciales, certificados, formularios, conversaciones y artefactos de diagnóstico
permanecen fuera de Git. Esta corrección no habilita modelos restringidos por AWS.
