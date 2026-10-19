# Auditoría del servidor ASUS (`ASUSVIDAL`), 19-10-2026

## Qué se pudo comprobar

- **Acceso directo desde la sesión: NO.** Desktop Commander mostraba `ASUSVIDAL` como *Online*, pero
  rechazó los comandos («no live connection»). Desde esta sesión en la nube no hay
  Tailscale y `100.112.57.2:22` no responde. Por eso no hay medidas actuales de RAM,
  disco, temperatura ni servicios tomadas por el agente; el usuario ejecutó después
  `audit.sh` (ver «Medidas en vivo»).
- **Sí se revisó** todo lo que el repositorio documenta sobre el ASUS (README de
  `tools/flow-linux-probe`, `tools/flow-bridge`, notas de compilación en `docs/`,
  `CONTINUAR.md`, `AGENTS.md`). Lo que sigue se basa en esas evidencias, que tienen fecha.
- **Para completar la auditoría**: ejecutar `tools/asus-health/audit.sh`, que es
  solo de lectura (ver más abajo), y analizar su salida.

## Medidas en vivo (19-10-2026, 22:10, `audit.sh` ejecutado por el usuario, salida parcial)

- **Equipo**: ASUS X540LA (BIOS X540LA.203), Intel Core i3-4005U (Haswell, 2 núcleos
  y 4 hilos, 1,7 GHz sin turbo), 3,8 GB de RAM, Debian 13 y kernel 6.12. Llevaba 18 días encendido.
- **CPU saturada**: carga de 3,3 a 6,4 en 4 hilos y *pressure* `cpu some` del ~84 % sostenido
  (avg10, avg60 y avg300). Siempre hay procesos esperando CPU.
- **El causante es el Chromium de Flow**: su renderer promedia el 87 % de un hilo y el proceso
  GPU el 12,5 % desde que arrancó (3 h 21 min). Funciona en Xvfb (1280×800×24) con
  `--enable-gpu-rasterization` y no hay GPU real, así que todo el dibujado se hace por
  software en la CPU. RAM de Chromium + Xvfb: ~1,5 GB.
- **Memoria justa**: 724 MB disponibles, 1,1 GB en swap (zram de 2 GB con zstd, más swap
  en disco; 5,9 GB en total) y `Committed_AS` de 7,8 GB frente a un `CommitLimit` de 8,0 GB,
  casi en el límite. `swappiness=60`. En el momento de la medida no había presión de memoria;
  la presión de E/S era moderada (avg300 del 7 %).
- **Procesos de larga duración identificados** (`ps` y `systemctl status` del 19-10):
  - PID 1680, 963 MB: **Home Assistant** (`python3 -m homeassistant --config /config`)
    en un contenedor Docker. Es el mayor consumidor de RAM.
    Contenedor `axel-homeassistant` (imagen 2026.9.3), creado el 24-09 y con la configuración
    inicial completada. Escucha en `127.0.0.1:8123` y `100.112.57.2:8123` (Tailscale). Su única
    integración real es **`dreame_vacuum`, «Conga»** (el robot aspirador, añadido el 01-10, con
    la opción `prefer_cloud`); las demás son las predeterminadas (`sun`, `backup`, `analytics`,
    `go2rtc`, `google_translate`, `radio_browser`, `shopping_list`). Tiene 4 dispositivos y
    1 automatización. La búsqueda de `homeassistant|8123` en `/opt/axel-agent` solo encontró
    datos de botocore (falsos positivos) y `catalog_facts.json`, que está pendiente de revisar.
  - PID 2150, 64–88 MB: **Desktop Commander** remoto (`axel-commander-0.2.51`), en marcha
    pero sin una conexión que responda.
  - PID 1481590, 41–52 MB: **`axel-bridge.service`** («AXEL authenticated durable
    agent», `/opt/axel-agent/server.py`), activo desde el 08-10.
- `vidal` tiene `sudo` sin contraseña (al menos para `docker`): equivale a acceso de
  administrador para cualquier agente que entre como `vidal`.
- No había procesos de Gradle ni de Kotlin activos.
- Falta la segunda mitad del informe (servicios, puertos, discos, temperatura y batería).
  En esa ejecución `swapon` no se encontraba porque `/usr/sbin` no estaba en el PATH;
  el script ya lo corrige.

### Cambio aplicado: Home Assistant en reposo (19-10-2026, ejecutado por el usuario)

- La política de reinicio anterior era `unless-stopped`; ahora es `--restart=no`. Se ejecutó
  `docker stop axel-homeassistant` y el estado quedó en `Exited (137)`: no se cerró en los
  10 s de margen y Docker lo terminó a la fuerza. La próxima vez conviene usar `docker stop -t 60`.
- Resultado: memoria disponible de **724 → 1780 MB** y swap usado de 1115 → 734 MB.
- No se borró nada: configuración, integración «Conga» y automatización intactas.
- Para despertarlo: `sudo docker start axel-homeassistant`. Para volver a dejarlo siempre
  encendido: `sudo docker update --restart=unless-stopped axel-homeassistant`.
- Mientras está parado, la app oficial de la Conga sigue funcionando; solo dejan de funcionar
  la voz y la automatización que dependen de Home Assistant.

### Medidas directas por Desktop Commander (19-10-2026, 22:34)

Desktop Commander volvió a conectar tras `systemctl --user restart desktop-commander-remote`,
ejecutado por el usuario.

- **Disco**: Seagate ST500LT012, un **HDD de 5400 rpm** (`ROTA=1`) de 500 GB. Usados 34 GB (8 %).
  Hay también un pendrive USB de 3,8 GB conectado (`sdb`). Esto explica las esperas de E/S
  cuando entraba en swap.
- **Swap**: zram de 1,9 GB con prioridad 100 y partición `sda3` de 4 GB con prioridad -2.
  El orden ya es el correcto.
- **Temperaturas**: CPU 44 °C y PCH 40 °C. Sin problemas.
- **Batería**: **0 %, «Not charging»**, con el cargador conectado. Salud del 79 %
  (26,1 de 33,2 Wh) y 266 ciclos. No sirve como reserva ante un corte de luz; si está hinchada,
  hay que retirarla físicamente.
- **Sin escritorio gráfico**: solo `getty@tty1`. No hay nada que quitar en este punto.
- **Servicios AXEL activos** (sistema): `axel-adb`, `axel-awake`, `axel-bridge`, `axel-devices`,
  `axel-home`, `axel-lamp-link`, `axel-lamp-panel` y `axel-taildrop`. Del usuario:
  `axel-tv-bridge`, `nexus-flow-recovery` y `desktop-commander-remote`. Además, un túnel SSH
  al Pixel (`~/aki/bin/pixel-ssh-tunnel.py`). Cada uno ocupa menos de 90 MB; ninguno ha fallado.
- **Chromium de Flow**: lo lanza `nexus-flow-recovery` (`login.py --persistent`, desde las 18:48).
  La pestaña estaba en **`https://flow.google.com/about`, la portada pública: la sesión de
  Google estaba cerrada**, y la portada, con sus vídeos y animaciones, era lo que consumía la CPU.

### Cambio aplicado: pestaña de Flow en blanco (22:35, por el agente)

Se llevó esa pestaña a `about:blank` con Playwright por CDP (`page.goto`), sin cerrar el
navegador. La sesión ya estaba cerrada, así que no se perdió nada.

| | Antes | Después (~1 min) |
|---|---|---|
| CPU inactiva | ~58 % (renderer al 67 %, GPU al 17 %) | **98,5 %** |
| `cpu some` avg10 | ~84 % | **7,7 %** (bajando) |
| RAM de Chromium | ~1,5 GB | **529 MB** |
| RAM disponible | 1780 MB (tras parar Home Assistant) | **2339 MB** |

Mejora frente al inicio de la auditoría: memoria disponible de **724 → 2339 MB**, y la CPU pasa
de saturada a estar casi en reposo.

Pendiente: para usar Flow hay que volver a iniciar sesión con el portal de recuperación. Cuando
haya un proyecto abierto, el consumo volverá a subir; de ahí el plan de «Chromium en reposo»
(congelar la pestaña o dejarla ligera entre usos).

### Flow con la sesión iniciada (19-10-2026, ~22:50)

El usuario volvió a iniciar sesión desde el portal. `GET /api/flow/status` (autenticado, en local)
devolvió `bridge_status=connected`, `generation_ready=true`, `project_open=true` y
`composer_editable=true`. No se generó nada.

Con un proyecto abierto y en reposo: carga de 0,79, `cpu some` avg10 del 4 % y CPU inactiva
del ~98 %. **El consumo alto venía de la portada `/about`** (la que aparece cuando se cierra
la sesión), no de tener un proyecto abierto. Por eso congelar la pestaña no es necesario por
ahora. Lo que sí conviene es un vigilante que, si la pestaña se queda en `/about` (sesión
caída) durante más de dos comprobaciones, la deje en blanco y avise. No está implementado.

### Por qué se perdía Flow y vigilante instalado (19-10-2026, 22:52)

- **Las cookies de Google de este perfil son persistentes y caducan entre el 05-11-2026 y
  2027** (solo se contaron, sin leer valores). La sesión no se pierde por tiempo.
- El último cierre de Chromium quedó registrado como `exit_type=Crashed`: los reinicios del
  servicio (dos hoy, a las 18:21 y a las 18:48, por despliegues de la rama
  `codex/windows-corte-integration-20261018`) mataban el navegador sin dejarle guardar las cookies
  que Google rota. Es la hipótesis más probable de las pérdidas de sesión; no está demostrada.
- El usuario indica que **Flow le saca del proyecto a la galería** y tiene que volver a entrar.
  El puente exige un proyecto abierto.
- Instalado: `nexus-flow-keepalive.timer`, que se ejecuta cada 5 minutos y vuelve a abrir el
  último proyecto si la pestaña se queda en la galería, o deja la página en blanco si la sesión
  cae. Instalado también: el `ExecStop` de cierre limpio. Detalles y límites en
  `tools/flow-linux-probe/README.md`. No se reinició `nexus-flow-recovery`; el puente sigue
  `connected`.
- **Reinicio automático de sesión con contraseña: descartado.** Google ya lo rechazó, y
  repetir intentos automáticos puede bloquear la cuenta.

**Conclusión provisional**: aunque ahora mismo **no compila**, el ASUS está saturado de CPU
solo por tener Flow abierto. Dedicarlo a Flow tiene sentido, pero hay que reducir el coste del
navegador mientras está inactivo (ver «Chromium en reposo»), y decidir
si Home Assistant se queda en esta máquina o se ajusta.

## Hardware conocido

| Dato | Valor | Fuente |
|---|---|---|
| Modelo | ASUS X540LA | `audit.sh` 19-10 |
| CPU | i3-4005U, 2 núcleos y 4 hilos, 1,7 GHz | `audit.sh` 19-10 |
| RAM | 3,8 GB, zram de 2 GB + swap hasta 5,9 GB | `audit.sh` 19-10 |
| SO | Debian 13, kernel 6.12, Python 3.13, Chromium 154 | `audit.sh`, `flow-linux-probe/README.md` |
| Acceso | SSH `vidal@100.112.57.2` por Tailscale | `AGENTS.md`, `build-agent-asus.py` |
| Disco | HDD Seagate de 5400 rpm, 500 GB (8 % usado) | DC 19-10 |
| Batería | 0 %, no carga, salud del 79 %, 266 ciclos | DC 19-10 |

## Funciones que tiene hoy en el ecosistema

| # | Función | Evidencia | ¿Tiene sentido mantenerla en el ASUS? |
|---|---|---|---|
| 1 | **Compilación Gradle** de Android y del motor de escritorio (JDK 17 en `~/aki/toolchain/jdk17`, SDK 35, copias en `~/builds`) | Varios `BUILD SUCCESSFUL` de 7 a 24 min; trabajos cancelados por falta de RAM (`20261006T043115Z`, `…043740Z`); empaquetado abortado por swap y espera de E/S (09-10); SSH casi inaccesible con unos 150 MiB libres | **No.** Es su peor tarea: con 3,7 GB Gradle + Kotlin necesitan casi toda la RAM y acaban en swap. Ya existen alternativas mejores: AXELPC (prioridad 1 en `AGENTS.md`) y **GitHub Actions** (`.github/workflows/build-apk.yml` ya compila la APK en cada push). |
| 2 | **Clave de firma** de las APK instaladas (`~/.android/debug.keystore`, certificado `8b75db0f…cd7127`) | `docs/HISTORIAL-NAVEGACION-199-20261016.md`; AXELPC tuvo que volver a firmar con la clave del ASUS | **No debería depender solo de él.** Si ese disco falla, el S25 y el Pixel no podrán actualizarse sin borrar datos. Algunos documentos dicen que hay una copia en Windows, pero esto no se ha verificado. Hay que guardar una copia cifrada y comprobada en AXELPC y otra sin conexión, nunca en Git. |
| 3 | **Sesión persistente de Google Flow**: Chromium en Xvfb, portal de recuperación noVNC (`nexus-flow-recovery.service`, `100.112.57.2:8899`), puente que usa la app Android (`bridge_status=connected`, `generation_ready=true` el 09-10) | `tools/flow-linux-probe/README.md`, `docs/ANDROID-STALLED-JOBS-20261009.md` | **Sí: es la función que mejor le encaja.** Pide pocos recursos si es lo único que hace (pico medido de 500 MiB a 1 GiB), necesita una máquina siempre encendida y ya está desplegada. |
| 3b | **Home Assistant** en Docker (963 MB) | `ps` del 19-10 | Encaja como servicio siempre encendido, pero compite con Flow por la RAM. Conviene ajustar el *recorder* y las integraciones, o moverlo a un equipo dedicado (por ejemplo, una Raspberry Pi o un HA Green). Lo decide el usuario. |
| 3c | `axel-bridge` y Desktop Commander remoto (~100 MB entre los dos) | `ps` y `systemctl` del 19-10 | Ligeros. Mantenerlos; Desktop Commander necesita reiniciarse porque su conexión no responde. |
| 4 | Ejecutar pruebas JVM y Python del puente | Pruebas de recuperación y autenticación en el ASUS | Solo las del propio puente de Flow, que son ligeras. Las de la app y de `agent-core`, en AXELPC, el Pixel o Actions. |
| 5 | Restos de pruebas: perfil de sondeo de Playwright, credencial cifrada con caducidad de 24 h (06-10), copias en `~/builds`, cachés de Gradle | `flow-linux-probe/README.md`, `build-agent-asus.py` | **No.** La credencial ya habrá caducado y el inicio de sesión desatendido falló (Google lo rechazó). Lo demás ocupa disco y, si quedan *daemons* activos, también RAM. |

## Por qué los problemas de Flow encajan con un ASUS saturado

Fallos registrados de la sesión de Flow:

1. La sesión se cerró tras cerrar Chromium y volverlo a abrir con el mismo perfil.
2. `health.py` abrió **otro** Chromium sobre el perfil y la sesión pasó a `auth_required`
   (las cookies persistentes bajaron de 27 a 10).
3. Diferencia de `--password-store` entre el Chromium normal y el de Playwright.
4. Consultas del móvil que expiraron **mientras el ASUS compilaba**.

No se ha demostrado la causa de 1 y 2. Pero tienen algo en común: **se arranca y se cierra
Chromium a menudo sobre el mismo perfil**. Y el 4 sí está relacionado directamente con compartir
la máquina con Gradle. Dedicar el ASUS solo a Flow ataca las dos cosas.

## Propuesta: ASUS como «nodo Flow» dedicado

Tu idea es buena, siempre que se cumplan estas reglas:

1. **Un solo Chromium de larga duración** con un solo perfil y el mismo `--password-store`
   siempre. Los chequeos de estado y el puente deben **conectarse por CDP a ese navegador**
   en vez de lanzar otro. Así se elimina el escenario del fallo 2.
2. **Un servicio de usuario permanente** (`systemd --user`, con `loginctl enable-linger vidal`)
   que mantenga ese Chromium y el portal, con `Restart=on-failure`. Ya tiene `MemoryMax`,
   `Nice` y `UMask=0077`. Es mejor reiniciar el proceso del portal que reiniciar Chromium.
3. **Un *keep-alive* suave**: recargar el proyecto o consultar el estado cada X horas para que la
   sesión no se quede inactiva, sin generar nada ni gastar créditos.
4. **Avisar en lugar de reautenticar a ciegas**: cuando el estado pase a
   `auth_required`, enviar una notificación al móvil (por ejemplo, con ntfy por
   Tailscale) con el enlace al portal de recuperación. El inicio de sesión automático con contraseña ya se
   rechazó, así que no conviene volver a intentarlo.
5. **Nada más en la máquina**: ni compilaciones ni pruebas pesadas de Gradle. `AGENTS.md`
   ya lo deja como último recurso; pasaría a «nunca, salvo petición expresa».

### Chromium en reposo

La medida del 19-10 muestra que la pestaña de Flow, aunque nadie la mire, gasta casi un hilo
entero: dentro de Xvfb la página siempre cuenta como visible y Chromium nunca la frena.
Opciones, de menos a más trabajo:

1. Dejar la pestaña en una página ligera (la lista de proyectos o `about:blank` en otra
   pestaña) en vez de un proyecto con vídeos o animaciones. Las cookies de sesión no se pierden
   mientras el navegador siga abierto.
2. Congelar la página por CDP cuando no se usa (`Page.setWebLifecycleState frozen`) y
   descongelarla al recibir un trabajo o al hacer el *keep-alive*.
3. Probar `--disable-gpu` en lugar de `--enable-gpu-rasterization` (sin GPU, el dibujado
   por software de Skia suele costar menos que emular GL), con una pantalla de Xvfb más pequeña
   y menos profundidad de color.

Cada cambio se mide antes y después con `cpu some` y el %CPU del renderer, sin generar nada de pago.

Esto **no está implementado ni probado**: es el plan. El paso 1 requiere cambiar
`health.py` y el puente para que se conecten por CDP. Hay que hacerlo con pruebas y validarlo sin
generaciones de pago.

## Optimizaciones del sistema (pendientes de confirmar con `audit.sh`)

Ordenadas por impacto esperado en una máquina de 3,7 GB:

1. **Quitar el escritorio gráfico** si hay uno arrancado (GNOME, KDE, etc.): `systemctl set-default
   multi-user.target`. Xvfb ya da la pantalla virtual a Chromium. Suele liberar entre 300 y 800 MB.
2. **Matar los *daemons* de Gradle o Kotlin** que queden (`gradle --stop`) y borrar
   `~/builds/*` y `~/.gradle/caches`. Si deja de compilar allí, también el SDK de Android, pero
   **antes** hay que sacar la copia del `debug.keystore`.
3. **zram**: ya existe (2 GB, zstd). Falta darle prioridad sobre el swap en disco y subir
   `vm.swappiness` a 100–150, para que se use antes el swap comprimido que el disco.
4. **Limitar el journal** (`SystemMaxUse=200M`) y la caché de disco de Chromium
   (`--disk-cache-size`).
5. **Batería** (si es un portátil que siempre está enchufado): fijar el umbral de carga en 60–80 % si
   el firmware lo admite (`/sys/class/power_supply/BAT*/charge_control_end_threshold`
   con `asus-wmi`) y configurar `HandleLidSwitch=ignore` para que no se suspenda al cerrar la tapa.
6. **Actualizaciones**: activar `unattended-upgrades` solo para seguridad, pero **retener
   Chromium** (`apt-mark hold`) y actualizarlo a mano junto a `gflow-cli`, porque una actualización
   sin control puede romper el puente.
7. **Hardware: descartado.** El usuario no quiere gastar dinero en este equipo de 2013, que ya iba a tirar.
   Solo se aplicarán mejoras de software. Batería: lleva años sin funcionar; no se sustituye.
   El equipo trabaja enchufado y conectado a internet por cable.

Ninguna de estas acciones se ha ejecutado. Todas requieren acceso al ASUS y, en las que borran
datos o cambian el arranque, la confirmación del usuario.

## Reparto de funciones propuesto

| Función | Antes | Después |
|---|---|---|
| Compilar APK y motor | ASUS / AXELPC / EliteBook | **AXELPC**; GitHub Actions como respaldo |
| Pruebas Android | Pixel / emulador | **Pixel 8 Pro** por ADB |
| Firma | `debug.keystore` solo en el ASUS (y quizá en Windows) | Copia verificada en AXELPC y otra cifrada sin conexión; el ASUS deja de ser imprescindible |
| Sesión de Flow, portal y puente | ASUS, compartido con compilaciones | **ASUS dedicado** |

## Cómo ejecutar la auditoría en vivo

Desde un equipo con Tailscale (AXELPC o EliteBook):

```sh
ssh vidal@100.112.57.2 'bash -s' < tools/asus-health/audit.sh > asus-audit.txt
```

O con Desktop Commander, cuando la conexión del ASUS vuelva a funcionar: reiniciar en el
ASUS el terminal o servicio que lo ejecuta. El script no usa `sudo`, no reinicia nada y no borra
nada. El informe puede incluir rutas y nombres de procesos, así que hay que guardarlo **fuera de Git**.
