# Nexus 1.9.75 — generación, interfaz y contenido del chat

## Alcance

Encargo: corregir el rechazo «Metadatos locales no admitidos.», verificar creación desde texto sin referencias e implementar las mejoras propuestas salvo el punto 10 de aquella lista (organización/filtros de Biblioteca). Ampliación: renderizar contenido del chat y exigir activación explícita de Anotaciones antes de dibujar con el lápiz.

## Corrección de generación

El rechazo procedía de `LocalJobMetadata` en Nexus, antes del proveedor. `GenerationDispatcher` añadía `__project_id` y `__conversation_id`, pero el repositorio solo aceptaba los prefijos de asistente y comparación. Se admiten esas dos claves exactas como contexto local. La validación de metadatos del llamante se realiza antes de subir archivos; el repositorio devuelve el fallo por su contrato `Result`.

El serializador Atlas ya excluía claves `__`. Se conserva esa separación y se verifica el JSON final producido por `NativeGenerationRequest` + `AtlasGenerationPayload`, incluidos tipos y exclusión de opciones ajenas al modelo.

La ficha de Sunburst desde texto, consultada mediante el puente de lectura del APK instalado, declara `model` y `prompt` obligatorios, sin referencias. Las pruebas usan tanto esa ficha pública como los campos del contrato oficial conservado en `model_endpoint_evidence.json` (fuente y hash dentro del registro). No se deduce que Atlas rechazase esos metadatos: el bloqueo encontrado era anterior al envío.

## Cambios de interfaz

- Pie de acciones opaco y medido; cantidades secundarias plegadas al escribir.
- Navegación visible inicialmente, con modo oculto opcional y acceso visible a conversaciones.
- Flow: resultados en el área principal, compositor compartido abajo, varios prompts en una hoja y una sola fila de filtros con selección real.
- Lectura explícita de proyecto/modelo/formato en los controles de Flow. Si el puente antiguo no informa esos campos, se muestra su ausencia; no se inventan ajustes.
- Compositor: pegar, borrar, restaurar, contador breve, referencias visuales, eliminación y ordenación.
- Acceso Crear desde el catálogo; información/evaluación secundaria; separación de Sunburst/Flare/Developer para evitar saltos entre productos.
- Plantillas por contrato: campos obligatorios y opciones principales primero; resto en Avanzado. Se mantienen las operaciones sin prompt y sin referencias creativas.
- Cantidad exacta 1–20 y cantidades por modelo en los lotes; grupos guardados como acción secundaria; singular/plural.
- Visor limitado a la colección de origen y ampliación de imagen. El único cambio de Biblioteca es pasar esa colección al visor: sus carpetas, filtros y densidad se mantienen.
- Conversaciones en panel: búsqueda, cierre de presentación con X, recuperación de cerradas, fecha y vista previa. El historial no se elimina.
- Agente/modelo fuera del placeholder. Respuestas AGY vacías fallan explícitamente en vez de inventar una respuesta «…»; las antiguas se presentan con explicación.
- Personalización cotidiana con interruptores/deslizadores; reglas por ámbito en Avanzado. Logotipos adaptados al contraste y controles compartidos que respetan la preferencia háptica.
- Identidad de compilación visible: versión, código, canal, revisión y fecha.

## Contenido y lápiz

- Tablas Markdown con o sin barras exteriores y celdas con barras escapadas/código.
- Bloques cerrados `mermaid`, `html` y `svg` con vista renderizada, código, ampliar y guardar HTML. Mermaid se incluye en el APK con su licencia MIT; no requiere CDN en uso.
- HTML interactivo autocontenido en una vista separada, con JavaScript para sus controles/animaciones. Sin puente JavaScript a la app ni lectura de archivos privados. Los recursos externos se abren como enlaces; no se cargan dentro del panel.
- Enlaces Markdown/URLs con Abrir, Guardar archivo y Copiar enlace; documentos HTML creados por el agente con vista previa y acciones de archivo.
- Imágenes registradas y sincronizadas reconocidas como medios de usuario, corrigiendo el rechazo de `view_library_asset` por su carpeta.
- El S Pen y el dedo solo dibujan después de mantener pulsado el tablero y elegir Anotaciones; hay una acción visible para terminar. El toque normal conserva navegación/desplazamiento.

Renderizar una imagen en pantalla no demuestra que el proveedor LLM haya recibido sus píxeles. La ruta AGY existente serializa las imágenes como marcadores de texto; esta actualización no debe atribuirle visión multimodal sin soporte real de su puente.

## Validación

- APK offline QA compilada e instalada en Pixel conservando su sandbox; aún no se ha sustituido Nexus Agent.
- JVM: 681 pruebas de app sin fallos; núcleo 317 casos, 0 fallos y 4 omitidos. Estos resultados no prueban una generación real de pago.
- Puente Flow: 36 pruebas de recuperación/ajustes sin fallos; después del ajuste a los iconos reales, sus 5 pruebas de ajustes vuelven a pasar.
- Puente actualizado en AXELPC conservando los 10 IDs de trabajos y de petición. Solo se reinició seguimiento pasivo, sin reenvío. Verificación HTTP del nuevo workspace: proyecto real, Omni 1.1 Flash y 16:9.
- Pendiente: pruebas físicas, certificado/instalación de Agent y validación de generación real. No se han reenviado encargos anteriores.
