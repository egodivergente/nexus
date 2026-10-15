# APK Explorer

Explorador local para APK Android privadas: recorre elementos accesibles, captura
pantallas distintas y genera una galería HTML, árboles XML y un mapa de navegación JSON.
No requiere API de IA, suscripción ni dependencias Python externas.

También incorpora [renderizado desde el código](SOURCE_RENDER.md): compila y renderiza
previews Compose y layouts XML sin abrir la app en un teléfono. **Proyecto / GitHub**
acepta una carpeta, una APK o un repositorio y prepara escenarios automáticamente
cuando las fuentes son compatibles. Las pantallas que necesitan datos propios aparecen
como pendientes de adaptar. El motor Gradle descarga sus dependencias la primera vez.

## Abrir

En Windows, doble clic en `Explorador.cmd`. Requiere Python 3.10+ con Tkinter y
Android Platform Tools (`adb`). En Linux puede necesitar el paquete `python3-tk`.

1. Conecta un Android con depuración autorizada y la pantalla desbloqueada.
2. Al abrir, detecta los móviles autorizados y selecciona uno. Puedes elegir **Este PC**
   o **ASUS** sin escribir direcciones. Si no encuentra ninguno en la conexión recordada,
   prueba la otra. El selector muestra el modelo y el serial vigente; **Dispositivos**
   actualiza la lista. Se usan las claves SSH existentes y la clave de host conocida.
3. Pulsa **Apps instaladas** para elegir la app o introduce su package. Al elegir una APK
   se intenta detectarlo automáticamente con `aapt2`/`aapt` del SDK Android instalado.
4. Elige una APK para instalarla o deja el campo vacío para usar la app instalada.
5. Ajusta los límites y pulsa **Explorar**. **Detener** conserva lo ya capturado.

La instalación usa `adb install -r`: no desinstala ni borra datos. Si la firma no
coincide, falla y muestra el error. Las sesiones se guardan en `sessions/`, excluido
de Git. Los nombres de botones de la interfaz se muestran en español.

Recuerda conexión, dispositivo preferido, app, APK, repositorio, referencia y límites.
No hay una app ni un repositorio fijados por defecto. Si cambia el puerto ADB, intenta
seleccionar el mismo modelo; en la primera apertura prioriza el S25 y después el Pixel.
Las preferencias se guardan en `%LOCALAPPDATA%/APK Explorer/settings.json` en Windows
o `~/.config/APK Explorer/settings.json` en Linux, sin tokens ni claves. Abrir la
ventana solo detecta dispositivos; no instala ni explora hasta pulsar **Explorar**.

## GitHub privado

Requiere GitHub CLI (`gh`) autenticado: `gh auth login`. Reutiliza su almacén de
credenciales; no pide ni escribe tokens en archivos del proyecto.

- Introduce `propietario/repositorio` o su URL y pulsa **APK de GitHub**.
- Selecciona el APK de la última release publicada (incluye prereleases) o indica un
  tag concreto. Si no hay APK publicada, usa un archivo local.
- **Leer candidatos de pantallas del código GitHub** consulta la rama predeterminada
  o la referencia indicada y añade el SHA y un inventario estático parcial al informe.
  Lee hasta 30 archivos de navegación/pantallas/actividades/manifest mediante la API;
  no clona ni ejecuta código del repositorio.

La conexión lee repositorios y descarga APK. No publica capturas ni informes en
GitHub. El acceso depende de los permisos de la cuenta autenticada. El inventario
del código y la APK seleccionada pueden pertenecer a revisiones diferentes: el
inventario no implica que esas pantallas existan en el binario ni que se hayan visitado.

## Línea de comandos

Para agentes, usar el [modo de inspección visual](AGENT_USAGE.md):

```powershell
python tools/apk-explorer/explorer.py --inspect "C:\ruta\app.apk" --json
```

Ese ejemplo se ejecuta desde la raíz del proyecto. Detecta la conexión y el dispositivo,
recorre primero las pantallas cercanas al inicio y devuelve láminas de capturas, originales,
rutas y acciones pendientes. No necesita que el agente maneje la ventana. Las láminas
usan Pillow si está disponible; los originales y el informe se generan sin esa dependencia.
Para revisar una sesión anterior sin recorrer otra vez la app, usar `--bundle CARPETA --json`.
El modo rápido prioriza las pestañas inferiores y la navegación antes que los mensajes
y el desplazamiento. Espera a que el árbol de interfaz se estabilice para evitar capturar
el splash como si fuera la pantalla final. El JSON usa escapes Unicode compatibles con
consolas Windows de codificación heredada.

Los comandos tradicionales siguientes se ejecutan desde la carpeta de la herramienta:

```powershell
python explorer.py --devices
python explorer.py --ssh vidal@100.112.57.2 --devices
python explorer.py --serial SERIAL --package com.empresa.app --apk C:\ruta\app.apk
python explorer.py --ssh vidal@100.112.57.2 --serial SERIAL --package com.empresa.app --max-states 60 --minutes 15
python explorer.py --serial SERIAL --package com.empresa.app --repo propietario/repo --release latest --asset app.apk --source-map
python explorer.py --serial SERIAL --package com.empresa.app --capture-only
```

`--capture-only` captura la pantalla actual sin lanzar la app, tocarla ni instalar.
`--output` debe señalar una carpeta nueva. `--max-actions` incluye las acciones de
reproducción de rutas; `--depth` limita la profundidad. Cada comando ADB tiene un
timeout; detener puede tardar hasta que termine el comando actual. El límite de
minutos corresponde a la exploración, no a la descarga/instalación/lectura de GitHub.

## Qué cubre

Exploración por profundidad (ventana y CLI tradicional) o por anchura (`--inspect`),
pulsaciones, desplazamiento vertical, detección de
estados por árbol de interfaz y reproducción de rutas desde el inicio. Guarda una
captura por estado encontrado y una huella SHA-256 del PNG. Los estados seleccionados,
marcados, textos y coordenadas forman parte de la identidad de una pantalla.

No garantiza cobertura completa. No rellena formularios ni contraseñas; no aprueba
permisos ni explora otras apps. Omite controles con textos/identificadores reconocibles
de borrar, pagar, generar, enviar, guardar o publicar. Este filtro es heurístico:
un botón sin etiqueta o una acción con nombre inesperado podría modificar datos.
Usa una instalación de prueba y datos de prueba para una exploración extensa.

Las rutas se reproducen cerrando y abriendo el proceso de la app, sin borrar sus
datos. Si el estado inicial o un paso cambia, se registra la ruta no reproducible.
Las superficies Canvas, juegos, WebViews con poca accesibilidad, gestos especiales,
desplazamiento horizontal, menús por pulsación larga, variantes de idioma/tema y
estados dependientes del servidor pueden requerir recorridos específicos.
Los textos dinámicos pueden producir estados adicionales. Pantallas protegidas
contra capturas pueden aparecer negras y requieren revisión visual.

Las capturas y árboles XML pueden contener información privada visible en pantalla.
La galería es un archivo local sin recursos de red. Los artefactos nunca se añaden
automáticamente al repositorio ni se envían a un servicio externo.

## Pruebas

```powershell
python -m unittest discover -s tools/apk-explorer -p test_explorer.py -v
```

Se ejecutan desde la raíz del repositorio. Cubren rutas ramificadas y su recuperación,
límites, capturas sin navegación, controles sensibles, límites externos, persistencia
ante desconexión, escape HTML, transporte SSH y rechazo de rutas de descarga inválidas.

## Una entrada para cada proyecto

```powershell
# Analizar y preparar, sin compilar ni usar un dispositivo
python explorer.py --project "C:\mis-apps\Tiempo" --json
python explorer.py --project propietario/repositorio --ref main --json
# Ejecutar los perfiles generados; si la entrada es una APK, usa el explorador ADB
python explorer.py --project "C:\mis-apps\Tiempo" --project-run --json
python explorer.py --project "C:\mis-apps\Notas.apk" --project-run --json
```

La conexión GitHub utiliza `gh` y su autenticación existente. Guarda la revisión exacta
que descarga en una carpeta de sesión; no publica cambios en el repositorio analizado.
NEXUS queda como ejemplo opcional mediante `--render-config`, nunca como requisito.
