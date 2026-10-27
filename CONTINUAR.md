# Segunda entrega ArtBox - 2026-10-24

Rama segura `codex/artbox-integral-2-20261024`, base local `13fa404`.
Ver `docs/ARTBOX-SECOND-DELIVERY-20261024.md`: acciones de chat/encargos,
restauración, punto del compositor, gestos, logos persistentes y selección experta.
Fal e inferencia local completa NO se declaran terminados. Pruebas UI requieren
instalación y quedan sin ejecutar por prohibición expresa. No publicar esta rama.
Conservar cambios ajenos de `gradlew.bat` y los demás worktrees.
Validación final: app 677 PASS; agent-core 312 PASS y 4 omitidas; APK y
compilación de tests UI PASS. APK SHA-256
`531EFEA3BF4CB3DAD27463604760AE180C66D9D72E1738F1479D3B83EA0C794B`.
Sin instalación ni push. Fal e inferencia con pesos/S Pen real siguen pendientes.

# ArtBox: editor local y servicio Atlas - 2026-10-24

Continuación en rama aislada `codex/artbox-local-inpaint-atlas-20261024`,
sobre `a9a91ec`; base remota ArtBox `d21cfe1`, no el main antiguo.
Editor real de máscara/S Pen, descarga con hashes y puente nativo SD 1.5 Inpainting;
servicio Atlas oficial para contratos/endpoints/cotización con fuente y fecha;
límites de prompt contractuales, tarjetas estrechas y fullscreen tras teclado.
Ver `docs/ARTBOX-LOCAL-INPAINT-ATLAS-20261024.md`: distingue código implementado,
pruebas ejecutadas y aceptación generativa/visual pendiente en S25.
672 pruebas unitarias PASS. APK final compilada e inspeccionada, SHA-256
`B5471C3680E84607660509A4E5F2CFA05B9B57DDED2C676363E30DA61E76B885`.
Motor/JNI/Whisper cargan en S25; inferencia NO EJECUTADA: 1,8–2,1 GiB libres
frente a la reserva de seguridad de 3 GiB. No afirmar incompatibilidad del hardware.
Se terminó primero la APK anterior.
El S25 tiene `com.vidadomu.nexus.agent` 1.9.75 / vc109, actualizado a las 11:34:59;
no equiparar esa instalación con la procedencia de esta rama ni sobrescribirla.
No se instalan APK ni se publica/pushea nada por orden expresa.
Mantener fuera del commit los cambios ajenos de `gradlew.bat`.

# ArtBox: correcciones locales de catálogo - 2026-10-24

Rama aislada `codex/artbox-catalog-fixes-20261024`, base `d21cfe1` de la rama
ArtBox documentada. Ocultación persistente al refrescar, categorías de Herramientas,
jerarquía independiente de ordenación, fullscreen y medidas de tarjetas.
Ver `docs/ARTBOX-CATALOG-20261024.md` para pruebas y evaluación Atlas CLI/inpainting.
656 pruebas unitarias de app PASS, incluidas 6 regresiones nuevas; validación visual
de fullscreen y tarjetas en Android pendiente. Atlas CLI bloqueado por Control de aplicaciones.
Sin instalación, publicación ni créditos por instrucción expresa de esta tarea.
No incorporar `gradlew.bat`: presenta diferencias de finales de línea ajenas al parche.

# Flow: conexión y recuperación - 2026-10-24

Código `5a450cf`, rama `codex/nexus-master-artbox-20261023`, APK propia
**1.9.74-agent-preview / vc108**. Corrige el corte de 5 s hacia AXELPC, evita la
consulta duplicada y conserva el borrador durante la renovación de sesión.
4 pruebas HTTP, 4 de configuración y 4 nuevas regresiones de recuperación PASS;
19 casos de backend existentes PASS. Recuperación real devuelve ready.

**No está validada una generación nueva.** El único intento propio falló antes
de pulsar Generar en Google (0 envíos). Posteriormente se desplegó la corrección.
El Pixel recibió otra APK 108 diferente y cambió de pantalla durante las acciones:
no sobrescribirla sin reconciliar su procedencia; se pidió dejar el teléfono libre.
**Google VPN sigue pendiente de restaurar**; la última lectura confirma Tailscale
activo. El usuario autorizó solo el cambio temporal y la restauración posterior.
Priorizar la restauración, no afirmar que se completó, ni reenviar trabajos inciertos.
Ver `docs/FLOW-PIXEL-1974-20261024.md` para hashes, pruebas y continuación.
Los estados siguientes se conservan como historial.

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

27/10/2026 — Rama `codex/nexus-seven-improvements-20261027`, sobre `42b8d8e`
(Android 1.9.97/vc133), entrega de código 1.9.98/vc134 para NEX-UX-001–007.
Chat, botón de voz, aurora, hover y pareja de fotogramas implementados; NEX-UX-006
parcial: protección de reenvíos integrada y motor de enrutamiento aislado, pendiente
adaptación comercial con evidencia exacta. No instalado: puente S25 offline y firma
privada no disponible aquí. Consultar `docs/NEXUS-SEVEN-IMPROVEMENTS-20261027.md`
para alcance y validación; no inferir una actualización del teléfono.

La captura del usuario `74858.jpg` confirma S25 SM-S938B con Nexus 1.9.97/vc133
y Android 17/API 37 antes de esta entrega; no es una comprobación ADB en vivo.
