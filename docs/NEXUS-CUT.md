# Nexus Cut — arquitectura e integración de Corte

Editor multipista dentro de la APK de Nexus, manejable a mano y por el agente por el mismo camino. Este documento recoge solo las decisiones; el detalle está en el código de `cut-core/`.

## Fuentes (comprobadas el 2026-11-11)

| Proyecto | Base usada | Cómo se comprobó |
|---|---|---|
| Nexus | `feat/flow-live-20261108` @ `90d9a5a` (vc204, 1.9.168) | Es la rama más reciente de GitHub y coincide con `C:\Users\User\nexus-vc185` de AXELPC, sin cambios locales. `main` va muy atrás. `nexus-vc204-src-90d9a5a` es una copia sin Git de ese mismo commit. No está demostrada la identidad binaria con el APK instalado. |
| Corte | `feat/importar-y-modo-nexus-20261018` @ `4d35f32` | Contiene a `codex/recovery-20261006` y a `feat/puente-agente-nexus-20261018`. `C:\Users\User\Documents\corte` está en ese commit, sin cambios locales. |

Rama de trabajo: `feat/nexus-cut-20261111`.

## Acceso y agente dentro del editor — actualización 11/11/2026

Por petición posterior de Vidal, Nexus Cut tiene una sección propia en la barra inferior. Su estantería abre los montajes y el editor conserva la ruta inmersiva. El editor usa una apariencia oscura local y un chat persistente por montaje, separado del chat principal. Las órdenes quedan vinculadas al montaje y pasan por la misma sesión; la exportación del chat usa una capacidad JOB local. Detalles y validación: `docs/NEXUS-CUT-CHAT.md`.

La continuación vc207 convierte esa portada en el centro de proyectos: lee el índice actual de Nexus y agrupa sus montajes sin copiar ni reasignar archivos. Cada proyecto reúne Guion, Material, Generaciones y acceso a su conversación del agente. Los borradores se preparan sin enviarlos. El archivado reversible usa `AssetIndex.updateProjectMetadata`, que conserva todas las asociaciones; la operación legacy de eliminar una carpeta mantiene su contrato anterior. La portada usa los componentes y el tema de Nexus, y el editor oscuro hereda su acento. Plan, criterios y resultados: `docs/NEXUS-CUT-PROJECTS.md`.

## Camino único

```
Interfaz / S Pen / Agente → Request → CutSession (transacción sobre un borrador) → motor (TimelineEditor) → proyecto guardado → eventos → preview / exportación
```

- `CutSession` es el único escritor de un montaje. `CutWorkspace` entrega la misma sesión viva a la pantalla y al agente.
- Una petición es una transacción: todas sus operaciones o ninguna; un solo paso de «Deshacer», compartido y con origen (usuario, S Pen, agente).
- Garantías implementadas y probadas: ensayo (`dryRun`), control de revisión (`expectedRevision`), idempotencia que sobrevive al cierre (registro `actions.jsonl`), errores tipados con la operación que falló, guardado atómico antes de dar el cambio por hecho, eventos observables.
- `CutRegistry` publica cada capacidad con id estable, versión, tipo (lectura, edición, vista, trabajo largo), JSON Schema de entrada y salida, precondiciones, errores, requisitos de dispositivo, permiso y coste. El agente la descubre por dominio o intención y solo carga los contratos que usa.
- Tiempos: microsegundos enteros. Velocidades: racionales. Sin floats en el modelo.

## Qué se reutiliza de Corte

| Pieza de Corte | Decisión |
|---|---|
| `core-model`, `core-timeline`, `core-storage` | Íntegros en `cut-core` (`com.vidadomu.nexus.cut.*`) con sus 84 pruebas. Esquema de proyecto 2: solo añade vínculos con Nexus y revisión; los proyectos de esquema 1 se abren tal cual. |
| `AgentOps` (16 operaciones JSON) | Queda como utilidad del motor; el contrato público pasa a ser el registro de capacidades. |
| `History` | Sustituida por el historial de `CutSession`, que también deshace nombre, formato y medios. |
| `engine/` (CompositionFactory, PreviewController, StillFrameRenderer, audio, color, rótulos) | Se migra en el hito B. Requiere Media3 1.11.1, ya habilitado. |
| `export/ExportService` | Se migra en el hito B como trabajo persistente; el resultado entra en la Biblioteca. |
| `media/` (probe, miniaturas, formas de onda, LUT) | Se migra en el hito B leyendo archivos de la Biblioteca, sin copiarlos. |
| `EditorViewModel` | No se reutiliza: su papel lo cubre el bus. Su lógica de colocación ya vive en `timeline.place`. |
| `ui/` | Referencia de geometría y gestos; la interfaz se rehace con los tokens de Nexus. |
| `AgentBridgeProvider` y, en Nexus, `CorteBridge.kt` + herramientas `corte_*` | Transición. Se retiran cuando el editor integrado esté entregado y los proyectos migrados. |

## Dependencias

Media3 1.11.1 (la versión con la que Corte verificó su motor) declara `minCompileSdk=36` y `media3-effect` depende de `kotlin-stdlib` 2.2.10, ilegible para el compilador 2.0.21. Por eso Nexus pasa a Kotlin 2.3.20 y `compileSdk` 36; `targetSdk` (35), AGP (8.7.3) y Gradle (8.9) no cambian.

Media3 y el reproductor multipista: las notas de 1.11.0, 1.11.1 y 1.12.0-beta01 no anuncian arreglo del seek en pausa con varias secuencias. Se mantiene la solución medida por Corte en el S25 (fotograma en pausa compuesto en CPU y reproducción desde una composición que empieza en el cabezal) y su prueba testigo; subir de versión no se da por solución.

## Riesgos abiertos

- Los proyectos de Corte ya creados en el teléfono están en el almacenamiento privado de Corte. El formato es compatible, pero traerlos exige que Corte los exporte: su puente (protocolo 3) solo los describe. Pendiente de un método de exportación en Corte.
- `minSdk`: Nexus 26, Corte 29. El motor se revisa al migrarlo.
- Nexus Desktop tiene su propio Corte para Windows (`codex/desktop-audiovisual-20261109`). La vía prevista es compartir el formato de proyecto, no el motor.
