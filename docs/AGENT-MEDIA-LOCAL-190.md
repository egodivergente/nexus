# NEXUS Agent 1.9.0: edición y Whisper en el teléfono

Estado: compilación final, pruebas físicas e instalación de 1.9.0 verificadas. El modelo Whisper está preparado en la app principal. Nexus guarda las credenciales originales del CSV por petición del usuario y queda con Sonnet 4.6 seleccionado en `us-east-1`: una llamada mínima autorizada desde el proveedor instalado en el S25 responde «OK.» (18 tokens de entrada, 5 de salida). Sonnet 5.5 falla en una llamada real por restricción de cuenta AWS, aunque el formulario y los acuerdos están completados. No se ha probado un turno completo del agente con herramientas ni se ha generado multimedia de pago; véase [la prueba real](AGENT-CLAUDE-55-20261011.md#prueba-de-respuesta-real-y-modelo-operativo).

## Procedencia y alcance

Rama `codex/agent-media-tools-20261011`, creada desde `98402529b018734922bcc2769c8a7407261e4c40`, que incluye Studio y la adaptación de accesibilidad sobre 1.8.9. APK AgentPreview: `com.vidadomu.nexus.agent`, `1.9.0-agent-preview`, versionCode 34.

Solicitud: recortar, unir, quitar/añadir audio, extraer fotogramas y transcribir con un modelo descargado, todo en el S25. La compilación se ejecuta en una copia exclusiva del ASUS; la edición y la inferencia se ejecutan en Android. No hay servidor de edición ni subida de audio.

Las herramientas conservan originales y generan nuevos archivos dentro de `files/agent_exports`. Su índice y su cola son independientes de los trabajos de generación. No se modifica el envío a proveedores, el cobro, Flow ni sus colas. El agente conversacional conserva su proveedor y presupuesto existentes: que la herramienta sea local no convierte el LLM conversacional en un modelo local.

## Herramientas

| Herramienta | Operación | Resultado |
| --- | --- | --- |
| `inspect_media_asset` | Verificar pistas, duración y dimensiones de un archivo indexado local | Metadatos reales de Android |
| `edit_video_assets` | `trim`, `concat`, `remove_audio`, `replace_audio`, `mix_audio`, `extract_audio` | ID de edición; MP4 H.264/AAC o M4A |
| `extract_video_frames` | 1–12 tiempos en milisegundos | JPEG indexados |
| `get_whisper_status` | Comprobar modelo local y descarga | Estado real |
| `prepare_whisper_model` | Descargar el modelo público fijado por SHA-256 | Descarga en segundo plano |
| `transcribe_media_asset` | Audio/vídeo local, opcionalmente un intervalo | TXT y SRT indexados |
| `get_media_edit_job` | Consultar estado/progreso/resultados, esperar hasta 20 s | Archivos e informe de error; texto de transcripción hasta 16.000 caracteres |

En modo Agente, las operaciones de organización pueden ejecutarse según la petición del usuario. Copiloto mantiene las propuestas y confirmaciones existentes. Las protecciones que requieren pulsar Generar para una generación de pago se conservan.

Entradas: IDs indexados, nunca rutas o comandos del LLM. Las URLs remotas deben importarse/descargarse antes. Los tiempos se validan contra la duración real antes de encolar. Unión de hasta 12 clips y una hora total; transcripción de hasta 15 minutos por encargo; audio mono o estéreo. Una pista añadida corta se repite hasta cubrir el vídeo. Formatos sujetos a los decodificadores/codificadores disponibles en Android; un fallo devuelve error y conserva originales.

Cola serial WorkManager `nexus-local-media-edits`, notificación de trabajo y registros atómicos por encargo. Las exportaciones interrumpidas se reconstruyen desde su archivo parcial propio. Un fallo de edición se registra como FAILED y libera la siguiente edición independiente, evitando que una entrada fallida deje encargos posteriores bloqueados. Hay límites de espacio y de tiempo; no se confunde un resultado en cola con uno completado.

## Interfaz y uso

- Ajustes del agente → **Edición y Whisper**: preparación del modelo, progreso y error de descarga.
- Adjuntar e importar al proyecto admiten audio además de imágenes, vídeo y documentos. El audio se describe al LLM por sus metadatos; sus bytes no se incorporan como documento o adjunto binario de Converse.
- Biblioteca → icono **Ediciones locales**: estado, resultados, Abrir, Compartir y Guardar una copia mediante el selector Android. La apertura externa concede permiso de lectura sólo al archivo elegido mediante FileProvider.
- Ejemplos: «Recorta este vídeo de 2 a 8 segundos», «Une estos clips en este orden», «Sustituye el audio por esta pista», «Saca los fotogramas de 1 y 3 segundos», «Transcribe este audio y crea subtítulos».
- SRT relativo al inicio del intervalo transcrito. No se incrustan los subtítulos en el vídeo en esta entrega.

## Whisper y dependencias

Media3 Transformer 1.4.1 coincide con Media3 ya usado por la app. JNI usa whisper.cpp oficial v1.7.6, MIT, CPU y dos hilos; licencia incluida en `assets/licenses/whisper.cpp-MIT.txt`.

- Fuente: `https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.7.6`.
- SHA-256 del código: `166140e9a6d8a36f787a2bd77f8f44dd64874f12dd8359ff7c1f4f9acb86202e`.
- Modelo: Whisper Base multilingüe `ggml-base.bin`, 147.951.465 bytes (~148 MB).
- Descarga: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin`.
- SHA-256 del modelo: `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`.
- La app verifica tamaño y SHA antes de marcarlo listo. No se usa una descarga incompleta o incorrecta. Después de la descarga, el motor de transcripción no utiliza red.
- Una copia del modelo ya presente en el directorio privado se puede reutilizar después de verificar de nuevo su SHA, sin descargarlo otra vez. El puente existente de depuración ADB incluye `local_whisper_prepare` y `local_whisper_status`, con los mismos controles de UID, perfil y permiso DUMP; no admite rutas/modelos externos ni generaciones.
- NDK 27.2.12479018, CMake 3.22.1, soporte de páginas de 16 KiB. Compilación nativa con un trabajo simultáneo.

## Verificación

Compilación final en ASUS: 394 pruebas unitarias superadas (118 core y 276 app), sin fallos ni omisiones. Otras 20 pruebas del proxy pasan con un proveedor simulado, sin AWS ni inferencia. Fuente y artefactos recuperados con SHA-256; 503 archivos de la copia de compilación comprobados contra el origen. ELF ARM64 y x86_64: segmentos alineados a 16 KiB.

Samsung SM-S938B, aplicación aislada `com.vidadomu.nexus.mediaqa`, sin permiso INTERNET: 5/5 pruebas físicas superadas. Recorte mediante Worker y concatenación ordenada, eliminación/sustitución/mezcla/extracción de audio con frecuencias decodificadas, JPEG reales y tiempos inválidos, transcripción Whisper con TXT y SRT. Una edición con una entrada inexistente falla y la siguiente, ya encolada como dependiente, termina correctamente. Los originales se comprobaron intactos. La prueba de entrada generada resuelve su descarga local aunque el índice todavía contenga la URL anterior; no reescribe historial ni descarga una URL.

Whisper transcribió el audio público JFK de la distribución oficial de whisper.cpp; la última operación local completa tardó 10.042 ms, incluida la preparación del audio. Es una medida de esa muestra en ese dispositivo, no una garantía para grabaciones largas. El modelo se precargó y verificó por SHA en el directorio privado de pruebas: no hubo descarga ni inferencia remota durante la prueba. El primer intento falló al descargar desde OfflineQa, que bloquea Internet; ese bloqueo se mantuvo y la comprobación posterior se realizó sin red.

La compilación final de producción duró 15 min 44 s. Después se reforzó únicamente `LocalMediaEditingTest.kt` para demostrar que la segunda edición estaba encolada antes del fallo; se recompiló su APK de instrumentación en 2 min 37 s. Los manifiestos de fuente confirman que ese archivo de pruebas es la única diferencia: el código de producción y sus binarios son los de la compilación final. Las cinco pruebas físicas terminaron en 18,749 s.

Instalación `adb install -r` de `com.vidadomu.nexus.agent`, `1.9.0-agent-preview`, vc34, sobre 1.8.9 vc33. Se guardó y verificó una copia de la APK anterior. El SHA del APK instalado coincide con el artefacto firmado:

- APK SHA-256: `a0ec5cecab66942ba3080532b7bdc557e0afaaa3f65426c261d0def75ccf50cd`.
- Certificado SHA-256 conservado: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
- Estado antes/después: 184 trabajos, 180 archivos indexados, 1 proyecto y 46 skills; acceso a credenciales, vínculo Flow y modo Agente conservados. Historial cargado sin error.
- Los pesos públicos se copiaron al directorio privado de producción y se verificaron de nuevo por SHA; `local_whisper_prepare` devuelve listo. La transcripción y exportación están probadas físicamente en la variante aislada con el mismo código de producción; aún no se ha ejecutado una edición desde una conversación del usuario en la app principal.
- Se retiraron únicamente los dos paquetes temporales de pruebas creados para este encargo. No se borraron datos de Nexus ni archivos personales.

No probado todavía: todas las combinaciones de códecs/4K/HDR, exportaciones de una hora, transcripciones de 15 minutos, métricas térmicas prolongadas, ni una revisión visual completa de la nueva hoja con TalkBack y fuentes grandes.

Pruebas unitarias: contratos de herramientas/categorías, opciones cerradas, orden de concatenación, intervalos, pistas requeridas, tiempos de fotograma, formato SRT y exclusión del audio de la carga binaria al LLM. Ejecutar:

```text
:agent-core:test :app:testOfflineQaUnitTest :app:assembleAgentPreview :app:assembleOfflineQa :app:assembleOfflineQaAndroidTest -PnexusMediaQa --max-workers=1 --no-parallel
```

`LocalMediaEditingTest` sólo admite la app aislada `com.vidadomu.nexus.mediaqa`: dos clips sintéticos de tres segundos (rojo/440 Hz y azul/660 Hz) y el fragmento público `samples/jfk.wav` de whisper.cpp v1.7.6. Comprueba duración y orden visual, originales idénticos byte a byte, fotogramas JPEG, pistas de audio y frecuencias reales tras sustituir/mezclar/extraer, rechazo de tiempos inválidos y transcripción real con TXT/SRT. No llama a proveedores de pago.

OfflineQa conserva su manifiesto sin permiso INTERNET. Antes de la prueba de Whisper se precargan los pesos públicos en `files/whisper_models/ggml-base.bin`; el código verifica de nuevo su SHA antes de usarlos. Esto permite comprobar la inferencia con acceso a red bloqueado por Android. Ningún archivo personal del usuario se usa en esta suite.

Los informes XML, manifiestos de fuente, resultados de instrumentación y comprobaciones de instalación se conservan en la evidencia privada `outputs/ui-minimal-20261009/agent-media-release` y `agent-media-final-proof`. Logs, modelos, APK, firmas y capturas permanecen fuera de Git.
