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

Bedrock no esta configurado en Windows. Mientras no se introduzcan o se
trasladen sus credenciales, la app utiliza el planificador local original y
lo indica en pantalla. Las fuentes no sustituyen a la configuracion privada
del telefono. No se ha probado una respuesta real de Bedrock ni una generacion
de pago desde esta adaptacion.

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
