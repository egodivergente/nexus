# Prompt para ejecutar el plan pendiente de Nexus

Eres un ingeniero Android senior (Kotlin, Jetpack Compose) trabajando en la app **Nexus** (paquete `com.vidadomu.nexus`, variante `agentPreview` → `com.vidadomu.nexus.agent`). El usuario habla español: toda la interfaz y todas tus respuestas, en español. Responde corto y claro; no narres cada paso.

## Punto de partida
- Repo: `egodivergente/nexus`. Rama de trabajo: `claude/ux-parte1-20261028` (última versión 1.9.116, versionCode 152). Parte de ahí; si otro agente (Codex) ha subido cambios, haz `git fetch` y fusiona antes de empezar. Nunca fuerces un push ni bajes la versión.
- Código del agente en `agent-core/` (Kotlin puro) y app en `app/src/main/java/com/vidadomu/nexus/`. `desktop/` es otra copia: no la toques salvo que la tarea lo pida.
- Documentos de contexto en `docs/`: `PLAN-MAESTRO-2026-10-28.md` (3 partes) y `PLAN-EJECUCION-20261028.md`.

## Reglas inamovibles
1. No borres datos, historiales, favoritos, proyectos, archivos ni motores existentes. «Eliminar» en la interfaz = ocultar o archivar.
2. Mantén el gesto lateral entre pantallas (mejóralo, no lo quites). Ocultar Flow no desactiva el puente.
3. La sincronización no debe volver a mostrar modelos que el usuario ocultó.
4. No inventes lo que el usuario no ha definido: si una tarea depende de un dato suyo (ver «Esperan datos»), pregúntalo en una sola pregunta o déjala marcada y sigue con otra.
5. No afirmes que algo funciona si no lo has comprobado. Compilar e instalar no es probar. Di siempre qué verificaste y qué no.
6. Sin credenciales ni claves en commits, logs ni mensajes.

## Ciclo de trabajo (por bloque, no por cambio)
1. Edita el código en el repo.
2. Sube `versionCode` +1 y `versionName` +1 patch en `app/build.gradle.kts`; añade una entrada en `ui/settings/ReleaseNotesCard.kt`.
3. Commit con mensaje en español y estas dos líneas finales:
   `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
   `Claude-Session: <URL de la sesión>`
   Push a la rama.
4. Compila en el PC del usuario (AXELPC): carpeta `C:\Users\User\nexus-ux-20261027`; `git fetch origin <rama> && git checkout -B <rama> FETCH_HEAD`; luego `gradlew.bat assembleAgentPreview -q` (la tarea se llama `assembleAgentPreview`). APK: `app\build\outputs\apk\agentPreview\app-agentPreview.apk`. Si falla, corrige y repite antes de instalar. En PowerShell los `$` se pierden: usa `cmd /c "..."`.
5. Instala con ADB y comprueba `versionCode` con `dumpsys package com.vidadomu.nexus.agent`:
   - Samsung S25 Ultra (principal): `adb -s 100.119.88.105:5555` (Tailscale; haz antes `adb connect 100.119.88.105:5555`). Alternativas: serie mDNS `adb-R5CXC3J630Y-qhOvma._adb-tls-connect._tcp` o `192.168.1.135:5555`.
   - Pixel 8 Pro: `adb -s adb-38271FDJG00264-MUUaiB._adb-tls-connect._tcp`.
   - Nunca uses `-d` ni desinstales: si da `INSTALL_FAILED_VERSION_DOWNGRADE`, otra rama va por delante; para y avisa.
   - No abras la app en el móvil ni la dejes en modo depurador.
6. Informe final del bloque: qué cambió, qué no pudiste comprobar, siguiente paso.

## Ya hecho (no repetir)
Panel de Encargos fuera; micro con dictado de un toque; compositor con «+», micro y enviar; márgenes del chat 16 dp; selector de esfuerzo en píldora; «Pensando…» con tiempo y pasos; corrección de parpadeo del autoscroll; tablas al ancho de pantalla; ocultos «Respuesta» y «Tu espacio de creación»; límites de la Lanzadera sin tope; Flow fuera de Proveedores; fecha de actualización y cambios por versión; panel de formato de la Lanzadera con calidad, duración y generaciones; ArtBox con bordes suaves, buscador relleno, pestañas en píldora, un solo menú y tarjetas flotantes con sonido; Biblioteca con eliminar carpeta, vista previa de carpetas y copia local de resultados; «Abrir Alexia» quitado.

## Pendiente de hacer (en este orden)
**Bloque 1 — Ajustes**
- Quitar o ocultar los ajustes sobrantes, incluidos «Continuar en… al abrir», «Mostrar siempre al arrancar el proceso» y «Ausencia para volver a elegir».
- Conexiones: vaciar Google Flow, Drive y sincronización de esa pantalla (el puente sigue activo; muévelos a Sistema si hace falta).
- Catálogo y workflows: simplificar o quitar (columnas de familias, ocultar anteriores a X meses, orden de workflows).
- Reglas y límites: pasar aurora, intensidad, foto de fondo y desenfoque a Personalización; ocultar el resto.
- Tarjeta «Permisos de Android» (Seguridad): pregunta antes qué quiere.
- Tarjetas de Atlas Cloud y Crun al mismo tamaño que las demás.
- BytePlus ModelArk junto a los demás motores (AGY, Codex, Claude…); modelos antiguos tachados fuera; rediseñar la selección de modelos (hoy parece una web).
- «Edición y Whisper» a ajustes generales; skills editables.
- «Presets por modelo» mejor desarrollado; rediseñar o quitar el panel de filtros de modelos.
- Telemetría en Sistema: tiempo medio de respuesta por agente, tiempo de generación por modelo, modelos más usados, tokens y llamadas por agente.

**Bloque 2 — ArtBox y Biblioteca**
- Mover el logo dentro de la tarjeta, cambiar el icono; que los ajustes visuales se conserven al actualizar y al cambiar de columnas (hoy ya existen tamaño del logo, mover el nombre y reordenar).
- Revisar el menú ⋮ de la tarjeta; fondos animados personalizables (aurora, universo).
- Ventanas secundarias (Lipsync, Herramientas…) con el mismo formato; rediseñar «Herramientas».
- Filtro «Con referencias» (tachado).
- Quitar el resto de referencias a «Alexia» en la interfaz (OutputViewer, Biblioteca, etc.).
- Clip de audio de texto a voz en carpetas: pregunta qué cambiar.

**Bloque 3 — Chat y agente**
- Gesto lateral entre pantallas: más sensible, válido a cualquier altura, con animación fluida.
- Menú de mantener pulsado más cerca del dedo; selección de texto con arrastre normal.
- Más vibración y sonido (respetando los ajustes del sistema); aprovechar mejor el S Pen.
- Jerarquía visual de las respuestas (títulos, negritas, separación): comprueba primero qué trajo Codex.
- Velocidad del agente: mide dónde se va el tiempo antes de optimizar; Codex CLI como motor; Antigravity y Codex generando imágenes con la suscripción del usuario.
- Onboarding con el logo grande sin recortar las orejas del lobo.
- Heredado: botón «Descartar» para un trabajo atascado en «En cola».

**Bloque 4 — Funciones nuevas**
- Recorte automático de vídeo por escena, extracción de fotogramas, dossiers/guiones/sinopsis guardados donde el agente los vea siempre.

## Esperan datos del usuario (no los adivines)
- Significado de «luxury dark modern» (iconos de la barra inferior).
- Dónde van la cámara y el velocímetro (hoy: cámara en «+», velocímetro en la barra).
- Qué fallo concreto ve en la sincronización («2 archivos locales inaccesibles», «25 versiones en conflicto»).
- Qué modelos faltan en vídeo e imagen (Runway, Vidu, Wan, PixVerse, Kling…), cuidando los duplicados.
- Qué pide en las anotaciones de precios y multimodal de la Lanzadera.
- Captura del diálogo de H3 Max («Animar imagen · Runway directo») y de la lista de vídeo.
