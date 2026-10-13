# Estado actual · 13 de octubre de 2026

Esta edición es **Nexus Studio 1.9.3-agent-desktop**, adaptada de la APK 1.9.2 del S25 y del escritorio en uso. Consulta [la entrega y sus comprobaciones](../docs/DESKTOP-AGENT-PARITY-20261011.md). Sol, edición local, Whisper, proyectos, adjuntos y seis temas están disponibles. Los apartados siguientes conservan la documentación de entregas anteriores y sus cifras históricas.

# Nexus Agente para Windows - 6 de octubre de 2026

Adaptacion de las fuentes de Drive `Nexus_codigo_fuente_2026-10-03.zip`,
Android `1.7.0-agent`, versionCode 18. No se comprobo la APK instalada en el
S25: el telefono estaba apagado y sin bateria.

## Edicion instalada

### Mejoras especificas para PC

Actualizacion de interfaz instalada:

- Tres secciones: Crear, Biblioteca y Asistente; Ajustes separado.
- Selector de modelos integrado en Crear, prompt conservado y ultimos valores
  recuperados al volver a un modelo.
- Biblioteca visual con pestanas Resultados/Actividad y panel de detalles.
- Reutilizacion de ajustes o de una imagen como referencia para otro modelo.
- Pegado y arrastre locales de archivos hasta 12 MB; el selector de archivos
  mantiene su limite anterior. No se suben al proveedor durante la importacion.
- Comprobaciones aisladas en desktop/tools/test-studio.py y verificacion de
  datos instalados en desktop/tools/verify-studio-installed.py.

Estos cambios se sirven directamente desde desktop/ui y desktop/local_host.py.
El ZIP de entrega anterior no se ha vuelto a empaquetar en esta actualizacion.

- Inicio en biblioteca; el motor Java solo se solicita al entrar al asistente.
- Biblioteca e historial con busqueda por prompt/modelo, filtros por proveedor,
  formato y estado, y bloques de 36 resultados.
- Comparacion visual de hasta cuatro resultados, incluso de lotes diferentes.
- Lotes secuenciales con pausa y detencion de solicitudes pendientes. No cancela
  trabajos ya recibidos por el proveedor ni reenvia solicitudes automaticamente.
- Consulta de estados sin reescribir datos ni reconstruir reproductores cuando
  nada cambia; consultas equitativas y espera creciente ante errores.

Verificacion aislada: 1.000 resultados sinteticos, interfaz a 1360 y 760 px,
comparacion, pausa/detencion y estabilidad de tarjetas. Cero generaciones pagadas.
No equivale a medir la velocidad de los proveedores ni a una prueba del S25.

El acceso directo **Nexus Studio** abre la edicion local actualizada mediante
Python y Edge. Conserva el almacen de datos de Windows en
`%LOCALAPPDATA%/NexusDesktop`. No requiere que el telefono este encendido.
Necesita el Java 17 ya instalado en este portatil para ejecutar el motor.

Se mantienen los 85 trabajos, las 84 referencias de resultados y las tres
credenciales de proveedores del escritorio. No se ha hecho sincronizacion
continua con el movil ni se ha trasladado informacion posterior desde el S25.

## Adaptacion

- Modulo Kotlin `agent-core` original, sin cambios en sus fuentes, y 34 skills originales.
- Asistente, Copiloto y Agente; conexion Bedrock directa o mediante proxy.
- Proyectos y memoria editable; consulta del historial y de sus resultados desde las herramientas del agente.
- Adjuntos visuales, planes editables, propuestas de organizacion y apertura en el lienzo.
- Confirmacion de cada generacion mediante el SpendGuard original, huella del plan y ticket de un solo uso.
- Lotes de imagen de 1 a 20 solicitudes por modelo, hasta seis modelos; costes y cantidades antes del envio.
- Navegacion independiente por todas las salidas de un trabajo y descarga de la salida seleccionada.
- Credenciales del asistente protegidas con DPAPI CurrentUser, separadas de las credenciales de generacion.

Bedrock esta configurado mediante credenciales existentes protegidas con DPAPI.
Se verifico una respuesta real de Sonnet; no se realizaron generaciones de pago.
La interfaz bloquea el envio conversacional si falta LLM y muestra configuracion.

## Asesor experto compacto

### Optimizacion de consultas y recuperacion de errores

- get_model_brief acepta focus limits/schema/prompt; limits omite guias y
  metadatos generales. La seleccion predeterminada sigue siendo prompt.
- Los esquemas publicos de Atlas se conservan cinco minutos en memoria, hasta
  64 entradas, nunca cotizaciones ni errores. Actualizar skills limpia esta cache.
  Su timeout de red es 15 segundos; los envios de generacion no cambian.
- Solo se compactan respuestas antiguas de get_model_brief mayores de 6000
  caracteres. Conserva nombres e identificadores, mensajes del usuario y todas
  las respuestas del turno actual, sin romper los pares de herramientas.
  No sustituye los mensajes por un resumen generado ni implica memoria infinita.
- Un envio fallido conserva texto y adjuntos sin duplicar mensajes en pantalla.
  No se reenvia automaticamente. Las imagenes de revision tienen una espera
  maxima de decodificacion de 15 segundos.

### Limites de texto

Se conservan minLength/maxLength y descripciones del esquema Input por campo,
incluido prompt y negative_prompt. El agente recibe textLimits en ModelSchema.
El formulario cuenta caracteres Unicode del texto que enviara y bloquea el
envio fuera de los limites confirmados, sin truncarlo. Ausencia de maximo no
significa ilimitado. No se convierten tokens en caracteres.

Los contratos Runway text_to_video de gen4.5, veo3.1, veo3.1_fast y
happyhorse_1_0 incorporan los limites UTF-16 publicados en el SDK oficial,
revisados el 2026-10-06. No se extrapolan a Atlas ni a image_to_video.
Otros contratos sin esquema o ficha comprobada siguen marcados sin verificar.
Fuente: https://github.com/runwayml/sdk-python/blob/main/src/runwayml/types/text_to_video_create_params.py

### Revision visual y preferencias aprobadas

- `Revisar imagen` analiza una imagen o compara dos (referencia primero,
  resultado despues). Comprueba que los pixeles se pueden cargar antes de pedir
  una valoracion al LLM. Respeta el ajuste de envio de imagenes.
- La revision pide conservar aciertos, identificar hasta dos fallos observables
  y cambiar una sola variable. No fuerza defectos cuando no se ven ni genera medios.
- `Revisar con Nexus` en los detalles de biblioteca lleva el resultado y su
  prompt al borrador. Las imagenes remotas requieren acceso CORS; si el proveedor
  lo bloquea, se debe adjuntar un archivo local. No se afirma haberlas visto.
- `Guardar preferencia` permite editar y aprobar una regla del proyecto.
  Conserva el resto de su memoria y evita duplicados exactos sin distinguir
  mayusculas. Se puede editar o eliminar desde Memoria del proyecto.
- La memoria se usa tambien en conversaciones nuevas. No hay entrenamiento,
  aprendizaje automatico ni memoria global entre proyectos.

Prueba `desktop/tools/test-review.py`: host y proyecto aislados, memoria
persistente, deduplicacion, controles de consentimiento, pantallas de 1360/760 px
y consulta visual real con formas sinteticas; sin generaciones de pago.

El adaptador incorpora `ExpertKnowledge.kt` y 11 guias en
`agent/assets/expert-guides.json`, con fuentes primarias, alcance por variante y
fecha de revision. No son benchmarks ni prueban todas las variantes del catalogo.
Las fichas con mas de 90 dias se marcan pendientes de revalidacion; no existe
actualizacion automatica desde Internet. El archivo se recarga cuando cambia.

`get_model_brief` agrupa hasta tres candidatos, esquemas y guias por consulta.
Los precios desconocidos quedan pendientes, nunca gratis. Las recomendaciones
creativas generales se distinguen de capacidades verificadas por variante.
Se conserva SpendGuard y la confirmacion de cada generacion.

El catalogo completo de skills no se inyecta en cada peticion. Los saludos no
incluyen herramientas; las consultas normales tienen cinco rondas como techo y
1800 tokens de salida. Estos limites no garantizan una latencia fija del proveedor.
La memoria conserva preferencias aprobadas, no supone aprendizaje automatico.

Verificacion de esta revision: 85 pruebas JVM aprobadas en ASUSVIDAL (74 del
core y 11 nuevas). En tres consultas aisladas con Bedrock real: saludo 3,63 s
(1297 tokens de entrada, cero herramientas), prompt FLUX 2 Pro 7,69 s y nombre
inventado rechazado 4,56 s (una ronda de herramientas en cada consulta tecnica).
Son muestras, no garantia de latencia ni comparativa de calidad de imagen.

## Ejecutable nativo

`dist/Nexus.exe` se ha actualizado y compilado. En la comprobacion final,
Windows rechazo su arranque con "Una directiva de Control de aplicaciones
bloqueo este archivo". Sigue sin firma de editor confiable; no se ha alterado
Smart App Control. La edicion local instalada funciona sin ejecutar ese EXE.

## Verificacion

- ASUSVIDAL: compilacion del adaptador JVM y 74 pruebas originales, cero fallos y errores.
- ASUSVIDAL: 20 pruebas del motor de formularios y comprobacion de 40 rutas del catalogo.
- Windows: seis pruebas del host, incluido DPAPI y aislamiento de callbacks entre ventanas.
- Edge con motor JVM real y datos aislados: proyecto, memoria, plan, edicion, revision de coste,
  lotes, salidas multiples y cambio de modo; cero generaciones.
- Interfaz a 1360 y 760 px; sin errores JS ni desbordamiento horizontal. Arranque adicional con CSP real.
- Edicion instalada: 838 modelos, 85 trabajos, 84 resultados y 34 skills, sin errores JS.

Las pruebas del formulario usan esquemas y cotizaciones simuladas. No
demuestran compatibilidad individual de todos los modelos ni equivalencia
total con Android. La interfaz se adapta a Windows; los selectores y la
integracion de archivos propios de Android no se replican literalmente.

## Fuentes y compilacion

`agent/core` y `agent/agent-skills` proceden del ZIP original. El adaptador es
`agent/src/main/kotlin/DesktopAgent.kt`; `agent_host.py` comunica el motor con
los repositorios y controles existentes de Windows mediante stdin/stdout.

Compilar el motor en ASUS, con Java 17:

```
gradle -p desktop/agent runtime :core:test --no-daemon --max-workers=1 --no-parallel
```

Recuperar `desktop/agent/build/runtime/*.jar` a `desktop/agent/runtime/` y
verificar sus hashes. Compilar el host C# en Windows con `desktop/build.ps1`.
El ASUS no dispone de C#/.NET: solo esa compilacion pequena se hizo aqui, con
prioridad BelowNormal. Los informes y capturas estan en
`outputs/desktop-parity-20261006` del proyecto.
