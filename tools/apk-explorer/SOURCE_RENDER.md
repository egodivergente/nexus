# Renderizado visual por proyecto

El explorador no depende de NEXUS. Acepta una APK, una carpeta local o un repositorio
GitHub y prepara el método disponible. No presupone que todo proyecto pueda renderizar
su interfaz desde fuentes: identifica los casos que necesitan un adaptador o datos.

## Uso

```powershell
python explorer.py --project "C:\apps\MiApp" --json
python explorer.py --project propietario/repositorio --ref main --json
python explorer.py --project "C:\apps\MiApp" --project-run --json
python explorer.py --project "C:\apps\MiApp.apk" --project-run --json
```

Sin `--project-run`, solo descarga/analiza y prepara escenarios. Con él, ejecuta
secuencialmente los perfiles generados; para una APK delega en la exploración ADB
existente, conservando el dispositivo y los límites indicados.

La ventana ofrece **Proyecto / GitHub**, **Descubrir pantallas** y **Renderizar código**.
El informe de preparación muestra los perfiles, el comando de ejecución y los casos
pendientes. El acceso directo del escritorio sigue abriendo la misma herramienta.
Las preferencias previas se conservan, pero una instalación nueva no fija app ni repo.

`gh repo clone` utiliza la autenticación existente. Se descarga a una sesión nueva
sin tocar un checkout del usuario. `repository.json` registra repositorio y commit.
`--ref` admite una rama o tag; no se hacen pushes al proyecto que se está inspeccionando.

## Preparación automática

- Android Kotlin con Gradle Kotlin DSL o Groovy: genera un fixture Paparazzi por módulo.
- Compose: detecta e invoca previews de nivel superior, accesibles, sin parámetros.
  Los argumentos de la anotación Preview (dispositivo, fondo, modo oscuro, etc.) no se
  expanden; se captura la función con sus valores internos en el dispositivo del motor.
- También prepara funciones `*Screen` accesibles, de nivel superior, sin parámetros ni
  dependencias detectadas. Es una selección conservadora, no una prueba de pureza.
- XML: infla layouts básicos de `src/main/res/layout` con el namespace real del módulo.
  No inventa datos, navegación ni contenido que solo aparece en ejecución.
- Los paquetes, módulos y nombres de funciones provienen del código seleccionado.
  Se copian las fuentes y scripts de los módulos locales para resolver dependencias.
- Flutter, React Native y proyectos JavaScript se identifican, pero no tienen todavía
  un adaptador automático de renderizado desde fuentes. Sus APK Android sí pueden
  explorarse con el modo de dispositivo.

Los previews privados, miembros de clases, parámetros, ViewModels y pantallas sin
preview se incluyen en la lista de pendientes. Data Binding, merge, fragments,
variantes Gradle complejas y módulos Android sin Kotlin requieren un perfil específico.
La detección de Kotlin es heurística, no un compilador; puede omitir declaraciones.
Los layouts alternativos por qualifiers tampoco se enumeran automáticamente.
Un perfil preparado no acredita que se pueda compilar: la ejecución real lo verifica.

## Render directo y perfiles personalizados

```powershell
python explorer.py --render "C:\apps\MiApp" --render-list --json
python explorer.py --render "C:\apps\MiApp" --json
python explorer.py --render "C:\apps\MiApp" --render-config perfil.json --json
```

`--render` utiliza `apk-explorer.render.json` si existe; de lo contrario genera un
perfil. Si hay varios módulos elegibles, usa `--project-run` para ejecutarlos todos
o selecciona uno con `--render-config`. El archivo declara entradas de fuentes,
módulo, variante, versión del motor, fixture Kotlin y estados esperados.
Una app con estado complejo puede aportar un fixture que llame a sus funciones reales
con datos controlados. Los perfiles NEXUS de `render_profiles/` son ejemplos explícitos;
no se seleccionan automáticamente y no son necesarios para otras apps.

El motor incluido es Paparazzi 1.3.5. La prueba de integración utiliza AGP 8.7.3,
Kotlin 2.0.21 y Compose BOM 2024.10.00. Otras combinaciones pueden requerir ajustar
la versión mediante un perfil; no se promete compatibilidad con todos los toolchains.
Los proyectos con Paparazzi ya configurado se señalan para usar sus propias tareas,
sin duplicar el plugin.

## Ejecución y evidencia

Compila una copia aislada, sin alterar los build scripts originales ni instalar la app.
Prioriza ASUS si está disponible, sin otro Gradle y con al menos 2200 MiB de RAM libre;
si no, usa el PC con prioridad baja, dos hilos y un worker. También comprueba el margen
de memoria del PC antes de iniciar. Las rutas SSH/toolchain son la configuración del
entorno actual; `--render-host usuario@host` y `--render-local` permiten elegir destino.
Se verifican las claves SSH conocidas. No cierra trabajos ajenos para liberar memoria.

Los fuentes y scripts del proyecto se ejecutan durante la compilación. Una preview
puede contener lógica propia: la preparación automática no sustituye servicios ni
asegura que sus funciones no hagan red. El aislamiento de archivos no es un sandbox
de ejecución. Usa proyectos de confianza.

Las sesiones se excluyen de Git. La copia omite cachés, outputs, `.env`, claves y
`local.properties`; no copia toda la raíz. El wrapper y las dependencias del proyecto
se usan desde la copia. La primera ejecución puede descargar dependencias Gradle.

- `project.json` / `project.html`: detección, perfiles, pendientes y enlaces a renders.
- `discovery.json` / `discovery.html`: símbolos, líneas, dependencias y rutas literales.
- `render.json` / `index.html`: imágenes reales, estados faltantes, errores y galería.
- `source-provenance.json`: hashes de fuentes y fixture, commit de contexto y comandos.
- `render.log`: resultado de la compilación y pruebas; un error nunca crea una captura ficticia.

`prepared` significa escenario generado; `rendered` significa que se obtuvieron todas
las imágenes registradas, no todas las interfaces posibles de la app. Las capturas
no acreditan equivalencia con una APK instalada. El CLI entrega JSON por stdout y
progreso por stderr. El render directo devuelve 0 al completar, 3 si es parcial y 1
si falla el motor; `--project-run` devuelve 3 si no completa todos los perfiles.

Referencias: [Paparazzi](https://cashapp.github.io/paparazzi/) y
[versión 1.3.5](https://github.com/cashapp/paparazzi/releases/tag/1.3.5).
