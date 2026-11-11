# Nexus Cut — centro de proyectos

Plan solicitado el 11/11/2026. Base: vc206, `60a36d3`, rama `feat/nexus-cut-20261111`.

## Resultado

Cut reúne los proyectos existentes de Nexus. Cada proyecto abre un espacio con sus montajes, guion, material, generaciones y acceso al agente con el contexto correcto. Un proyecto admite varios montajes; crear uno no duplica los archivos ni sustituye otros montajes.

## Plan de acción

1. **Datos y continuidad.** Proyectar el índice actual de Nexus y el workspace de Cut en una lista común. Mostrar proyectos sin montajes, conservar montajes sin proyecto o con vínculos antiguos y distinguir archivados. Reutilizar los repositorios existentes.
2. **Diseño.** Lista con miniatura local, nombre y actividad; búsqueda, acción principal para crear y operaciones secundarias en menú. Detalle compacto con accesos a Guion, Material y Generaciones, lista de montajes y una entrada al agente. Aplicar tipografía, colores, radios y tamaños táctiles de Nexus; adaptar el espacio al teclado y al tamaño disponible.
3. **Flujos.** Crear, renombrar, archivar y restaurar proyectos. Crear varios montajes y reabrir los existentes. Abrir Guion y su sección Material filtrados por el proyecto; preparar la conversación del agente con ese proyecto, sin enviar automáticamente un mensaje. El chat de montaje conserva su sesión propia.
4. **Verificación.** Probar agrupación, huérfanos, archivado reversible y aislamiento entre proyectos. Compilar y ejecutar las pruebas pertinentes; comparar los siete fallos conocidos con la base. Revisar visualmente el recorrido en Pixel y conservar los datos previos.
5. **Entrega.** Versionar como vc207 / 1.9.171, comprobar firma e integridad, actualizar Pixel y S25 por ADB conservando datos, y publicar los commits con verificación remota.

## Criterios de aceptación

- Los proyectos existentes aparecen sin una importación o migración manual.
- Desde Cut se llega al material, guion, generaciones o agente del proyecto elegido; volver conserva el contexto de navegación.
- Un proyecto puede contener varios montajes; todos los cambios de un montaje pasan por `CutSession`.
- Los archivos y relaciones existentes conservan su identidad. Archivar un proyecto es reversible.
- No aparecen controles decorativos sin función, fuentes de datos paralelas ni envíos automáticos al agente.
- La validación distingue modelo simulado, ejecución de herramientas reales y conversación con proveedor externo. No se hacen generaciones de pago durante QA.

## Implementación

- Plan publicado en `52f20a2`; implementación en `84c0535`; correcciones de revisión y prioridad visual en `71de517999e2ebf498051aabc681703e50624d6f`.
- Cut muestra los proyectos del índice compartido, incluidos los que aún no tienen montajes. Conserva un apartado para montajes sin proyecto o con un vínculo antiguo. Búsqueda, renombrado, archivado y restauración funcionan sobre las identidades existentes.
- El detalle reúne portada, nuevo montaje, Guion, Material, Generaciones, importación de medios, montajes y entrada al agente. Los montajes tienen prioridad sobre el panel del agente. Colores, tipografía, formas y marca proceden del tema de Nexus; el editor conserva su fondo oscuro y adopta el acento del tema.
- La importación conserva el proyecto capturado al abrir el selector. La navegación espera a restaurar las conversaciones y seleccionar el proyecto antes de añadir el borrador. El acceso al agente prepara el mensaje: no lo envía automáticamente.
- El archivado conserva assets, escenas, vínculos, montajes e historial. Si el agente está ocupado, espera a que termine para abandonar ese contexto; un proyecto archivado no admite turnos nuevos. El cambio efectivo de proyecto o su restauración cancela la salida pendiente.
- Generaciones utiliza la asignación actual del material y vínculos explícitos, conserva los índices de salida originales y descarta URIs locales ambiguas entre trabajos. No adivina la identidad de outputs antiguos relocalizados.

## Referencia de diseño posterior

Vidal aportó una referencia de barra con zorro central y menú radial como inspiración, sin exigir una réplica. Se conserva como dirección para reforzar el acceso contextual al agente. Esta entrega no implementa ese menú. Una futura iteración debe valorar una entrada única al chat, la legibilidad de las etiquetas y el uso con una mano; no añadir controles decorativos ni más pasos para acceder a proyectos y montajes.

## Validación y entrega

- APK final **1.9.171-agent-preview / vc207**, fuente `71de517999e2ebf498051aabc681703e50624d6f` comprobada dentro del DEX. `assembleAgentPreview` completado. La invocación conjunta con pruebas termina **BUILD FAILED**, exclusivamente por los siete fallos previos de la app.
- App: **943 pruebas, 936 PASS, 7 FAIL, 0 errores, 0 omitidas**. Clases, métodos y mensajes de los siete fallos coinciden exactamente con vc206. Las trece nuevas pruebas pasan: siete de agrupación/continuidad/archivado y seis de aislamiento de generaciones, incluida una colisión de URI local entre proyectos. Ninguna prueba desactivada.
- `agent-core:test`: **461 registradas, 457 PASS, 4 omitidas, 0 fallos**; ejecución de esta tarea. `cut-core:test`: 101 pruebas previamente correctas reutilizadas (`UP-TO-DATE`). Primer intento de integración falló al compilar antes de guardar el archivo de componentes; la repetición completa corrigió ese fallo. No se atribuye éxito al intento fallido.
- Pixel físico, por ADB: proyectos existentes; abrir Material y Generaciones del proyecto correcto; crear un proyecto; importar una imagen local por SAF y verla en Material; abrir Guion; crear dos montajes y reabrir uno; renombrar; preparar borrador en el agente y volver al detalle conservándolo. Estas operaciones se ejecutaron inicialmente sobre `84c0535`.
- Sobre la APK final `71de517`: proyectos y montajes persisten tras actualizar; prioridad visual de montajes revisada; archivar, comprobar contexto activo vacío, restaurar y reabrir montaje; volver a archivar la prueba; búsqueda por nombre. El proyecto de QA queda archivado, con su imagen y dos montajes recuperables. No se envió el borrador al proveedor.
- Pixel y S25: `adb install -r` **Success**, versión instalada vc207 y SHA-256 de `base.apk` idéntico al artefacto firmado. S25: portada de proyectos comprobada visualmente en su tema verde; no se atribuye al S25 el recorrido completo del Pixel. UIAutomator devolvió raíz vacía en S25; la captura posterior sí muestra la portada cargada.
- Conservación: Pixel mantiene los seis IDs de proyecto, 2624 assets, 683 generaciones del índice, 466 jobs, 505 outputs y todos los archivos de conversación originales; QA añade un proyecto, una imagen y dos montajes. S25 conserva siete proyectos, 3177 assets, 780 generaciones del índice, 774 jobs y 642 outputs. Escenas, planos y personajes mantienen todos sus IDs en ambos; SQLite `quick_check` correcto. Los ficheros de preferencias se conservan, con cambios de navegación/sincronización; no se afirma que sus hashes sean idénticos.
- Certificado SHA-256: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Alineación 16 KiB comprobada.
- APK SHA-256 local = Pixel = S25: `e257d4b572ac750ac9df2b928420f7d931d7158f8c4cd08b492af8836a028add`.
- Límites: no llamada a LLM externo ni generación de pago; no ensayo físico del archivado durante un turno de proveedor activo. El recorrido de exportación 720p corresponde a la prueba vc205 registrada en el relevo y no se ha repetido en vc207. La referencia radial es una idea, no una función entregada.
