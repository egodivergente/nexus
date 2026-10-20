# Límites del agente editables desde la APK

Ruta: Ajustes → Agente y modelos → Ajustes del asistente → Consumo y límites → Límites del agente.

El formulario expone los campos reales de `AgentLimits`, con etiquetas visibles y persistencia. Tiene 28 controles; el máximo de tokens se edita en Respuesta. Los importes de consumo y los ajustes propios de cada cerebro conservan sus controles actuales. Los valores numéricos fuera de formato o negativos se rechazan con un mensaje; no se sustituyen silenciosamente por el valor anterior.

## Controles

| Grupo | Ajustes |
| --- | --- |
| Conversación | Pasos de herramientas, duración del mensaje, imágenes, vídeos, documentos, historial reenviado, caracteres por resultado y estado de interfaz |
| Encargos | Cantidad por lote, envíos simultáneos globales y envíos simultáneos a Flow |
| Consultas | Resultados de búsquedas/listados/historial, candidatos por recomendación y esquemas que se consultan por recomendación |
| Memoria | Caracteres de contexto, escenas, archivos y generaciones del resumen, caracteres de sus prompts, coincidencias de búsqueda y caracteres por coincidencia |
| Flow | Resultados por encargo, archivos recuperados por respuesta, espera por consulta y minutos de seguimiento de trabajos |
| Subagentes | Concurrencia, niveles de delegación y permiso para preparar generaciones |

`0` desactiva el máximo local de estos controles. El máximo de tokens es positivo porque el contrato de respuesta lo requiere; no se aplica un máximo local adicional de 32.000. Los límites que efectivamente publique un modelo/proveedor se validan con su contrato.

Los topes de lote de 20 y de concurrencia de 3/1 son valores iniciales editables. También lo son las cantidades de consultas, memoria y recuperación de Flow. No hay una segunda constante de 20, 30 o 50 que vuelva a recortar las cantidades controladas por esos ajustes. Para pedir toda una consulta se admite `limit=0`; los límites de longitud del resultado y de contexto siguen siendo ajustes independientes del usuario.

## Aplicación y continuidad

La concurrencia se actualiza sin cancelar los trabajos activos. Si se baja, los siguientes esperan hasta tener capacidad; si se sube o se pone a cero, se liberan los pendientes por orden de llegada. Cancelar una espera o fallar un envío no deja plazas ocupadas. Los trabajos ya aceptados por el proveedor conservan su identidad y seguimiento.

Los campos existentes conservan sus claves de preferencias. Los nuevos se guardan en un registro JSON extensible separado; se mantienen las credenciales, las conversaciones y las opciones previas. El formulario se deriva de los campos serializados para que una opción nueva no quede oculta por omisión.

Las tres preferencias de subagentes se pueden editar y guardar, pero todavía no se ejecutan: el motor de delegación sigue pendiente de aprobación del [diseño](DISENO-SUBAGENTES-20261020.md). El diseño exige usar estos ajustes y no imponer un máximo fijo de cuatro trabajadores ni una profundidad invariable de un nivel.
