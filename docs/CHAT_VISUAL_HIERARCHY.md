# Chat: jerarquía y superficies — 30/10/2026

Corrección solicitada sobre la captura 76544: cuerpo y énfasis indistinguibles,
aviso «Ojo» demasiado dominante y superficies sin un criterio común.

## Decisiones y alcance

- Separar tipografía de lectura de las etiquetas operativas: cuerpo nativo regular
  de 16/24 sp, títulos de 20/28 sp, subtítulos de 17/24 sp y énfasis semibold.
  La preferencia Android de texto en negrita sigue aplicándose; no se altera.
- Avisos completos, sin colapsar ni reescribir contenido: panel neutro estable,
  borde tenue, icono y etiqueta semánticos. Advertencia usa el token warning, no
  error. Cuerpo secundario de 14/22 sp y menor protagonismo cromático.
- Compositor y avisos comparten superficie y radio medio del tema. Se elimina la
  sombra de 10 dp del compositor y la cápsula adicional del engranaje. Enviar y
  selección de navegación usan el radio pequeño de MaterialTheme.
- Resaltados de texto más suaves. No se modifican mensajes, parser, proveedores,
  generación, fondos, preferencias de accesibilidad ni gestos.
- El lápiz flotante pertenece a la superposición de accesibilidad de
  `xyz.inspoapps.spenpro`, confirmada con dumpsys window; no se modifica esa app.

## Verificación

- Rama: `codex/chat-visual-hierarchy-20261030`.
- Fuente de la APK: `d481edde8363a6e988f284814f44c8dcd1bd3619`, publicada y
  verificada con git ls-remote.
- AXELPC: BUILD SUCCESSFUL, 25m29s. Dos trabajadores; se inició a prioridad baja y
  se ajustaron únicamente los procesos propios a Normal ante la lentitud. Sin
  nuevos errores de compilación; permanecen avisos SDK XML/deprecaciones previos.
- 32 pruebas ejecutadas PASS, sin omitidas: 9 app (contraste/tokens/apariencias,
  incluidos texto y etiqueta warning sobre el panel neutro en las 12 apariencias)
  y 23 core (markdown, contenido enriquecido y presentación).
- APK 1.9.136-agent-preview, versionCode 172.
  SHA-256: `dd1acf5241e699ca97de9ca55ef98b781b9607e279d76eb31ad13be0760a26ef`.
  Certificado existente:
  `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
  apksigner y alineación 16 KiB PASS; cinco ARM64 idénticas a vc155.
- Instalación como actualización, hash instalado, versión y arranque en primer
  plano comprobados en Pixel 8 Pro y SM-S938B. Sin desinstalar ni borrar datos.
- Borradores de Lanzadera (prompt, parámetros, recursos), textos de borradores de
  chat y preferencias de apariencia iguales antes/después en ambos móviles.
  Se excluyen del hash de borradores de chat los offsets de scroll, que pueden
  variar con el nuevo flujo del texto. No se restauran copias antiguas.
- S25: captura anterior y nueva de la misma respuesta original de la captura del
  usuario, con resumen y «Ojo», revisadas visualmente. Se distinguen cuerpo,
  énfasis y título; el aviso conserva el texto, usa fondo estable y etiqueta de
  advertencia, y desaparecen el borde rosa y la cápsula extra del engranaje.
  La prueba de teclado se interrumpió al pasar el teléfono a Chrome/Termux. El
  guard de foco detuvo las pulsaciones; no se insistió sobre el teléfono en uso.
- Pixel: arranque y captura de Lanzadera comprobados; QA adicional de chat
  interrumpida por pantalla apagada/Keyguard (showing e inputRestricted true).
  Teclado y fuente 1,5x NO PROBADOS en vc172. No se cambió el escalado ni se
  intentó PIN/bypass. Se conservan Champagne en Pixel y Aurora en S25.
- Logcat posterior a la instalación: Pixel 11 284 líneas,
  epoch 1793317201.032–1793317397.850; S25 178 127 líneas,
  epoch 1793317241.003–1793317405.385. Cero crashes/ANR de Nexus detectados en
  esas ventanas; no equivale a una certificación global de estabilidad.
- No se repiten generación de pago, TalkBack auditivo, sensación háptica o
  mediciones de fps: no se modifican esas rutas y no se declaran verificadas.

Artefacto, capturas y evidencia privada en
`C:\Users\User\Documents\Codex\nexus-premium-20261029`, fuera de Git.
