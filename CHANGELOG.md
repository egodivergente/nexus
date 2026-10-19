# Changelog — NEXUS

Todos los cambios relevantes de **Nexus Agent** (Android) y **Nexus Studio** (Windows) se documentan en este archivo.

## [1.9.52-agent] - 2026-10-20 · Ajustes por categorías, cabecera despejada y prompt fiel

- **Ajustes con pantalla de categorías:** en lugar de una lista larga, Ajustes abre una pantalla con cinco categorías (Apariencia y moneda, Agente y modelos, Proveedores de generación, Generadores propios, Sistema y diagnóstico), cada una con su resumen. Cada categoría abre su subpágina y el botón atrás vuelve a la lista.
- **Un camino al asistente:** «Agente y modelos» incluye «Ajustes del asistente» (modelo, respuesta, consumo, herramientas y skills) además de los cerebros adicionales; desde el chat se sigue llegando por la rueda → «Asistente».
- **Un solo botón de ajustes:** el icono de parámetros y la rueda se unen en una rueda con dos entradas, «Asistente: modelo y conexión» y «Aplicación». La cabecera pasa de cinco a cuatro elementos.
- **Saldo discreto:** el saldo se muestra como texto sin fondo; al tocarlo sigue abriendo el detalle de gasto.
- **Tu prompt, tal cual:** si entregas un prompt ya hecho, el agente lo envía exactamente como lo escribiste. Solo lo cambia si supera el límite de caracteres del modelo, si el modelo exige otra sintaxis documentada o si el proveedor lo rechazó, y siempre lo dice en una frase (el original se conserva). La memoria del proyecto y las skills ya no se inyectan en prompts tuyos. Regla `PROMPT_FIDELITY_RULES` siempre activa; prueba `PromptFidelityTest`.
- **Cuadro de texto:** una sola forma (esquinas de 24 dp) que ya no cambia con el contenido ni corta los adjuntos.
- **Adjuntos pendientes en miniatura:** imagen, primer fotograma del vídeo, o icono con nombre para audio y documentos, con botón para quitar en la esquina.
- **Errores con salida:** el aviso de error del chat incluye «Reintentar» (si hay un turno que repetir), «Cambiar modelo» e «Historial» cuando aplica.
- **Formulario de proyecto:** campos agrupados en tarjetas (La historia, Aspecto y tono, Reglas del asistente) con icono y ejemplos.

## [1.9.51-agent] - 2026-10-19 · «Índigo»

- Flow: Nexus espera hasta 15 s a que AXELPC confirme que está listo (antes 4 s y a veces se iba al ASUS sin sesión). El diagnóstico dice qué puente usa.

## [1.9.50-agent] - 2026-10-19 · «Índigo»

- El agente de Nexus habla con el agente de Google Flow (flow_agent_send / flow_agent_read): le encarga generaciones en cantidad, colecciones o descargas, lee su respuesta, negocia si pregunta y trae las imágenes y vídeos a la Biblioteca.
- El puente de Flow se adapta al nuevo cuadro de Flow (el antiguo campo era el título del proyecto).

## [1.9.49-agent] - 2026-10-19 · «Índigo»

- Google Flow en lotes: un encargo pide hasta 40 resultados y el puente pulsa Generar las veces necesarias (según el «xN» de Flow, por defecto 4) y los recoge juntos, en vez de uno detrás de otro.
- El puente de Flow corre ahora en AXELPC como principal; si AXELPC está apagado o sin Flow listo, Nexus usa el ASUS de respaldo. Cada trabajo se sigue y descarga en el puente donde se envió.

## [1.9.48-agent] - 2026-10-19 · «Índigo»

- El agente puede crear proyectos nuevos en Corte (corte_create_project): crea uno vacío, abre Corte en él y deja listo importar clips. Requiere Corte con el puente protocolo 3.

## [1.9.47-agent] - 2026-10-18 · «Índigo»

- Corrige el cierre al abrir la app en la 1.9.46: una expresión de las plantillas de proveedores no era válida en Android y fallaba al cargar el proveedor BytePlus.

## [1.9.46-agent] - 2026-10-18 · «Índigo»

- Nuevo proveedor de medios BytePlus (ModelArk) con Seedance 2.0 y 2.0 Fast (texto o imagen a vídeo) y Seedream 5.0 Pro (imagen y edición). Usa la misma clave y el mismo saldo que el cerebro BytePlus; los IDs de modelo se pueden cambiar en Ajustes → Proveedores.
- La barra de pestañas se esconde al bajar por el chat y vuelve al subir (se puede desactivar en Apariencia).
- Botón de cámara en el cuadro de texto (solo con el campo vacío).
- Negritas más ligeras en las respuestas, mensajes que entran con animación y vibración suave al enviar.

## [1.9.45-agent] - 2026-10-18 · «Horizonte»

- Chat más panorámico: cabecera de una sola línea (cerebro y razonamiento pasan junto al cuadro de texto), márgenes laterales de 8 dp y campo de entrada en pastilla más bajo.
- Tus mensajes van completos, seleccionables, casi a todo el ancho y con el acento en degradado; las respuestas quedan casi sin fondo.
- Los enlaces «Encargos ↓ / Último ↓» se sustituyen por un botón redondo de bajar (solo si no estás al final) y un panel de encargos con estado, modelo y coste.
- Los IDs internos (ord_…) ya no aparecen en el texto del chat.
- Resultados agrupados en carrusel con miniaturas grandes y el modelo debajo.
- Barra inferior con pastilla activa más clara; tokens de radio y curva únicos.
- Historial vuelve a mostrar créditos cuando el proveedor no da importe en moneda.

## [1.9.44-agent] - 2026-10-18 · «Horizonte»

- Ajustes del asistente (aportado por el agente de Termux, punto 5): los filtros de proveedor del Cerebro van en una sola fila deslizable en lugar de varias líneas.

## [1.9.43-agent] - 2026-10-18 · «Horizonte»

- Biblioteca: se pueden importar archivos de hasta 4 GB (antes el límite era 512 MB y el fallo era silencioso); si un archivo falla, el aviso dice el motivo.
- Nunca se muestra «UNKNOWN» al usuario (aportado por el agente de Termux, punto 4).

## [1.9.42-agent] - 2026-10-18 · «Horizonte»

- Modelos (aportado por el agente de Termux): plural correcto en los contadores, descripción a 2 líneas, la píldora de coste solo aparece si hay precio y las tarjetas de la cuadrícula tienen altura uniforme.

## [1.9.41-agent] - 2026-10-18 · «Horizonte»

- Historial (aportado por el agente de Termux): los fallos muestran su motivo en una línea con botón «Reintentar», el coste se ve en una sola unidad, cada día suma su gasto y el prompt ocupa hasta 2 líneas.

## [1.9.40-agent] - 2026-10-18 · «Horizonte»

- Biblioteca (aportado por el agente de Termux): insignia de duplicados exactos, filtro de origen con nombre completo, botón de búsqueda visible y detalles de miniatura ocultos con menos de 3 columnas. La cabecera de día sigue sin ser fija (la cuadrícula no lo admite).

## [1.9.39-agent] - 2026-10-18 · «Horizonte»

### Añadido
- Integración con Corte (editor de vídeo): herramientas `corte_status`, `corte_list_projects`, `corte_read_project`, `corte_find_silences`, `corte_apply_ops` y `corte_undo`. El agente lee la línea de tiempo real y edita a través del motor de Corte (validado, en un solo «Deshacer»). Requiere Corte con el puente del agente (rama `feat/puente-agente-nexus-20261018` del repo de Corte) y la misma firma; permiso de firma concedido.

## [1.9.39-agent] - 2026-10-18 · «Horizonte»

### Añadido
- Herramienta del agente `corte_import_media`: importa en el proyecto abierto de Corte archivos de la biblioteca de Nexus (vídeo, imagen, audio) y los coloca al final de la línea de tiempo (o en `at_us`). Requiere Corte con el puente protocolo 2 (rama `feat/importar-y-modo-nexus-20261018` del repo de Corte). El agente deja de decir que no se puede importar.

## [1.9.38-agent] - 2026-10-18 · «Horizonte»

### Añadido (fusión de `aki/personalizacion-estudio-20261017`)
- Tarjetas de encargo Compactas / Equilibradas / Amplias a elección del usuario, con logos conservados; sección «Tu espacio de creación» en Ajustes del asistente.
- Actividad animada durante la generación (activable; se apaga con las animaciones del sistema o fuera de primer plano) y porcentaje solo si el proveedor lo informa de verdad (Atlas y proveedores propios rechazan valores fuera de rango; Flow y el simulado no muestran porcentaje falso).
- Visor de imagen dentro de Nexus para las que el agente trae al chat (pellizco, doble toque, Ajustar).

## [1.9.37-agent] - 2026-10-18 · «Horizonte»

### Añadido
- Versiones con nombre: cada 10 `versionCode` cambia el nombre (vc 70–79 = Horizonte, 80–89 = Índigo, 90–99 = Júpiter…; lista de 20 en `app/build.gradle.kts`). Se ve en la pantalla vacía del Agente, en el arranque y en Ajustes → Diagnóstico. Los APK llevan el nombre en el fichero.

## [1.9.36-agent] - 2026-10-18

### Cambiado
- Ajustes del asistente rediseñado: cabecera fija con estado y resumen, secciones con icono (Cerebro, Respuesta, Consumo y límites, Herramientas, Skills) en tarjetas, filtros de proveedor y modelo bien visibles, estado de la clave como etiqueta, filas plegables con resumen y flecha, y botonera Cerrar/Guardar fija abajo.

## [1.9.35-agent] - 2026-10-18

### Cambiado (revisión visual a partir del vídeo)
- Jerarquía de color: barras superior e inferior con un tono propio por tema, distinto del fondo y de las tarjetas; el tema Claro pasa de beige a gris azulado frío. La banda de entrada del chat usa el mismo tono que las barras.
- Barras más finas: superior de 56 a 40 dp e inferior de unos 64 a 46 dp; el contenido gana espacio.
- Filtros (Vídeo, Imagen, Biblioteca, Historial…): seleccionado con relleno de acento sólido, sin seleccionar con borde propio; mucho más visibles.
- Chat: «Traer N encargos» sustituido por «Encargos ↓», sin contador, que solo aparece si ninguna tarjeta está a la vista y desplaza hasta la última sin reordenar. Tres o más resultados terminados seguidos se agrupan en una tarjeta de miniaturas («Ver tarjetas» las separa; «Agrupar» las junta).
- Chat: una imagen adjunta que ya no existe muestra un hueco con icono y nombre en vez de un cuadro vacío.
- Ajustes ordenado en secciones con cabecera e icono (Personalización, Agente, Proveedores de generación, Generadores propios, Sistema), con tarjetas y filetes.
- Tamaño de letra de toda la app ajustable (80–140 %) en Ajustes → Personalización.
- Biblioteca: título y contador en una línea; zoom con dos dedos (2–6 columnas, recordado) como Google Fotos.
- Lienzo: los ajustes propios del modelo (hasta 6) a la vista, en vez de solo dentro de una hoja.

## [1.9.34-agent] - 2026-10-17

### Añadido
- 20 logos más (Logos_Oficiales_Branding_Completo): Antigravity, Dola, Cohere, Dots, Flow, Gemini, Hi3D, HiDream, Imagen, Kwaipilot, LTX, Recraft, Seedance, Seedream, Sync, Tencent, Udio, Veed, Veo y Youchuan. La marca concreta gana a su casa matriz (Seedream/Seedance antes que ByteDance, Veo/Gemini/Imagen/Flow antes que Google). BytePlus usa el logo de ByteDance porque el archivo recibido traía la cuadrícula de transparencia pintada.

## [1.9.33-agent] - 2026-10-17

### Añadido
- Logos nuevos (Logos_Oficiales_Branding): Crun, Amazon Bedrock, Agnes, Mistral, Perplexity, Pruna, Meshy, Meta y Atlas Cloud, recortados al símbolo y centrados en 256 px con fondo transparente. Se usan cuando el modelo no tiene logo propio; los oscuros llevan fondo claro.

## [1.9.32-agent] - 2026-10-17

### Cambiado
- Tarjetas de encargo: logo oficial del modelo (biblioteca de marcas de ALEXIA ya incluida en la app) en lugar de un círculo con inicial; monograma neutro si no hay logo conocido.

## [1.9.31-agent] - 2026-10-17

### Cambiado
- Agente: el cerebro pasa a ser un texto discreto bajo el título (sin recuadro que apretaba la barra).
- Una sola fila sobre el cuadro de texto: atajos (Traer encargos, Último) a la izquierda y Razonar a la derecha.
- El cuadro de texto va sobre una banda de otro tono con filetes arriba y abajo y borde más marcado.
- Tarjeta de encargo terminada: miniatura a la izquierda y datos a la derecha, sin el hueco vacío; el resto de resultados en una tira pequeña.

## [1.9.30-agent] - 2026-10-17

### Añadido
- Selector de cerebro en la barra del Agente (Bedrock, Claude, Compatible OpenAI, Proxy, Antigravity, BytePlus/Dola y los propios) sin abrir Ajustes.
- Modo de razonamiento junto al cuadro de texto (Auto, Rápido, Mínimo, Bajo, Medio, Alto) cuando el cerebro lo admite.
- Tarjetas de encargo: marca del proveedor (logo si existe como recurso `provider_<id>`, si no una inicial), miniaturas más pequeñas y animación al reordenarse.

## [1.9.29-agent] - 2026-10-17

### Cambiado
- El agente sabe que no puede traer tarjetas al frente y, si se lo piden, señala el botón «Traer los N encargos aquí ↓» en vez de decir que no hay forma.

## [1.9.28-agent] - 2026-10-17

### Corregido
- Crun (Qwen Image Edit y similares): el prompt se comprueba contra el máximo del contrato del modelo (800 caracteres) antes de enviar; antes se mandaban prompts de 858 y 946 caracteres y Crun respondía 422.
- Los errores 400/422 del proveedor muestran su detalle en vez de «HTTP 422» a secas.

## [1.9.27-agent] - 2026-10-17

### Corregido
- Biblioteca: con «Todo» los archivos importados iban todos primero y lo recién generado por Nexus quedaba enterrado debajo. Ahora generado e importado forman una sola línea de tiempo por fecha, lo último arriba.

## [1.9.26-agent] - 2026-10-17

### Añadido
- Botón «Traer los N encargos aquí ↓» en el chat: reúne al final todas las tarjetas de encargo (sin las descartadas) para no tener que buscarlas hacia arriba.

## [1.9.25-agent] - 2026-10-17

### Corregido
- Encargos con referencias grandes (5 MB+) en Crun y Atlas fallaban con «Se ha interrumpido la conexión»: la conexión compartida cortaba la subida a los 60 s de escritura / 120 s totales. Ahora 5 / 10 min, y el corte por tiempo se explica como tiempo agotado en vez de conexión interrumpida.

## [1.9.24-agent] - 2026-10-17 (rama `feat/byteplus-custom-providers-20261017`)

### Añadido
- Detector de modelos sin activar (`ModelNotOpen`): «Probar conexión» muestra un diagnóstico con pasos y el botón «Activar modelo» (enlace directo a la consola de ModelArk de la región), «Volver a probar» y la ficha del modelo.
- «Comprobar cuáles están activados»: petición mínima real por modelo; los chips marcan ✓ activado, ⚠ sin activar o ✕ inexistente. Los modelos de imagen/vídeo/voz se ocultan en el selector de cerebro de BytePlus.
- Guardar no admite un ID de modelo que no figure en la lista del proveedor si hay uno parecido (propone la corrección).

## [1.9.23-agent] - 2026-10-17 (rama `feat/byteplus-custom-providers-20261017`, sobre la de Antigravity `b1bc7b0`)

- **BytePlus ModelArk como cerebro** (Android vc57). Preconfigurado con `https://ark.ap-southeast.bytepluses.com/api/v3`,
  modelo `dola-seed-2-1-turbo-260628` y **Responses API** (también Chat Completions): razonamiento (`thinking` /
  `reasoning_effort`, excluyentes como exige ModelArk), streaming, imágenes, function calling con todas las herramientas
  del agente, y continuidad del contexto reenviando la conversación completa (no depende de `previous_response_id`).
  Se elige en Ajustes → Cerebros del agente o en los chips del asistente; la clave se guarda cifrada.
- **Proveedores LLM propios** (Ajustes → Cerebros del agente → «Añadir proveedor personalizado»): nombre, URL y endpoint,
  autenticación (Bearer, cabecera con prefijo, parámetro o ninguna), protocolo (Chat Completions, Responses, REST de solo
  texto), descubrimiento de modelos por `/models` o ID a mano, capacidades (herramientas, visión, razonamiento, streaming,
  JSON), parámetros extra, cabeceras, tarifas y límites. **Probar conexión** hace dos cosas: lista modelos y lanza una
  petición mínima al modelo elegido (un modelo puede aparecer en la lista sin estar activado). Guardar, editar, duplicar y
  eliminar; aparecen al momento en el selector y usan el agente real. El REST personalizado es solo texto: sin
  herramientas ni imágenes (se dice en pantalla).
- **Generadores multimedia propios** (Ajustes → Generadores personalizados → «Añadir proveedor API»): URL, autenticación,
  modelos con capacidad (texto→imagen, imagen→imagen, texto→vídeo, imagen→vídeo, escalar imagen/vídeo), plantilla JSON de la
  petición con marcadores, parámetros editables (una línea por parámetro), rutas para extraer la respuesta, trabajos
  síncronos o asíncronos (id de tarea, consulta, estados de éxito/fallo, progreso, error) y subida de adjuntos (incrustados o a
  un endpoint). Sus modelos entran en el Lienzo y para el agente, y sus resultados usan las tarjetas, el Historial y la
  Biblioteca de siempre. Plantillas orientativas (genérico síncrono/asíncrono, estilo Atlas, estilo Runway, estilo fal.ai);
  **no** se promete compatibilidad con protocolos que necesiten un adaptador propio (firmas, subidas en varios pasos, WebSocket).
- **Todos los límites son tuyos** (Ajustes del asistente → «Límites del agente» y, por proveedor, «Límites de este
  proveedor»; 0 = sin límite): pasos por mensaje, tiempo por mensaje, tokens, resultados de Flow, imágenes/vídeos/documentos
  por mensaje, historial, tamaño de resultados de herramienta, contexto de la interfaz y minutos de seguimiento de un trabajo.
  Antes estaban fijos (20 pasos, 600/240 s, recorte 256–32 000 tokens, «hasta 20» de Flow, 4 imágenes, 2 vídeos, 5 documentos,
  60 mensajes, pausa de seguimiento a los 30 min…). El prompt ya no fija cifras: recibe un bloque «LÍMITES CONFIGURADOS POR EL
  USUARIO» que prevalece sobre cualquier otra. Presupuesto de gasto: acepta 0 (sin límite) y cualquier importe; la temperatura
  ya no se recorta a 0–1. Los valores por defecto son los de siempre, así que sin tocar nada el comportamiento no cambia.
- Seguridad: claves cifradas con SecureStorage (una por proveedor, fuera del perfil); URLs solo `https://` (o `127.0.0.1` /
  `localhost`) y sin credenciales incrustadas; cabeceras sin saltos de línea; los parámetros extra no pueden pisar modelo,
  mensajes ni herramientas; nada de claves en logs, repositorio ni APK.
- Sin regresiones: Bedrock, Claude API, OpenAI-compatible, Proxy y Antigravity, y Atlas, Crun, Runway, Flow y el simulado no se
  tocan (los proveedores propios se **suman** al registro). El proveedor OpenAI-compatible conserva su comportamiento por defecto.
- Pruebas: agent-core 218 (4 son pruebas reales opt-in), app 332, núcleo de escritorio 218, todas sin fallos.
- **Validación real de BytePlus**: la clave autentica y `/models` devuelve 58 modelos, pero `dola-seed-2-1-turbo-260628` **no
  está activado en la cuenta** (`ModelNotOpen`), así que el flujo completo (chat, herramientas, streaming, razonamiento) está
  probado solo con servidor simulado. `BytePlusLiveTest` lo comprobará con la clave real en cuanto se active el modelo.
- No validado en el S25 (no estaba alcanzable por ADB ni Tailscale al terminar). En el Pixel se revisaron los editores; la
  integración del catálogo en pantalla no se pudo observar porque otro proceso (Termux) tomó el primer plano.
## [1.9.18-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc52, los pendientes de la auditoría:
  - Biblioteca: **una sola hoja** al abrir un archivo importado (antes dos hojas seguidas con «Abrir»
    repetido), con vista previa, ficha, **«Usar en Lienzo»** (entra como entrada del modelo, igual que
    «compartir con Nexus»), **«Al Agente»** (lo adjunta al chat), Abrir, Compartir, Guardar y Quitar.
    Mantener pulsado marca (el menú de un archivo suelto está en «Más opciones» de la selección).
  - Chat: **micrófono** cuando no hay nada que enviar. Usa el reconocimiento de voz del sistema
    (sin permisos ni descarga de 148 MB) y el texto se inserta SIN enviar, para revisarlo. Es una decisión
    consciente frente a Whisper local, que solo transcribe ficheros grabados y exigiría una grabadora propia
    y el modelo descargado; el reconocedor del sistema puede procesar el audio fuera del móvil.
  - Agente: herramienta **`suggest_replies`** (32 herramientas). El modelo da 2–4 opciones y salen como
    botones sobre el campo, solo bajo su última respuesta; pulsar uno las envía como tu mensaje. No se
    deduce del texto: es un bloque estructurado (sin duplicados, rótulos ≤40, textos ≤300).
  - Trabajos atascados: a partir de 10 min la fila de Biblioteca dice «Tarda más de lo normal» en ámbar con
    **consultar estado** (solo consulta, nunca reenvía) y **dejar de seguir** (no cancela en el proveedor y lo
    avisa). En Historial, 3 o más fallos seguidos del mismo modelo y texto se pliegan en una fila.
- Windows: `desktop/agent/core` sincronizado con agent-core (148 ficheros, 165 pruebas, 0 fallos) y JAR
  recompilados. El host solo pinta progreso: no muestra encargos ni botones, así que `suggest_replies` no
  se ofrece allí. El test de cobertura de skills ahora encuentra el catálogo también desde `desktop/agent/core`.
- Sin capturar en el S25 (estaba en uso): la hoja única y el micrófono. En el Pixel se vio el chat con el
  micrófono y las respuestas en tarjeta.
## [1.9.17-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc51, a partir de una auditoría de las capturas del S25 (cotejada con el código):
  - Biblioteca: **marcado rápido**. Mantener pulsado marca; con algo marcado, tocar suma o
    quita; vaciar la selección la cierra (también con Atrás). La barra muestra «N marcados»,
    marcar todo lo visible, **exportar en ZIP** (se guarda donde elijas; los archivos sin copia
    local se omiten y se avisa), favorito, ocultar y «Más opciones del archivo». El buscador
    pasa a la barra de arriba.
  - Chat: las respuestas del agente llevan un fondo suave, como tus mensajes. Se quita el
    toque vacío de la respuesta (queda la pulsación larga, con etiqueta para TalkBack).
  - Lienzo: el prompt ya no tiene altura fija; la 8.ª línea, «Expandir» y el contador de
    tokens dejan de recortarse.
  - Historial: estado y hora van primero en la línea (un modelo largo ya no los esconde),
    «Todos» sustituye a «Recientes/Todos», los filtros sin contenido se ocultan y usan el
    mismo chip sin borde que el resto; el menú de cada fila ya no lee el UUID del trabajo.
  - Barra inferior: «Biblioteca» entera y sin leerse dos veces; Modelos sin el «Ejemplo:»
    repetido.
- Pendiente de la auditoría: micrófono en el chat (hoy Whisper solo transcribe ficheros, no
  hay captura en vivo), respuestas rápidas con opciones estructuradas, hoja única al abrir un
  archivo importado con «Usar en Lienzo/Agente», y trabajo atascado con acción rápida.
## [1.9.16-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc50, Biblioteca: vuelven los metadatos y el filtro de origen, sin etiquetas
  sobre las miniaturas. (vc49/1.9.15 era lo mismo con un pie menos contrastado sobre
  fotos claras; estuvo instalada unos minutos en el S25 y el Pixel.)
  - Cada miniatura lleva al pie resolución · tamaño («1080p · 12,4 MB», «2048×2048 · 3,1 MB»)
    y fecha y hora («16 oct · 07:14»). Se miden del fichero local una sola vez. Se ocultan
    desde ⋮ → «Ocultar detalles de miniaturas». El ▶ y la duración pasan arriba a la derecha.
  - «Origen ▾» en la fila de filtros: Todo, Generado por Nexus, Importado por ti (y
    Editado/Compartido si hay). Favoritos pasa a una estrella para que quepa en la fila.
  - La vista previa de un archivo importado muestra resolución, tamaño, duración, fecha
    y formato. La hoja de un resultado generado sigue con su ficha completa.

## [1.9.14-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc48, revisada pestaña por pestaña con capturas del S25:
  - Biblioteca sin fila «Origen» ni etiqueta «Generado», sin menú encima de cada
    miniatura (mantener pulsado) y trabajos en curso en una línea.
  - Historial: título útil, fotograma de los vídeos, dos líneas y coste solo si es real.
  - Lienzo: fotograma del vídeo adjunto, pastillas iguales de 36 dp y prompt que crece.
  - Modelos: filas más compactas y sin menú por fila.
  - Chat: burbujas del usuario en texto medio.
- 1.9.12/vc46: elegir el LLM. «Claude API» con tu clave de Anthropic y «Compatible
  OpenAI» (OpenAI, OpenRouter, Gemini, DeepSeek, Groq, Mistral o servidor local), clave
  cifrada por proveedor y tarifa opcional para el límite de consumo. Icono de la APK
  sin fondo. Selector de fotos con fotogramas reales y selección en la esquina.

## [1.9.11-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc45: chat más compacto. La tarjeta pone estado, detalles (chevron) y un
  menú ⋮ (Lienzo, Descartar, Historial) en la cabecera; el resultado se ve como
  miniatura 16:9 que abre el visor; un único botón de 40 dp. Las respuestas del
  agente ya no añaden una fila ⋮ ni la pastilla de herramientas: copiar, compartir
  y ver pasos se abren manteniendo pulsado. Barra de entrada más baja.
- Skills nuevas para los 43 generadores que no tenían (upscalers, voz, música, 3D,
  NVIDIA Cosmos, Atlas Studio, ERNIE), escritas desde su README o schema oficial;
  una prueba exige skill para los 400 generadores del catálogo. Al preparar un
  encargo, el agente recibe las restricciones, los errores habituales, la estructura
  de prompt y la plantilla de la tarea de ese modelo.

## [1.9.10-agent] - 2026-10-16 (rama `fix/encargo-unico-agente-ui`)

- Android vc44: integra 1.9.9 (acceso a Historial, Biblioteca sin etiquetas de origen
  en las miniaturas) y corrige un cierre al enviar mensajes: el flag de regex `(?U)`
  no existe en Android; una prueba lo vigila en agent-core y en la app.
- Una sola tarjeta por encargo con id persistente; reintentos, correcciones
  y recargas la actualizan en su sitio. Estados borrador → pendiente → enviando →
  procesando → completado / error / incierto, con un único botón por estado y
  bloqueo de pulsaciones repetidas (idempotencia por intento).
- El agente recibe en cada turno el estado real de la interfaz (tarjetas, lo que
  muestran, pulsaciones, errores literales y diagnóstico), dispone de `get_ui_state`,
  corrige con `update_plan(order_id)` sin crear tarjetas y rehace su respuesta si
  afirma un envío o resultado que el estado no respalda.
- Tencent Video Upscaler: sin prompt (el schema no lo tiene), `resolution` deducida
  de la plantilla, `short` por defecto incompatible omitido, `fps` medido del vídeo
  original (Atlas lo exige aunque el schema permite omitirlo) y tarifa por nivel y fps.
- Los chats antiguos se limpian al abrirlos: tarjetas duplicadas/triplicadas y
  envíos fallidos repetidos del mismo vídeo pasan a un encargo con historial.
- Alcance y validación: [encargo único 1.9.10](docs/ENCARGO-UNICO-199-20261016.md).

## [1.9.9-agent] - 2026-10-16

- Android vc43: los avisos que remiten a Historial llevan un acceso directo
  «Abrir Historial» (chat, tarjetas de encargo y visor de resultados), que abre
  la misma pestaña de la barra. Las compilaciones anteriores a 1.9.8 tenían solo
  cuatro destinos y no podían llegar a Historial aunque el código definía su ruta.
- Alcance y comprobación: [navegación de Historial](docs/HISTORIAL-NAVEGACION-199-20261016.md).

## [1.9.8-agent] - 2026-10-16

- Android vc42 mantiene el turno del asistente en un servicio de primer plano
  mientras Nexus está minimizado. Una notificación silenciosa indica que sigue
  trabajando y permite volver a la conversación; el servicio se cierra al acabar,
  al producirse un error o cuando el usuario pulsa «Parar».
- Volver desde Ajustes/Flow a una pestaña recupera esa pestaña en vez de restaurar
  Ajustes encima. Las respuestas del agente muestran medios abiertos o creados:
  imágenes, vídeo y audio reproducibles, y documentos/archivos abribles.
- Alcance y validación: [turnos del asistente en segundo plano](docs/AGENT-BACKGROUND-TURN-198-20261016.md).

## [1.9.7-agent] - 2026-10-16

- Android vc41: bienvenida una sola vez por arranque del proceso; temas claros
  Arena y Rosa; selector del Lienzo entre modalidades sin perder prompt ni adjuntos.
- Biblioteca con importación visible, archivos locales/documentos/ediciones,
  descarga y eliminación accesibles desde el menú. Los fallidos se retiran de
  «En curso», se pueden archivar y sus tarjetas se pueden quitar del chat.
- El agente puede listar y abrir la biblioteca durante un turno, crear PDF con
  imágenes y archivos de código, y analizar niveles y bandas de audio local.
  La conversión de imágenes incluye resultados guardados en la galería.
- Investigación web explícita para recomendar modelos, con evidencia de la
  variante y fuentes actuales; presupuesto monetario local sólo si se activa.
- Preparación de upscale IA con modelos del catálogo, revisión visual con medidas
  locales, panorama del proyecto y búsqueda de decisiones antiguas. Caché por
  encargo y reducción del contexto técnico antiguo para evitar trabajo repetido.
- Alcance y validación: [experiencia 1.9.7](docs/EXPERIENCE-197-20261016.md).

## [1.9.6-agent] - 2026-10-15

- Android vc40: elimina las cuotas fijas locales de 150.000 tokens por mensaje
  y 500.000 diarios que bloqueaban el chat aunque quedase presupuesto monetario.
- Conserva el registro de consumo y los importes configurados, sin cambiar modelo,
  backend, cuentas, claves, proyectos ni conversaciones.
- Añade regresiones con el consumo real del S25 y sincroniza la política de
  tokens con el código del agente de Windows.
- Detalles: [corrección del bloqueo diario](docs/AGENT-TOKEN-QUOTA-FIX-20261015.md).

## [1.9.5-agent] - 2026-10-15

Release `v1.9.5-agent` (Android `1.9.5-agent-preview` / `versionCode 39` y Windows `1.9.5-agent-desktop`) que incorpora el logo oficial de NEXUS y el indicador de versión en el preview/onboarding de apertura, pantallas de bienvenida y cabeceras en móvil y EliteBook.

### Logo oficial de NEXUS y versión en preview / onboarding (Android y Windows)
1. **Android (`1.9.5-agent-preview` · `versionCode 39`)**:
   - Incorporados los recursos gráficos oficiales `drawable/nexus_logo.png` (isotipo + wordmark NEXUS) y `drawable/nexus_mark.png`, y actualizado el icono adaptativo `mipmap-anydpi-v26/ic_launcher.xml` para usar el isotipo oficial.
   - Añadido `NexusStartupOnboardingOverlay` en `MainActivity.kt`, que muestra al abrir la aplicación (y al regresar tras segundo plano) una tarjeta de preview/onboarding con el logo oficial de NEXUS y el badge de versión `v1.9.5-agent-preview · build 39` (se desvanece suavemente o se cierra al tocar; desactivado en `BuildConfig.OFFLINE_QA`).
   - Actualizado `AssistantScreen.kt` (`EmptyHint` y `NexusTopAppBar`) para mostrar el logo oficial completo y la píldora de versión `v1.9.5-agent-preview · build 39` en el onboarding del asistente y en la barra superior.
2. **Windows (`1.9.5-agent-desktop` en HP EliteBook)**:
   - Añadido `mountStartupOnboarding()` (`#nx-startup-onboarding`) en `desktop/ui/desktop-design.js` y `desktop-design.css`, que muestra el logo oficial `branding/nexus-logo.png` y el badge `v1.9.5-agent-desktop` cada vez que se abre Nexus Studio.
   - Actualizados el onboarding de bienvenida (`desktopWelcome`), el banner superior de conversaciones activas (`.nx-chat-brand-banner`), el lienzo de previsualización (`.studio-blank`), la guía de primeros pasos (`#studio-start-guide`), la cabecera de Crear (`.nx-creative-brand`), la marca lateral (`.sidebar .brand`) y la barra superior (`#parity-version-chip` en `agent-parity.js` / `agent-parity.css`) para presentar el logo oficial completo sin recortes junto con la versión activa `v1.9.5-agent-desktop`.
3. **Recuperación automática de vistas previas en Biblioteca (`studio-gallery.js`, `polish.js`, `local_host.py`)**:
   - Priorización de copias locales `/media/...` y URLs permanentes frente a URLs temporales firmadas de 24 h (`X-Tos-Expires=`, `Expires=`) en `merge_jobs` y `studio-gallery.js`.
   - En `polish.js`, cuando falla la carga de una imagen en `.result img`, antes de mostrar «Vista previa no disponible» se prueban el resto de salidas de `job.outputs` y, para tareas de `atlas_cloud` con `remoteId`, se consulta `/api/v1/model/prediction/{remoteId}` para recuperar y persistir la URL permanente archivada en `atlas-media.oss-us-west-1.aliyuncs.com/assetd-history/v1/...`.
4. **Auditoría y refactorización integral de UI/UX (Windows Desktop y Android APK)**:
   - **Windows (`desktop/ui/`)**: Unificación de cabeceras en una única Top Bar de `56px` con botón de colapsar sidebar integrado junto al logo (`#creative-dock-toggle`); eliminación de controles duplicados (`#header-actions [data-nav="canvas"]`, botones `+` redundantes); sistema de jerarquía visual de 3 niveles (Primario, Secundario y Badges informativos); rejilla con eje vertical izquierdo único (`24px`), tarjetas de sugerencias e historial rediseñadas (`44x44px`), selectores `.select-trigger` alineados a la izquierda con chevron fijo a la derecha, truncado seguro (`ellipsis`) y composer unificado con `resize: none` y barra interna de herramientas.
   - **Android (`app/src/main/java/com/vidadomu/nexus/`)**: Unificación del eje vertical izquierdo (`screenMargin = 16.dp`) y altura de Top Bar (`topBarHeight = 56.dp`) en todas las pantallas (`Tokens.kt`); `StudioBalanceChip` rediseñado como píldora compacta de 1 línea con divisores sutiles de `0.5.dp` en `NexusTopAppBar` y `NexusBottomBar`; eliminación de las 4 barras superiores apiladas en `Biblioteca` (`LibraryScreen.kt`) integrando búsqueda y contador en la Top Bar y «Imágenes del proyecto · Gestionar» en la tira única de filtros; selector de proyecto con chevron desplegable visible en `Asistente` (`AssistantScreen.kt`), burbujas de usuario e `InputBar` refinados (`AssistantExperience.kt`) y truncado seguro de 1 línea en `PlanCardView`, `GenerationSummaryRow` y `CreateScreen`.

---

## [1.9.4-agent] - 2026-10-14

Release `v1.9.4-agent` (Android `1.9.4-agent` / `versionCode 38` y Windows `1.9.4-agent-desktop`) con las mejoras de seguridad de cobro, idempotencia, concurrencia, puentes (Flow y Sol/AKI), paridad entre núcleos del asistente y las correcciones de la revisión del commit `b5401b8`, conservando intactos los artefactos de la Release `v1.9.3-agent`.

### Correcciones de revisión (`b5401b8`)
1. **Windows — `job_id` obligatorio en `generate` y asignación exacta de `remoteId`**:
   - `desktop/ui/app.js` incluye `job_id: job.id` en cada petición `rpc('generate', ...)`.
   - `desktop/local_host.py` exige `job_id` no vacío y asigna `remoteId` **únicamente** al trabajo cuyo `id == job_id` bajo `STATE_LOCK` (`threading.RLock()`), eliminando la selección implícita del primer trabajo `SENDING` del proveedor.
   - Añadida prueba de concurrencia en `desktop/tools/test-host-integrity.py` con dos envíos simultáneos cuyas respuestas llegan en orden inverso.
2. **PDFs mixtos — preservación de páginas escaneadas o gráficas**:
   - `desktop/document_reader.py` y `AgentDocuments.kt` conservan la vía del documento PDF original (`format = "pdf"`) cuando cualquier página carece de texto extraíble o contiene imágenes incrustadas, evitando descartar páginas escaneadas o gráficas al convertir a texto.
   - Añadida prueba en `desktop/tools/test-library-pdf-193.py` con un PDF mixto de 3 páginas (`texto + página gráfica + texto`).
3. **Google Flow — verificación SHA-256 de la copia local y recuperación automática**:
   - `desktop/flow_provider.py` verifica el hash SHA-256 de la copia local en `media/` antes de reutilizarla y, si está alterada o corrupta (incluso manteniendo el mismo tamaño en bytes), la restaura atómicamente desde el archivo original válido en `RUNTIME / 'results'`.
   - Añadida prueba en `desktop/tools/test-flow-automation.py` con una copia local alterada del mismo tamaño exacto en bytes.
4. **Android — separación entre «Detener seguimiento» y «Cancelar generación»**:
   - `GenerationRepository` y `GenerationCancellationPolicy` (`Repositories.kt`) separan `stopTrackingJob` (acción estrictamente local que detiene el `WorkManager` y pausa el seguimiento con `__tracking_paused = "true"` sin llamar al proveedor) de `cancelRemoteGeneration` (acción explícita que verifica `supportsRemoteCancellation` en el proveedor y explica claramente si el proveedor admite cancelación remota: Runway sí admite `DELETE /v1/tasks/{id}`; Atlas Cloud, cRun y Google Flow no admiten cancelación remota una vez enviada la tarea).
   - `GenerationsScreen.kt` ofrece ambas acciones por separado en el menú de tareas en curso y muestra un diálogo explícito de confirmación para «Cancelar generación».
   - Añadida suite unitaria `GenerationCancellationTest.kt`.
5. **Windows — arranque desde la instalación actual y verificación de accesos directos**:
   - `desktop/local_host.py` registra `root` y `version` (`1.9.4-agent-desktop`) en `local-session.json` y, al detectar el mutex activo (`GetLastError() == 183`), verifica `is_same_installation_root`: si el proceso previo pertenecía a otra ruta (como `Documents\ChatGPT\NEXUS` o una versión anterior), detiene el host antiguo y toma el relevo desde la instalación actual conservando `%LOCALAPPDATA%\NexusDesktop`.
   - `desktop/install-local.ps1` y `desktop/tools/deploy-desktop-parity.py` actualizan y verifican los accesos directos de Escritorio, `Documents\Biblioteca\Aplicaciones\Nexus Studio.lnk` y barra de tareas (`User Pinned\TaskBar\Nexus Studio.lnk`).

### Seguridad de cobro e idempotencia (`agent-core`, Android y Windows)
- **Protección de planes con envío incierto (`B-C1`, `B-A6`)**:
  - `SpendGuard.requestConfirmation` conserva los estados `PlanState.UNCERTAIN` y `PlanState.CONFIRMED` frente a re-llamadas del modelo en vez de degradarlos a `AWAITING_USER_CONFIRMATION`.
  - `PrepareGenerationTool` bloquea la ejecución directa de Google Flow (`execute=true`) cuando ya existe un plan con idéntica huella en `PlanState.UNCERTAIN` sin `new_copy=true`.
  - En Android (`NexusAgentAdapters.kt`), el adaptador de envío comprueba `__submission_uncertain == "true"` antes de evaluar `job.status == GenerationStatus.FAILED`, marcando el plan como `UNCERTAIN` en lugar de devolver un error reintentable al agente.
- **Confirmación de un solo uso y sincronización (`B-A1`)**:
  - `SpendGuard` sincroniza sus mutaciones (`@Synchronized`), impide que `confirmByUser` sobrescriba un plan ya en `PlanState.CONFIRMED`, exige `states[plan.id] == PlanState.CONFIRMED` en `consume`, y rechaza re-envíos de planes ya en vuelo en `submitAccountFlow`.
  - En Android (`AssistantViewModel.kt`), se bloquea el envío de nuevos turnos de chat mientras exista un plan con envío en curso (`busy`).
- **Persistencia atómica tras envío al proveedor (`B-A2`, `B-A3`)**:
  - En Android (`Repositories.kt`), el bloque de llamada al proveedor + guardado de `providerJobId` en Room + encolado de `GenerationPollingWorker` se ejecuta bajo `withContext(NonCancellable + Dispatchers.IO)` para que el cierre de la pantalla `CreateScreen` no cancele la corrutina tras haber enviado la tarea al proveedor.
  - En Windows (`desktop/local_host.py`), el servidor persiste inmediatamente `remoteId`, `status` y `provider` en `state.json` bajo `STATE_LOCK` nada más recibir respuesta de `provider_request` en `operation == 'generate'`, evitando perder el identificador remoto si la pestaña se cierra antes del guardado del cliente.
  - Se afinó la clasificación de errores definitivos (`definite`) en `Repositories.kt` para no tratar cadenas genéricas como `"timeout"` dentro del mensaje de excepción como no-facturables (`B-M4`).
- **Guardia contra doble envío en Google Flow (`B-A4`, `B-M7`)**:
  - `FlowBridgeProvider.pollJobStatus` devuelve inmediatamente las tareas terminales (`COMPLETED`, `FAILED`, `CANCELLED`) y rechaza despachar tareas `queue_` que no estén en `QUEUED` o `PROCESSING`, limpiando `providerJobId = null` cuando falla el envío inicial.
  - `GenerationRepository.refreshConfirmedCost` incorpora bloqueo por `jobId` (`pollLocks`) y omite tareas de Flow, `queue_` o no completadas. En `GenerationsScreen.kt`, el botón `"Consultar coste"` solo se muestra en generaciones completadas de proveedores con consulta de coste por API.
- **Preservación de créditos esperados en Windows Flow (`B-A5`)**:
  - `desktop/ui/app.js` conserva el valor numérico de `plan.cost.amount` en `flowSubmit`, y `desktop/flow_automation.py` garantiza que `expected_credits` sea siempre un entero no negativo antes de invocar `flow_process_asset`.

### Agente, presupuesto y paridad `agent-core` / `desktop/agent`
- **Recuperación limpia tras cancelación de turno (`B-A8`)**:
  - `AgentOrchestrator.kt` añade bloques sintéticos `ContentBlock.ToolResult(..., isError = true)` para cualquier `ToolUse` pendiente cuando se cancela un turno (`CancellationException`), evitando que el siguiente mensaje falle la validación de Bedrock Converse (`toolResult blocks at messages.N.content`).
  - Se captura `TimeoutCancellationException` por herramienta sin abortar todo el turno como cancelación del usuario.
- **Presupuesto de contexto y límites de salida (`B-M10`)**:
  - `AgentOrchestrator.kt` limita cada `ToolResult` serializado a `32_000` caracteres e impide ejecutar bloques `ToolUse` truncados cuando `stopReason == StopReason.MAX_TOKENS`, manteniendo `BudgetedLlmProvider` acotado a `BedrockDirectProvider` para que los modelos del puente (`openai.gpt-5.6-sol`) deleguen el presupuesto en el proxy.
- **Unificación de `NativeOperations.kt` y sincronización de skills (`B-A9`, `B-M1`, `B-M2`)**:
  - `agent-core` incorpora `"sol"` en el `enum` de `agent_model` de `configure_assistant` y propaga `isError == "true"` de operaciones nativas como fallos reales (`fail(...)`) al orquestador.
  - Se sincronizaron `agent-core` y `agent-skills` (incluyendo `mateo_madrid_no_duerme/SKILL.md`) hacia `desktop/agent/` mediante `desktop/tools/sync-agent-core.py` y se recompilaron los JAR de `desktop/agent/runtime/`.

### Puentes Sol / AKI y red (`agent-proxy`, Android y Windows)
- **Alineación de timeouts, salud real del túnel y fallback Wi-Fi LAN (`B-A7`)**:
  - `BedrockProxyProvider.kt` amplía `readTimeout` a `170s` y `callTimeout` a `180s` (superando los `150s` de `aki_proxy.py`).
  - `agent-proxy/aki_proxy.py` espera hasta `8.0s` en `AkiClient.lock.acquire(timeout=8.0)` antes de devolver `429 Busy`, y distingue salidas vacías por expiración de sesión OAuth (`[cli] Error: failed to load AWS credentials` con código de salida `0` -> `aki_auth_expired`) frente a errores de lectura de adjuntos (`aki_tools`).
  - `desktop/sol_bridge.py` verifica `/health` por HTTP antes de dar por bueno un puerto abierto, limpia procesos `ssh.exe` colgados tras timeout y añade fallback automático a la IP Wi-Fi local del S25 (`192.168.1.134:8022`) cuando Tailscale (`100.119.88.105:8022`) está desconectado.
  - `desktop/install-local.ps1` actualiza también el acceso directo anclado en la barra de tareas (`User Pinned\TaskBar\Nexus Studio.lnk`) para evitar que lance un worktree antiguo.
- **Autenticación previa a lectura de cuerpo y protección SSRF (`B-B2`, `B-B3`, `B-B5`)**:
  - `agent-proxy/nexus_proxy.py` valida el encabezado `Authorization` antes de leer `Content-Length` bytes del socket.
  - `desktop/native_internet.py` y `AgentInternet.kt` bloquean direcciones loopback, privadas, link-local y reservadas en `fetch_web_page` / `AgentInternet.page` para prevenir SSRF hacia `127.0.0.1`.
  - `desktop/native_internet.py` restringe la limpieza de tokens MCP en fallo `401/403` al servidor afectado en lugar de vaciar todos los servidores configurados.

### Estabilidad, base de datos, multimedia e interfaz
- **Protección de índice Alexia y base de datos Room (`B-A10`, `B-M8`)**:
  - `AlexiaBridge.kt` evita sobrescribir un `axel-index.json` no vacío con un índice vacío si falla la lectura o el parseo JSON, y se eliminó la doble llamada a `AlexiaBridge.classifyJob` en `GenerationPollingWorker.kt`.
  - `NexusDatabase.kt` añade `onDowngrade` no destructivo y conversión tolerante a valores desconocidos en `Converters`.
- **Proveedores y red en Android (`B-M3`, `B-M5`)**:
  - `AtlasCloudProvider.kt` falla explícitamente si la respuesta carece de `id` de predicción en lugar de generar un `unknown_<timestamp>` imposible de sondear.
  - `NetworkModule.kt` respeta cualquier encabezado `Authorization` explícito en `AuthInterceptor`, y `SettingsRepository.testConnection` envía la cabecera `X-Nexus-Provider: atlas_cloud`.
- **Multimedia, PDF, caché y UI en Windows y Android (`B-M11`..`B-M15`, `B-H1`, `B-B1`)**:
  - `AssistantViewModel.kt` restablece `includeInBatch = false` al restaurar planes persistidos.
  - `desktop/ui/assistant.js` persiste `Agent.owner` en `sessionStorage` para no mezclar historiales al recargar.
  - `desktop/native_operations.py` limpia siempre el archivo temporal `.wav` de Whisper en un bloque `finally:` y resuelve alias de caché (`asset-file?...`) al renombrar archivos en la biblioteca.
  - Mejoras visuales en `NativeParameterControl.kt` (`verticalArrangement = Arrangement.spacedBy(6.dp)` en `FlowRow`), `InlineNotice.kt` (iconos diferenciados para error vs información) y `desktop/ui/studio-ux.css` (espaciado y truncado limpio en barra de estado inferior).
  - Añadido `.gitattributes` en la raíz con `eol=lf` para `gradlew` y `*.sh`.

---

## [1.9.3-agent] - 2026-10-13

- Consolidación en `main` de **Nexus Agent 1.9.3** para Android (`versionCode 37`, opciones visuales de modelos), **Nexus Studio 1.9.3** para Windows (biblioteca, lector de PDF de hasta 50 páginas y visor multi-salida) y el widget de Termux para el puente **Nexus–AKI** (`Sol`).
- Véase [`docs/RELEASE-193-CONSOLIDADA-20261013.md`](docs/RELEASE-193-CONSOLIDADA-20261013.md).
