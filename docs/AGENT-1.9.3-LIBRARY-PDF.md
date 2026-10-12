# Agent 1.9.3: biblioteca y documentos

- La Biblioteca muestra los encargos activos desde su registro local, incluyendo envíos de estado incierto y seguimientos pausados.
- El progreso porcentual procede del proveedor. Los hitos fijos de Flow no se presentan como porcentaje medido. La estimación de tiempo usa la mediana de hasta 20 encargos recientes del mismo proveedor/modelo y exige al menos tres muestras; puede superarse.
- El visor permite pasar entre imágenes de diferentes encargos con un gesto horizontal o los botones anterior/siguiente. Con zoom, el gesto desplaza la imagen. Excluye archivos ocultos/eliminados.
- En Android 15 o superior se extrae localmente el texto de PDF; si una página carece de texto, se conserva la vía de documento visual. El original siempre se conserva. La extracción textual declara que no analiza imágenes/gráficos.
- Los documentos de texto se envían como texto y su resumen se reutiliza en rondas posteriores. Se muestran las fases de preparación/lectura/respuesta.
- Cancelar una petición cancela también la llamada HTTP y su descarga. CountTokens tiene un máximo de 30 s; cada llamada al modelo, 120 s; un turno con documentos, 240 s. Los errores permiten reintentar sin volver a importar los archivos.

## Comprobaciones

ASUSVIDAL: 128 pruebas de agent-core superadas en la versión integrada (114 en la base inicial), sin errores ni omisiones. Incluyen lectura única de documentos, texto extraído y cancelación de HTTP. No se han realizado llamadas de generación ni de LLM de pago. La lectura completa de un PDF por el servicio real no queda certificada por estas pruebas.

## Integración con la versión instalada

La primera APK 1.9.0 (34) compiló correctamente, pero Android rechazó su instalación porque el S25 ya tenía 1.9.2 (36). Se integró la rama `codex/aki-attachments-20261011` (fbae847) conservando Sol/AKI, adjuntos visuales, edición local, Whisper, reparación de CountTokens y mejoras de presentación. La actualización conjunta usa 1.9.3 (37). No se forzó ninguna instalación de versión inferior.

## Instalación final

Compilación AgentPreview en ASUS: BUILD SUCCESSFUL. Fuentes: 509 archivos verificados. APK 1.9.3-agent-preview (37), SHA-256 `b95f88354fff953d8f7cbd805db94c478c22cbd0ce02e4d523741e7310853cd3`, firmada con el certificado existente e instalada con actualización sobre 1.9.2: Success. Inicio en frío: Status ok, 1868 ms. No se desinstaló ni borraron datos.

La comprobación visual posterior coincidió con el uso del teléfono por el usuario (Drive en primer plano). No se operó esa pantalla ni se afirma una prueba manual del gesto del visor. Tampoco se ejecutó una lectura de PDF contra el LLM real ni una generación de pago.
