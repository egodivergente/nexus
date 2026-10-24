# Nexus / ArtBox y chat compacto - 2026-10-23

Rama `codex/nexus-master-artbox-20261023`, código Android `b588f7c`.
**Pixel 8 Pro: 1.9.73-agent-preview / vc107 instalada**, misma firma, actualización
en sitio y datos previos comprobados: 379 trabajos, 418 resultados, 1.132 archivos,
4 proyectos y 50 mensajes conservados. S25 no utilizado en este encargo.

Cinco destinos nativos: Agente, ArtBox, Lanzadera, Flow, Biblioteca. El chat oculta
pestañas y nombre del proyecto por defecto; el menú del compositor permite abrir
conversaciones y cerrar pestañas con X sin borrar historial/borradores. Biblioteca
filtra por uno o varios proyectos, incluidos archivos sin proyecto.

955 pruebas unitarias PASS, 4 omitidas; Pixel: 8 casos de workspace/multimedia y
6 de galería/contratos PASS. Puente Flow: 92 pruebas y 4 casos nuevos de borradores
PASS. Hay descarga real de un resultado existente, pero **no está validada una
generación nueva de Flow desde el Pixel**: la VPN de Google impide la ruta privada.
Pendiente respuesta a la autorización para usar Tailscale temporalmente y restaurar
la VPN. No cambiarla por silencio ni reenviar los registros inciertos.

702 destinos con datos de contrato/metadatos y evaluación de controles. No confundir
ese índice con un benchmark propio de calidad visual; las notas personales son aparte.
Ver `docs/NEXUS-MASTER-20261023.md` para APK, hash, fuentes, pruebas y pendientes.
Preview, no aceptación completa del paquete maestro. Estados inferiores históricos.
`gradlew.bat` conserva cambios anteriores ajenos; no incluirlos en esta entrega.

# Integración Studio + segundo plano + Drive - 2026-10-20

Fuente unificada `401c5ac`, rama `codex/agent-studio-integration-20261020`.
**S25 Ultra: 1.9.63-agent-preview / vc97 instalada**, misma firma, datos e IDs conservados.
Pixel todavía vc96 background: pendiente recuperar ADB para repetir QA e instalar97.
707 pruebas unitarias PASS, 4 omitidas. Diez casos físicos Studio previos PASS en Pixel;
la reconciliación background se ha compilado y requiere la repetición física conjunta.
Continuidad real S25 vc97 ↔ Windows PASS: retorno cifrado, claves, skill ida/vuelta, 20/20 adjuntos.
Para device_sync_configure, enviar peer explícito al reactivar; su omisión lo vacía.
Ver `docs/AGENT-STUDIO-INTEGRATION-20261020.md` para procedencia, APK y límites.
No instalar una variante vc96 sobre la nueva97. Los estados inferiores son históricos.

# Agente Android en segundo plano - 2026-10-20

Estado comprobado: **Pixel 8 Pro y S25 Ultra, 1.9.62-background-preview / vc96**,
actualizados sobre vc95 con firma compatible y datos comprobados. Rama
`codex/android-agent-background-20261020`; conserva Drive y continuidad de
`c650f14` e incorpora controles de evidencia visual. Núcleo: 284 PASS, 4 omitidas.
Pixel físico: 4/4 pruebas del servicio y ciclo de vida PASS con proveedor simulado.
Ver `docs/AGENT-BACKGROUND-1962-20261020.md` para APK, firma y límites de validación.
Los estados siguientes son históricos; no instalar una APK anterior sobre vc96.

# Continuidad entre dispositivos - 2026-10-20

Rama `feat/device-continuity-20261020`, checkout `C:/Users/User/Documents/nexus-audit/agent-ux`.
Codigo vc93; validacion fisica bidireccional sobre la integracion vc94 instalada
 en S25 y Pixel. Pruebas: 617 PASS, 4 omitidas, 2 Node PASS.
Ver `docs/DEVICE-CONTINUITY-1959-20261020.md`.
Drive usa `codex/nexus-drive-sync-android-20261020` y prepara una APK posterior.
No instalar vc93 sobre esa integracion. Credenciales y backups fuera de Git.
`gradlew.bat` contiene trabajo ajeno y no se incluye en esta entrega.
Los estados siguientes se conservan como historial, no como estado actual.

# Continuar NEXUS

Actualización comprobada 2026-10-20 (CODEX): **S25 1.9.58-agent-preview / vc92**, consumo BytePlus nativo integrado y puente ASUS administrativo ampliado. Rama `codex/byteplus-consumption-20261020`, reconciliada desde `a4088da` (1.9.57/vc91 instalada realmente). Véase `docs/BYTEPLUS-CONSUMPTION.md` para fuentes, firma, pruebas, despliegue y límites de validación. Las versiones antiguas que siguen abajo son antecedentes históricos.

Fuente canónica: https://github.com/egodivergente/nexus, rama `main`.
Estado actual: Android 1.9.34-agent-preview / vc68 (BytePlus, cerebros y generadores propios, límites del usuario) en el Pixel; el S25 sigue en 1.9.18/vc52 (no estaba alcanzable). Rama eat/byteplus-custom-providers-20261017 (sobre la de Antigravity eat/agy-provider-s25-20261016), sin fusionar en main; Windows 1.9.5-agent-desktop.

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


El 24 de octubre de 2026 se aplicó el paquete de correcciones UX/UI (ArtBox, Lanzadera, Flow, Biblioteca, «Continuar en…») en la rama `ux/correcciones-capturas-20261024` sobre `83db30a` (1.9.73/vc107): Android `1.9.74-agent-preview`/vc108 instalada en el Pixel 8 Pro con los datos conservados. Detalle, pruebas (app 659, agent-core 313) y límites en `docs/UX-CORRECCIONES-20261024.md`. Pendiente: compositor colapsable en el visor de Biblioteca, dependencias de campos de otros modelos, pruebas instrumentadas y generación nueva en Flow desde el Pixel. El S25 no se ha tocado.
