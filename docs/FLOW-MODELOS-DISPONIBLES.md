# Modelos de la cuenta Flow en Nexus

El 6 de octubre de 2026 se leyó el selector autenticado del perfil privado `nexus`. Ofrecía Nano Banana Pro, Nano Banana 2, Nano Banana 2 Lite, Omni 1.1 Flash y Veo 3.1 Lite, Fast y Quality. Nexus expone todos esos modelos excepto Nano Banana 2 Lite, prohibido previamente por el usuario. Nano Banana Pro aparece primero y destacado conforme a su preferencia.

Las rutas actuales son de texto a imagen y texto a vídeo. Ampliar los modelos no habilita automáticamente imágenes de referencia, edición de vídeo ni otras funciones de Flow. Los escalados y exportaciones ya existentes conservan su ruta y sus identificadores.

## Verificación real sin consumir cuota

Se seleccionaron los modelos en la cuenta real. Se interceptaron sus solicitudes de inspección y se abortaron antes de alcanzar Google. Los campos estructurados medidos fueron:

| Modelo | Identificador del puente | Campo de envío observado |
| --- | --- | --- |
| Nano Banana Pro | `nano-pro` | `GEM_PIX_2` |
| Nano Banana 2 | `nano2` | `BELUGA`, observado previamente |
| Veo 3.1 Fast | `veo_fast` | `veo_3_1_t2v_fast_ultra`, observado previamente |
| Veo 3.1 Quality | `veo_quality` | `veo_3_1_t2v` |
| Veo 3.1 Lite | `veo_lite` | `veo_3_1_t2v_lite` |
| Omni 1.1 Flash | `omni_flash` | `abra_t2v_8s` |

El control anterior al envío vincula el modelo de vídeo elegido con ese campo; un modelo distinto, ausente o desconocido aborta la petición. Nano Banana Pro sigue la ruta de imagen y conserva su identidad en MCP, reservas y reintentos. Un identificador ya reservado no puede cambiar de modelo ni volver a generar.

La lectura de ajustes de la cuenta devolvió Pro 1:1: 0 créditos; Quality 16:9: 100; Lite 16:9: 5; Omni 16:9: 12. Son importes de esas configuraciones y ese momento, no tarifas universales ni saldos. Nexus vuelve a consultar al caducar la lectura. Los modelos nuevos de vídeo no heredan la estimación de Fast si falla la consulta.

Pruebas locales: selección/modalidad/precio, persistencia de la identidad Pro, idempotencia de cada modelo de vídeo y rechazo de campos de modelo ajenos. La generación completa y la descarga de un resultado nuevo con Pro, Quality, Lite u Omni no se ejecutaron en este cambio. No se compiló ni empaquetó ningún artefacto.

Referencia del proveedor: [modelos y funciones de Google Flow](https://support.google.com/flow/answer/16352836?hl=en). La disponibilidad expuesta aquí se verificó en la cuenta, no se dedujo de esa documentación general.
