# Compilador del ecosistema

Sistema por encargo para Codex: mide el ecosistema antes de cada trabajo, filtra los ejecutores compatibles y elige entre ellos según disponibilidad, carga y preferencia por equipos dedicados. No necesita un servicio residente, una suscripción ni abrir aplicaciones.

La selección busca reducir interferencia. La puntuación es una política configurable; no representa una comparación medida de velocidad entre CPUs. No garantiza cero lag: limita el consumo propio y aplaza el inicio si observa el equipo ocupado.

## Funcionamiento

`plan` y `submit` consultan CPU, RAM libre, disco, presión de memoria/E/S en Linux, trabajos activos, bloqueo por nodo y herramientas reales. En el Linux del Pixel comprueban tanto la RAM de la VM como la RAM física disponible en Android: no consideran memoria virtual libre como capacidad física adicional. Detectan uso reciente del EliteBook y consultan los teléfonos por ADB local y del ASUS, verificando modelo y serial físico. Solo conectan puertos mDNS actuales de los seriales conocidos; no emparejan ni reinician ADB.

ASUS y Pixel reciben preferencia al ser dedicados. S25 requiere pantalla apagada, batería/temperatura comprobadas y un ejecutor de fondo real. `executor: null` excluye el teléfono de compilaciones automáticas aunque ADB permita leerlo. El ejecutor activo del Pixel es Termux nativo con SSH privado sobre ADB. El Linux nativo se actualizó y conservó, pero sus bloqueos de Terminal impiden usarlo como ejecutor automático estable. El S25 está detectado por ADB y sigue pendiente de preparar un ejecutor de fondo.

El worker verifica de nuevo los recursos bajo un bloqueo exclusivo. Ejecuta el código de la instantánea, nunca fuentes remotas antiguas. Cada trabajo conserva procedencia, hashes, decisión, logs y resultados. SSH usa la clave conocida del servidor y no admite automáticamente claves nuevas. La salida remota continúa con `nohup` aunque se cierre la conexión. Un resultado de envío ambiguo requiere consultar el mismo trabajo antes de cualquier nuevo envío.

En ASUS, Gradle usa un worker, Kotlin en proceso, heap ajustado a RAM disponible, `nice 15` y `ionice` cuando existe. En Windows, un Job Object cubre el worker y sus hijos con CPU al 15 % y memoria máxima de la receta; se comprueban los límites antes de lanzar. Si no puede aplicarlos, la compilación falla antes de iniciar. La prioridad es baja y no se abren ventanas. Los [Job Objects de Windows](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects) agrupan procesos y sus límites.

## Uso

Instalación revisada en `C:/Users/User/.codex/tools/ecosystem-build`. Fuentes mantenidas en `C:/Users/User/Documents/ChatGPT/NEXUS/tools/ecosystem-build`. Actualizar la instalación ejecutando `python tools/ecosystem-build/install.py` después de cambios y pruebas. El instalador local no modifica PATH ni servicios existentes. La preparación del Pixel instala un servicio SSH propio dentro de su VM.

```powershell
$router = 'C:/Users/User/.codex/tools/ecosystem-build/router.py'
$recipe = 'C:/Users/User/.codex/tools/ecosystem-build/recipes/nexus-apk.json'
python $router plan --recipe $recipe --source 'C:/Users/User/Documents/ChatGPT/NEXUS'
python $router submit --recipe $recipe --source 'C:/Users/User/Documents/ChatGPT/NEXUS' --wait-seconds 60
python $router status JOB_ID --tail 20
python $router collect JOB_ID --dest 'C:/Users/User/Documents/ChatGPT/NEXUS/outputs/ecosystem-build'
```

`--prefer pixel` aumenta la preferencia sin eludir compatibilidad, recursos o bloqueos. `--wait-seconds` reevalúa cada 15 segundos hasta el plazo; sin nodo apto devuelve `waiting` y no inicia ningún proceso de compilación. Después de un `deferred` definitivo se puede reenviar; `running`, `unknown` y `dispatch_unknown` requieren resolver el trabajo existente. Una pérdida de conexión no es un fallo de compilación confirmado.

La habilidad y la regla global de Codex aplican este procedimiento a nuevas peticiones. No intercepta compiladores lanzados manualmente ni sustituye reglas de otros asistentes. La carga global de instrucciones se hace al iniciar la conversación, según la [documentación oficial de AGENTS.md](https://developers.openai.com/codex/guides/agents-md/).

## Recetas de otros proyectos

Crear un JSON con `kind: command`, `host_os`, `host_arch` si procede, `requires_tools`, `command` como lista de argumentos, `source_includes`, `artifacts` y presupuesto de RAM/disco. Declarar `low_impact: true` solo después de limitar el paralelismo y consumo del comando Windows; `max_memory_mb` define el techo del Job Object. La extensión EXE no determina por sí sola la plataforma: un compilador cruzado comprobado puede permitir Linux; PyInstaller o WebView2 pueden necesitar Windows según el producto.

Para Android, `kind: android-gradle`, `java_major`, `compile_sdk`, nombres de `tasks` y Gradle Wrapper del proyecto. Comprobar NDK, CMake y demás requisitos adicionales mediante `requires_tools` y una receta ajustada: esta primera versión no instala automáticamente toolchains ni valida todas las variantes nativas. Las recetas NEXUS son ejemplos operativos para este código, no plantillas universales.

## Rutas y pendientes

Los ajustes de equipos se guardan en `nodes.local.json` de la instalación, como un objeto por ID (`asus`, `pixel`, `s25`, `elitebook`) con los campos a reemplazar. Se conserva al reinstalar. No guardar contraseñas ni claves privadas: usar las identidades SSH existentes. Los requisitos adicionales de cada proyecto se declaran en su receta.

Comprobación del 2026-10-06: se actualizó el Terminal Linux nativo (`com.android.virtualization.terminal`) con autorización del usuario. Se verificó la copia anterior en `/mnt/backup`, que se conservó. Se copiaron JDK 17 ARM64 y SDK 35 a `/home/droid/.local/share/ecosystem-build/toolchain-20261006`, comprobando 11.872 archivos mediante SHA-256. AAPT2 ARM64 está aislado y su ejecución se verificó. No se borraron datos de Termux ni del backup.

`prepare-linux-pixel.sh` y `restore-pixel-toolchain.py` prepararon el ejecutor nativo. `ecosystem-build-ssh.service` usa clave propia de host y autenticación por la clave pública existente del EliteBook. Solo el puerto 2222 de la VM se habilitó en Terminal; Android escuchaba en localhost. El SSH por defecto añadido por apt se desactivó al validar el servicio propio. La configuración nativa quedó archivada en `outputs/ecosystem-build/pixel-setup/native-linux-executor.json` del proyecto; no es la ruta activa.

`prepare-pixel.sh` preparó el SSH propio de Termux en `127.0.0.1:8022`, sin modificar su SSH habitual. La clave de host se fijó mediante ADB en `pixel_known_hosts`, alias `ecobuild-pixel-termux`. Se activó `termux-wake-lock` en el Pixel dedicado, conectado al cargador, y se verificó el canal con pantalla apagada. `nodes.local.json` contiene esta configuración activa: JDK 17 y AAPT2 ARM64 aislados en `nexus-build/batches-20261002`, SDK 35 y caché Gradle existente. No usa proot ni abre la interfaz para compilar. La preparación no implica arranque automático tras reiniciar Android: si Termux no está disponible, se usa otra alternativa apta.

El adaptador `executor.transport: adb-ssh` descubre el forward ADB actual y permite usarlo desde Windows o por ASUS; si el acceso directo falla, prueba la ruta del ASUS. `host_key_alias` y `known_hosts` fijan la identidad SSH incluso si cambia el puerto del forward. Se verifica el serial por ADB y se transfiere una muestra de batería/uso de menos de 60 segundos antes de iniciar el worker.

La app nativa permite mantener la VM activa durante un máximo configurable de un día. La asignación inicial de 5.444 MB acabó con un ANR de StorageBalloonWorker durante una compilación; Android terminó esa VM. Se conservaron el trabajo y sus logs, se verificó que sus procesos ya no existían y se redujo la asignación a 3.072 MB. El servicio SSH volvió a arrancar por systemd, pero la disponibilidad siguió siendo intermitente. Esa VM se cerró antes de preparar Termux y quedó fuera de la selección automática. Su software y datos permanecen disponibles para uso manual. La ruta activa de Termux reserva 1.024 MB de RAM física además del presupuesto de la receta.

La batería mínima es 20 % para Pixel y 30 % para S25, incluso cargando. Un ejecutor Linux dentro del Pixel mantiene esos controles: se identifica con `device_kind: android` aunque el sistema de la VM sea Linux. Un Linux nativo o Termux deben demostrar por separado su JDK, SDK y AAPT2 compatibles; no intercambiar binarios Bionic y glibc.

ASUS: `vidal@100.112.57.2`, identidad `ASUSVIDAL`. ADB queda en localhost. Desktop Commander ASUS se verificó con `pong` el 2026-10-06. `probe-command --node asus` genera la misma sonda para `start_process` de Commander si SSH falla. La CLI automatiza SSH y local; el transporte Commander alternativo lo ejecuta Codex mediante sus herramientas, no una API inventada.

Para incorporar otro móvil, configurar `executor` solo tras verificar una ruta SSH no interactiva en su Linux o Termux, clave de host, usuario, puerto, Python y herramientas. Los campos siguen el nodo SSH (`ssh`, `ssh_port`, `proxy_jump` si hace falta, `python`, `state_root`, `env`). Antes de habilitar, probar el worker, la identidad física, los límites, la batería/temperatura y la recuperación de archivos. La CLI requiere además ADB actual; no usa Tailscale `Online` como prueba suficiente. Si no se dispone de esa ruta, mantener `null` y explicar el impedimento. No abrir Termux para automatizar con teclas. Android separa el acceso [ADB](https://developer.android.com/tools/adb) del usuario de las aplicaciones.

Mantener las claves privadas de firma en su custodio actual. El APK de validación remota puede tener un certificado debug distinto: comprobar o volver a firmar antes de actualizar una instalación. Los resultados de esta tarea son compilaciones y transferencias verificadas; no pruebas funcionales de las apps.

## Comprobaciones

Resultados reales del 2026-10-06, conservados en `outputs/ecosystem-build/verification.json` del proyecto:

- ASUS: APK NEXUS compilado y recuperado; primera compilación 13 min 56 s y repetición con caché 1 min 24 s.
- EliteBook: EXE NEXUS compilado en 7,3 s, con CPU al 15 %, prioridad baja, ventana oculta y memoria limitada; límites confirmados por el kernel.
- Pixel: APK NEXUS compilado mediante Termux nativo, con pantalla apagada, en 10 min 26 s. Los 291 archivos de código coinciden con el manifiesto compilado en ASUS. Recuperación SHA-256, manifiesto Android y firma APK v2 comprobados. No se instaló la app ni se hicieron pruebas funcionales.
- Linux nativo del Pixel: actualización y restauración de herramientas comprobadas, ejecución ligera en segundo plano comprobada; compilación completa interrumpida por ANR de Terminal. Excluido de la ruta automática activa.
- S25: conexión ADB comprobada, sin ejecutor de fondo verificado y con batería inferior al mínimo durante la evaluación. No se abrió ninguna app para configurarlo.
- 22 pruebas del selector superadas, incluida reserva física de RAM, permisos restringidos de Termux, exclusión de nodos ocupados y recuperación del mismo trabajo tras cambiar su puerto ADB.

```powershell
python -m unittest discover -s tools/ecosystem-build -p test_router.py -v
python C:/Users/User/.codex/skills/.system/skill-creator/scripts/quick_validate.py C:/Users/User/.codex/skills/ecosystem-build
```

Los trabajos se conservan para recuperar resultados y revisar evidencias. No hay borrado automático ni cambios de estado en Termux. Se pueden retirar copias de trabajo terminadas tras comprobar la recuperación; nunca borrar fuentes originales ni trabajos activos.
