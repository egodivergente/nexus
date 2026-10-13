# Continuar NEXUS

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.3-agent / vc37 y Windows 1.9.3-agent-desktop.

El 13 de octubre de 2026 se consolidaron Android de opciones visuales,
Windows de biblioteca/PDF y el botón Termux Nexus–AKI. Véase
`docs/RELEASE-193-CONSOLIDADA-20261013.md` y `MIGRATION-STATUS.json`.

Antes de trabajar, leer `AGENTS.md`, hacer fetch, comprobar versiones instaladas
y crear una rama propia desde `main`. No asumir que el directorio antiguo
`Documents/ChatGPT/NEXUS` contiene esta versión: conserva cambios de otras tareas.
La ruta de esta consolidación es
`C:/Users/User/.codex/worktrees/nexus-unified-release-193/NEXUS`.

Al terminar, revisar el diff, ejecutar verificaciones proporcionadas, crear commit,
publicar sin force y verificar el SHA remoto. Conservar trabajos ajenos. No instalar
ni ejecutar operaciones de pago por compilar o publicar una entrega.

La APK visual entregada aún no está instalada en el S25. El S25 lleva 1.9.3 previa
a ese ajuste; el EliteBook lleva Windows 1.9.3. Las pruebas físicas de las nuevas
opciones visuales siguen pendientes. El código del puente AKI y su lanzador están
versionados; su sesión y configuración activa viven en Termux, fuera de Git.
