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

- APK offline QA y pruebas instrumentadas compiladas e instaladas en Pixel conservando su sandbox.
- JVM: 681 pruebas de app sin fallos; núcleo 317 casos, 0 fallos y 4 omitidos. Estos resultados no prueban una generación real de pago.
- Puente Flow: 36 pruebas de recuperación/ajustes sin fallos; después del ajuste a los iconos reales, sus 5 pruebas de ajustes vuelven a pasar.
- Puente actualizado en AXELPC conservando los 10 IDs de trabajos y de petición. Solo se reinició seguimiento pasivo, sin reenvío. Verificación HTTP del nuevo workspace: proyecto real, Omni 1.1 Flash y 16:9.
- Pixel físico: los 10 comportamientos dirigidos de compositor, referencias, cantidades, errores, Mermaid, HTML, enlaces y lápiz han pasado. El primer recorrido dio 9/10: la inyección de lápiz tras activar Anotaciones necesitó esperar la recomposición y usar tiempos reales. Corregida la sincronización de la prueba, los 4 casos de contenido/lápiz y el compositor Flow vuelven a pasar (5/5). No se oculta aquel primer fallo.
- Revisión visual: referencias Flow compactadas a 72 dp (48 con teclado); diagrama con vista de 200 dp y ampliación para evitar un panel vacío excesivo.
- Agent 1.9.75-agent-preview, código 109, compilada en AXELPC desde `f09a8ae4d6230fd91e26217ea47653017fe89ebf`. Instalación `-r` confirmada en Pixel 8 Pro y S25 Ultra, sin desinstalar ni borrar datos.
- Certificado conservado: SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. SHA-256 de la APK y de ambos APK instalados: `df0b9567beed52844fa3c92505623781ba144a38f819d3ae672c2db6d07fd155`.
- Conservación por IDs: Pixel 434/434 trabajos anteriores; S25 600/600. Ambos conservan 1779 archivos, cuatro proyectos y acceso al almacén de credenciales. Los dos ficheros de borrador del Pixel son idénticos. En S25 hubo cambios de borrador durante la sesión; no se han restaurado copias antiguas sobre ellos.
- CI del primer commit: compilación, unidades y seis pruebas de presentación pasaron; falló la recogida posterior de capturas porque no estaba la carpeta del sandbox QA. Se cambia a instrumentación directa y recogida antes de la limpieza, incorporando los cuatro casos nuevos. El siguiente recorrido detectó `pipefail` no admitido por `/usr/bin/sh`; corregido a opciones POSIX en `401a232`. El nuevo recorrido CI todavía no está confirmado al escribir este informe.
- **Generación real Atlas: PASS, 24/10/2026.** Una petición nueva desde el botón Generar de la APK instalada en Pixel, Sunburst desde texto sin referencias, calidad `low`, `1024x1024`, `n=1`. Atlas aceptó la petición y la completó; Nexus descargó el PNG y lo mostró en el visor (1/1). Coste real informado por Atlas: **0,005592 USD**; ciclo registrado de unos 32 segundos. Archivo local verificado: 913544 bytes, 1024×1024, SHA-256 `cea48fc2abbeb46a415a057bf2aeb8bba4be0586025a669c9f3b2e8be7682efb`.
- Tras esa prueba, Pixel conserva los 434 trabajos anteriores y suma el de prueba (435); 1780 archivos y cuatro proyectos. Borradores global y por modelo restaurados a sus copias inmediatamente anteriores a la prueba y comprobados byte a byte; borrador temporal de prueba retirado. No se han reenviado encargos anteriores ni la orden de San Isidro.
- **VPN Google restaurada: PASS.** Android informa paquete activo `com.google.android.apps.privacy.wildlife`, tipo de VPN 2 y evento CONNECTED; comprobación repetida después de salir de la app. Tailscale detenido para evitar que recupere la VPN durante la restauración. Tiempo de apagado de pantalla devuelto a sus 300000 ms previos.
- Pendiente independiente: una generación real de Flow con esta versión y visión multimodal de AGY. La prueba Atlas no valida esas dos rutas.
