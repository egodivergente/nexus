# Control de proyectos y Biblioteca por el agente

Petición de Vidal del 11/11/2026, posterior a la entrega vc207: poder ordenar una migración entre proyectos, comprobarla y retirar el origen; buscar en toda la Biblioteca y mostrar el material en la interfaz. «Mateo → Madrid no duerme» se toma como ejemplo de capacidad, no como orden de mover los proyectos reales durante el desarrollo.

## Plan de ejecución

1. Reutilizar las herramientas reales de búsqueda, inspección y muro de resultados. Completar su conexión en el chat de Cut y unificar la pertenencia de archivos a proyectos con los vínculos compartidos del índice.
2. Añadir inventario y huella previa a una transferencia de proyecto. Mover las referencias actuales en una escritura atómica del índice, conservando IDs, URIs, contenido, procedencia e historial.
3. Reasignar cada montaje mediante `CutSession` y su comando existente `project.link_story`, con revisión e idempotencia. Guardar el progreso; una interrupción no debe dar por terminada la operación.
4. Verificar el índice y todos los montajes antes de retirar el origen. La retirada será un archivado recuperable, expresado así al usuario. Ofrecer deshacer con comprobaciones que eviten sobrescribir cambios posteriores.
5. Probar fallos, reanudación, aislamiento y deshacer. Ejecutar en Pixel con proyectos de prueba y herramientas reales sin llamadas de pago; compilar y entregar vc208 con firma y datos conservados.

## Alcance y límites

El agente trabaja sobre los mismos repositorios que la interfaz. Los archivos físicos no se borran ni duplican para reorganizar proyectos. La transacción del índice y las transacciones de cada montaje son distintas: el registro de progreso y la verificación final coordinan el conjunto. No se afirmará atomicidad global ni éxito de pasos no confirmados.

La búsqueda por nombres, prompts y metadatos no equivale a visión o audición de todo el contenido. Mostrar los resultados no los añade por sí solo al montaje ni los envía a un proveedor. El muro permite abrirlos y añadirlos mediante una acción explícita.

Estado: implementación y verificación en curso.
