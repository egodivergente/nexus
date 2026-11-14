# Cut: anotaciones del vídeo del 14/11/2026

Base comprobada: APK vc214 del S25, marcador de fuente `c0961dd1`, rama
`aki/story-sync-hydrogen-20261114`. La rama de este encargo es
`codex/cut-video-gestures-20261114`. No se usa el checkout antiguo vc175.

El vídeo original y su audio se inspeccionan localmente. La transcripción se
realizó con el Whisper ya instalado; vídeo, audio y transcripción quedan fuera
de Git. Anotaciones y decisiones:

| Observación | Corrección |
|---|---|
| 0–18 s: desplazarse durante la reproducción | Scrub recuerda si estaba reproduciendo y reanuda al soltar. Durante reproducción, arrastrar la pista no mueve el clip seleccionado. |
| 18–28 s: vídeo pequeño, ampliar con dos dedos | Pinch y desplazamiento de la previsualización, doble toque/Ajustar para volver. TextureView admite la transformación de la vista; no cambia el encuadre exportado. |
| 28–40 s: paneles como cortinas, tamaños regulables | Separadores arrastrables entre previsualización/línea de tiempo y agente, con acciones de accesibilidad y tamaños acotados al espacio disponible. |
| 40–48 s: esconder agente y dejar un círculo | Minimizar elimina el panel entero y conserva borrador/conversación; el círculo de herramientas lo recupera. |
| Mensaje posterior: copiar varios mensajes o chat entero | Selector común con Todo el chat, selección múltiple y copia íntegra en orden, disponible en Cut y en las acciones del chat principal. |
| Mensaje posterior: cinco clips separados con audio | render.export admite each_clip o clip_ids, procesa ventanas inmutables en cola y devuelve todos los asset_ids. No borra clips ni utiliza ripple/undo. La UI permite montaje/cada clip/selección. |

La exportación por segmento conserva lo que se ve y oye en su intervalo: capas,
audio, velocidades y efectos. Se evalúan en el tiempo original y la salida empieza
en cero. No se cambia la revisión ni el historial del montaje. Cancelar detiene
lo pendiente y conserva los archivos completos ya guardados. Las opciones de
salida se pasan como overrides; abrir/exportar no edita los ajustes del proyecto.

Se añaden pruebas de ventanas con extremos y velocidad, cinco segmentos y audio
vinculado, selección/deduplicación, copia íntegra, distribución del espacio,
zoom, intención de reproducción y cola/cancelación. La instrumentación usa dos
renders de prueba privados, con audio, sin entradas en Biblioteca, sin llamadas
a modelos y sin editar montajes. El recorrido de vista recibe el id del montaje
explícitamente y compara el proyecto antes/después.

## Entrega y pruebas verificadas

APK **1.9.181-agent-preview / vc217**, AWS CodeBuild
`7b074c77-e1ed-48b8-ab70-328ee639995e`, fuente
`88102ee24c87102f86734d73e03f394dc8216266`.
SHA-256 firmado e instalado, coincidentes:
`b010a2acf05dbb304d14444b78825262115b1bda018a99aec16a04df96e56f1c`.
Certificado existente conservado:
`8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
Alineación 16 KiB y tres bibliotecas ARM64 idénticas a vc214 comprobadas.
Firma local, claves fuera de Git y AWS. Actualizada en **S25 Ultra SM-S938B**
con `adb install -r -t`, sin borrar datos ni desinstalar. Esta Preview necesita
`-t`. Versión, hash del paquete instalado y apertura de MainActivity verificados.

- **135 JVM PASS**, cero fallos/errores/omitidas: app 19, cut-core 109, agent-core 7.
- **3 pruebas físicas PASS en vc217**: renders secuenciales con audio; paneles,
  pinch, ajustar, arrastrar reproduciendo y reanudar, minimizar/reabrir sin editar;
  copiar todo y dos de tres mensajes comparando el texto íntegro del portapapeles.
- Capturas reales revisadas de paneles, zoom y selección múltiple.
- FFprobe: dos MP4 H.264 1920×1080, inicio cero, **10 fotogramas / 1 s completo
  de vídeo cada uno**, AAC de 1,012 s y 1,033292 s. Archivos privados de prueba,
  sin registros en Biblioteca. Sin llamadas a LLM ni generaciones de proveedores.
- Sin eventos de crash/ANR de Nexus registrados durante las pruebas.

Los **8 archivos de los dos proyectos Cut** son idénticos por hash desde vc214
hasta vc217. Todos los IDs previos del índice permanecen. Antes de vc217 y después
de sus pruebas: 12 proyectos, 2 escenas, 5 planos, 9 personajes, 3605 archivos y
857 generaciones. El índice recibió resultados reales durante la sesión; las
pruebas no los generaron. Una copia corta de SQLite dentro del dispositivo fue
estable y pasó `integrity_check=ok`.

## Hallazgos de la propia verificación

La primera automatización de casillas no esperaba recomposición y sus acciones
no cambiaron la selección. Se sustituyó por toques reales y espera del contador;
la comparación de copia múltiple pasó en vc217.

En un primer render de vc216, audio y contenedor duraban aproximadamente un
segundo, pero el vídeo sólo 0,7 s. Al repetir salieron completos. **El origen
exacto del resultado transitorio no está establecido.** Ahora se verifica también
la pista de vídeo antes de guardar: un resultado incompleto se repite una sola
vez; si vuelve a fallar, se informa sin registrarlo en Biblioteca. Detector
probado en JVM; el reintento automático no se activó en la pasada física final.
Los dos archivos finales sí se comprobaron completos.

El empaquetado AWS necesitó opt-in de pruebas, metadatos de fuente y ABI
coherentes. AGP con ABI inyectada escribe en `intermediates/apk`; se corrigió
el recolector y se exigieron ambas APK. Las compilaciones sin artefactos no
cuentan como entrega. Una copia caliente de SQLite por ADB fue inconsistente
porque cambiaba durante la lectura: no se restauró ni se consideró corrupción
del móvil. La copia corta estable posterior fue válida.

## Límites y decisiones

- Montaje 1 **ya era r15 con un segmento visual y su audio**, antes de esta tarea.
  Los cinco clips del ejemplo eran r6. No se reconstruyó esa revisión ni se
  exportaron cinco archivos del montaje actual. Restaurar contenido histórico
  requiere una decisión explícita del usuario.
- La copia incluye texto guardado, no binarios adjuntos ni registros internos
  de herramientas. Integrada en Cut y acciones del Agente principal; la prueba
  física usa el componente compartido con mensajes de prueba, no una
  conversación privada real completa.
- Cancelación y callbacks tardíos: JVM. No se interrumpió un trabajo real ni
  se forzó un fallo de hardware en vc217. Reintento automático: no observado.
- **Pixel no conectado: no instalado ni probado en esta entrega.** No se midieron
  60/120 fps, batería, S Pen ni lectura de TalkBack. Semántica y pruebas de tamaños
  no equivalen a una auditoría auditiva de TalkBack.
- Esta entrega cubre el vídeo y los dos requisitos añadidos; no se presenta como
  una nueva auditoría de todas las interfaces de Nexus.

Evidencias, APK, grabación, transcripción y copias privadas fuera de Git:
`C:/Users/User/Documents/Codex/nexus-video-20261114`.
