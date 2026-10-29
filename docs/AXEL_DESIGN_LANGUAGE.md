# Axel: lenguaje de apariencia de Nexus

## ADN compartido

La referencia es el zorro de `nexus_logo.png`, no una mascota nueva: orejas triangulares altas, mejillas angulares, mirada oblicua, malla convergente y hocico fino. `AxelMark` reduce esa malla a pocos trazos vectoriales para funcionar a 24–56 dp. El contorno y el punto central son constantes; el tema aporta tinta, luz y acento de la mirada. No hay stickers ni animación independiente del animal.

La firma explícita aparece en cabeceras compartidas y vacíos de conversación/Biblioteca; en fondos se sugiere mediante curvas de cola, convergencia y luz periférica. Los controles funcionales mantienen una escala común y superficies opacas. No se sobrepone un logo a cada mensaje o miniatura. El icono adaptable de arranque reutiliza orejas, mejillas y hocico; es una firma estática que no depende de preferencias aún no cargadas.

| Identidad | Composición e interpretación |
|---|---|
| Aurora Borealis | Noche azul, cortinas verticales de cian/turquesa y violeta; malla luminosa y agilidad de luz. |
| Obsidian Liquid | Base OLED negra, masas de grafito fuera de centro, borde reflectante plateado y curva líquida; sigilo tecnológico. |
| Champagne Silk | Marfil y cobre, títulos editoriales serif con cuerpo sans serif nativo de peso funcional; pliegues satinados y líneas paralelas cálidas. |
| Emerald Atmosphere | Verde profundo, horizonte de bruma y halos orgánicos periféricos; mirada de menta y luz biológica contenida. |
| Nocturne Nebula | Índigo/borgoña, nubes descentradas y profundidad magenta; firma nocturna cinematográfica. |

Los acabados aplican una escala común con radios medios de 12–20 dp, hojas de 20–28 dp y sombras de 2–8 dp según identidad: Obsidian es preciso y casi plano, Emerald más orgánico y Champagne suave. Estos valores viven en NexusSurfaceTreatment.

## Una fuente de verdad

`NexusThemeSpecs` define paleta Material completa, estilo ambiental, luz y cromado. `NexusVisualTokens` añade editor, medios, conversación, respuestas, código, hojas, diálogos, estados éxito/advertencia/proceso, placeholders y deshabilitados. Los alias históricos se resuelven contra los roles activos. Las ocho identidades almacenadas anteriores siguen siendo legibles; la galería presenta las cinco nuevas y no sobrescribe un tema anterior al abrirse.

`AppearanceSelection` agrupa tema, modo, intensidad y movimiento reducido. `previewAppearance` mantiene una selección transitoria en memoria; `applyAppearance` realiza una escritura explícita. Cancelar, Atrás, cerrar y salir retiran el preview. Las entradas desde ajustes y conversación utilizan el mismo picker. La tipografía se resuelve sin crear otro NexusTheme ni tocar ventanas dentro de las miniaturas.

## Movimiento y trabajo concentrado

Un reloj compartido, leído solo en el dibujo Canvas y limitado a una invalidación cada 80 ms, mueve las capas ambientales. Las composiciones de mensajes, texto y layout no leen la fase. Estático no ejecuta el reloj; Sutil es el valor por defecto; Inmersivo aumenta presencia sin animar controles individualmente. Frecuencias diferenciales y fase continua evitan un reinicio visible cada dos minutos. Pausa por lifecycle, ahorro de batería, escala Android de animaciones desactivada, regla existente de movimiento y preferencia de reducción. Las miniaturas no seleccionadas son estáticas; la seleccionada usa ese mismo reloj.

El prompt comienza compacto y se amplía por una acción explícita; sus controles no se estiran con la pantalla. El espacio útil puede ampliar previsualizaciones de medios reales, dejando el ambiente visible cuando no hay contenido que deba crecer. Teclado/fuente grande conservan scroll de respaldo y foco; no se trunca texto ni se inventa un límite de endpoint.

## Cobertura y límites

Ver APPEARANCE_INVENTORY.md para rutas y subpantallas. Logos de proveedores, viewport de reproducción, crop/máscara y tinta de documentos son excepciones de contenido que conservan sus colores, no temas independientes. Las mediciones AA, compilación y revisión física se registran en APPEARANCE_REPORT.md al completar la verificación; esta especificación no las acredita por sí sola. No equivale a medición de consumo, temperatura o 120 Hz en hardware.
