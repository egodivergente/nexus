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
- El control flotante lateral de la captura no se atribuye a Nexus sin evidencia;
  no se eliminan controles del sistema ni de otras aplicaciones.

## Verificación

Pendiente en esta revisión: compilación AXELPC, contraste de los paneles en todas
las apariencias, regresión del parser y revisión física de la versión 172.
Los resultados definitivos se añadirán al cerrar la entrega.
