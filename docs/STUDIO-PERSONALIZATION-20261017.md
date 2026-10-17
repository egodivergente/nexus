# Primera entrega: presentación personalizable del agente

Base: feat/byteplus-custom-providers-20261017 @ 759bf7c (1.9.34/vc68).
Rama: aki/personalizacion-estudio-20261017. Versión prevista: 1.9.35/vc69.

## Decisiones del usuario

Conservar logos oficiales. Las tarjetas deben poder reducirse por elección del usuario.
Mantener que el agente traiga imágenes y archivos de Biblioteca al chat. Mostrar actividad
visual durante generaciones sin inventar una previsualización o un porcentaje.

## Alcance implementado

- Ajustes del asistente: Tu espacio de creación, tamaños Compactas/Equilibradas/Amplias,
  animación de actividad activable y restablecer solo presentación. Aplicación inmediata.
- Preferencias nuevas independientes de claves, configuración LLM, presupuesto e historial.
  Equilibradas por defecto, logos conservados en los tres tamaños.
- Imágenes que el agente presenta: toque o botón Ampliar imagen, visor dentro de Nexus,
  pellizco/doble toque, Ajustar y cierre de vuelta al chat. No roba foco al mostrar un archivo.
- Progreso: animación de actividad, nunca una imagen que se haga pasar por resultado. Porcentaje
  solo si es finito, está entre 0 y 1 y no viene de hitos simulados de Flow.
- No se cambia el protocolo showMedia/view_library_asset, ni generación, pagos o enrutado del cerebro.
- Esta iteración personaliza las tarjetas de encargo actuales del chat; no las tarjetas históricas
  sin encargo ni las de Biblioteca/Historial. El rediseño del orquestador queda para otra entrega.

## Verificación

Se añaden pruebas JVM de tamaños/defaults/zoom/progreso y pruebas Compose offline de
persistencia, observación, visor, logos y dimensiones de las tarjetas. CI compila AgentPreview
además de Debug, ejecuta suites JVM y pruebas nuevas en un emulador API 35 sin claves reales.
Estado inicial: pendiente de CI. No instalado ni probado en el S25. Ninguna generación de pago.

## Siguiente entrega recomendada

Separar estado de proyecto de la sesión de proveedor; contrato de traspaso entre cerebros,
capacidades comprobadas, presupuesto y permisos separados de perfiles creativos. Empezar con
cambio manual verificable, sin fallback silencioso ni volver a enviar acciones ya ejecutadas.
