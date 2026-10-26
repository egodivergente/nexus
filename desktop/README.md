# Desktop integrado 2026.10.26-desktop-modern.1

Edición Python/Edge para Windows con agente JVM, Corte, importación local de hasta 4 GiB, continuidad de dispositivos y la interfaz y catálogo del 26 de octubre. Consulta [procedencia y verificación](../docs/DESKTOP-MODERN-20261026.md). Los apartados siguientes documentan versiones anteriores.

# Actualización Desktop 1.9.6-agent-desktop · 25/10/2026

Actualización selectiva de conversaciones, catálogo y núcleo compartido. No implica paridad completa con Android 1.9.77. Ver [alcance, procedencia y pruebas](../docs/DESKTOP-UPDATE-20261025.md). Los apartados siguientes son históricos.

# Edición actual: Nexus Studio 1.9.3

La fuente actual está unificada en `main`. Consulta [biblioteca/PDF/visor](../docs/DESKTOP-LIBRARY-PDF-193.md),
[la Release consolidada](../docs/RELEASE-193-CONSOLIDADA-20261013.md) y [el README principal](../README.md).
Los apartados siguientes conservan la presentación histórica del escritorio de octubre de 2026.

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
