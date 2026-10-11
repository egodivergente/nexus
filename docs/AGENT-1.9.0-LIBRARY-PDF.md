# Agent 1.9.0: biblioteca y documentos

- La Biblioteca muestra los encargos activos desde su registro local, incluyendo envíos de estado incierto y seguimientos pausados.
- El progreso porcentual procede del proveedor. Los hitos fijos de Flow no se presentan como porcentaje medido. La estimación de tiempo usa la mediana de hasta 20 encargos recientes del mismo proveedor/modelo y exige al menos tres muestras; puede superarse.
- El visor permite pasar entre imágenes de diferentes encargos con un gesto horizontal o los botones anterior/siguiente. Con zoom, el gesto desplaza la imagen. Excluye archivos ocultos/eliminados.
- En Android 15 o superior se extrae localmente el texto de PDF; si una página carece de texto, se conserva la vía de documento visual. El original siempre se conserva. La extracción textual declara que no analiza imágenes/gráficos.
- Los documentos de texto se envían como texto y su resumen se reutiliza en rondas posteriores. Se muestran las fases de preparación/lectura/respuesta.
- Cancelar una petición cancela también la llamada HTTP y su descarga. CountTokens tiene un máximo de 30 s; cada llamada al modelo, 120 s; un turno con documentos, 240 s. Los errores permiten reintentar sin volver a importar los archivos.

## Comprobaciones

ASUSVIDAL: 114 pruebas de agent-core superadas, sin errores ni omisiones. Incluyen lectura única de documentos, texto extraído y cancelación de HTTP. No se han realizado llamadas de generación ni de LLM de pago. La lectura completa de un PDF por el servicio real no queda certificada por estas pruebas.
