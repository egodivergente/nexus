# Continuar NEXUS

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.24-agent-preview / vc58 (BytePlus, cerebros y generadores propios, límites del usuario) en el Pixel; el S25 sigue en 1.9.18/vc52 (no estaba alcanzable). Rama eat/byteplus-custom-providers-20261017 (sobre la de Antigravity eat/agy-provider-s25-20261016), sin fusionar en main; Windows 1.9.5-agent-desktop.

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

El 16 de octubre se instaló en el S25 Android 1.9.8-agent-preview/vc42. Mantiene los
turnos del asistente al minimizar Nexus, corrige el retorno desde Ajustes/Flow a la
pestaña elegida y representa en el chat los medios abiertos o creados por el agente.
El APK se instaló encima de vc41 con firma compatible y datos conservados. No se
ejecutaron pruebas conversacionales ni controles de esos flujos por petición del
usuario. Véase `docs/AGENT-BACKGROUND-TURN-198-20261016.md`.

El 16 de octubre, en la rama `fix/encargo-unico-agente-ui` (main + 1.9.7 + 1.9.8), se
reconstruyeron el chat y las tarjetas (un encargo por tarjeta, estado compartido
UI↔agente, autocorrección de afirmaciones falsas) y el contrato de Tencent Video
Upscaler (sin prompt, `resolution` deducida, `fps` medido, tarifa por nivel). Se
integró la rama de Historial (`codex/android-ux-polish-20261016` @ `4512d93`, Biblioteca
sin etiquetas de origen) y se quitó el flag `(?U)`, que cerraba la app en Android.
Android `1.9.10-agent-preview`/vc44 (SHA-256 `4D18F43A…E5B5`, firmada con el
`debug.keystore` del ASUS) está instalada en el S25 con datos conservados. Pruebas:
agent-core 154 y app 295. Atlas completó un upscale real con `resolution=4k` + `fps`
y sin prompt. Pendiente: comprobar en el teléfono una respuesta del agente tras el
arreglo de `(?U)`. Véase `docs/ENCARGO-UNICO-199-20261016.md`.

Después, en la misma rama: el agente respondió en el S25 sin cierres tras quitar `(?U)`
(vc46). Hay skills para los 400 generadores del catálogo y se puede elegir LLM: Bedrock,
Claude API, compatible con OpenAI o proxy. Los dos proveedores nuevos solo se han probado
con un servidor simulado. También hay una revisión de todas las pestañas con capturas
(vc48). En 1.9.16/vc50 (SHA-256 `67D5240A…77FB`, clave del ASUS) la Biblioteca vuelve a
mostrar al pie de cada miniatura la resolución, el tamaño y la fecha y hora, y tiene el
filtro «Origen ▾» (Generado por Nexus / Importado por ti) sin etiquetas sobre las
miniaturas. Está instalada con los datos conservados en el S25 y en el Pixel 8 Pro, que
volvió a estar disponible por ADB inalámbrico y pasa de vc39 a vc50. La Biblioteca y el
filtro se verificaron en el Pixel. Después (1.9.17/vc51 y 1.9.18/vc52): marcado rápido y
exportación a ZIP, hoja única para archivos importados («Usar en Lienzo», «Al Agente»),
micrófono con el reconocedor del sistema, botones de respuesta rápida del agente
(`suggest_replies`), trabajos atascados con acción y fallos repetidos plegados, y
`desktop/agent/core` sincronizado (el host de Windows solo pinta progreso). Pruebas: app 309,
agent-core 165, núcleo de escritorio 165. Pendiente: probar las claves reales de los LLM nuevos,
capturar Modelos, la hoja única y el micrófono en el S25 (estaba en uso) y pintar encargos en Windows.

El 17 de octubre se añadió BytePlus ModelArk (Responses API, modelo dola-seed-2-1-turbo-260628), proveedores LLM y multimedia definidos por el usuario y límites configurables en todo el agente (ver CHANGELOG 1.9.23). Pendiente: **activar el modelo en la consola de Ark** (la cuenta devuelve ModelNotOpen) y repetir BytePlusLiveTest con la clave real; instalar en el S25 cuando esté conectado; y comprobar en pantalla que los modelos de un generador propio aparecen en el Lienzo.
