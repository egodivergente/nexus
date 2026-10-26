# Desktop integrado 26/10/2026

Versión: `2026.10.26-desktop-modern.1`. Edición Python/Edge Windows x64, Java 17.

## Procedencia y comportamiento

Base: `feat/windows-device-continuity-20261020` (`b236cae27d410d50f1342076f91d4ae99f485517`), coincidente con la instalación que abría el acceso directo del escritorio de AXELPC.

Integra los cambios Desktop de `c02f4d8`, `7674e38`, `2c1578d` y `889ef81`: conversaciones con identificación estable, búsqueda y títulos propios; controles y precios del catálogo; interfaz Crear, Modelos, Asistente y Ajustes; cierre del panel de estado; persistencia del índice y caché de skills; representación Markdown y contexto de vídeo medido.

Conserva Corte y su chat integrado, proyectos, edición y mezcla multipista, importación por bloques hasta 4 GiB, proveedores propios, Drive y continuidad entre dispositivos. Los adjuntos de conversaciones sincronizadas siguen visibles y descargables. El índice de conversaciones oculta únicamente la proyección textual derivada de Drive, sin borrar archivos ni fusionar conversaciones de distinto origen.

Gradle compila el núcleo de `agent-core/src/main/kotlin`; las mejoras de fiabilidad se han aplicado a esa fuente efectiva además de la copia histórica de Desktop. No implica paridad completa con todas las ramas Android.

El inventario actual tiene 81 herramientas. Los grupos `models` y `connections` mantienen todas accesibles con un máximo de 64 por petición. Archivos, proyectos y edición local siguen disponibles en ambos. `connections` incluye agente Flow e historial; `models` incluye el estado de las tarjetas. Se mantiene la ruta normal de permisos y ejecución.

## Verificación en AXELPC

- Compilación Gradle 9.1.0/JDK 17 con un trabajador y sin paralelismo: PASS.
- JVM: 423 casos; 419 aprobados, 4 omitidos; cero fallos. Incluye núcleo, Desktop, timeline y almacenamiento Corte.
- Interfaz NEXUS 10 en Edge con perfil aislado: 168 comprobaciones; cero errores JavaScript y cero solicitudes remotas.
- Panel de estado: 23 comprobaciones en Edge; PASS.
- Conversaciones: 18 casos Python, 15 comprobaciones JavaScript y prueba DOM; PASS. Incluye adjuntos y deduplicación de la proyección Drive tras reiniciar el índice.
- Catálogo: 9 comprobaciones JavaScript; PASS.
- Continuidad de dispositivos: 26 casos; PASS con fixtures, no constituye una nueva sincronización física.
- Integridad del host: 14 casos; PASS.
- Importación: copia por bloques autenticada en navegador, registro en Biblioteca y lectura HTTP Range; límites, integridad, cancelación, originales y edición local; PASS. Copia completa de un archivo real de 4 GiB: NO EJECUTADA en esta actualización.
- Corte: 7 casos de edición, seguridad transaccional, render/export y mezcla real; PASS. UI Edge con chat, reproducción de preview, navegación y controles multipista: PASS.
- Proyectos portables: 3 casos de ida/vuelta, recuperación y corrupción: PASS.
- Proveedor: host → DPAPI → JVM → servidor HTTP local → cambio de grupo → herramienta nativa → respuesta; PASS. Las 81 herramientas son alcanzables y ninguna petición supera 64.
- Identidad Corte: 44 fuentes verificadas contra `5869228ae887a439fc522c2cbf76c13e29a143b2`.

No se han enviado generaciones ni inferencias a proveedores de pago. Las pruebas de proveedor usan un servidor local. La nueva edición no convierte estas pruebas en una validación de cada modelo remoto.

## Reproducir y entregar

Usar Gradle 9.1.0 disponible en AXELPC:

```powershell
gradle -p desktop/agent :core:test test :corte-timeline:test :corte-storage:test runtime --max-workers=1 --no-parallel --no-daemon --console=plain
```

Copiar los JAR de `desktop/agent/build/runtime` a una carpeta `desktop/agent/runtime` vacía de la misma compilación. Empaquetar con `python desktop/tools/package-windows-corte.py --install`. El paquete contiene código y runtime; los datos y credenciales permanecen en `%LOCALAPPDATA%/NexusDesktop`. Cada archivo instalado se verifica por SHA-256 y el ZIP por CRC. La versión anterior se conserva.

Antes de activar, crear respaldo privado de estado, credenciales cifradas, conversaciones y metadatos. Verificar el paquete instalado con:

```powershell
python desktop/tools/test-modern-install.py --desktop <instalacion>/desktop --output <carpeta-de-informes-privada>
```

Actualizar los accesos del escritorio y Biblioteca con `desktop/install-local.ps1 -NoLaunch -AdditionalShortcut <acceso-biblioteca> -Port 63896`. Verificar que ambos apuntan a la misma instalación y que la sesión real anuncia la versión y raíz correctas. No incluir perfiles, claves, backups ni paquetes privados en Git.
