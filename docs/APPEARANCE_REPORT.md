# Apariencia Axel de Nexus — entrega vc171

29/10/2026 · AXELPC · rama `codex/nexus-axel-appearance-20261029`.
Fuente compilada [4961c5a7d1383ae924073d2fcdce7c9260b66d95](https://github.com/egodivergente/nexus/commit/4961c5a7d1383ae924073d2fcdce7c9260b66d95), publicada y verificada en remoto.

## Entregado

Cinco identidades funcionales: **Aurora Borealis, Obsidian Liquid, Champagne Silk,
Emerald Atmosphere y Nocturne Nebula**. Difieren en composición ambiental, luz,
paleta, cromado y acabados; Champagne añade títulos editoriales. Los roles Material
completos y NexusVisualTokens centralizan texto, superficies, editor, medios,
estados, bordes y cromado. NexusSurfaceTreatment define formas y sombras de los
componentes que lo consumen; no se afirma sombra variable de todas las hojas.
Siete paletas legacy siguen cargando, además de Aurora renovada: doce valores
compatibles del enum, cinco opciones premium en el selector.

Motor Canvas de cinco geometrías con fase continua y frecuencias diferenciales.
Un reloj compartido lee su fase durante dibujo, con intervalo mínimo 80 ms;
previews no seleccionados estáticos. Estático, Sutil e Inmersivo, Sutil por defecto;
respeta lifecycle, escala del sistema, reducción de movimiento, reglas existentes
y ahorro. No hay vídeo de fondo, blur animado ni dependencia nueva. Fotos previas
conservan su velo y configuración.

Selector común para Ajustes y conversación: mini interfaz real por tema, tipografía
y luz; selección transitoria de tema/modo/intensidad/reducción. Solo Aplicar guarda;
Cancelar/Atrás/cierre retiran preview. Prompt compacto2–3 líneas con expansión
explícita, bordes/superficie de tema y toolbar estable, sin truncar contenido.
Texto operativo con SansSerif Android500; títulos de marca/serif según identidad.

AxelMark deriva de la malla del zorro existente: orejas, mejillas y hocico angular,
punto convergente y mirada. Firma en cabeceras compartidas, previews, vacíos y
vector de icono/arranque; presencia abstracta en luz/curvas, sin mascota animada.
Ver [lenguaje Axel](AXEL_DESIGN_LANGUAGE.md) e [inventario](APPEARANCE_INVENTORY.md).

## Artefacto y pruebas

| Evidencia | Resultado |
|---|---|
| APK | **1.9.135-agent-preview / vc171** |
| Archivo privado | `C:/Users/User/Documents/Codex/nexus-premium-20261029/Nexus-Agent-1.9.135-v171.apk` |
| SHA-256 | `b7da59716343f2413f3c38c04a31ebb02670534e669edc046261a042adb654b9` |
| Certificado conservado | `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127` |
| Build | PASS en AXELPC; dos workers, sin paralelo y prioridad baja |
| Pruebas app | **303 ejecutadas PASS**, cero fallos/errores/omitidas |
| Núcleo | **4 UP-TO-DATE**, reutilizadas válidamente, sin cambios de núcleo |
| Contraste | Pruebas de texto≥4,5:1 y bordes funcionales≥3:1 en las doce paletas; estados/roles premium y superficies estables |
| Política ambiental | Siete pruebas de modos, ahorro, intensidad, fase continua, preview y velo |
| Galería | Tres pruebas de estado guardable, legacy y corrupción/valores inválidos |
| APK | Firma PASS, zipalign16 KiB PASS y cinco ARM64 idénticas a vc155 |
| Actualización | `adb install -r -t` PASS en Pixel 8 Pro y SM-S938B; hashes/versiones instalados coinciden |
| Datos del compositor al actualizar | Prompt, negativo, parámetros, IDs, URI y conteos iguales antes/después en ambos |

No se ha ejecutado una generación/pago ni se han modificado proveedores, payloads,
modelos, firmas o secretos. La comprobación de borrador no acredita identidad de
cada byte de las bases ni de todos los datos de la aplicación.

## Comprobado en dispositivo

**Pixel 8 Pro, vc171:** cinco previews reales y Cancelar preservando exactamente el
XML de preferencias Arena; Aplicar Champagne/Sutil al 55 % y reducción desactivada, guardar y
reabrir PASS. Champagne se deja como sustituto cálido conservador del Arena previo.
No se ha cambiado la escala propia de la app. En paisaje se alcanzó Avanzado mediante el scroll de respaldo y se abrió/cerró su hoja, con retorno a retrato. La primera tentativa se detuvo antes de enviar el gesto por bloqueo automático del Pixel; un despertar/dismiss normal recuperó Nexus, sin PIN ni bypass.

Prompt con72 caracteres y2 referencias, teclado abierto/cerrado, fuente Android150 %,
regreso al 100 %, cuatro pestañas y paisaje/retrato comprobados. Huella final del
borrador completa igual a antes de actualizar. Rotación automática 1 / user 0 y fuente
Android 1,0 restauradas. Se preservó el ajuste de accesibilidad de negrita del Pixel
(`font_weight_adjustment=300`), que explica su mayor peso visual.

Muestra del mismo recorte de preview Champagne, separada3 segundos: modo Sutil
cambió **4469 píxeles**; modo Estático cambió **0**. Demuestra movimiento
visible/estático en esa muestra; no mide FPS, batería, temperatura ni costeGPU.
Revisión independiente de capturas de los cinco temas, prompt/fuente150/IME y
Cancelar sin otro defecto visual de impacto dentro de ese alcance.

**S25 Ultra, vc171:** instalación/hash/versión y borrador preservado PASS. La QA
visual queda **BLOQUEADA**: Keyguard showing/inputRestricted true, focoBouncer tras
un único despertar/dismiss normal. Nexus permanece detrás; no se acredita
foreground, previews, Apply/Cancel ni navegación171. Sin PIN/bypass ni reintentos.
Su preferenciaAURORA y fuente propia≈0,95 se conservan.

| Logs disponibles de vc171 | Pixel | S25 |
|---|---|---|
| Líneas revisadas / Fatal o ANR de Nexus | 12724 / 0 | 72776 / 0 |
| Epoch disponible | 1793298068.172 → 1793299114.143 | 1793298113.178 → 1793299117.488 |

Cero incidentes detectados en estas ventanas no certifica otros intervalos ni
aceptación visual del S25. Capturas, XML, bases/huellas, APK y logcat permanecen privados.

## Defectos encontrados, corregidos y límites

- El primer wrapper de PowerShell trató un aviso del SDK como error. Se corrigió la
  captura del proceso; build explícito SUCCESS y pruebas ejecutadas después.
- La revisión170 encontró cuerpo visualmente demasiado fino;171 usa tipografía
  nativa con peso operativo, respetando accesibilidad.170 no se instaló en S25.
- Se retiró el reinicio periódico de fase y se diferenciaron frecuencias para
  evitar saltos/repetición visual corta. Se permite animar el preview compartido
  aunque exista foto personal, sin eliminar esa foto ni cambiar su velo.
- Cambios de formas/bordes mantienen el mismo recorte y el foco del editor.
- El inventario detectó una ruta Flow no montada y una ramaConnections latente,
  previas al rediseño. Se registran sin alterar navegación/proveedores fuera del
  alcance. Logos de proveedores, reproducción/crop/máscara y tinta son contenido
  con colores propios que se preservan.

**Pendiente:** aceptación visual S25 cuando esté desbloqueado; uso auditivo con TalkBack,
S Pen y sensación háptica, temperaturas/batería/GPU y120 Hz sostenidos; un plegable
real y todas las combinaciones de contenido/hojas. Paisaje Pixel no prueba un
plegable. El inventario cubre rutas y componentes por código; no se presenta como
prueba física exhaustiva de cada subpantalla en cada tema. No se certifica nota10.
