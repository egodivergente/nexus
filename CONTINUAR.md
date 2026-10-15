# Continuar NEXUS

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.5-agent-preview / vc39 y Windows 1.9.5-agent-desktop.

El 13 de octubre de 2026 se consolidaron Android de opciones visuales,
Windows de biblioteca/PDF y el botón Termux Nexus–AKI. Véase
`docs/RELEASE-193-CONSOLIDADA-20261013.md` y `MIGRATION-STATUS.json`.
El 14 de octubre de 2026 se aplicaron las mejoras de la auditoría integral
y la corrección de la revisión de `b5401b8` en la versión `1.9.4-agent`.
El 15 de octubre de 2026 se publicó `1.9.5-agent` (`vc39` en Android y
`1.9.5-agent-desktop` en Windows) incorporando el logo oficial de NEXUS y el
número de versión en el preview/onboarding al abrir la app y en las cabeceras
principales tanto en móvil como en el EliteBook. Véase `CHANGELOG.md`.

Antes de trabajar, leer `AGENTS.md`, hacer fetch, comprobar versiones instaladas
y crear una rama propia desde `main`. No asumir que el directorio antiguo
`Documents/ChatGPT/NEXUS` contiene esta versión: conserva cambios de otras tareas.
La ruta de esta consolidación es
`C:/Users/User/.codex/worktrees/nexus-unified-release-193/NEXUS`.

Al terminar, revisar el diff, ejecutar verificaciones proporcionadas, crear commit,
publicar sin force y verificar el SHA remoto. Conservar trabajos ajenos. No instalar
ni ejecutar operaciones de pago por compilar o publicar una entrega.

La APK `1.9.5-agent-preview` (`versionCode 39`, `entregas/Nexus-Agent-1.9.5-preview.apk`)
está instalada en el Samsung S25 Ultra (`SM_S938B`) y en el Pixel 8 Pro conservando sus
datos, y el EliteBook ejecuta Windows `1.9.5-agent-desktop` desde
`%LOCALAPPDATA%\Programs\NexusStudio`. El código del puente AKI y su lanzador están
versionados; su sesión y configuración activa viven en Termux, fuera de Git.


