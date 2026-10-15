# Renderizar desde el código

Este modo compila las funciones Compose reales y las ejecuta con Paparazzi/Layoutlib
en el host. No instala una APK, no arranca un emulador y no navega por el móvil.
Está integrado en el botón **Renderizar código** y en la CLI para agentes.

```powershell
python tools/apk-explorer/explorer.py --render "C:\ruta\proyecto" --json
python tools/apk-explorer/explorer.py --render "C:\ruta\proyecto" --render-list --json
```

La ejecución prioriza ASUSVIDAL y devuelve los resultados al PC. En modo automático,
si tiene menos de 2200 MiB disponibles, hay otra compilación o no está accesible, usa
el PC con prioridad baja y dos hilos, e indica el motivo. Reutiliza
las claves SSH existentes, exige la clave de host conocida y ejecuta Gradle con
prioridad baja, un worker, sin paralelismo y sin daemon persistente. El SDK/JDK y
la caché Gradle del ASUS son los configurados en las instrucciones de este proyecto.
`--render-host usuario@host` cambia el destino, pero ese host necesita las mismas
rutas de toolchain. `--render-local` usa el JDK/SDK del equipo que ejecuta el script.

La primera ejecución descarga el motor y sus dependencias. Las siguientes reutilizan
la caché Gradle; cada sesión conserva una copia aislada de las fuentes. No se tocan
los build scripts originales ni el workspace compartido del servidor. Los archivos
del proyecto sí se compilan: usa fuentes y perfiles de confianza.

## Perfil inicial de NEXUS

El perfil incluido invoca directamente:

- `ModelsScreen`: seis modelos sintéticos de vídeo, temas Aurora y Claro.
- `LibraryScreen`: repositorio vacío, temas Aurora y Claro.
- `GenerationProgressPanel`: trabajo sintético en cola, temas Aurora y Claro.

Usa `NexusTheme`, sus recursos y fuentes reales. Las dependencias de datos se sustituyen
por dobles de prueba; no se inicia `NexusApplication.onCreate` ni se usan cuentas,
credenciales o datos del teléfono. El fixture no replica el diseño en HTML ni dibuja
una imitación de las pantallas: llama a las funciones de producción.

Son seis estados registrados, no toda la app. No están preparados todavía Agente,
Lienzo ni todos los diálogos. Las imágenes corresponden a las fuentes seleccionadas,
que pueden diferir de la APK instalada. El manifiesto identifica el alcance de cada
imagen (`screen` o `component`) y sus datos de prueba.

## Otras apps y nuevos estados

Cada proyecto puede tener `apk-explorer.render.json` en su raíz, o usar
`--render-config perfil.json`. Copia como punto de partida el perfil y el fixture
de `render_profiles/`. El perfil declara módulo, variante, entradas de la copia
de fuentes, clase JUnit y estados esperados. El fixture Kotlin llama a los componentes
reales y proporciona los datos que necesitan. Una pantalla con servicios, ViewModel,
base de datos o red necesita ese estado preparado; no se infiere automáticamente.

Se soportan proyectos Android con `build.gradle.kts` y Compose. La copia añade el
plugin Paparazzi y un source set de pruebas separado; no ejecuta los tests ajenos
al fixture. La combinación incluida fija Paparazzi 1.3.5 para Kotlin 2.0.21/Compose
1.7 del NEXUS local. Otros toolchains pueden necesitar otro perfil y versión.

## Resultado para el agente

`stdout` contiene un objeto JSON y `stderr` el progreso. `render.json` incluye las
imágenes originales, láminas resumen, galería, estados pendientes y errores. La salida
de proceso es 0 si se generaron todos los estados registrados, 3 si faltan estados
y 1 si falló la compilación o el motor. Un fallo no se sustituye por una imagen ficticia.

`source-provenance.json` guarda SHA-256 de las entradas originales, del perfil y del
fixture. El commit Git es contexto: los hashes identifican también archivos locales
sin commit o sin seguimiento. Los cambios de build necesarios se aplican exclusivamente
en la copia aislada. Las sesiones y sus fuentes se guardan en `sessions/`, excluido de Git.

La GUI mantiene el renderizado en segundo plano. El botón **Detener** se desactiva
durante esa compilación; el comando tiene un límite de ejecución de 30 minutos.

Referencias del motor: [Paparazzi](https://cashapp.github.io/paparazzi/) y
[versión 1.3.5](https://github.com/cashapp/paparazzi/releases/tag/1.3.5).
# Descubrimiento y escenarios ampliados (2026-10-15)

En la ventana, **Descubrir pantallas** analiza el proyecto sin compilar ni usar ADB.
Genera `discovery.html` y `discovery.json` con funciones Compose, ubicación y línea,
previews, registros de navegación con rutas literales y dependencias que pueden
necesitar dobles de prueba. Excluye builds, sesiones, outputs y backups.

```powershell
python tools/apk-explorer/explorer.py --discover . --json
python tools/apk-explorer/explorer.py --render . --render-list --json
```

El análisis es heurístico, no un parser completo de Kotlin ni un inventario exhaustivo.
Las rutas dinámicas, extensiones, anotaciones propias y estados internos pueden faltar.
`registered` identifica un escenario por archivo y función; **no significa que se haya
renderizado**. `preview_candidate` necesita comprobar dependencias; `fixture_required`
necesita preparar un escenario. Ninguna preview detectada se ejecuta automáticamente.
El resultado de una ejecución real sigue estando en `render.json` y su registro de build.
Cada render nuevo incorpora también el informe de descubrimiento y vincula sus imágenes
a los símbolos de origen declarados en el perfil.

Perfiles seleccionados automáticamente según las fuentes:

- NEXUS local: seis escenarios anteriores y seis nuevos de Lienzo (vacío, carga,
  error; Aurora y Claro).
- NEXUS con `assistant/AssistantScreen.kt`: perfil `nexus-agent.json`, diez escenarios:
  Agent vacío y conversación en Aurora/Claro, pensando y error en Aurora; Lienzo vacío
  en Aurora/Claro, carga y error en Aurora. Incluye el módulo `agent-core`.

Los escenarios nuevos usan las funciones de producción con ViewModels y servicios
simulados. No instancian el runtime del agente, no llaman a un LLM ni generan contenido.
No se mezclan fuentes de distintos checkouts. Se ha localizado una fuente compatible en
`C:\Users\User\.codex\worktrees\nexus-android-parity\NEXUS` (1.9.3-agent);
esto no acredita equivalencia con la APK 1.9.5 instalada.

**Estado de validación:** el descubrimiento y la protección de memoria tienen pruebas
Python. Los nuevos fixtures Kotlin están preparados pero pendientes de compilación y
validación visual: se interrumpió el intento local al quedar aproximadamente 250 MiB
libres; ASUS tenía aproximadamente 1420 MiB. Las seis capturas anteriores conservan su
validación histórica. Ahora el render local rechaza iniciar Gradle con menos de 2200 MiB
libres y devuelve un error en el manifiesto, sin cerrar trabajos ajenos.
