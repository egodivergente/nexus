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

El acuerdo está comprobado en la cuenta de las credenciales del CSV. La consulta desde Nexus, con sus credenciales existentes y la app abierta, todavía devuelve acuerdo NOT_AVAILABLE para ambos a las 08:59:53 UTC del 11 de octubre. En segundo plano se observan además fallos DNS intermitentes; abrir la app permite completar esa consulta HTTP 200. El usuario confirma posteriormente que ambas cuentas son suyas y distintas. Se verifica la diferencia con [STS GetAccessKeyInfo](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetAccessKeyInfo.html): se lee únicamente el identificador de acceso existente en los ajustes de Nexus, se consulta su cuenta por la CLI autenticada y se compara con GetCallerIdentity del perfil del CSV. El resultado confirma cuentas diferentes. Se restaura la ocultación del campo; no se lee el secreto de Nexus, no se guardan ajustes ni se crean usuarios, políticas o nuevas claves. La evidencia con el identificador de cuenta queda en el informe privado `s25-nexus-account-identity.json`, fuera de Git. El acceso de Nexus no está resuelto por los acuerdos de la otra cuenta.

Los ajustes actuales permiten mostrar el identificador de acceso, pero no presentan el identificador de cuenta AWS. Es una carencia de identificación de la configuración: almacenar el secreto cifrado no impide consultar ni mostrar a su propietario qué cuenta está usando. GetAccessKeyInfo identifica la cuenta; no comprueba por sí solo que la clave siga activa o tenga permisos de inferencia.

## Credenciales para cambiar a la cuenta con acuerdos activos

El usuario autoriza expresamente cambiar de cuenta y después solicita terminar o entregarle las claves. Se crea en la cuenta del CSV el usuario IAM `nexus-agent-s25-20261011` con una clave propia para Nexus. La política permite InvokeModel, InvokeModelWithResponseStream y GetInferenceProfile para los perfiles US de Sonnet 5.5 y Opus 5.5; incluye los destinos foundation-model devueltos por GetInferenceProfile con la condición InferenceProfileArn y la consulta de disponibilidad. No otorga administración de cuentas ni suscripciones. La identidad de las nuevas claves pertenece a la cuenta del CSV y no es root; GetInferenceProfile devuelve ACTIVE, ambos acuerdos siguen AVAILABLE y la simulación IAM permite las acciones de inferencia consultadas. No se ejecuta una conversación de pago.

Las claves están en un archivo privado del S25 con permisos 0600 y en `Nexus-AWS-S25.csv` dentro de los outputs privados de esta tarea, para entregarlas al usuario. Las preferencias cifradas y la configuración anterior se conservan antes del intento de cambio. La automatización de la interfaz no consigue guardar la configuración: la última ejecución no encuentra los ajustes abiertos. Por tanto, la cuenta y el modelo guardados en Nexus no se dan por cambiados; se entrega el CSV para completar los campos AWS en los ajustes, con región `us-east-1` y modelo `us.anthropic.claude-sonnet-5-5`. No se reinstala la APK ni se alteran proyectos, historial o límites de gasto. Ninguna clave, preferencia privada o CSV forma parte del commit.

`GetUseCaseForModelAccess` devuelve ResourceNotFoundException con la cuenta de la CLI; no se presenta un formulario con datos inventados. Los acuerdos AVAILABLE no sustituyen la validación de una conversación ni prueban todos los requisitos adicionales del proveedor. Evidencia privada: `s25-agreements-inspect.json`, `s25-agreements-activate.json`, `s25-agreements-status.json` y las consultas con fecha `s25-nexus-agreements-*.json`. Ofertas completas, URLs firmadas, claves y documentos privados quedan fuera de Git.

## Corrección final de cuenta y requisitos de Anthropic

La siguiente comprobación, posterior al mensaje del usuario «Ya está conectado», sustituye el estado pendiente de configuración descrito arriba. STS acepta las credenciales originales del CSV. La inspección de las preferencias de Nexus confirma que tanto el identificador como el secreto guardados eran distintos de los del CSV; su formato era válido, pero no eran la pareja elegida por el usuario. No se concluye que esas otras claves fueran inválidas sólo a partir del mensaje genérico de Nexus: el proveedor directo agrupa varios HTTP 401/403 bajo el diagnóstico de autenticación.

Se guardan exactamente las credenciales originales del CSV por petición del usuario, backend directo, región `us-east-1` y perfil `us.anthropic.claude-sonnet-5-5`. Una nueva inspección en un proceso independiente confirma que ambos valores coinciden con el CSV después de persistir las preferencias. Se conserva la APK 1.9.0 vc34, sus datos, otras claves y límites. El usuario IAM creado anteriormente no sustituye ahora las credenciales originales y no se elimina.

La cuenta carecía del formulario de primer uso de Anthropic. Se completa una sola vez con el uso real de Nexus como proyecto individual de desarrollo y creación multimedia, destinatarios internos y el perfil GitHub público del desarrollador. No se inventa una empresa, identidad legal ni dirección. AWS acepta `PutUseCaseForModelAccess` con HTTP 201 a las 09:51:49 UTC; la lectura posterior devuelve HTTP 200 y confirma el contenido presentado. La [documentación de acceso de AWS](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html) exige este formulario para Anthropic y permite una URL de GitHub para desarrolladores individuales.

Comprobación desde la propia app a las 10:00:38 UTC del 11 de octubre: Sonnet 5.5 y Opus 5.5 devuelven HTTP 200, autorización AUTHORIZED y entitlement, región y acuerdo AVAILABLE. El estado de la app confirma backend directo y Sonnet 5.5 seleccionado. Esto valida la consulta de acceso con las credenciales guardadas; no demuestra una conversación real con Converse.

Para recuperar también las alternativas 4.6 mencionadas por el usuario, se revisan las ofertas públicas de `anthropic.claude-sonnet-4-6` y `anthropic.claude-opus-4-6-v1`. Sólo presentan términos de uso, soporte y licencia; `validityTerm` es nulo y no existe término de precio fijo. El documento legal tiene el mismo SHA-256 que el ya revisado durante la activación 5.5. Se aceptan los acuerdos exactos mediante `CreateFoundationModelAgreement`, ambos con HTTP 202. Después de PENDING, la comprobación de las 10:02:18 UTC confirma acuerdo AVAILABLE para ambos, con autorización, entitlement y región favorables. Esta consulta 4.6 se ejecuta desde Windows con las mismas credenciales originales del CSV; la consulta nativa de Nexus descrita arriba cubre 5.5. No se selecciona 4.6 en lugar de Sonnet 5.5.

La configuración se repara mediante una APK temporal de instrumentación firmada con el mismo certificado que Nexus, sin reemplazar la aplicación. Se utiliza un payload privado que se elimina después de la operación y se retira la APK auxiliar al terminar. La copia anterior de preferencias, formularios, claves y evidencia detallada permanecen en outputs privados, excluidos de Git. No se realiza inferencia ni generación de pago; queda pendiente comprobar una conversación nueva iniciada por el usuario.

### Nueva comprobación tras persistir el fallo comunicado

El usuario comunica que sigue sin funcionar. La lectura del historial a las 10:14:21 UTC muestra los mismos 132 elementos, sin una respuesta nueva desde la comprobación anterior; el último error guardado especifica `The security token included in the request is invalid`. La selección actual ha cambiado a `us.anthropic.claude-opus-4-6-v1`. No se fecha ese error como si fuese un intento posterior a la reparación.

La inspección actual de SecureStorage confirma que el identificador coincide con el CSV y el secreto ya no coincide, aunque conserva un formato válido. No se ha determinado qué operación cambió ese valor. Se restauran exclusivamente las dos credenciales originales y se conserva la selección Opus 4.6, región, presupuesto y demás ajustes. Dos inspecciones posteriores, en procesos independientes, confirman ambos valores exactos y `settingsOffline=false`. La APK auxiliar se retira y se vuelve a abrir Nexus. Se prepara una única prueba de respuesta sin herramientas ni adjuntos, con 16 tokens de salida como máximo, pero no se ejecuta: la autorización solicitada para esa llamada de pago sigue pendiente. No se da el chat por resuelto a partir de esta corrección de configuración.

### Prueba de respuesta real y modelo operativo

Tras preguntar expresamente si autoriza una llamada mínima de pago, el usuario pide «Pero vamos hombre termina». Se ejecuta la prueba preparada mediante la clase `BedrockDirectProvider` de la APK instalada en el S25, con las credenciales leídas por `AssistantSettingsStore.awsCredentials()`. No hay herramientas, adjuntos, historial ni sistema personalizado: sólo «Responde únicamente OK.» y un límite de 16 tokens de salida. No se realiza ninguna generación multimedia ni se cambian límites de gasto.

La selección vuelve a ser Sonnet 5.5 al iniciar la prueba. La llamada real falla con `MODEL_NOT_ALLOWED`: AWS indica que `anthropic.claude-sonnet-5-5 is not available for this account` y remite a AWS Sales. Las dos credenciales coinciden con el CSV en esa misma ejecución. Queda demostrado que los estados AVAILABLE del plano de control no garantizan acceso a Converse en esta cuenta; no es un fallo de las claves en esa petición. No se insiste ni se repiten llamadas 5.5.

Se selecciona `us.anthropic.claude-sonnet-4-6` como alternativa operativa y se repite la misma prueba mínima. El proveedor instalado devuelve una respuesta real **«OK.»**, con 18 tokens de entrada y 5 de salida. El proceso nuevo confirma las dos credenciales exactas, región `us-east-1` y Sonnet 4.6 seleccionado. La APK auxiliar se desinstala y se abre la app principal. Evidencia privada: `account-helper/result-probe.json` para 4.6 y `account-helper/sonnet55-failed-probe.json`, que conserva el resultado observado antes de sustituir el informe inicial.

Resultado: conexión e inferencia reales comprobadas en el S25 con el proveedor de producción de Nexus y Sonnet 4.6. No equivale a una prueba de todo el bucle del agente, de sus herramientas ni de una conversación extensa con el proyecto. Sonnet 5.5 continúa bloqueado por AWS para esta cuenta; los acuerdos y el formulario ya completados no eliminan esa restricción.
