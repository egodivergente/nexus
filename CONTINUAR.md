# Continuar NEXUS

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.7-agent-preview / vc41 en el S25 y Windows 1.9.5-agent-desktop.

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

El Samsung S25 Ultra (`SM_S938B`) ejecuta `1.9.6-agent-preview` (`versionCode 40`),
con la cuota fija de tokens corregida y sus datos conservados. El Pixel 8 Pro mantiene
`1.9.5-agent-preview` (`versionCode 39`). El EliteBook ejecuta Windows `1.9.5-agent-desktop` desde
`%LOCALAPPDATA%\Programs\NexusStudio`. El código del puente AKI y su lanzador están
versionados; su sesión y configuración activa viven en Termux, fuera de Git.



El 15 de octubre se corrigió e instaló en el S25 la cuota fija de tokens en 1.9.6/vc40.
Véase docs/AGENT-TOKEN-QUOTA-FIX-20261015.md. El Pixel continúa en su versión anterior;
esta tarea no actualizó Windows ni Pixel.

El 16 de octubre se instaló en el S25 Android 1.9.7-agent-preview/vc41 con temas
cálidos, importación y acciones de Biblioteca, búsqueda entre modalidades, edición
de archivos, PDF con imágenes, análisis de audio local, upscale y revisión visual,
memoria de proyecto consultable y menor repetición de contexto. Véase
docs/EXPERIENCE-197-20261016.md. No se ejecutaron pruebas adicionales tras la
petición del usuario; la compilación final de la APK sí terminó correctamente.
