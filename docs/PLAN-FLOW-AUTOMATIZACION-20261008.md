# Plan de ejecución: Flow y automatización

Objetivo: resolver los hallazgos de la auditoría sin generar contenido ni consumir créditos. Ámbito: NEXUS Desktop y su puente local instalado; Android y pruebas de generación real quedan fuera de esta intervención.

1. Implementar una cola SQLite compartida por el host, exclusión por perfil durante el trabajo completo y controles de pausa/cancelación para solicitudes no enviadas.
2. Recuperar reservas interrumpidas al arrancar mediante consultas del mismo identificador. Nunca convertir una recuperación en un nuevo envío; mantener detenidos los bloques futuros de una solicitud incierta.
3. Unificar el diagnóstico del MCP y NEXUS en la comprobación del editor de Flow, con origen, fecha y caché explícitos. Conservar saldo separado.
4. Propagar modelo solicitado/confirmación hasta historial y visor; corregir Quality a 8 s y limitar resultados/bloques en servidor.
5. Añadir presupuesto configurable para solicitudes pendientes y controles visibles, junto con tiempos por fases del trabajo sin prompts, claves ni URLs.
6. Ejecutar regresión offline, pruebas reales del host con datos aislados y QA de interfaz. Corregir cobertura antigua.
7. Aplicar el runtime local sin compilar APK/EXE, comprobando que no haya trabajo activo. Preservar fuentes ajenas, credenciales, datos e identificadores. Publicar exclusivamente los cambios de este encargo y verificar GitHub.

La validación completa de generación por modelo requerirá una solicitud que autorice el consumo; no se declara probada mediante simulaciones.

## Cierre

Pasos 1–6 completados y actualización del host/runtime aplicada. El registro de implementación, pruebas y límites está en [la entrega](FLOW-AUTOMATIZACION-20261008.md). La publicación del paso 7 se verifica mediante comparación del SHA remoto. Las conexiones MCP ya abiertas necesitan reconectar para cargar el diagnóstico nuevo; no se reinicia Codex durante esta tarea.
