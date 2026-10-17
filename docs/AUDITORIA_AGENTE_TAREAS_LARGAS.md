# Auditoría del agente de Nexus y hoja de ruta hacia un agente de tareas largas (tipo Claude Code / Codex)

Fecha: 2026-10-17 · Código auditado: rama `feat/byteplus-custom-providers-20261017`, 1.9.34/vc68.
Alcance: auditoría **del código fuente** de la APK (el S25 estaba fuera de alcance por ADB; en él sigue la 1.9.23). No se ejecutó nada en el móvil.

## 1. Qué es hoy el agente

Un agente de **producción multimedia**, no de ingeniería: prepara encargos (`prepare_generation`, `update_plan`, `submit_generation`), gestiona biblioteca, proyectos y memoria de proyecto, y supervisa trabajos de proveedores (Crun, Atlas, Runway, Flow, custom).

| Área | Estado actual | Evidencia |
|---|---|---|
| Herramientas | ~45, todas de medios/biblioteca/ajustes + `web_search`, `read_web_page` | `agent-core/.../tools/*` |
| Ejecución de código / shell | **No existe** | sin herramienta de comandos, edición de ficheros ni git |
| MCP | Cliente Streamable HTTP (2024-11-05…2025-11-25), `connect_mcp_server`, `list_mcp_tools`, `call_mcp_tool` | `AgentInternet.kt` |
| Límite del MCP | `call_mcp_tool` es categoría **READ** (solo herramientas declaradas de solo lectura), 45 s por llamada, sin progreso ni cancelación | `NativeOperations.kt`, `AgentInternet.kt:25` |
| Termux | Solo el puente de Antigravity (`127.0.0.1:8788`) como **proveedor de LLM** | `AntigravityProvider.kt` |
| Tailscale | Solo para Flow (ASUS) y AGY; el MCP admite cualquier URL http(s), incluidas `100.x` | `FlowBridgeClient.kt`, `AssistantRuntime.kt` |
| Bucle del agente | 20 pasos/mensaje, 600 s, 60 mensajes de contexto, 32 000 caracteres por resultado de herramienta (todo configurable, 0 = sin límite) | `AgentLimits.kt` |
| Trabajo en segundo plano | Servicio en primer plano `dataSync` + WorkManager; el turno muere con el proceso | `AndroidManifest.xml`, `AssistantTurnService` |
| Contexto largo | Sin compactación ni resumen automático; se descartan mensajes antiguos | `maxMessages` |
| Planificación | Sin lista de tareas propia del agente (los «planes» son encargos de generación) | — |
| Permisos de Android | `MANAGE_EXTERNAL_STORAGE`, medios, notificaciones; sin `RUN_COMMAND` de Termux | `AndroidManifest.xml` |
| Seguridad | Guardia de gasto, claves cifradas, texto externo tratado como dato, sin repetir llamadas fallidas | `spend/`, `SecureStorage` |

Conclusión: la base es sólida (proveedores LLM intercambiables, límites configurables, MCP, servicio persistente), pero **le faltan cuatro piezas** para comportarse como Claude Code/Codex: manos (shell y ficheros), memoria de trabajo larga, ejecución que sobreviva y un modelo de permisos.

## 2. Arquitectura recomendada

```
 App Nexus (agente + UX)            Tailscale (100.x)              Ejecutores
 ┌──────────────────────┐   MCP HTTP + token + SSE   ┌────────────────────────────┐
 │ Bucle del agente     │ ─────────────────────────► │ nexus-shell (MCP server)   │
 │ Permisos / aprobación│ ◄───────────────────────── │  · AXELPC  (compilar)      │
 │ Tareas largas (jobs) │      progreso / logs       │  · Termux  (móvil)         │
 └──────────────────────┘                            │  · ASUS    (último recurso)│
                                                     └────────────────────────────┘
```

Un solo servidor MCP, `nexus-shell`, desplegable en Termux, AXELPC y ASUS, con el mismo contrato. Respeta la prioridad de `AGENTS.md`: AXELPC primero, el móvil para pruebas y el ASUS solo como último recurso (`--max-workers=1`).

Herramientas del servidor:
- `exec(cmd, cwd, timeout, background)` → devuelve `job_id`; el comando corre dentro de `tmux`/proceso desacoplado.
- `job_status`, `job_tail(lines|since)`, `job_cancel`, `job_list`.
- `read_file`, `write_file`, `edit_file` (reemplazo exacto), `list_dir`, `grep`, `glob`.
- `git_status`, `git_diff`, `git_commit` (sin force-push).
- `device_info` (CPU, RAM, disco, batería) para elegir ejecutor.

Termux: dos vías compatibles. (a) `com.termux.RUN_COMMAND` + `allow-external-apps` para comandos puntuales sin red; (b) servidor MCP en `127.0.0.1` dentro de Termux (recomendada: streaming, sesiones persistentes y mismo contrato que AXELPC).

## 3. Cambios necesarios en la app

1. **MCP de acción**: nueva categoría `ACTION` con aprobación; progreso por SSE; `job_id` y sondeo; cancelación; timeout por herramienta configurable (hoy 45 s fijos).
2. **Modelo de permisos** (como Claude Code): *Preguntar siempre* · *Aprobar ediciones* · *Autónomo en este directorio*. Listas de comandos permitidos/prohibidos, directorio de trabajo acotado, redacción de secretos, botón de parada inmediata.
3. **Tareas largas**: `TaskRunner` con estado persistente (reanudable tras matar el proceso), notificación con progreso, y servicio en primer plano apropiado (revisar `dataSync`: Android 15 lo limita a 6 h/24 h; valorar `specialUse` con justificación).
4. **Contexto**: compactación automática (resumen + últimas N interacciones + ficheros tocados) y límite de salida por herramienta con «ver más».
5. **Lista de tareas del agente** (`todo_write`) y plan visible, con pasos que se marcan al completarse.
6. **Perfil de cerebro «Agente de código»**: `dola-seed-2-1-turbo`, Claude, AGY, con razonamiento medio/alto y herramientas activadas.
7. **Seguridad**: el contenido de ficheros, logs y webs es dato, no instrucciones; aprobación obligatoria para borrar, `git push`, instalar paquetes, red saliente y cualquier cosa fuera del directorio de trabajo; registro de auditoría local.

## 4. UX de categoría premium

- Pestaña/hoja **Tareas**: línea de tiempo con cada paso (pensando · comando · edición · prueba), estados y duración.
- **Tarjetas de herramienta** plegables: comando, salida en vivo con cola, código de salida, copiar/reintentar.
- **Visor de diff** con aprobar/rechazar por archivo y por bloque.
- **Consola en vivo** (monoespaciada, colores ANSI, búsqueda) para compilaciones largas.
- Selector de **ejecutor** (AXELPC · Termux · ASUS) con estado, RAM libre y latencia; el agente propone el mejor.
- **Modo de permisos** visible en la barra, hápticos al aprobar, notificaciones con acciones (Aprobar/Parar), widget con tareas en curso.
- Coherencia con lo ya hecho: cerebro y razonamiento en la barra, tarjetas compactas con logo, banda propia del cuadro de texto.

## 5. Fases

| Fase | Entrega | Verificable |
|---|---|---|
| 1 | `nexus-shell` en AXELPC y Termux + `exec` en segundo plano, `job_tail`, ficheros y git | prueba real de una compilación desde el móvil por Tailscale |
| 2 | Categoría `ACTION`, aprobaciones, permisos y parada | pruebas unitarias del guardián + prueba en el S25 |
| 3 | Tareas largas reanudables, notificaciones, compactación de contexto | matar la app a mitad y reanudar |
| 4 | UX: Tareas, tarjetas de herramienta, diff, consola, selector de ejecutor | capturas y recorrido en el S25 |
| 5 | Perfiles de agente de código y evaluación comparada (Dola, Claude, AGY) | batería de tareas reales con coste y tiempo |

## 6. Riesgos y decisiones abiertas

- **Un LLM con shell es el mayor riesgo de la app**: toda acción de escritura/red/borrado exige aprobación por defecto; el modo autónomo solo dentro de un directorio de trabajo y con lista de comandos.
- **Exposición por Tailscale**: el servidor debe escuchar solo en la interfaz de Tailscale/loopback, con token por dispositivo y ACLs.
- **Android mata procesos en segundo plano**: la ejecución real vive en el servidor (`tmux`), no en el móvil; la app solo observa y aprueba.
- **Termux** puede ser suspendido por el sistema: requiere `termux-wake-lock` y exclusión de optimización de batería.
- Decidir: ¿el agente de código es un modo del Agente actual o una pestaña nueva? Recomendación: **modo** con su propia pantalla de Tareas, para reutilizar cerebro, límites y memoria.
