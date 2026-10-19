# Auditoría del servidor ASUS (`ASUSVIDAL`), 19-10-2026

## Qué se pudo comprobar

- **Acceso en vivo: NO.** Desktop Commander mostraba `ASUSVIDAL` como *Online*, pero
  rechazó los comandos («no live connection»). Desde esta sesión en la nube no hay
  Tailscale y `100.112.57.2:22` no responde. Por eso no hay medidas actuales de RAM,
  disco, temperatura ni servicios.
- **Sí se revisó** todo lo que el repositorio documenta sobre el ASUS (README de
  `tools/flow-linux-probe`, `tools/flow-bridge`, notas de compilación en `docs/`,
  `CONTINUAR.md`, `AGENTS.md`). Lo que sigue se basa en esas evidencias, que tienen fecha.
- **Para completar la auditoría**: ejecutar `tools/asus-health/audit.sh`, que es
  solo de lectura (ver más abajo), y analizar su salida.

## Hardware conocido

| Dato | Valor | Fuente |
|---|---|---|
| RAM | ~3,7 GB | `AGENTS.md` |
| SO | Debian, Python 3.13, Chromium 154 | `flow-linux-probe/README.md` |
| Acceso | SSH `vidal@100.112.57.2` por Tailscale | `AGENTS.md`, `build-agent-asus.py` |
| CPU, disco (SSD/HDD), batería, temperatura | **desconocido** | lo obtiene `audit.sh` |

## Funciones que tiene hoy en el ecosistema

| # | Función | Evidencia | ¿Tiene sentido mantenerla en el ASUS? |
|---|---|---|---|
| 1 | **Compilación Gradle** de Android y del motor de escritorio (JDK 17 en `~/aki/toolchain/jdk17`, SDK 35, copias en `~/builds`) | Varios `BUILD SUCCESSFUL` de 7 a 24 min; trabajos cancelados por falta de RAM (`20261006T043115Z`, `…043740Z`); empaquetado abortado por swap y espera de E/S (09-10); SSH casi inaccesible con unos 150 MiB libres | **No.** Es su peor tarea: con 3,7 GB Gradle + Kotlin necesitan casi toda la RAM y acaban en swap. Ya existen alternativas mejores: AXELPC (prioridad 1 en `AGENTS.md`) y **GitHub Actions** (`.github/workflows/build-apk.yml` ya compila la APK en cada push). |
| 2 | **Clave de firma** de las APK instaladas (`~/.android/debug.keystore`, certificado `8b75db0f…cd7127`) | `docs/HISTORIAL-NAVEGACION-199-20261016.md`; AXELPC tuvo que volver a firmar con la clave del ASUS | **No debería depender solo de él.** Si ese disco falla, el S25 y el Pixel no podrán actualizarse sin borrar datos. Algunos documentos dicen que hay una copia en Windows, pero esto no se ha verificado. Hay que guardar una copia cifrada y comprobada en AXELPC y otra sin conexión, nunca en Git. |
| 3 | **Sesión persistente de Google Flow**: Chromium en Xvfb, portal de recuperación noVNC (`nexus-flow-recovery.service`, `100.112.57.2:8899`), puente que usa la app Android (`bridge_status=connected`, `generation_ready=true` el 09-10) | `tools/flow-linux-probe/README.md`, `docs/ANDROID-STALLED-JOBS-20261009.md` | **Sí: es la función que mejor le encaja.** Pide pocos recursos si es lo único que hace (pico medido de 500 MiB a 1 GiB), necesita una máquina siempre encendida y ya está desplegada. |
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
3. **zram** (`zram-tools`, zstd, 50 % de la RAM) con `vm.swappiness` alto (100–180). En
   un equipo con poca RAM, el swap comprimido en memoria evita las esperas de E/S que
   se vieron, sobre todo si el disco es mecánico.
4. **Limitar el journal** (`SystemMaxUse=200M`) y la caché de disco de Chromium
   (`--disk-cache-size`).
5. **Batería** (si es un portátil que siempre está enchufado): fijar el umbral de carga en 60–80 % si
   el firmware lo admite (`/sys/class/power_supply/BAT*/charge_control_end_threshold`
   con `asus-wmi`) y configurar `HandleLidSwitch=ignore` para que no se suspenda al cerrar la tapa.
6. **Actualizaciones**: activar `unattended-upgrades` solo para seguridad, pero **retener
   Chromium** (`apt-mark hold`) y actualizarlo a mano junto a `gflow-cli`, porque una actualización
   sin control puede romper el puente.
7. **Hardware**: si `audit.sh` confirma que el disco es un HDD, cambiarlo por un SSD SATA barato es la
   mejora más notable. Ampliar la RAM a 8 GB, si tiene ranura libre, es la segunda.

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
