# NEXUS Desktop

Edición Windows de NEXUS, con catálogo de modelos, formularios, comparación y biblioteca. El host usa C#/.NET Framework y WebView2; la interfaz está en `ui/`.

## Ejecutar

`Nexus.exe`, la DLL de WebView2 y la carpeta `ui` deben permanecer juntos. Se requiere Windows x64, .NET Framework compatible y WebView2 Runtime.

En la entrega documentada del 2 de octubre, la compilación pasó y Smart App Control bloqueó el ejecutable sin firma de editor confiable. La revisión en Edge con datos de laboratorio no valida el host nativo. La firma y el arranque nativo siguen pendientes en ese informe.

## Datos

Las credenciales se guardan con DPAPI para el usuario de Windows, fuera del código y del ejecutable. El historial importado conserva referencias; no implica sincronización de los archivos multimedia del teléfono. Los presets y todas las preferencias de Android tampoco se migraron en esa entrega.

El catálogo documentado tiene 838 registros. Es una instantánea: los endpoints, las opciones y los precios deben comprobarse con el proveedor antes de enviar una generación.

## Desarrollo

Código: `src/Program.cs`, `ui/app.js`, `ui/engine.js` y `ui/style.css`. La compilación se define en `build.ps1`; las pruebas del motor, en `tools/test-engine.js`. Ejecutar la compilación mediante `ecosystem-build` según las instrucciones del proyecto.

El informe de la entrega registra 20 pruebas del motor sin llamadas de red y una revisión de UI en Edge. Quedan la ejecución nativa, el selector de archivos, DPAPI desde el host y las respuestas reales de proveedores. Las pruebas de catálogo no acreditan compatibilidad individual de todos los modelos.
