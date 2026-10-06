# Google Flow desde Codex

Puente local basado en [gflow-cli 0.82.1](https://github.com/ffroliva/gflow-cli), con MCP por stdio y un perfil independiente de Chromium. Usa la sesión personal de Flow iniciada por el usuario. Grok Bots no es necesario para esta ruta.

## Estado comprobado el 2026-10-06

- **PASS:** Google reconoce la sesión guardada del perfil `nexus`.
- **PASS:** petición real por MCP a Flow; una imagen creada en un proyecto propio.
- **PASS:** recuperación del resultado por MCP, JPEG de 1024 × 1024, lectura completa con Pillow y SHA-256. La recuperación no envía otra generación.
- **PASS:** una nueva conexión MCP devuelve el mismo archivo. Repetir el identificador conserva un único trabajo nativo.
- **PASS:** seis pruebas del control de modelo y cinco de persistencia/idempotencia. Estas pruebas usan datos locales y mocks; la generación real se comprobó por separado.
- **PASS:** petición de inspección interceptada y abortada antes de Google; el selector mostraba Nano Banana 2 y el campo de modelo enviaba `BELUGA`. El control corregido acepta ese campo.
- **PASS:** nueva generación completa con la capa final de idempotencia y control de modelo: petición MCP, trabajo nativo completado y JPEG de 1024 × 1024 descargado y verificado, sin recuperación adicional. Se enviaron dos generaciones reales en total, una antes de la corrección y otra para validar la versión final.
- **NO PROBADO:** vídeo, imágenes de referencia, otros modelos y saldo/deducción exacta de créditos.
- **PENDIENTE:** incorporación a NEXUS Agent. El alcance implementado es Codex en este PC.

La respuesta de Google observada no atribuye un modelo al archivo generado. Se conserva `requested_model` separado y `model_confirmed=false`, conforme al [hallazgo upstream](https://github.com/ffroliva/gflow-cli/issues/789). El proyecto es un wrapper no oficial; los cambios del proveedor pueden requerir mantenimiento.

## Herramientas disponibles

`flow_generate_image(request_id, prompt, aspect="1:1", model="nano2")` crea una imagen. `request_id` identifica una intención concreta: repetirlo con los mismos parámetros recupera su resultado; cambiar los parámetros con el mismo id devuelve un error.

`flow_get_result(request_id=...)` consulta o recupera un trabajo existente. También acepta `task_id=...` para importar una generación nativa anterior. No vuelve a generar, incluso ante un resultado ambiguo.

`flow_list_jobs()` enumera los trabajos persistidos por este puente. `gflow_auth_status(profile="nexus")` consulta autenticación. `gflow_get_credits` sigue disponible para consulta, sin suponer que el endpoint funciona en una cuenta migrada.

La configuración de Codex habilita siete herramientas, incluidas `flow_generate_video` y `flow_get_quote`. Las herramientas nativas de generación quedan fuera de la lista habilitada para que las llamadas habituales pasen por el registro de identificadores. El proceso usa el [MCP original](https://github.com/ffroliva/gflow-cli/blob/develop/docs/MCP.md); no abre un servidor HTTP en la red.

## Rutas y operación

El runtime está en `$HOME/.codex/integrations/google-flow`: `venv`, `browsers`, `state/profile_nexus`, `jobs` y `results`. El perfil, la base de datos, los logs y los resultados son privados y no se versionan.

La instalación registra `google-flow` en `$HOME/.codex/config.toml`, conserva las demás tablas y limita la concurrencia a uno. El navegador de generación se posiciona fuera de pantalla; la autenticación manual usa su ventana de acceso normal. La compatibilidad selecciona Chromium para ese perfil, comprueba los modelos antes del envío y reconoce la forma de vídeo observada. Nano Banana Lite está explícitamente prohibido. La validación de sesión contra Google sigue activa.

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

Si falta el checkpoint o hay varias coincidencias, devuelve `needs_reconciliation`. Si un proceso se interrumpe antes de guardar su task id, el identificador permanece reservado y no permite otro envío. La recuperación de vídeo exige un registro con el prompt exacto del proyecto existente. La forma medida del registro lleva `null` donde upstream esperaba `CAE`. Cuando el listado entrega una miniatura, se abre el editor del activo y se descarga exclusivamente 720p de tamaño original. La recuperación no solicita mejoras; los escalados pasan por la operación explícita descrita abajo. No presentar el puente como una API oficial.

## Reproducción

`install.ps1` usa Python ya instalado, dependencias fijadas desde ruedas binarias y Chromium de Playwright. El runtime de este PC se instaló y verificó durante la tarea; la reinstalación íntegra mediante ese script queda NO PROBADA. No compila ni empaqueta NEXUS. Ejecutar las pruebas con el Python del entorno privado:

```powershell
& "$flowRuntime/venv/Scripts/python.exe" -X utf8 -m unittest discover -s tools/flow-bridge -p 'test_*.py'
```

`probe_model.py --project ID --output RUTA` inspecciona el envío de imagen en un proyecto de pruebas existente, interceptando y abortando el RPC antes del proveedor. Cambia el texto del editor; no se debe ejecutar sobre un proyecto de trabajo del usuario.

Al repetir la autenticación con `auth login --browser internal --profile nexus`, el usuario debe completar Google y cerrar únicamente esa ventana. La versión fijada puede no completar el marcador al cerrar una sesión migrada: con el navegador ya cerrado, guardar `internal` en `state/profile_nexus/.gflow_browser_strategy`, sin BOM, y ejecutar `client.py check`. Ese marcador selecciona el navegador; la sesión solo se acepta después de la verificación contra Google.

## GitHub

Destino identificado y acceso Git verificado: https://github.com/vidaltb94-collab/nexus. La integración se publica en `codex/google-flow-account`, partiendo de la rama de recuperación existente y conservando su historial. El perfil, las credenciales, las bases de datos y los resultados se excluyen.

## Vídeo, precios y NEXUS comprobados el 2026-10-06

`flow_generate_video(request_id, prompt, aspect="16:9", model="veo_fast")` genera una sola salida con Veo Fast y duración automática. No ofrece Lite. El MP4 real recuperado mide 1280 × 720, dura 8 segundos y tiene 930214 bytes. El primer envío sufrió un error del lector upstream; se recuperó el mismo clip sin volver a generar. Se comprobó el contenedor y su SHA-256.

`flow_get_quote(model, aspect)` lee los ajustes de la cuenta sin generar: Nano Banana 2 mostró 0 puntos y Veo Fast 10 puntos. No se comprobó el saldo ni el descuento efectivo. Las cotizaciones de NEXUS se conservan como máximo cinco minutos y se renuevan para otro formato. No se convierten a USD. Si falla la lectura de imagen, el envío queda bloqueado; para Veo Fast existe una estimación conservadora publicada de hasta 20 créditos, claramente identificada.

NEXUS integra dos modelos del proveedor `google_flow` en su catálogo y las funciones comunes de su Asistente. La prueba real pasó por `agentCallback submit`, el host local, el MCP y la biblioteca: generó una imagen Nano Banana 2 de 1024 × 1024. Las llamadas comunes de modelos, esquema y cotización se comprobaron también con el motor Java existente. No se probó una conversación nueva con el LLM ni la edición Android.

El host instalado sigue en su puerto loopback y conserva los 85 trabajos anteriores. Se añadieron los dos resultados verificados: 87 trabajos después de la integración. Los procesos de generación se ejecutan ocultos y con prioridad baja; una reserva persistente evita repetir un envío. Los resultados se copian al almacén de NEXUS después de comprobar SHA-256 y se sirven mediante sus rutas locales existentes.

Pruebas reproducibles: `desktop/tools/test-flow-provider.py`, `desktop/tools/test-flow-integration.py` y las pruebas `test_*.py` del puente. `desktop/tools/verify-flow-live.py` es una prueba real autorizada que crea una imagen solo cuando el coste leído es 0; preserva su identificador y no debe utilizarse como prueba offline. No se compiló ni empaquetó APK o EXE.

## Escalado y exportación de activos existentes

`flow_get_asset_options(source_request_id)` abre únicamente el activo identificado por un trabajo completado de este puente. Lee su menú nativo, excluye opciones deshabilitadas y devuelve el precio indicado antes de cualquier clic de mejora. Se comprobaron en esta cuenta: imágenes original/2K/4K; vídeos original/1080p/4K/GIF 270p. El vídeo 4K muestra 50 puntos; las otras opciones no muestran cargo. Eso no verifica el saldo ni el descuento efectivo.

```json
{"request_id":"imagen-2k-001","source_request_id":"imagen-ejemplo-001","resolution":"2k","expected_credits":0}
```

Enviar esos argumentos a `flow_process_asset`. Una reserva SQLite se guarda antes de la acción, el precio se comprueba otra vez antes del clic y un identificador repetido nunca vuelve a escalar. Un archivo ya descargado puede reconciliarse desde disco; una acción incierta permanece bloqueada para evitar duplicar consumo. Los prompts y RPC de generación de imagen/vídeo están bloqueados en este navegador de postprocesado, sin fallback a Lite. Solo se admite un archivo original propio con SHA-256 válido y sus UUID de proyecto y activo. Se conserva el original; los resultados nuevos verifican formato, dimensiones y SHA-256.

La biblioteca de NEXUS ofrece «Mejorar / exportar» en los detalles de resultados Flow. Su catálogo y el gateway común del Agente ofrecen `image_upscale`, `video_upscale` y `video_gif`, con `source_request_id` (jobId del historial) y `resolution`. El coste se expresa en créditos Flow. El envío comparte el almacén de trabajos y la descarga local; no requiere otra cuenta o API de pago.

Validación real: imágenes 2K de 2048 × 2048 y 4K de 4096 × 4096; vídeo 1080p de 1920 × 1080 y 8 segundos; GIF animado de 480 × 270 y 128 fotogramas. Se comprobó la lectura repetida de los mismos identificadores sin reenviar. La imagen 4K pasó por `agentCallback submit`, el host, el MCP y la biblioteca de NEXUS mediante `desktop/tools/verify-flow-upscales.py`; esta prueba solo permite la opción cotizada en 0. El menú de mejora de vídeo se verificó en la interfaz y no registró errores JavaScript. El contador de generaciones nativas siguió en cuatro: no se enviaron prompts nuevos en esta ampliación. Vídeo 4K: opción y coste comprobados, ejecución de pago NO PROBADA. Conversación con el LLM y Android siguen NO PROBADOS.

Las pruebas offline del puente cubren reservas, cambios de parámetros, fuente desconocida, recuperación desde archivo, precio ilegible/opciones deshabilitadas, integridad y resolución. `verify-flow-upscales.py` debe ejecutarse con un host de prueba aislado; nunca constituye una prueba offline ni envía vídeo 4K.

Verificación del host instalado: cinco entradas Flow en el catálogo y 91 trabajos en la biblioteca, conservando los 87 anteriores. Los cuatro resultados se descargaron por la ruta local; el MP4 1080p se reprodujo realmente en Edge y el GIF se sirvió como `image/gif`. La lista permitida mantiene identificadores SHA-256 y rechaza rutas con traversal. Pasaron 25 pruebas del puente y seis del proveedor/ruta de medios. El host conserva su puerto loopback y funciona oculto con prioridad baja.
