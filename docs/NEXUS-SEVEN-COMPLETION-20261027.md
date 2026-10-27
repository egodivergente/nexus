# Continuación de las siete mejoras · 27/10/2026

## Entrega física comprobada

Nexus Agent **1.9.98-agent-preview / 134** instalado en Samsung SM-S938B por actualización `install -r --user 0`, resultado `Success`. Paquete `com.vidadomu.nexus.agent`. Se firmó en AXELPC con la clave privada existente, sin exportarla. Certificado SHA-256 coincidente con la APK previamente instalada: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Verificación de firma y alineación de 16 KB correctas.

Antes de instalar se guardaron en AXELPC la APK anterior y una copia privada de preferencias, bases de datos e iconos (51 archivos; 11.578.368 bytes). No se copiaron los 6,6 GB de modelos y medios. No se desinstaló ni borró información. Tras instalar, ArtBox abrió y se capturó la cuadrícula personalizada de tres columnas. Los archivos de iconos mantienen sus hashes. Ninguna entrada de las preferencias comparadas desapareció. Dos preferencias presentaban cuatro cambios entre copias (orden, columnas y dos escalas de logos), con fechas 23:24:16/44 UTC anteriores a la actualización de las 23:29:17 UTC; no se sobrescribieron estos ajustes recientes con la copia antigua.

El documento `NEXUS-SEVEN-IMPROVEMENTS-20261027.md` describe la entrega 134 y sus verificaciones previas; su apartado sobre ausencia de acceso físico es histórico y queda actualizado por este informe. La instalación no acredita por sí sola autonomía, rendimiento sostenido, sensación háptica o hover físico del S Pen.

## Integración del punto 006 en 1.9.99 / 135

`GenerationDispatcher` conecta agente y generación individual con una planificación automática antes de subir archivos o enviar una generación. Conserva el límite monetario aceptado. La comparación deliberada entre modelos y el lote conservan sus rutas explícitas.

- Registro explícito de identidad para Seedream 4/4.5/5 Pro y Kling 3 estándar/Pro entre los adaptadores indicados en el código. Los nombres comerciales, logos y familias no acreditan equivalencia. Fal H3 Max queda excluido: su propia descripción declara una variante con entrenamiento posterior.
- `ExactRouteCompatibility` exige conservar todos los parámetros efectivos, valores por defecto, modalidad, roles y orden de referencias. No elimina campos desconocidos para hacer encajar una ruta. Las URL de carga de un proveedor no se transfieren a otro.
- Cotizaciones de petición y saldos numéricos separados de etiquetas de presentación. Importes monetarios almacenados como decimales; caducidad de cinco minutos, comprobada de nuevo después de preparar archivos. Crun necesita una conversión USD/crédito configurada según la compra; cero significa desconocida. Fal y plantillas genéricas sin total/saldo verificable no participan.
- Una única fila y un único bloqueo por trabajo, recibo lógico, proveedor del intento y estado incierto guardados antes del POST. Solo un rechazo explícito y persistido sin ID remoto permite probar el siguiente proveedor. Timeout, 409, 5xx y errores al interpretar la respuesta bloquean el fallback. Un ID ya usado nunca autoriza otro POST.
- Las variantes registradas se muestran una sola vez en el selector, conservando la ruta preferida y todos los registros originales. No se reescribe la cuadrícula principal de ArtBox.
- Ajuste `routing.enabled`, conversión por proveedor `routing.creditUsd` y diagnóstico técnico local acotado. El diagnóstico no guarda prompts, claves ni URLs firmadas.

### Límites comerciales comprobados

Estas APIs no certifican una cuota de generación previa al envío. Se consulta el saldo, y un rechazo de admisión permite fallback; una respuesta incierta lo impide. No se presenta la cuota desconocida como suficiente. La reconciliación usa el rechazo persistido y el ID remoto cuando existe; no inventa una búsqueda remota por ID local que el proveedor no ofrece.

Los contratos reales de Seedream y Kling pueden diferir en controles y valores por defecto. En esos casos la alternativa queda excluida y se conserva el proveedor elegido. El registro de identidad no garantiza que cada combinación sea intercambiable. No se han ejecutado generaciones de pago para probar equivalencia visual ni facturación. No se promete un ahorro para todos los modelos.

## Validación

Resultado de compilación, pruebas y publicación: completar con la ejecución final de esta rama. La suite de presentación de CI de la entrega 134 tuvo un fallo: buscaba el texto «Ampliar imagen», mientras la acción visible dice «Ampliar» y su descripción accesible es «Ampliar imagen». Se corrige la prueba para usar esa descripción, manteniendo las comprobaciones de apertura, cierre y conservación del chat. No se altera el visor para satisfacer una expectativa obsoleta.

No se certifica paridad con productos de grandes empresas sin mediciones físicas de latencia, batería, temperatura, accesibilidad y S Pen. El estándar se expresa mediante controles y evidencia verificables.
