# Auditoría de la APK «Aki» (com.vidadomu.aki) y de su puente

Fecha: 2026-10-17. Método: análisis estático. APK extraída del Pixel (`/data/app/.../com.vidadomu.aki-.../base.apk`); la copia del S25 (`installed_aki.apk`, 77 KB) es de la misma instalación del 2026-10-06. No se ejecutó ni se envió ninguna petición al puente. Fuente del puente: copias de la auditoría del 2026-09-25 (`Codex/.../aki-audit-20260925`) y `AXEL-AI/aki.py`.

## 1. Qué es

Aki es **una pantalla de chat en WebView** (78 KB: `classes.dex` 6 KB + `index.html` 44 KB + marked + highlight.js). No contiene lógica de IA. Habla con un **puente HTTP** (`axel_bridge.py`, Termux → Debian/proot, puerto 8765) que ejecuta el CLI `aki` por cada mensaje.

```
Aki (WebView) ──HTTP 127.0.0.1:8765──► axel_bridge.py ──subprocess──► `aki --output-format stream-json --model X "<prompt>"`
PC (aki.py) ──HTTP 100.119.88.105:8765 (Tailscale) ─┘                      └─► credenciales importadas (Midway/Cognito)
```

Endpoints: `GET /health`, `GET /models`, `POST /chat` (SSE `data:{delta|error|done}`), `POST /stop`, `POST /extract-file`.

## 2. Hallazgos

| # | Gravedad | Hallazgo | Evidencia |
|---|---|---|---|
| 1 | **Alta** | El puente **no autentica a nadie** y escucha en `0.0.0.0:8765` con `CORS: *`. Cualquier dispositivo de la tailnet o la wifi, y cualquier página web abierta en un dispositivo con acceso, puede enviar prompts y parar trabajos con la sesión del usuario. | `axel_bridge_hardened.py` L26, L147-152; informe 25-09 |
| 2 | **Alta** | Si el CLI ejecuta herramientas (`remoteExecutionEnabled=true` en su configuración), un `POST /chat` sin autenticar podría acabar en acciones en el móvil. No verificable desde aquí. | `global.json.excerpts.txt` |
| 3 | Media | **Un solo proceso global** (`CURRENT_PROCESS`): una segunda petición mata la primera, y `/stop` de cualquiera cancela el trabajo de otro. Dos chats o dos clientes se pisan. | `axel_bridge_hardened.py` L28, L264-276 |
| 4 | Media | **El historial no se envía**: el cliente manda solo `prompt` + `model`. Cada mensaje es un turno suelto salvo que el CLI guarde estado propio; las «conversaciones» de la pantalla son solo visuales. | `index.html` L1266-1273 |
| 5 | Media | **XSS por Markdown**: la respuesta del modelo se inserta con `innerHTML = marked.parse(...)` sin sanear (sin DOMPurify ni CSP). Un texto malicioso (documento adjunto, web leída) puede ejecutar JS en el WebView, que puede llamar al puente sin autenticación y leer `localStorage` (chats). | `index.html` L1302-1329; sin CSP |
| 6 | Media | Prompt pasado como **argumento de proceso** (visible en `ps` dentro de proot); límite de 100 KB. Los adjuntos se convierten en texto y se pegan en el prompt. | puente L242, L262 |
| 7 | Media | `usesCleartextTraffic=true` global; solo hace falta para `127.0.0.1`. | `AndroidManifest.xml` |
| 8 | Baja | `READ_EXTERNAL_STORAGE` declarado sin uso (el selector es `<input type=file>`). `allowBackup` sin fijar (por defecto, `true`): las copias de seguridad incluirían el `localStorage` con los chats. | manifiesto, strings de `classes.dex` |
| 9 | Baja | WebView con `setAllowFileAccess` y `setAllowContentAccess` activos y `setDomStorageEnabled`; carga `file:///android_asset/index.html`. Sin `setAllowFileAccessFromFileURLs`/`UniversalAccess`. | strings de `classes.dex` |
| 10 | Baja | Firmada con certificado propio «CN=Aki» (SHA-256 `1e9a4d52…dd0ea`); versionCode 1; sin actualización automática. Dependencias empaquetadas (marked, highlight.js) sin versión fijada ni verificación. | `apksigner` |
| 11 | Info | `/health` marca «Midway Conectado» solo si existe `~/.midway/cookie` de ≥ 500 bytes; no comprueba validez ni caducidad. | puente L91-101 |
| 12 | Info | Lista de modelos fija en el puente (`/models` no consulta permisos reales). | puente L34-89 |

## 3. Consideración de cumplimiento (importante)

La auditoría del 25-09 documenta que el puente reutiliza **credenciales corporativas** (tokens Cognito y cookies Midway importados desde Windows al móvil, con un respaldo que reescribe el dominio de las cookies) y la clasificó como «comportamiento no documentado», sin poder demostrar que sea un flujo permitido. Eso puede incumplir las políticas de dispositivo gestionado de la empresa.

Recomendación: **confirmar con la empresa que ese uso está permitido** antes de ampliarlo. Este informe no propone ni automatiza esa importación de credenciales. La hoja de ruta de un agente de tareas largas (ver `AUDITORIA_AGENTE_TAREAS_LARGAS.md`) debe construirse sobre **cerebros con credenciales propias** (BytePlus/Dola, Claude API, Antigravity, etc.), que Nexus ya soporta.

## 4. Correcciones inmediatas si se mantiene Aki

1. Puente: escuchar solo en `127.0.0.1` y en la interfaz de Tailscale; token obligatorio (`Authorization: Bearer`) para `/chat`, `/stop`, `/extract-file`; quitar `CORS: *`.
2. Un trabajo por sesión (id de sesión), `/stop` por `job_id`, y cola en vez de matar el anterior.
3. Enviar el historial (o un id de sesión) en cada petición.
4. WebView: sanear con DOMPurify, añadir CSP, `networkSecurityConfig` con cleartext solo a `127.0.0.1`, `allowBackup=false`, quitar `READ_EXTERNAL_STORAGE`, deshabilitar acceso a ficheros.
5. Firmar con la clave de release y fijar versiones de marked/highlight.js.
