# Corrección del bloqueo diario del agente — 15 de octubre de 2026

## Causa reproducida

El Samsung S25 ejecutaba `1.9.5-agent-preview`, vc39, con backend Bedrock directo
y Sonnet 4.6. La configuración conservada permitía 10 USD por turno y 20 USD por
día, pero `LlmLimits` añadía cuotas fijas de 150.000 tokens por turno y 500.000
por día, independientes de esos importes.

El registro del teléfono contenía 454.054 tokens y 1,6793352 USD estimados o
reservados el 15 de octubre. Reservar una petición siguiente de 72.000 tokens
superaba el techo de 500.000 e impedía incluso enviar «hola». El presupuesto
monetario no era la causa del bloqueo. El puente AKI respondió a su health, pero
no era el backend seleccionado; no se cambió la selección para ocultar el fallo.

## Corrección

- Se eliminan las cuotas fijas locales de tokens por turno y día. Una cuota de
  tokens sólo se aplica si se configura explícitamente al construir `LlmLimits`.
- Se conserva el registro de consumo, las reservas y los importes configurados.
- La pantalla de consumo explica el comportamiento nuevo.
- Se sincronizan la definición y las pruebas entre Android y el núcleo Windows.
- Android pasa a `1.9.6-agent-preview`, vc40, desde las fuentes actuales de main.

La regresión usa los valores del S25: 454.054 tokens existentes y una nueva reserva
de 72.000. También comprueba un turno de 160.000 y que una cuota configurada
explícitamente sigue aplicándose. No se añade un modelo, cuenta ni límite nuevos.

## Verificación

- Compilación completa en AXELPC: BUILD SUCCESSFUL, 21 min 6 s, un trabajador.
- 131 pruebas de agent-core y 279 de Android: aprobadas, cero fallos u omisiones.
- APK 1.9.6-agent-preview, vc40, firmada con el certificado existente e instalada
  mediante actualización sobre la 1.9.5. Se conserva una copia verificada de la APK anterior.
- Se respaldaron las preferencias, bases de datos y el directorio assistant antes
  de actualizar. Los bytes de ajustes, almacenamiento de claves, índice de proyectos,
  chat y registro de consumo permanecieron idénticos tras la instalación.
- Prueba real desde la interfaz, en el proyecto y conversación existentes: el modelo
  respondió NEXUS_RESPONDE_1. El registro anterior se conserva; no se reinició su contador.
  El registro pasó de 454.054 a 478.801 tokens tras esa respuesta. La reserva previa
  de la petición habría excedido 500.000 con el límite antiguo.
- Sólo se probó una respuesta del chat: no se ejecutaron generaciones multimedia.
  La segunda comprobación de UI se dejó sin ejecutar al cambiar el teléfono a otra app.
- No se actualizó Windows: su núcleo fuente comparte la corrección, su runtime
  instalado no se ha reemplazado en esta tarea.
