# Continuar NEXUS

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.4-agent / vc38 y Windows 1.9.4-agent-desktop.

El 13 de octubre de 2026 se consolidaron Android de opciones visuales,
Windows de biblioteca/PDF y el botón Termux Nexus–AKI. Véase
`docs/RELEASE-193-CONSOLIDADA-20261013.md` y `MIGRATION-STATUS.json`.
El 14 de octubre de 2026 se aplicaron las mejoras de la auditoría integral
y la corrección de la revisión de `b5401b8` en la versión `1.9.4-agent`
(`job_id` explícito en envíos concurrentes de Windows, preservación de PDFs
mixtos con páginas escaneadas o gráficas, verificación SHA-256 y recuperación
de copias locales en Flow, separación entre «Detener seguimiento» local y
«Cancelar generación» remota en Android, y verificación de accesos directos y
raíz de instalación activa en Windows). Véase `CHANGELOG.md` y
`docs/MEJORAS-AUDITORIA-20261014.md`.

Antes de trabajar, leer `AGENTS.md`, hacer fetch, comprobar versiones instaladas
y crear una rama propia desde `main`. No asumir que el directorio antiguo
`Documents/ChatGPT/NEXUS` contiene esta versión: conserva cambios de otras tareas.
La ruta de esta consolidación es
`C:/Users/User/.codex/worktrees/nexus-unified-release-193/NEXUS`.

Al terminar, revisar el diff, ejecutar verificaciones proporcionadas, crear commit,
publicar sin force y verificar el SHA remoto. Conservar trabajos ajenos. No instalar
ni ejecutar operaciones de pago por compilar o publicar una entrega.

La APK `1.9.4-agent-preview` (`versionCode 38`, `entregas/Nexus-Agent-1.9.4-preview.apk`)
está instalada en el Samsung S25 Ultra (`SM_S938B`) y en el Pixel 8 Pro conservando sus
datos, y el EliteBook ejecuta Windows `1.9.4-agent-desktop` desde
`%LOCALAPPDATA%\Programs\NexusStudio`. El código del puente AKI y su lanzador están
versionados; su sesión y configuración activa viven en Termux, fuera de Git.

