# NEXUS Studio · auditoría visual y funcional

La aplicación de escritorio se construye con **WinForms y WebView2** sobre .NET Framework. La edición que se puede abrir actualmente en este EliteBook usa el mismo `desktop/ui` servido por el host local de Python y una ventana de Edge. El ejecutable nativo está sin firmar y Smart App Control impide probarlo como aplicación instalada; no se ha cambiado esa protección.

## Punto de partida

| Prioridad | Hallazgo inicial | Efecto | Estado actual |
| --- | --- | --- | --- |
| Alta | El catálogo empezaba con 53 familias y 227 rutas de imagen sin una guía de decisión; muchas fichas decían «ideal», «líder» o repetían la API. | Había que abrir variantes sin saber para qué servían. | Guía de 40 rutas documentadas, agrupadas por modelo, más **catálogo completo visible debajo**. Las fichas individuales dan uso y límite derivados de la ruta o capacidad. |
| Alta | `Youchuan` figuraba como `Midjourney` sin atribución fiable. | Identidad engañosa. | Se muestra como Youchuan en escritorio. |
| Alta | La ventana nativa usaba barra de título clara y podía mostrar fondo blanco antes de cargar WebView2. | Arranque ajeno al tema. | Se compila con título nativo oscuro mediante DWM y fondo inicial oscuro. **Sin validación visual del EXE** por Smart App Control. |
| Media | Menús `<select>` y scrollbar del navegador; botones de puntos hechos con texto. | Sensación de página web. | Menús propios con teclado, Escape y selección visible; scrollbar oscuro; icono coherente. |
| Media | Avatares de letra e imagen sin fallback; se notificó una imagen rota en MiniMax. | Tarjetas incompletas. | Símbolo Nexus cuando falta logo o falla la carga. El archivo MiniMax existe y es válido; no se atribuye el fallo previo al archivo sin reproducirlo. |
| Media | «Ver 1 variantes», separador final «·», fecha cruda `2026-07`, «Windows · EliteBook». | Producto sin pulir. | Pluralización, separadores, mes legible y texto de máquina corregidos. |
| Media | El lienzo vacío mostraba un icono enorme sin siguiente paso claro. | Primer uso frío y poco útil. | Inicio directo para imagen, vídeo o catálogo completo. |
| Media | Precio base en turquesa incluso cuando faltaba la unidad. | Parecía una cotización verificada. | Se reserva el acento para navegación/CTA; se distingue tarifa con unidad de base sin unidad. El envío sigue bloqueado sin cotización válida. |

La navegación, historia y biblioteca se inspeccionaron en la edición local real. Ajustes se revisó sin abrir credenciales. El formulario de Runway Gen4 Image se abrió con contrato local: campos visibles, control de formato propio, CTA «Generar» desactivado sin prompt y precio. No se abrió ningún endpoint de generación.

## Sistema aplicado

Tokens de superficie `surface-0…4`, espaciado base 4 px, radios de 8/12/16 px, sombra para desplegables y transiciones de 120/180 ms. El turquesa se concentra en navegación activa, acción principal y enlaces de guía; el coste no usa el acento como jerarquía principal. Los selectores se presentan como listboxes propios con foco y Escape. Los controles conservan el comportamiento de los `<select>` subyacentes para no romper formularios ni preferencias.

## Capturas

Las capturas **antes** se renderizaron sin red ni datos personales a partir del ZIP de NEXUS Studio anterior a esta intervención. Las **después** proceden del host local activo con los datos reales; no deben publicarse sin revisar su contenido. No son una prueba del EXE nativo bloqueado.

| Pantalla | Antes | Después |
| --- | --- | --- |
| Modelos · imagen | [PNG](../outputs/desktop-audit-20261002/before/modelos-imagen.png) | [PNG](../outputs/desktop-audit-20261002/after/modelos-imagen.png) |
| Modelos · vídeo | [PNG](../outputs/desktop-audit-20261002/before/modelos-video.png) | [PNG](../outputs/desktop-audit-20261002/after/modelos-video.png) |
| Lienzo | [PNG](../outputs/desktop-audit-20261002/before/lienzo.png) | [PNG](../outputs/desktop-audit-20261002/after/lienzo.png) |
| Historial | [PNG](../outputs/desktop-audit-20261002/before/historial.png) | [PNG](../outputs/desktop-audit-20261002/after/historial.png) |
| Biblioteca | [PNG](../outputs/desktop-audit-20261002/before/biblioteca.png) | [PNG](../outputs/desktop-audit-20261002/after/biblioteca.png) |
| Ajustes | [PNG](../outputs/desktop-audit-20261002/before/ajustes.png) | [PNG](../outputs/desktop-audit-20261002/after/ajustes.png) |

## Segunda revisión y límites

- **PASS:** búsqueda alcanza modelos fuera de la guía; pestañas de imagen y vídeo mantienen el catálogo completo; desplegable de proveedor filtra; no hay errores JavaScript en la sesión de revisión; 1024 px sin desbordamiento horizontal a escala simulada 100 %, 125 % y 150 %.
- **PASS:** 40 rutas de la guía existen en el catálogo de escritorio, sin IDs repetidos; el código Android conserva la misma lista; las ocultaciones voluntarias previas se respetan. No se ha ocultado automáticamente ningún modelo.
- **PARCIAL:** las ventajas de las 40 rutas describen capacidades o ficha del proveedor, **no** una evaluación comparativa de calidad. El resto del catálogo tiene orientación funcional por entradas/modalidad, pero una clasificación de calidad real exige un banco de pruebas con resultados; no se han hecho generaciones de pago.
- **NO PROBADO:** arranque, barra DWM, menú contextual y comportamiento multimonitor del EXE compilado: Smart App Control lo bloquea al no estar firmado. La edición local que sí abre se ha probado en Edge.
- **NO PROBADO:** 60 fps medidos con 400 tarjetas simultáneas. La vista inicial agrupa familias y pagina resultados de 60 en 60; falta una medición instrumental en el host nativo.
- **PENDIENTE:** normalizar todos los nombres de modelos crudos del proveedor y dar a cada ruta una comparación editorial específica, sin inventar calidad. La corrección de Youchuan evita la atribución falsa detectada.

La alternativa nativa para la barra y WebView2 ya está implementada. Para distribuir el EXE en este equipo falta una firma de código aceptada por Smart App Control o una publicación firmada mediante Microsoft Store; ambas requieren una decisión externa documentada en [SIGNING.md](../desktop/SIGNING.md). La edición privada local no exige cambiar la política de Windows.
