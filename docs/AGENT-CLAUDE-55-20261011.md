# Claude 5.5 en Nexus

## Alcance

Se añaden los perfiles Bedrock oficiales `us.anthropic.claude-sonnet-5-5` y `us.anthropic.claude-opus-5-5` al selector y a `set_agent_settings`. No se cambia automáticamente la elección existente ni las credenciales, región, límites diarios o por mensaje.

El catálogo público no prueba el acceso de esta cuenta. La anotación anterior sobre AccessDenied corresponde al 2 de octubre y no demuestra el acceso actual.

Fuentes consultadas el 11 de octubre de 2026:

- [Modelos de Anthropic](https://platform.claude.com/docs/en/models/overview).
- [Sonnet 5.5 en Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-anthropic-claude-sonnet-5-5.html).
- [Opus 5.5 en Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-anthropic-claude-opus-5-5.html).
- [Disponibilidad del modelo en AWS](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_GetFoundationModelAvailability.html).
- [Gestión del acceso y significado del estado del acuerdo](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html).

## Compatibilidad

- Los bloques `reasoningContent` se devuelven íntegros al proveedor durante los pasos con herramientas; no se muestran ni se registran como texto.
- Se conservan firmas y contenido redactado. La conversación firmada no se reescribe al retirar adjuntos antiguos. Al alcanzar el límite de contexto, o cambiar de modelo dentro de un chat firmado, se pide abrir una conversación nueva y se conserva el proyecto.
- Los 5.5 usan los parámetros de muestreo del modelo, sin enviar la temperatura personalizada que provoca HTTP 400.
- Tarifas de referencia por millón de tokens con el mismo margen conservador del 20%: Sonnet 2,4/12 USD y Opus 4,8/24 USD. Los topes existentes y las reservas siguen aplicándose. Los modelos sin tarifa siguen bloqueados.
- Bedrock runtime no ofrece CountTokens para estos 5.5 según sus fichas. El bloqueo preventivo de documentos cuyo coste no puede contarse se conserva; no se inventa una cuenta ni se desactiva el presupuesto. Audio y edición local siguen disponibles sin enviar sus bytes al LLM.
- El backend proxy requiere permitir los perfiles y configurar sus tarifas en el servidor correspondiente. Esta entrega no cambia una configuración privada del proxy.

## Diagnóstico sin inferencia

El puente de depuración existente añade `agent_model_availability`, sólo para los dos modelos anteriores, con las credenciales cifradas que permanecen en el teléfono. Realiza GET en el plano de control de AWS. No ejecuta Converse, no acepta contratos ni suscripciones y no devuelve claves, cabeceras firmadas o cuerpos de error privados.

HTTP 403 en esta consulta sólo demuestra que no se permite consultar disponibilidad; no demuestra que InvokeModel esté permitido o denegado. Una respuesta positiva tampoco sustituye una prueba de chat. No se ejecutan pruebas de inferencia de pago durante este encargo.

## Resultado de esta entrega

1.9.0-agent-preview vc34 está instalada en el S25 conservando los datos y la firma de 1.8.9. Tras instalarla, la configuración conservada era `us.anthropic.claude-opus-4-6-v1`, backend directo. La última consulta durante la activación de acuerdos muestra `us.anthropic.claude-sonnet-4-6`, también directo; esta tarea no ha cambiado la selección. Añadir modelos al selector no los activa automáticamente.

Compilación final en ASUS y 394 pruebas unitarias aprobadas (118 core y 276 app). Las nuevas pruebas verifican ida y vuelta del contexto firmado y redactado, exclusión de la temperatura y rechazo del cambio de modelo en una conversación firmada. Otras 20 pruebas del proxy pasan con un proveedor simulado; no se ha desplegado un proxy ni modificado su configuración privada.

La consulta real desde el teléfono devuelve HTTP 200 para Sonnet 5.5 y Opus 5.5, con `authorizationStatus=AUTHORIZED`, `entitlementAvailability=AVAILABLE`, `regionAvailability=AVAILABLE` y `agreementStatus=NOT_AVAILABLE` en ambos. La documentación AWS indica que NOT_AVAILABLE significa que el acceso mediante ese acuerdo no existe todavía; la invocación de un modelo de terceros puede iniciar la suscripción y acepta su EULA. No se acepta ningún acuerdo ni se ejecuta inferencia. Estos campos se conservan por separado: no equivalen a una conversación funcionando. Hay que completar la activación correspondiente y probar una conversación autorizada antes de dar la actualización del modelo activo por completada.

La edición y Whisper pasan además cinco pruebas físicas sin permiso INTERNET en el S25. Su instalación, modelo preparado, SHA y límites están documentados en [AGENT-MEDIA-LOCAL-190.md](AGENT-MEDIA-LOCAL-190.md).

Consulta adicional por AWS CLI en Termux del S25: SSH volvió a estar accesible y la CLI respondió `aws-cli/1.46.1`. El archivo de credenciales del entorno principal no se puede interpretar. El perfil `default` dentro de Debian tiene formato válido y región `eu-south-2`; usándolo mediante variables de entorno sólo para cada proceso, STS devuelve `InvalidClientTokenId` y las consultas Bedrock devuelven `UnrecognizedClientException` tanto en `us-east-1` como en `eu-south-2`. AWS rechaza esas credenciales; no se ha demostrado la causa ni que correspondan a la cuenta de Nexus. No se modifica ningún archivo de credenciales, no se copian claves ni se acepta ningún acuerdo. La consulta positiva del teléfono descrita arriba usa las credenciales propias de Nexus y sigue siendo la evidencia de disponibilidad de esa configuración.

Reverificación posterior con el CSV de credenciales proporcionado expresamente por el usuario: STS valida la identidad antes de guardar la configuración. Se configura el perfil `default` del entorno principal de Termux, región `us-east-1`, con archivos privados de permisos 0600 y copias de los archivos anteriores; se verifica de nuevo STS leyendo los archivos guardados. El perfil de Debian y las credenciales de Nexus no se modifican. Las claves viajan por el canal SSH existente y no se incluyen en logs, comandos, documentación ni Git. Las consultas de disponibilidad a Sonnet 5.5 y Opus 5.5 vuelven a devolver AUTHORIZED / AVAILABLE / AVAILABLE y acuerdo NOT_AVAILABLE; también se consulta Opus 4.6 como referencia y devuelve esos mismos estados. No se crean acuerdos ni suscripciones ni se ejecuta inferencia. Se conserva el modelo activo de Nexus.

## Activación de acuerdos autorizada posteriormente

El usuario pide expresamente «Ocúpate del acuerdo». Se consultan las ofertas públicas de ambos modelos y sus condiciones: sólo términos de precio por uso, licencia y soporte, sin términos de precio fijo o compromiso de duración en la oferta recibida. El documento legal remite a [los términos de modelos de terceros en Bedrock](https://aws.amazon.com/legal/bedrock/third-party-models/). Se solicita `CreateFoundationModelAgreement` para Sonnet 5.5 y Opus 5.5 en `us-east-1`, usando cada oferta exacta. Ambos pasan a PENDING y luego AVAILABLE. La última comprobación por CLI devuelve los cuatro estados favorables: AUTHORIZED, entitlement AVAILABLE, región AVAILABLE y acuerdo AVAILABLE. No se activa Opus 4.6 ni se ejecuta ninguna inferencia o generación de pago.

El acuerdo está comprobado en la cuenta de las credenciales del CSV. La consulta desde Nexus, con sus credenciales existentes y la app abierta, todavía devuelve acuerdo NOT_AVAILABLE para ambos a las 08:59:53 UTC del 11 de octubre. En segundo plano se observan además fallos DNS intermitentes; abrir la app permite completar esa consulta HTTP 200. No se ha demostrado que la cuenta del CSV coincida con la de Nexus, ni se sustituyen las credenciales de la app. Se solicita esa aclaración al usuario antes de atribuir la discrepancia a otra cuenta o dar el acceso de Nexus por resuelto.

`GetUseCaseForModelAccess` devuelve ResourceNotFoundException con la cuenta de la CLI; no se presenta un formulario con datos inventados. Los acuerdos AVAILABLE no sustituyen la validación de una conversación ni prueban todos los requisitos adicionales del proveedor. Evidencia privada: `s25-agreements-inspect.json`, `s25-agreements-activate.json`, `s25-agreements-status.json` y las consultas con fecha `s25-nexus-agreements-*.json`. Ofertas completas, URLs firmadas, claves y documentos privados quedan fuera de Git.
