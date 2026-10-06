# Google Flow desde Codex

Puente local basado en [gflow-cli 0.82.1](https://github.com/ffroliva/gflow-cli), con MCP por stdio y un perfil independiente de Chromium. Usa la sesión personal de Flow iniciada por el usuario. Grok Bots no es necesario para esta ruta.

## Estado comprobado el 2026-10-06

- **PASS:** Google reconoce la sesión guardada del perfil `nexus`.
- **PASS:** petición real por MCP a Flow; una imagen creada en un proyecto propio.
- **PASS:** recuperación del resultado por MCP, JPEG de 1024 × 1024, lectura completa con Pillow y SHA-256. La recuperación no envía otra generación.
- **PASS:** una nueva conexión MCP devuelve el mismo archivo. Repetir el identificador conserva un único trabajo nativo.
- **PASS:** seis pruebas del control de modelo y cinco de persistencia/idempotencia. Estas pruebas usan datos locales y mocks; la generación real se comprobó por separado.
- **PASS:** petición de inspección interceptada y abortada antes de Google; el selector mostraba Nano Banana 2 y el campo de modelo enviaba `BELUGA`. El control corregido acepta ese campo.
- **NO PROBADO:** una nueva generación completa con la última capa de idempotencia. Se verificó contra el resultado real anterior y mediante mocks para evitar una segunda generación.
- **NO PROBADO:** vídeo, imágenes de referencia, otros modelos y saldo/deducción exacta de créditos.
- **PENDIENTE:** incorporación a NEXUS Agent. El alcance implementado es Codex en este PC.

La respuesta de Google observada no atribuye un modelo al archivo generado. Se conserva `requested_model` separado y `model_confirmed=false`, conforme al [hallazgo upstream](https://github.com/ffroliva/gflow-cli/issues/789). El proyecto es un wrapper no oficial; los cambios del proveedor pueden requerir mantenimiento.

## Herramientas disponibles

`flow_generate_image(request_id, prompt, aspect="1:1", model="nano2")` crea una imagen. `request_id` identifica una intención concreta: repetirlo con los mismos parámetros recupera su resultado; cambiar los parámetros con el mismo id devuelve un error.

`flow_get_result(request_id=...)` consulta o recupera un trabajo existente. También acepta `task_id=...` para importar una generación nativa anterior. No vuelve a generar, incluso ante un resultado ambiguo.

`flow_list_jobs()` enumera los trabajos persistidos por este puente. `gflow_auth_status(profile="nexus")` consulta autenticación. `gflow_get_credits` sigue disponible para consulta, sin suponer que el endpoint funciona en una cuenta migrada.

La configuración de Codex habilita estas cinco herramientas. Las herramientas nativas de generación quedan fuera de la lista habilitada para que las llamadas habituales pasen por el registro de identificadores. El proceso usa el [MCP original](https://github.com/ffroliva/gflow-cli/blob/develop/docs/MCP.md); no abre un servidor HTTP en la red.

## Rutas y operación

El runtime está en `$HOME/.codex/integrations/google-flow`: `venv`, `browsers`, `state/profile_nexus`, `jobs` y `results`. El perfil, la base de datos, los logs y los resultados son privados y no se versionan.

La instalación registra `google-flow` en `$HOME/.codex/config.toml`, conserva las demás tablas y limita la concurrencia a uno. El navegador de generación se posiciona fuera de pantalla; la autenticación manual usa su ventana de acceso normal. La modificación local de compatibilidad se limita a seleccionar Chromium para ese perfil y a comprobar el modelo de imagen antes del envío. La validación de sesión contra Google sigue activa.

Si el catálogo de herramientas del turno se creó antes de registrar el servidor, el agente puede usar el cliente MCP incluido:

```powershell
$flowRuntime = Join-Path $HOME '.codex/integrations/google-flow'
& "$flowRuntime/venv/Scripts/python.exe" -X utf8 "$flowRuntime/client.py" check
& "$flowRuntime/venv/Scripts/python.exe" -X utf8 "$flowRuntime/client.py" --output resultado.json call flow_generate_image --args-file peticion.json
```

Contenido de `peticion.json`:

```json
{"request_id":"imagen-ejemplo-001","prompt":"Una esfera azul sobre un pedestal blanco","model":"nano2","aspect":"1:1"}
```

La segunda llamada genera contenido y utiliza la cuota de la cuenta. El agente debe leer el `status` de `structured_content` (o el texto JSON si el servidor no proporciona estructura), no solo `is_error`, y mostrar el archivo de `files[0]` al usuario. Ante timeout, reutilizar el mismo id y consultar el resultado.

## Recuperación y límites

La primera llamada real encontró un cambio de modelo (`NARWHAL` frente a `BELUGA`) después del envío. La recuperación lee el checkpoint nativo para localizar el proyecto y decodifica su listado `Zzl0ze`; exige una única imagen con el prompt exacto. Descarga únicamente desde dominios Google admitidos, comprueba cada redirección y solicita la versión original del handle `/asb/`, exigiendo las dimensiones que registra Google. No publica URLs firmadas ni cookies.

Si falta el checkpoint o hay varias coincidencias, devuelve `needs_reconciliation`. Si un proceso se interrumpe antes de guardar su task id, el identificador permanece reservado y no permite otro envío. La recuperación automática se limita a imágenes; no presentar el puente como una API oficial ni como vídeo validado.

## Reproducción

`install.ps1` usa Python ya instalado, dependencias fijadas desde ruedas binarias y Chromium de Playwright. El runtime de este PC se instaló y verificó durante la tarea; la reinstalación íntegra mediante ese script queda NO PROBADA. No compila ni empaqueta NEXUS. Ejecutar las pruebas con el Python del entorno privado:

```powershell
& "$flowRuntime/venv/Scripts/python.exe" -X utf8 -m unittest discover -s tools/flow-bridge -p 'test_*.py'
```

`probe_model.py --project ID --output RUTA` inspecciona el envío de imagen en un proyecto de pruebas existente, interceptando y abortando el RPC antes del proveedor. Cambia el texto del editor; no se debe ejecutar sobre un proyecto de trabajo del usuario.

Al repetir la autenticación con `auth login --browser internal --profile nexus`, el usuario debe completar Google y cerrar únicamente esa ventana. La versión fijada puede no completar el marcador al cerrar una sesión migrada: con el navegador ya cerrado, guardar `internal` en `state/profile_nexus/.gflow_browser_strategy`, sin BOM, y ejecutar `client.py check`. Ese marcador selecciona el navegador; la sesión solo se acepta después de la verificación contra Google.

## GitHub

Al realizar esta tarea, el checkout no tenía commits ni remoto y el conector GitHub no mostraba repositorios accesibles. Se ha solicitado al usuario la URL del destino. Un commit local no equivale a publicación verificada.
