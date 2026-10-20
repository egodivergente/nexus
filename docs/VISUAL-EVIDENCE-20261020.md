# Evidencia visual del agente — 20 de octubre de 2026

El incidente comunicado incluye afirmaciones incompatibles sobre un chorro,
césped, física y continuidad de un plano. La conversación pegada demuestra las
contradicciones, pero no identifica por sí sola qué imágenes recibió el modelo
en cada llamada. No se ha reproducido la conversación original contra el LLM.

El vídeo adjunto a este encargo dura aproximadamente 6,63 segundos y muestra
un hombre junto a barrotes, no el plano exterior descrito en aquella conversación.
No se incorpora el vídeo privado al repositorio ni se infiere que sea el mismo
asset `ast_8dead0fa33d9`.

## Fallos comprobados en el código

- La compactación sustituye imágenes antiguas por marcadores de texto y conserva
  las descripciones anteriores del asistente. Faltaba un inventario actualizado
  que diferenciase esas descripciones de los píxeles todavía presentes.
- La carga de vídeo desde Biblioteca no comprobaba que se hubieran recibido las
  seis imágenes antes de incorporarlas al contexto. La ruta de adjuntos sí lo
  comprobaba.
- Las etiquetas de vídeo del escritorio no incluían la duración medida y la
  identidad del asset junto al tiempo de extracción.

Estas condiciones pueden favorecer el fallo descrito; no demuestran cuál causó
cada frase de la conversación original.

## Corrección

`agent-core` es la fuente compartida que compila también `desktop/agent/core`.
El motor registra la procedencia de las imágenes cargadas, delimita cada fuente
en el mensaje y añade a cada solicitud un inventario de las imágenes realmente
presentes. Compara las instancias de imagen, no su contenido ni sus nombres:
dos vídeos con imágenes idénticas siguen siendo dos fuentes diferentes.
Las referencias débiles evitan conservar los bytes retirados del historial.

Las muestras de vídeo de Biblioteca y de adjuntos pasan la misma validación:
seis imágenes no vacías y solo bloques de texto/imagen. Una carga incompleta
de Biblioteca no envía las imágenes parciales al LLM.

Las instrucciones visuales se aplican incluso con un prompt personalizado.
Exigen identificar archivo y tiempo, separar observación e interpretación,
recomprobar una objeción sin dar la razón automáticamente y limitar las
conclusiones sobre movimiento a la evidencia disponible. Las imágenes extraídas
deben abrirse: una lista de resultados no demuestra su inspección.

## Validación y alcance

`VisualEvidenceTest` cubre separación de fuentes, pérdida de píxeles al compactar,
historial restaurado sin imágenes, carga incompleta de Biblioteca, reapertura,
reglas con prompt personalizado y rechazo de bytes vacíos. Se ejecuta junto a
las pruebas existentes de vídeo y Biblioteca: 15 pruebas focalizadas aprobadas.

En AXELPC, con JDK 17, Gradle 9.1.0, `--offline --max-workers=1 --no-parallel
--no-daemon`, también terminó `-p desktop/agent :core:test test runtime`:
252 pruebas aprobadas del núcleo, 15 del escritorio, 7 de almacenamiento de
Corte y 77 de su línea de tiempo; 351 aprobadas, ningún fallo y 4 omitidas.
El runtime completo contiene 15 JAR. Se comprobaron las clases nuevas y el
SHA-256 de cada JAR; el informe local está en
`outputs/visual-evidence-20261020/validation.json` (no versionado).

Estas pruebas comprueban el contrato del motor con un proveedor simulado. No
demuestran que un LLM deje de cometer errores de visión. La corrección tampoco
es un detector automático de objetos ni un verificador de física. No se ha
regenerado ni subido el vídeo del usuario a un proveedor. La instalación en el
dispositivo afectado y una prueba conversacional real deben informarse por
separado de la compilación y las pruebas JVM.
