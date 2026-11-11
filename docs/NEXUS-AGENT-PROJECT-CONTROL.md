# Control de proyectos y Biblioteca por el agente

Petición de Vidal del 11/11/2026, posterior a la entrega vc207: poder ordenar una migración entre proyectos, comprobarla y retirar el origen; buscar en toda la Biblioteca y mostrar el material en la interfaz. «Mateo → Madrid no duerme» se toma como ejemplo de capacidad, no como orden de mover los proyectos reales durante el desarrollo.

## Implementación publicada

Fuente final: `2d6557bccfa2bc23ca3bc32d0b15b5ad1573eb8b` (implementación inicial `f5c40ca`), rama `feat/nexus-cut-20261111`.

### Biblioteca y resultados en Cut

`ProjectAssetMembership` es la proyección compartida por la búsqueda del índice y el centro de proyectos. Reconoce dueño actual, asociaciones n:m y vínculos legacy de escena o plano; los filtros narrativos también resuelven asociaciones a bloques. Excluye los archivos retirados de la Biblioteca. El proyecto antiguo de una generación es procedencia y no vuelve a incorporar un output que después se movió o quedó sin proyecto. Una consulta con proyecto desconocido devuelve vacío, sin ampliar silenciosamente la búsqueda a toda la Biblioteca.

El chat de Cut incorpora `search_assets`, `inspect_asset_metadata`, `open_results_wall` y, cuando existe el adaptador nativo, `list_library_assets` para recorrer la Biblioteca por páginas. El evento de resultados llega al muro y al visor del editor. Las imágenes y vídeos encontrados se resuelven otra vez por su ID actual al mostrarlos: nombres y URIs relocalizados se actualizan; un archivo retirado deja de ofrecerse.

Ver o reproducir un resultado no modifica el montaje ni lo adjunta automáticamente al modelo. El botón explícito «Añadir al montaje» importa sus IDs actuales mediante `media.import` por `CutSession`. Las herramientas de lectura del editor no incluyen `view_library_asset`: esta conexión no acredita visión ni escucha del material.

### Transferencia entre proyectos

Las cuatro herramientas se registran cuando está disponible el workspace de Cut, tanto en el toolkit de Nexus como en el chat del editor:

| Herramienta | Comportamiento |
|---|---|
| `preview_project_transfer` | Resuelve IDs o nombres exactos, inventaría el contenido y los montajes y guarda una huella con `request_id`. Todavía no traslada contenido. |
| `transfer_project` | Ejecuta o reanuda esa operación con la huella esperada. El índice se confirma en una escritura atómica; cada montaje usa `project.link_story` por su `CutSession`, con revisión e idempotencia persistente. |
| `get_project_transfer` | Consulta el recibo y comprueba índice, vínculos y revisiones. Puede reabrir el origen como compensación si detecta una retirada incompleta. No repite el traslado. |
| `undo_project_transfer` | Restaura los registros y vínculos anteriores solo si las comprobaciones permiten hacerlo sin sobrescribir trabajo posterior. |

Se conservan los IDs, URIs y originales. Los archivos que pertenecen al origen pasan al destino; un archivo compartido de otro dueño conserva su dueño y se trasladan sus asociaciones. Escenas, planos, personajes, bloques, referencias y montajes mantienen su identidad. Los códigos de escena o plano coincidentes entre origen y destino bloquean la operación; también se rechazan nombres ambiguos, origen igual a destino, proyectos archivados o desconocidos, trabajo activo en el origen y la reutilización de un `request_id` para otra orden.

La memoria y el historial del origen se conservan con su identidad. No se concatenan memorias, no se reescribe el historial de trabajos de SQLite y no se trasladan ni modifican conversaciones anteriores. Las propuestas pendientes requieren una nueva revisión; las tareas en cola, preparadas o pendientes de reanudación se bloquean para revisar su alcance y no se amplía por esta operación la autonomía del destino.

Si el traslado verificado retira el origen y la conversación llamadora tenía ese proyecto activo, solo su contexto pasa al destino. El recibo identifica esa conversación para que deshacer pueda restaurar su contexto si aún corresponde; no cambia indiscriminadamente el proyecto activo de otros chats.

El origen solo se archiva si se pidió `retire_source` y se verificaron todos los pasos. Es un archivado recuperable, no una eliminación definitiva. Si una lectura o una comprobación posterior a la retirada falla, la compensación vuelve a hacer visible el origen y el informe no declara el traslado verificado. Esa comprobación y su compensación terminan incluso si el llamador cancela en ese punto.

### Reanudación, deshacer y recibos locales

El recibo distingue `PREVIEW`, `INDEX_MERGED`, `COMPLETED`, `UNDOING` y `UNDONE`, con los pasos ya confirmados. Las claves idempotentes permiten recuperar un cierre entre el commit de un montaje y la escritura de su recibo, sin repetir la edición. Una cancelación o un fallo parcial conserva ese progreso; se consulta y reanuda con el mismo `request_id`.

Deshacer comprueba el índice de origen y destino y las revisiones de los montajes. Rechaza ediciones posteriores, una inversión manual que no corresponda a su propia operación y dependencias nuevas de otros montajes sobre escenas o planos trasladados. Una inversión parcial se puede reanudar con sus claves propias; antes de restaurar el índice se vuelven a comprobar los vínculos. Los cambios ajenos se conservan.

`IndexData.projectTransfers` contiene snapshots de recuperación y es **local al dispositivo**. No se exporta en snapshots portables ni se replica por sincronización; al aplicar un índice recibido se conservan únicamente los recibos locales. Las bases y la normalización de sync excluyen este campo. Su presencia local tampoco provoca una reaplicación continua de un índice sin cambios.

## Alcance y límites

El agente trabaja sobre los mismos repositorios que la interfaz. Los archivos físicos no se borran ni duplican para reorganizar proyectos. La transacción del índice y las transacciones de cada montaje son distintas: el registro de progreso y la verificación final coordinan el conjunto. No se afirmará atomicidad global ni éxito de pasos no confirmados.

La búsqueda por nombres, prompts y metadatos no equivale a visión o audición de todo el contenido. Mostrar los resultados no los añade por sí solo al montaje ni los envía a un proveedor.

El chat mantiene el cerebro configurado y las herramientas disponibles en ese contexto. Esta implementación no establece un enrutamiento universal de LLM ni demuestra que cualquier proveedor interprete y ejecute correctamente estas órdenes. Los comandos de edición ordinarios del chat de montaje siguen ligados a su montaje; las herramientas de transferencia tienen sus destinos explícitos y deben usarse para la orden concreta del usuario. No se han usado proyectos reales del ejemplo para validar una migración.

## Validación JVM conocida

| Conjunto | Resultado |
|---|---|
| `agent-core`, ejecución final | **481 registradas: 477 PASS, 4 omitidas, 0 FAIL. BUILD SUCCESSFUL in 2m 20s.** Incluye 15 pruebas de `ProjectTransferTest`. |
| `cut-core`, resultados disponibles | **103 PASS**, sin fallos ni omitidas. |
| App, suite combinada anterior | **949 pruebas: 942 PASS, 7 FAIL, 0 errores y 0 omitidas. BUILD FAILED in 4m 3s.** Los siete casos y sus mensajes de aserción coinciden exactamente con el baseline vc206. |

Las pruebas de transferencia usan fixtures locales: vista previa sin cambios de contenido, huella obsoleta, colisiones, trabajo activo, interrupción y reanudación, montajes nuevos o modificados, deshacer protegido, dependencias nuevas, retiro compensado ante fallo de lectura y cancelación, contexto de la conversación llamadora y conservación de procedencia e historial. La prueba de sync comprueba que un marcador privado del snapshot del recibo no se transmite, que el recibo local sobrevive a una actualización entrante y que no provoca reaplicaciones ni commits repetidos por sí solo. Las pruebas de búsqueda y presentación cubren asociaciones compartidas, pertenencia actual y resultados visibles sin incorporación automática al montaje.

Los resultados JVM se distinguen del recorrido físico descrito abajo. No acreditan la interpretación de órdenes por un LLM externo. No se hicieron llamadas a proveedores ni generaciones de pago en estas pruebas.

## Comprobaciones físicas y entrega

En Pixel se ejecutaron las cuatro herramientas reales de transferencia mediante el puente ADB de depuración, sobre un proyecto de prueba con un archivo sintético y dos montajes vacíos. La vista previa no trasladó contenido; ejecutar y consultar confirmaron `COMPLETED`, IDs/URIs y contenido conservados, ambos montajes vinculados al destino y origen archivado. El centro de proyectos mostró un archivo y dos montajes en el destino. Deshacer devolvió todos al origen y su recibo persistió como `UNDONE` tras reinstalar. Se compararon los registros de archivos, escenas, planos, personajes y generaciones antes/después: idénticos. Los proyectos reales del ejemplo no se modificaron.

El chat se probó con `ScriptedLlmProvider` local, anunciado en pantalla como «Prueba local · sin modelo externo». El proveedor ejecutó `search_assets` y `open_results_wall` reales; la interfaz mostró el archivo, lo abrió y el listado permitió añadirlo al montaje mediante `CutSession` y deshacerlo. No se atribuye esta prueba a la interpretación por un modelo externo.

La primera ejecución física falló en el comprobador local porque exigía `ok=true` a resultados válidos de Biblioteca que no incluyen ese campo. Se corrigió en `e1fbdfc`. La revisión visual detectó después el botón del visor recortado por el desplazamiento de la ventana respecto a la cámara del Pixel. `fc87801` corrigió la distribución y los singulares, pero su prueba física aún falló; `f46d39b` configura además el modo de recorte de la ventana y permitió añadir/deshacer desde el visor. La cabecera aún invadía el área de cámara: `77f6f34` no resolvió ese margen con los insets de Compose; `2d6557b` obtiene el recorte físico de las métricas de la ventana. En la APK final se verificó la cabecera por debajo del recorte y el botón íntegro dentro de la pantalla, además del toque real para añadir un clip y deshacerlo.

### Artefacto final y Pixel

- Fuente: `2d6557bccfa2bc23ca3bc32d0b15b5ad1573eb8b`.
- `:app:assembleAgentPreview`: **BUILD SUCCESSFUL in 4m 2s**, AXELPC. Versión **1.9.172-agent-preview / vc208**.
- APK final: `Nexus-Agent-1.9.172-vc208-preview.apk`. SHA-256 `8f6ef24a886715783b1dd0b4df920e6c9c3a522acc08de38989cf1b522ac52fd`.
- Certificado SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`, identidad existente; `zipalign` con páginas de 16 KiB correcto. Revisión fuente encontrada en el DEX.
- Pixel actualizado con `adb install -r`: **Success**. Versión y SHA-256 del `base.apk` instalado coinciden con el artefacto final.
- Recorrido final: búsqueda en chat local → muro real → visor con cabecera y acción visibles → añadir una imagen como clip de 5 s → deshacer → montaje vacío. Cambios confirmados en el estado de `CutSession`. No se llamó a un modelo externo.
- Conservación en Pixel: todos los IDs originales de proyectos, archivos, escenas, planos, personajes, generaciones, trabajos y outputs permanecen. La prueba añadió solo el proyecto destino; no añadió trabajos ni outputs. 121 archivos no transitorios de Assistant conservados. Los registros de material y proyectos reales comparados antes/después son idénticos; las preferencias de navegación/sync pueden cambiar. Ambos proyectos de prueba quedaron archivados de forma recuperable.

### Entrega S25 pendiente por conexión

La instalación por ADB de vc208 **no se completó**: el dispositivo LAN dejó de estar disponible. Reconectar por LAN y por la IP de Tailscale agotó el tiempo de conexión; SSH de Termux también agotó el tiempo. Tailscale sigue mostrando el S25, pero eso no acredita acceso ADB ni instalación. Se ha pedido al usuario reconectar Wi-Fi y depuración inalámbrica para completar personalmente la actualización. La última entrega verificada en S25 de este encargo es vc207; no se atribuyen al S25 las pruebas vc208 del Pixel.

Pendientes: instalar y comprobar esta misma APK en S25 cuando vuelva ADB; validación de interpretación de órdenes por el modelo externo, sin llamadas de pago realizadas; exportación 720p en S25 no repetida. El recorrido de edición/exportación 720p en Pixel pertenece a la validación histórica vc205.
