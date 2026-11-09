# Nexus Desktop audiovisual · 9 de noviembre de 2026

Esta entrega actualiza la edición Windows `2026.10.29-desktop-premium.6` desde su fuente real `0c92537c`. Conserva Corte/Montaje, la biblioteca, originales, conversaciones, credenciales DPAPI y las integraciones propias de Windows. No sustituye Desktop por el directorio Desktop antiguo de la rama Android.

## Funciones conectadas

- **Guion** es una sección principal, junto a Agente, ArtBox, Biblioteca, Flow y Montaje. Guion, Storyboard y Material usan el mismo `assistant/index.json` mediante las transacciones audiovisuales compartidas con Android (`c4cb3675`). No hay una base de datos paralela de guiones.
- Edición directa de escenas, acción, diálogo, ideas y planos; importación de texto; reordenación; referencias, personajes, continuidad, propuestas, preferencias y tareas. Los borradores conservan su revisión base y se restauran aunque cambie el puerto del host.
- Las herramientas reales del agente leen y modifican ese proyecto. El alcance queda capturado por turno. Las operaciones concurrentes del editor tienen respuestas separadas por `requestId` y detectan conflictos; no interrumpen ni repiten un turno del agente.
- Los planes y trabajos iniciados desde un plano conservan el contexto inmutable y las referencias realmente utilizadas. Comparar modelos y reutilizar un trabajo preserva su origen explícito. El resultado no se atribuye a la última pantalla abierta. El contexto local no entra en el payload del proveedor.
- ArtBox conserva sus formularios existentes y utiliza la selección de vídeo aceptada en Android. Las rutas retiradas siguen disponibles en historial; los nuevos envíos se validan antes de subir o generar. Los identificadores ocultados por el usuario se respetan. No se incorpora un sistema nuevo de fallback automático de proveedores.
- **Flow** conecta explícitamente con el servicio Flow Live existente de AXELPC, mediante un proxy del mismo origen protegido por sesión. Permite ver y controlar la pestaña, descargar resultados e importarlos en Biblioteca/Por colocar. No se adjudica un resultado a un plano sin una relación verificable.
- El transporte `.nexus.zip` incluye guion y asociaciones múltiples, remapea identidades y conserva originales. La copia importada queda inactiva: no transfiere permisos de gasto, ejecuciones activas ni recibos de ejecución.
- Biblioteca proyecta también los usos del guion y sus distintos proyectos sin duplicar el archivo. Los usos narrativos se cambian desde Guion → Material; mover su carpeta desde Biblioteca se rechaza con esa indicación para evitar alterar solo una asociación antigua.
- El núcleo rechaza una escritura si otro proceso o una restauración han cambiado el índice desde su carga. La escritura utiliza bloqueo compartido, huella del contenido, temporal independiente y sustitución atómica.

## Verificación

Compilación real del runtime JVM de Desktop y pruebas de núcleo audiovisual, concurrencia, preservación del índice, lectura PDF y Corte. No es una compilación ni una prueba de la APK Android.

Recorridos reproducibles con perfiles temporales y el runtime real:

```powershell
python desktop/tools/test-desktop-story.py --output outputs/story-ui-qa
python desktop/tools/test-modern-install.py --output outputs/modern-ui-qa
python desktop/tools/test-project-portable.py
python desktop/tools/test-story-host.py
python desktop/tools/test-host-integrity.py
python desktop/tools/test-host-lifecycle.py
python desktop/tools/test-agent-host.py
python desktop/tools/test-current-catalog-policy.py
node desktop/tools/test-current-catalog-policy.js
node desktop/tools/test-story-generation.js
```

`verify-flow-live-readonly.py` comprueba por separado el servicio real, un fotograma JPEG, la lista de resultados, el cierre de conexión y las restricciones de sesión. No envía entradas, órdenes ni generaciones. Los recorridos del editor bloquean el tráfico externo y no ejecutan llamadas a modelos LLM.

El paquete se crea con `desktop/tools/package-windows-corte.py --install`. Incluye un manifiesto de hashes y se instala en un directorio nuevo. La activación es posterior a las pruebas: comprobar inactividad, guardar ambos índices si memoria y disco difieren, respaldar metadatos, detener solo el host identificado y su JVM, preservar los registros de ambos sin sobrescribir los de disco y verificar la nueva instancia y el acceso directo. Los perfiles, credenciales y evidencias privadas nunca se empaquetan ni se publican.

## Límites comprobados

La sincronización Drive existente sigue usando su protocolo anterior de proyectos, material y conversaciones. El diario del S25 actual no admite nuevas claves de entidades del guion; activarlas unilateralmente haría que rechazase el intercambio completo. Por ello **no se promete sincronización automática del guion completo entre Windows y S25** en esta entrega. El guion local se conserva al recibir sincronizaciones antiguas y puede transportarse íntegro mediante `.nexus.zip`.

Las pruebas no incluyen generaciones facturables, llamadas LLM de prueba ni una validación individual de todos los contratos de proveedor. Las rutas sin esquema accesible continúan mostrando esa limitación; no se inventan parámetros ni precios. El servicio Flow Live, su sesión y las credenciales existentes no se reemplazan ni se despliegan de nuevo.
