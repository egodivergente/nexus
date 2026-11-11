# Nexus Cut — centro de proyectos

Plan solicitado el 11/11/2026. Base: vc206, `60a36d3`, rama `feat/nexus-cut-20261111`.

## Resultado previsto

Cut reúne los proyectos existentes de Nexus. Cada proyecto abre un espacio con sus montajes, guion, material, generaciones y acceso al agente con el contexto correcto. Un proyecto admite varios montajes; crear uno no duplica los archivos ni sustituye otros montajes.

## Plan de acción

1. **Datos y continuidad.** Proyectar el índice actual de Nexus y el workspace de Cut en una lista común. Mostrar proyectos sin montajes, conservar montajes sin proyecto o con vínculos antiguos y distinguir archivados. Reutilizar los repositorios existentes.
2. **Diseño.** Lista con miniatura local, nombre y actividad; búsqueda, acción principal para crear y operaciones secundarias en menú. Detalle compacto con accesos a Guion, Material y Generaciones, lista de montajes y una entrada al agente. Aplicar tipografía, colores, radios y tamaños táctiles de Nexus; adaptar el espacio al teclado y al tamaño disponible.
3. **Flujos.** Crear, renombrar, archivar y restaurar proyectos. Crear varios montajes y reabrir los existentes. Abrir Guion y Biblioteca filtrados por el proyecto; preparar la conversación del agente con ese proyecto, sin enviar automáticamente un mensaje. El chat de montaje conserva su sesión propia.
4. **Verificación.** Probar agrupación, huérfanos, archivado reversible y aislamiento entre proyectos. Compilar y ejecutar las pruebas pertinentes; comparar los siete fallos conocidos con la base. Revisar visualmente el recorrido en Pixel y conservar los datos previos.
5. **Entrega.** Versionar como vc207 / 1.9.171, comprobar firma e integridad, actualizar Pixel y S25 por ADB conservando datos, y publicar los commits con verificación remota.

## Criterios de aceptación

- Los proyectos existentes aparecen sin una importación o migración manual.
- Desde Cut se llega al material, guion, generaciones o agente del proyecto elegido; volver conserva el contexto de navegación.
- Un proyecto puede contener varios montajes; todos los cambios de un montaje pasan por `CutSession`.
- Los archivos y relaciones existentes conservan su identidad. Archivar un proyecto es reversible.
- No aparecen controles decorativos sin función, fuentes de datos paralelas ni envíos automáticos al agente.
- La validación distingue modelo simulado, ejecución de herramientas reales y conversación con proveedor externo. No se hacen generaciones de pago durante QA.

## Estado

Plan registrado; implementación y comprobaciones en curso. Los resultados reales se añadirán al completar cada fase.
