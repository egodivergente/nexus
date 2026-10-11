# Adjuntos en Nexus con GPT-5.6 Sol

## Implementación

- El puente acepta bloques Converse de imagen y PDF, además de texto y herramientas.
  No cambia el modelo `openai.gpt-5.6-sol`, las claves AWS ni la cuota diaria configurada.
- Las imágenes se leen con el comando nativo AKI **`view /ruta`**. `aki read` devuelve
  metadatos y NO es evidencia de que el modelo haya visto los píxeles.
- PDF: Poppler local (`pdfinfo`, `pdftotext`, `pdftoppm`) extrae texto y renderiza todas
  las páginas. El modelo debe ver cada página, incluidos gráficos/PDF escaneados.
  No se envía sólo el nombre ni se finge haber leído el archivo.
- Android incorpora un loader de vídeo que extrae seis fotogramas equidistantes,
  JPEG hasta 1024 px, duración y marcas de tiempo. Sólo análisis visual muestreado:
  **no transcribe audio ni garantiza detectar acciones entre muestras**. No genera
  multimedia de pago ni modifica los originales.
- La preferencia de enviar imágenes también controla los fotogramas. Desactivada,
  se informa al modelo de que NO ha visto el vídeo. El modo offline no lo decodifica.

## Aislamiento

Cada petición visual crea un directorio privado de adjuntos y un perfil AKI efímero.
Sólo se permiten comandos `view` exactos a esos archivos generados. Bash/Read
restantes requieren aprobación, sin aprobador conectado, y un hook `PreToolUse`
rechaza cualquier otra operación (incluidas concatenaciones de shell). Escritura,
edición y delegación están denegadas; memoria y skills heredadas, desactivadas.
Se comprueban también los eventos de herramientas y que todas las imágenes hayan
sido vistas antes de aceptar la respuesta JSON. No se registran prompts ni bytes.
Al terminar, se eliminan exclusivamente los temporales de esa invocación.

## Límites explícitos

- PDF hasta 4,5 MB (límite de entrada existente), 20 páginas y 200000 caracteres
  extraídos. Hasta 40 páginas/imágenes de contexto visual en el adaptador.
- Hasta dos vídeos por mensaje; seis fotogramas por vídeo. El límite previo del
  proxy de 20 imágenes por petición sigue incluyendo las imágenes del historial.
- No se trunca un PDF silenciosamente: se rechaza indicando cómo dividirlo.
- Texto UTF-8 TXT/MD/HTML/CSV admitido. Office binario debe exportarse a PDF.
- Contexto firmado de otro backend: conversación nueva, sin borrar el historial.
- AKI conserva sus requisitos de sesión y sus cuotas externas. No se afirma una
  tarifa cero verificada ni supervivencia del proceso Termux a reinicios del móvil.

## Verificación

Pruebas Python: 33 casos aprobados, incluidos guard exacto, inyección de shell,
limpieza de temporales, lectura visual obligatoria y rechazo de lectura parcial.
Núcleo Android: 126 pruebas aprobadas, con seis casos nuevos de adjuntos de vídeo
(entrega, visión desactivada, loader ausente, exceso, extracción incompleta y cancelación).
Llamadas reales por el adaptador con Sol identificaron correctamente una imagen
de formas/colores/texto y un PDF con texto y dibujo. Estas pruebas aisladas no
sustituyen la comprobación final desde la interfaz Android.

Compilación en EliteBook completada. Instalada por actualización (`adb install -r -t`)
la versión **1.9.2-agent-preview (36)**, certificado original verificado, sin
desinstalar ni borrar datos. SHA256 de la APK:
`014d243af4f5dd35507267669eaba9b28670bd40efba004586577a6c9f0a6c08`.
Tras instalar: backend proxy, modelo Sol, 32000 tokens y envío de imágenes activado
conservados. Las 33 pruebas Python también pasan en Windows.

Pendiente: turno visual completo y vídeo desde la interfaz de Nexus. El móvil
seguía siendo utilizado por el usuario y las pantallas cambiaban durante los
intentos; no se sobrescribieron sus borradores ni se presenta una llamada aislada
al puente como prueba del recorrido UI/orquestador/decodificador.
