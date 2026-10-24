# ArtBox: editor local y servicio Atlas — 24/10/2026

## Continuidad y límites

- Rama de trabajo: `codex/artbox-local-inpaint-atlas-20261024`.
- Base local: `a9a91ec`, sobre `d21cfe1` de `origin/codex/nexus-master-artbox-20261023`.
- La rama ArtBox documentada, no `origin/main` (`631aa29`), es la referencia de esta continuación.
- Se terminó primero `:app:assembleOfflineQa` de la base: BUILD SUCCESSFUL, 8 min 7 s.
- `gradlew.bat` tiene modificaciones ajenas de finales de línea: excluido del parche/commit.
- S25 instalado: `com.vidadomu.nexus.agent`, 1.9.75-agent-preview / vc109, `lastUpdateTime=2026-10-24 11:34:59`. Esta versión es posterior a la identidad de la base; no se sustituye ni se afirma que sus fuentes coincidan con esta rama.
- No instalar APK, publicar, hacer push ni generar mediante proveedores de pago. La prohibición expresa de esta tarea prevalece sobre la instrucción general de sincronización GitHub.

## Inpainting implementado

Entrada en ArtBox > Herramientas y menú > **Editar región · local**:

1. Abrir imagen mediante el selector del sistema, sin modificar su original.
2. Pintar máscara con S Pen y presión; rechazo de palma activado inicialmente. Puede habilitarse dibujo con el dedo, borrar, deshacer tres trazos y limpiar la máscara.
3. Escribir el resultado deseado. Para borrar un objeto, describir el fondo que debe ocupar su región; el modelo es generativo, no una operación determinista.
4. Descargar explícitamente los tres componentes, con revisión, tamaños y SHA-256 fijados. No se descargan pesos al arrancar la app ni durante los tests.
5. Ejecutar mediante JNI/WorkManager: progreso, errores, cancelación y resultado privado. La cancelación durante carga espera a que el motor libere el contexto; no se simula que la carga sea interrumpible.
6. Guardar una copia PNG con `CreateDocument`. Se conserva el alfa original y cada píxel fuera de la máscara; no se sobrescribe la imagen importada.

El borrador de máscara y prompt se conserva. Los resultados privados usan identificadores UUID independientes; no se eliminan trabajos previos al reintentar. Los archivos parciales de descarga no se promocionan antes de verificar hash y tamaño.

### Motor y modelo

- [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp), MIT, commit `95ff76230889898e1bf40058ba272a3699b50dc6`.
- ggml fijado a `a5915eb0e47adef21c3f25b889a008925ea838c2`. Ambos archivos fuente descargados por CMake verifican SHA-256.
- CPU ARM64/NEON, dos hilos, cuantización Q4_0 durante carga, mmap y VAE por tiles. **No se declara GPU/Adreno probado.**
- [SD 1.5 Inpainting](https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-inpainting), mirror no afiliado a Runway, licencia CreativeML Open RAIL-M. Revisión `8a4288a76071f7280aedbdb3253bdb9e9d5d84bb`.
- Tres componentes FP16 safetensors: CLIP, UNet y VAE; 2.132.634.310 bytes. Manifiesto en `InpaintContract.kt`.
- Consulta real a la API del repositorio confirma tamaños y hashes. Lectura HTTP Range del encabezado UNet confirma `conv_in.weight`, FP16, forma `[320,9,3,3]`: es el UNet de inpainting, no un modelo de texto a imagen presentado como editor.
- El motor upstream carga componentes separados y convierte nombres Diffusers; incluye `VERSION_SD1_INPAINT`. Esto es inspección de código/contrato, **no prueba de inferencia**.
- Inferencia de 256 o 512 píxeles en el lado mayor, dimensiones múltiplos de 64, 4–40 pasos. Composición de vuelta sobre imagen de hasta 4 MP. Una imagen mayor se rechaza explícitamente; no se reduce ni trunca silenciosamente.

### S25: evidencia y límite de aceptación

ADB fijado al S25 `100.119.88.105:5555`: `SM-S938B`, SoC `SM8750`, ARM64, RAM total 11.379.712 KiB, /data con unos 189 GB libres.

Lecturas de `MemAvailable`: 1.419.608 y 2.026.148 KiB en esta sesión. La implementación reserva 3 GiB libres antes de cargar el motor. Es una **decisión conservadora de esta app**, no un mínimo publicado del modelo ni una demostración de incompatibilidad del S25. No se cierran apps, vacía memoria ni alteran datos para forzar la prueba.

El programa `tools/inpaint_probe.cpp` comprueba carga real de la biblioteca ARM64, símbolos y preflight de memoria, sin APK ni pesos. Su salida siempre distingue `INFERENCE_NOT_RUN`; nunca fabrica una imagen de prueba.

Prueba física ejecutada con las bibliotecas extraídas del APK final: `COMPANION_LOAD_PASS` para Whisper y JNI, `ENGINE_LOAD_PASS`, NEON=1/ARM_FMA=1, commit del motor `95ff76230889898e1bf40058ba272a3699b50dc6`. RAM disponible 2.214.340 y 1.916.988 KiB; salida 41, `BLOCKED_BY_APP_SAFETY_RESERVE_NOT_HARDWARE_INCOMPATIBILITY`. No se cargaron pesos ni se generó una imagen. El paquete instalado sigue en vc109 y conserva `lastUpdateTime=2026-10-24 11:34:59`.

Artefactos de sonda propios en `/data/local/tmp/nexus-inpaint-probe-artbox-20261024-a9a91ec` (unos 43 MB), fuera de los datos de Nexus. Biblioteca de la sonda procedente de `lib/arm64-v8a/` del APK, no de otra descarga. Copia local de inspección en `C:/Users/User/.cache/nexus-native/artbox-probe-runtime-20261024`.

**Pendiente de aceptación:** ejecución completa con pesos en S25 y prueba visual de añadir/cambiar/eliminar, fidelidad fuera de máscara, presión S Pen, rotación, cancelación, suspensión y guardado. La interfaz etiqueta la integración experimental. No afirmar que el resultado generativo ya está probado en el teléfono.

Una alternativa local reproducible es el mismo motor en un equipo Linux/Windows con memoria suficiente, usando los componentes/revisiones anteriores y máscara. El PC actual tiene Intel UHD 620 y unos 15,3 GiB RAM; no se encontraron Torch/Diffusers instalados ni un motor de inferencia de escritorio validado. **No se instala ni se presenta esa alternativa como comprobada extremo a extremo.** Para un siguiente ensayo local debe compilarse el CLI oficial y usarse su `--help` de la revisión fijada; no introducir comandos de otra versión ni servicios de pago.

## Atlas: integración oficial, no envoltorio ficticio

- CLI instalado 0.1.36 bloqueado por Windows Application Control. No se modifica ni elude esa política.
- Se integra el **servicio HTTPS oficial**, permitido expresamente por el encargo: catálogo `GET https://api.atlascloud.ai/api/v1/models`; esquemas desde `static.atlascloud.ai`; calculador `POST https://api.atlascloud.ai/api/v1/model/calculate`.
- Catálogo real consultado: 547 modelos. La actualización explícita invalida contratos/cotizaciones y consulta los contratos con concurrencia máxima 3 y progreso; conserva avisos de contratos fallidos, no los declara verificados.
- Se guardan fecha/fuente del contrato, fecha del catálogo y endpoint POST explícito si hay servidor oficial y una ruta soportada inequívoca. Los identificadores API no dependen del nombre comercial.
- El endpoint Atlas se almacena separado de `contractEndpoint`, para no cambiar la semántica de adjuntos de Runway/Crun/Flow.
- Las claves existentes se leen mediante SecureStorage en memoria; no se incorporan a archivos, logs o Git. El calculador añade Bearer si hay clave y también admite la consulta pública comprobada sin clave. Caché separada por huella de cuenta y parámetros.
- Resolución, duración, FPS y otros campos escalares se envían cuando están publicados por el esquema, con su tipo nativo. No se inventa un control FPS donde el endpoint no lo publica.
- `estimated` y `estimated_tokens` se conservan; una cotización aproximada no se presenta como cargo final. Se muestran fuente y fecha. Tarifas históricas o por unidad no se convierten en totales oficiales; las de fuente/fecha desconocida se identifican como tales.
- Audio/chat y modalidades que requieren texto o medios privados no se cotizan inventando contenido. Si solo hay tarifa base se etiqueta como tal; si no hay tarifa utilizable se muestra desconocida, nunca cero. No se sube una imagen o vídeo solo para obtener una cotización.

### Consulta real no generativa

Según [facturación oficial](https://www.atlascloud.ai/docs/billing/model-billing), el calculador no crea tareas ni cobra. En esta sesión respondió HTTP 200, código 200 para Seedance 2.0 texto→vídeo, 5 s, 720p, 16:9, sin audio, bitrate standard y demás defaults documentados. `price=0.975744`, `estimated=true`, `estimated_tokens=108900`.

Ese importe es **evidencia fechada de esa consulta**, no una tarifa embebida en producción. Una primera sonda incluyó FPS=24, pero el contrato de ese endpoint no publica FPS: se repitió sin ese campo, con los inputs documentados. No se deduce soporte FPS de que el servidor acepte un campo extra.

## Catálogo, prompts y UI

- Se conserva la corrección `a9a91ec`: ocultos persistentes con recuperación, rutas capturadas por regla, familias/variantes independientes de ordenación y categorías reales de Herramientas. Se vuelven a ejecutar sus seis regresiones.
- Prompt máximo desde `maxLength` del esquema y por endpoint; ausencia = desconocido. La recomendación de Seedance de menos de 1000 palabras **no es** un máximo contractual. Conteo Unicode por puntos de código para Atlas; sin truncado global.
- El refresco elimina máximos obsoletos. La copia persistida permite conservar un máximo conocido si el esquema sale de caché, con su fuente.
- Fullscreen de actividad/diálogos ya corregido en la base; se añade reaplicación al cerrar el teclado. El gesto de Android puede mostrar barras transitorias.
- Tarjetas conservan slots homogéneos de logos/texto/precio/Crear. Número de columnas acotado por ancho y fuente, sin cambiar la preferencia guardada. Lista para variantes en pantallas estrechas; precio separado cuando no cabe junto al título.
- Validación visual Android pendiente porque no se instala esta APK.

## Validación

- `:app:testOfflineQaUnitTest --max-workers=1 --no-parallel --console=plain`: 672 tests PASS, 0 fallos, 0 errores, 0 omitidos (incluye 16 regresiones nuevas de esta continuación).
- Primera compilación Kotlin detectó una inserción UI fuera de ámbito; corregida y repetida la suite completa con éxito.
- `:app:externalNativeBuildOfflineQa`: PASS (44 min 24 s, un proceso). Ajustes posteriores de enlace/empaquetado compilados incrementalmente.
- `:app:assembleOfflineQa --max-workers=1 --no-parallel --console=plain`: PASS. La inspección detectó inicialmente que faltaba libc++ compartida; se corrigió el runtime común y su empaquetado explícito desde el NDK configurado. No se dio esa APK intermedia por válida.
- `tools/Test-InpaintApk.ps1`: PASS, seis bibliotecas exigidas entre ARM64/x86_64. x86_64 incluye el stub, no promete inferencia.
- APK final de esta entrega: `app/build/outputs/apk/offlineQa/app-offlineQa.apk`, 85.178.447 bytes, SHA-256 `B5471C3680E84607660509A4E5F2CFA05B9B57DDED2C676363E30DA61E76B885`.
- Sonda nativa en S25: carga de motor/JNI/Whisper PASS; inferencia NO EJECUTADA por preflight de memoria. No equivale a aceptación de edición generativa ni de UI.
- `git diff --check`: PASS. Revisión de patrones conocidos de claves privadas/tokens en archivos del encargo: 0 coincidencias; no es una auditoría externa completa.
- No pruebas ni generaciones de pago, no instalación APK, no push.
