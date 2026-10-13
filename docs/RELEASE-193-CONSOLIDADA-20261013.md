# Fuente y Release unificadas — 13 de octubre de 2026

## Procedencia

Se unificaron mediante merge, sin conflictos y sin reescribir historial:

| Componente | Rama conservada | Commit de procedencia |
|---|---|---|
| Android y opciones visuales | `codex/visual-model-options-20261012` | `7d45857b6cfe8f85735f3a306651ba94ec6a3890` |
| Windows: biblioteca, PDF y visor | `codex/desktop-library-pdf-193` | `fde6de635521f3ed0e9cdefde8fce70c51acf7bf` |
| Botón Termux Nexus–AKI | `codex/nexus-aki-widget-20261012` | `1d8c0ec7648d67628c263816a62f970fa53bdaaa` |

`main` pasa a ser la rama canónica y predeterminada. Las ramas anteriores se
conservan. README, CONTINUAR y MIGRATION-STATUS describen la fuente unificada.
Los manifiestos de recuperación inicial se conservan como documentos históricos.
No se incorporaron los cambios sin commit de worktrees de otras tareas.

## Validación de la unión

- Android: 512 archivos de código/configuración coinciden con el archivo de
  fuentes utilizado en la compilación visual existente. No cambió código Android
  al consolidar. Se releyeron sus informes: 276 pruebas unitarias aprobadas, cero
  fallos, errores u omisiones. No se afirma una compilación nueva de esta unión.
- Windows: se conservó el código funcional de 1.9.3. Se ejecutaron de nuevo las
  nueve pruebas de integridad del host: PASS. La regresión de biblioteca/PDF/visor
  volvió a pasar: extracción real de 21 páginas, conservación del original,
  fallback de escaneados, progreso/ETA, navegación y reemplazo de tareas activas.
- La documentación del escritorio se actualizó a 1.9.3 y se añadió al paquete.
  Los JAR del runtime se reutilizaron del escritorio ya compilado; no se recompiló
  JVM ni se cambiaron las herramientas del agente.
- No se instalaron APK, no se cambiaron datos/configuración de aplicaciones y
  no se realizaron llamadas de generación o de LLM de pago.

## Entregables

[Release v1.9.3-agent](https://github.com/egodivergente/nexus/releases/tag/v1.9.3-agent),
marcada como prerelease porque Android sigue siendo `agent-preview`:

- `NEXUS-Agent-1.9.3-opciones-visuales.apk`, paquete `com.vidadomu.nexus.agent`,
  vc37, SHA-256 `41902c9adec2d2746a8a3ddaefcca7c650d40ba23af974558958e901baef9720`.
  Se reutiliza la APK visual ya compilada y firmada con el certificado existente.
- `Nexus-Studio-Agent-1.9.3-Windows.zip`, reempaquetado con runtime y documentación.
- `SHA256SUMS.txt`, con hashes de los archivos exactos publicados.
- Source code ZIP/TAR generados por GitHub a partir del commit unificado.

## Límites

La APK visual está compilada y firmada, pero no instalada en el S25; la prueba
física de sus nuevos controles sigue pendiente. La edición Windows funcional 1.9.3
sí estaba instalada; esta tarea sólo publica un paquete nuevo con documentación.
Sol requiere el puente autenticado del S25 y Tailscale para acceder desde Windows.
Whisper usa runtime y pesos locales aparte del ZIP. No se acredita aquí una prueba
de generación de pago, todos los modelos ni una sincronización continua entre apps.
