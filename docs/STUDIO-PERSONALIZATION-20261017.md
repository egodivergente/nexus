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

## Regresión cerrada al revisar el límite con proveedores

Los adaptadores Atlas y configurable recortaban datos fuera de rango a 0/100 antes de
que la vista pudiera rechazarlos. Se rechazan no finitos, negativos y valores superiores
a 100 en el origen. Se conserva soporte para formatos 0..1 y 0..100, sin fingir que se
conoce la unidad de un campo genérico progress; percentage/percent de Atlas sí se leen
como porcentajes explícitos. Si una respuesta posterior no comunica avance válido,
no se arrastra como actual un porcentaje anterior. Mock se etiqueta como simulación
y no se muestra su 50% fijo como real. La etiqueta dice informado, no medido.

Regresiones añadidas: ProviderProgressTest (5), AtlasReportedProgressTest (4),
GenerationActivityTest (ahora 13). Pendientes de ejecución de CI del nuevo commit.

## Capturas verificables, no solo nodos semánticos (18-10)

CI 39086968697 pasó compilación, JVM y seis tests UI. La revisión manual rechazó
los PNG: el fixture heredaba tamaño mínimo de pantalla completa del tema, no
aplicaba insets, y UiAutomation podía capturar un frame anterior a la carga Coil.
Se corrige SOLO el fixture: contenedor como el contenido real, captura sincronizada
con PixelCopy/Compose y comprobación del color real de las imágenes y el logo.
No se cambia código de producción ni se acepta el CI verde como prueba visual.
La revisión/firma sigue pendiente de estas nuevas capturas y de su CI.

El filtro Mock sin porcentaje se aplica a las tarjetas actuales del agente.
Los paneles heredados de Historial/visor, Biblioteca y Comparación quedan fuera
del rediseño, así como Nexus Desktop.

## Visor en Android 15: pie recortado

CI 39090853625: cinco pruebas UI pasan; el nuevo assertIsDisplayed de Ajustar
falla. Las capturas de tarjetas/actividad ya están completas. Se mantiene la
aserción: la acción existía semánticamente pero su pie no era visible.
Compose 1.7 mide Dialog con screenHeightDp; con target35 incluye las barras.
El visor ahora usa decorFitsSystemWindows=false y mantiene safeDrawingPadding
como único responsable de los insets. Se comprueba texto del pie, botón completo
dentro del diálogo y apertura/Ajustar/cierre mediante toques, no solo acciones
semánticas. Arreglo pendiente de nuevo CI y captura, no instalado en S25.

### Resultado medido, hipótesis anterior insuficiente

Run39095259205 registra ventana visible1079x2146 en y128, pero DialogLayout
y Compose miden1079x2399. Ajustar está en y2284..2389 dentro de esa ventana,
fuera del frame visible. Los insets de la ventana flotante son cero; por eso
decorFits=false por sí solo NO resolvió.
Se conserva el camino de medida de plataforma (usePlatformDefaultWidth=true)
y se configura su propia ventana MATCH_PARENT. Así se evita la rama de Compose1.7
que ignora el MeasureSpec real y remide con screenHeightDp. Assertions intactas.
El diagnóstico también capturó un ANR del launcher Quickstep del emulador;
CI cierra solo com.android.launcher3 tras calentamiento, no Nexus, y conserva
logcat completo. No se deshabilitan errores ni aumentan timeouts. Pendiente CI.
Corrección de texto anterior: performClick ya inyectaba toques en Compose1.7;
la nueva cobertura la aportan visibilidad, bounds y píxeles, no cambiar su nombre.
