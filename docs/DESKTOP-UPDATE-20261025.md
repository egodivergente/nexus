# Nexus Studio 1.9.6-agent-desktop · 25/10/2026

Actualización **selectiva de Windows**, desde `main` @ `631aa29`, en `aki/desktop-agent-parity-20261025`. No modifica ni instala Android, ni fusiona otras ramas.

## Qué se revisó en GitHub

- Última descarga Windows publicada al iniciar esta tarea: [1.9.4-agent](https://github.com/egodivergente/nexus/releases/tag/v1.9.4-agent). `main` ya declara Desktop 1.9.5, mientras sus notas de continuidad contienen datos históricos.
- Android 1.9.76/vc110 está en `codex/artbox-resume-20261024`. El [PR #11](https://github.com/egodivergente/nexus/pull/11), `claude/rescate-pixel-20261025` @ `3b6082cecde9fe37c3bc4b642f7c9114dc62bdaf`, prepara 1.9.77/vc111 sobre esa rama, no sobre `main`.
- El PR aporta correcciones de bloqueos, índice de conversaciones, caché de skills, ArtBox, títulos y chat. Su autor documenta pruebas hasta `16b5d69` y **cambios posteriores todavía sin compilar**. Esa evidencia es del Pixel, no acredita el S25 ni Windows.
- El [PR #10](https://github.com/egodivergente/nexus/pull/10), comparador Fal/Atlas, permanece separado. Esta actualización no lo fusiona ni anuncia ahorros basándose en él.

## Trasladado a Windows

- Índice incremental del historial: solo decodifica conversaciones nuevas o modificadas. Un archivo dañado no bloquea los demás y se conserva.
- Búsqueda de conversaciones, títulos automáticos más limpios, renombrar y restablecer el título automático. Los nombres propios se guardan aparte del chat. Los cambios de conversación conservan identidad, borrador y adjuntos.
- Plegado de mensajes largos **del usuario**; las respuestas del agente siguen completas. Copiar, seleccionar y menú contextual accesible sin secuestrar enlaces, código ni selección.
- Catálogo con precios desconocidos explícitos, unidades verificadas y aviso visible de tarifas parciales. Son tarifas del catálogo, no cotizaciones ni una comparación de coste total. Siempre se mantiene el paso de cotización/confirmación antes de generar.
- Familias y rutas ocultas persistentes, recuperación y deshacer; las variantes nuevas de una familia oculta se mantienen ocultas. Tamaño de tarjeta configurable. Logos existentes preservados.
- Persistencia del índice de recursos fuera del hilo llamante, con exclusión mutua y publicación coherente incluso si se cancela la operación.
- Caché de skills derivada del contenido, versionada y reemplazada atómicamente; corrupción o fallo de escritura no impide cargar skills desde los originales.
- Markdown Desktop con enlaces HTTP(S) escapados y listas ordenadas/anidadas. No se habilita HTML crudo ni esquemas ejecutables.
- Fotogramas con identificador de recurso y tiempos aproximados; duración y audio desconocidos se identifican como no medidos.

## Lo que no cambia

No se trasladan WorkManager, servicios Android, gestos Atrás, Corte, sincronización entre móviles ni los últimos cambios Flow aún sin compilar. No se añaden proveedores ni se promete compatibilidad total con los catálogos Android. La app nativa C#/WebView2 no se recompila: se actualiza la edición local Python/Edge que usa este equipo.

Las claves DPAPI, el estado y los medios permanecen fuera del paquete. El relé USB a Sol y el lanzador específico del equipo permanecen fuera de la carpeta versionada. No se copian credenciales del teléfono, no se instala en los móviles y no se generan medios de pago.

## Compilación y pruebas

El proyecto JVM independiente está en `desktop/agent`, sin configurar Android:

```text
gradle -p desktop/agent :core:test :test runtime --no-daemon --no-parallel --max-workers=1 -Dorg.gradle.jvmargs="-Xmx600m -XX:MaxMetaspaceSize=384m" -Pkotlin.compiler.execution.strategy=in-process
```

Desactivar `BYTEPLUS_API_KEY` al ejecutar la suite offline: las cuatro pruebas explícitamente live se omiten por diseño. Los informes XML son la fuente del conteo final.

Empaquetado, después de las pruebas:

```text
python desktop/tools/package-update.py --output entregas/Nexus-Studio-1.9.6-Windows-x64.zip --source-commit <SHA verificado>
```

El ZIP incluye un manifiesto SHA-256 por archivo y valida su CRC. No contiene estado, claves, perfiles de navegador ni el relé privado del usuario.

### Resultados del código fuente

- JVM: **261 pruebas, 257 superadas y 4 live omitidas**, cero errores/fallos (236 core + 25 Desktop). Build y `runtime` completados, Java 17, un worker, heap máximo 600 MiB. No se compiló Android ni se utilizó ASUS.
- Python: **51 pruebas distintas** superadas (14 paquete + 17 conversaciones + 14 integridad + 6 adaptador/DPAPI); las 14 de paquete también pasan con `python -O` (65 ejecuciones en total).
- JavaScript: **13/13 suites** superadas, sin llamadas a proveedores.
- Interfaz completa: **40 capturas en seis tamaños de ventana**, cero errores JavaScript; Biblioteca, visor, Lienzo, temas y borradores verificados offline.
- E2E real Python + Edge + JVM: **22/22 comprobaciones** superadas, incluyendo historial, renombrado sin tocar el original, búsqueda, borradores/adjuntos, ausencia de duplicados, plegado, persistencia tras recarga, bloqueo de envío durante el cambio de conversación y caché de skills. Cero errores JS y cero operaciones externas.
- Recuperación: Enter/clic único, Shift+Enter, IME, borrador tras error, desconexión/reinicio real del host y recuperación de 403, todo superado.
- Manifiesto de procedencia Desktop: sus cinco hashes coinciden con los archivos compilados. `git diff --check` limpio con `core.whitespace=cr-at-eol`, requerido por los CRLF heredados de `app.js`.

Los logs y capturas detallados quedan locales en `outputs/desktop-update-20261025/`. No contienen inferencias ni generaciones reales. Las pruebas offline no acreditan conectividad actual con Sol ni cada proveedor multimedia.

### Límites de la regresión

Dos suites antiguas (`test-chat-ux.py`, aserción `data-remember`, y `test-chat-card-order.py`, fixture de plan sin `ref`) fallan también contra HEAD limpio. Se conserva ese resultado como fallo preexistente, sin contarlo entre las suites superadas. El flujo actualizado se cubre con las nuevas pruebas de continuidad y el E2E real.

### Instalación reversible

El paquete requiere Python 3, Edge y Java 17 ya instalados. Extraerlo en una **carpeta versionada nueva**, sin sobrescribir una versión abierta. Los JAR van en `desktop/agent/runtime/`. El manifiesto enumera los hashes de los archivos; el informe exterior cuenta también el propio manifiesto y sus campos `bytes`/`sha256` corresponden al ZIP completo.

No debe ejecutarse sobre el perfil real en paralelo con la versión anterior. Para comprobar el paquete sin tocar ese perfil:

```text
python <carpeta-extraida>/desktop/local_host.py --headless --offline-qa --data-dir <perfil-temporal-nuevo>
```

La activación en este equipo requiere cerrar Nexus de forma controlada y cambiar únicamente la carpeta versionada del lanzador local, manteniendo `usb_proxy.py` y la versión 1.9.4 para volver atrás. La carpeta nueva por sí sola no cambia la aplicación abierta.


## Corrección del panel «Estado y consumo»

El desplegable carecía de un cierre visible y no se ocultaba al pulsar fuera.
Ahora incluye **Cerrar**, cierre por clic fuera y Escape con retorno del foco al
control de apertura. Los diálogos de Telemetría conservan su propio cierre y
regresan al panel sin ocultar el botón que recupera el foco. Su anchura respeta
el espacio disponible incluso con la navegación expandida.

Verificación adicional: `python desktop/tools/test-status-panel.py` supera
**23 comprobaciones** en Edge con host real, perfil temporal y `--offline-qa`,
incluyendo cuatro tamaños y navegación expandida/contraída. Cero errores JS y
cero operaciones externas. Las **13 suites JavaScript** también pasan.
La captura e informes están en `outputs/status-panel-20261025/` (locales).
No se cambia el motor JVM, Sol, las claves, el chat ni los datos del usuario.
El parche consta de `creative-engine.js` y `creative-engine.css`; sustituirlos
con respaldo actualiza los recursos en disco, pero la ventana abierta necesita
una recarga posterior. No recargar una sesión con operaciones en curso.
