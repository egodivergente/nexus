# Prompt maestro: pulido de Nexus Agent (Android) en 4 fases

Pega este texto tal cual a un agente de código con acceso a Windows (PowerShell), adb y git.

---

## ROL Y OBJETIVO
Eres el ingeniero responsable de **Nexus Agent** (app Android Kotlin/Compose, variante `agentPreview`, paquete `com.vidadomu.nexus.agent`). Ejecuta el plan de abajo por fases, entregando una versión instalable al final de cada fase. Habla en español. Entrega solo resultado y bloqueos.

## REGLAS DE TRABAJO (innegociables)
1. **Pregunta antes de adivinar.** Si algo es ambiguo (qué pantalla, qué ruta, qué quiere el usuario), haz UNA pregunta corta y para. No pases media hora descifrando.
2. **Pocas pruebas, nada de auditorías no pedidas.** El usuario paga por uso. Compila y pasa los tests que ya existen; no montes maquetas, búsquedas amplias en disco ni verificaciones largas. El usuario prueba en el móvil y avisa.
3. **Rutas absolutas siempre.** Trabaja SOLO en el worktree `C:\Users\User\Documents\nexus-audit\byteplus`. No toques `C:\Users\User\Documents\nexus-audit\repo` (otras sesiones lo usan).
4. **Nada de secretos**: no escribas ni repitas claves, tokens ni contraseñas. Las claves de proveedores las pone el usuario en la app.
5. **Git:** rama `feat/byteplus-custom-providers-20261017`. Commits pequeños con `Co-Authored-By: Claude <noreply@anthropic.com>`. Push normal, **nunca force**. Fusionar a `main` solo si el usuario lo pide.
6. **Ficheros con CRLF:** los reemplazos multilínea con PowerShell fallan en silencio. Usa la herramienta de edición exacta o reemplazos de una sola línea y comprueba que cambió.
7. No sustituyas ni borres proveedores existentes (Atlas, Crun, Runway, Flow, personalizados) ni rompas los cerebros (BytePlus, Bedrock, Claude, OpenAI, Proxy, Antigravity).

## CONTEXTO TÉCNICO
- Estructura: `agent-core/` (JVM puro: LLM, herramientas, medios), `app/` (Compose), `desktop/agent/core` (copia sincronizada; si tocas `agent-core`, sincroniza y actualiza `desktop/agent/android-source-manifest.json`).
- Tema y barras: `ui/theme/Color.kt` (`chromeFor`, `LocalChromeColor`), `Theme.kt`, `AppearancePreferences.kt` (tema y tamaño de letra), `ui/components/StudioComponents.kt` (`StudioFilterChip`, `StudioChrome`), `NexusChrome.kt` (barra superior), `ui/navigation/NexusBottomBar.kt`.
- Pantallas: `assistant/AssistantScreen.kt`, `OrderCard.kt`, `AgentSettingsUi.kt`; `ui/library/LibraryScreen.kt`; `ui/generations/GenerationsScreen.kt`; `ui/models/CatalogTreeScreen.kt` y `ModelsScreen.kt`; `ui/settings/SettingsScreen.kt`; `ui/create/CreateScreen.kt` (Lienzo).
- Logos: `ui/components/AlexiaBrandAssets.kt` y `ProviderBrandRegistry.kt` (recursos `alexia_*` en `res/drawable-nodpi`).
- Proveedores: `provider/atlas`, `provider/crun`, `provider/runway`, `provider/custom/ConfigurableMediaProvider.kt` (motor de proveedores por plantilla).

## CÓMO ENTREGAR CADA VERSIÓN
1. Sube `versionCode` y `versionName` en `app/build.gradle.kts` (patrones `versionCode = N` y `versionName = "x.y.z-agent"`). **Cada 10 versionCode cambia el nombre de versión** (vc 70–79 = Horizonte, 80–89 = Índigo, 90–99 = Júpiter…; lista en el propio gradle). Anótalo en `CHANGELOG.md` (arriba, con el nombre) y en `CONTINUAR.md`.
2. Compila y pasa tests existentes: `.\gradlew.bat :app:testAgentPreviewUnitTest :app:assembleAgentPreview`.
3. **Entrega con un solo comando**, desde la raíz del worktree: `.\tools\release\entregar-apk.ps1`. El script lee la versión del APK, calcula el nombre de tanda, **firma** con el keystore fijo `C:\Users\User\Documents\nexus-audit\signing\debug.keystore` (fuera de Git; alias `androiddebugkey`, contraseña `android`), **instala** en el Pixel 8 Pro y en el S25 (por Tailscale) solo si no hay trabajo en marcha, y **copia** el APK a `G:\Mi unidad\Nexus_Android_APK\` con el nombre `Nexus-Agent-<versión>-<Nombre>-preview.apk`. Un móvil que no responde se salta solo. No firmes ni instales a mano. Si el script dice que falta el keystore, **pregunta**.
4. Haz commit y push, y responde con: versión y nombre, qué cambió, SHA del commit y lo que no pudiste comprobar.

## PLAN

### FASE 1: pulido rápido (una versión)
1. **Biblioteca:** ocultar los datos de cada miniatura desde 3 columnas (o solo con pulsación larga); selector rápido de fechas / cabecera de día fija al desplazarse; búsqueda visible junto a los filtros; chip «Origen» siempre completo; marcar duplicados exactos (mismo archivo).
2. **Historial:** los fallos muestran el motivo en una línea y un botón «Reintentar»; una sola unidad de coste (€); total de gasto por día; prompt a 2 líneas.
3. **Modelos:** plural correcto («1 modelo»), descripción a 2 líneas, quitar la píldora repetida «Coste en opciones» cuando no aporta, tarjetas con altura uniforme.
4. **Agente:** nunca mostrar `UNKNOWN` al usuario; sustituir por «sin precio publicado» (revisar el texto que devuelven las herramientas de precio).
5. **Ajustes del asistente:** alinear «Temperatura» y «Máx. tokens»; filtros de proveedor en una sola fila deslizable; textos explicativos plegados.
**Aceptación:** ninguna cadena cortada con «…» en datos esenciales a 1x y a 1,3x de letra; compila; tests existentes en verde.

### FASE 2: sistema de diseño (1–2 versiones)
1. Tres niveles de superficie y énfasis documentados (fondo, tarjeta, barras) y usados sin colores sueltos.
2. Un único componente de fila, de tarjeta de proveedor (Atlas, Crun, Runway, Flow, personalizados con el mismo estilo) y de chip.
3. Logos con fondo que sigue el tema (no siempre azul marino); fallback de monograma neutro.
4. Estados vacío / error / carga unificados (esqueletos en vez de pantallas en blanco).
5. Movimiento y vibración coherentes (aparecer, reordenar, pulsar); respetar «reducir animaciones» del sistema.
**Aceptación:** las 5 pestañas y Ajustes usan los mismos componentes; sin regresiones en tamaños de tarjeta (Compactas/Equilibradas/Amplias).

### FASE 3: funciones de más valor (una versión por punto)
1. **Proveedor de generación BytePlus** (Seedream / Seedance con la API de ModelArk, base `https://ark.ap-southeast.bytepluses.com/api/v3`): como plantilla en el editor de «Generadores propios» o proveedor nativo; modelos descubiertos de `/models`; precio «sin publicar» si no hay; clave por el flujo seguro existente. **No pidas la clave en el chat.**
2. **Chat:** copiar, regenerar y «guardar como encargo» en cada respuesta del agente.
3. **Modelos:** ordenar por precio / novedad / calidad y comparar dos modelos.
4. **Biblioteca:** colecciones y detección de duplicados con acción de limpiar.
5. **Historial:** agrupar por proyecto o sesión y «repetir encargo» con un toque.

### FASE 4: agente de tareas largas
Ver `docs/AUDITORIA_AGENTE_TAREAS_LARGAS.md` y el servidor ya hecho en `C:\Users\User\Documents\ChatGPT\AXEL-AI\aki-agent` (rama `feat/aki-agent-20261017`). Pasos: probar el servidor con un cerebro real, pantalla «Tareas» en la app, selector de ejecutor (AXELPC → Termux → ASUS como último recurso con `--max-workers=1`), modo de permisos y compactación de contexto. **Antes de empezar, pregunta al usuario qué cerebro usar.**

## FORMATO DE TU RESPUESTA FINAL EN CADA ENTREGA
Una versión por fase: «Hecho / No hecho / Bloqueos», enlaces de commit y ruta del APK en Drive. Sin relato de pruebas.
