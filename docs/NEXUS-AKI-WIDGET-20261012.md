# Botón de Termux para Nexus y AKI

El S25 usa Termux `googleplay.2026.06.21`, que ya integra Termux:Widget.
Se conserva esta instalación y el entorno Debian existente.

El botón **Nexus - Conectar** ejecuta
`~/.shortcuts/tasks/Nexus - Conectar` en segundo plano:

- Arranca SSH si está detenido.
- Comprueba el puente local en `127.0.0.1:8787/health`.
- Si falta, arranca el servidor existente de `/root/.local/share/nexus-aki/`.
- Comprueba el resultado y muestra un mensaje breve.
- Evita duplicados por pulsaciones repetidas mediante `flock`.

La fuente está en `agent-proxy/termux/nexus-connect.sh`. No contiene cuentas,
claves ni tokens. No cambia la sesión de AKI, los modelos ni los datos de Nexus.
El registro del servidor sigue en `~/.nexus-sol.log`.

Se añadió y verificó el acceso directo fijado de Termux en la pantalla de inicio
de One UI. La etiqueta de esta versión de Termux incluye el directorio:
**tasks/Nexus - Conectar**. También está disponible para el widget de lista de
Termux. El acceso directo puede mostrar una sesión de Termux durante el arranque;
el servidor queda separado de esa sesión.

El túnel del EliteBook se abre al enviar un mensaje desde Nexus Desktop.
Para acceder desde el PC, Tailscale debe estar conectado en ambos dispositivos.
Este botón comprueba el servidor; una sesión AKI caducada requiere su inicio de
sesión habitual. No se afirma que Android mantenga el proceso indefinidamente.

## Validación en el S25

- `bash -n` aprobado sobre el script instalado.
- Arranque con el puente previamente detenido e inactivo: se pulsó el botón
  real en One UI y `/health` volvió a responder `{"ok": true}`.
- Dos ejecuciones posteriores conservaron un solo proceso del puente.
- SSH siguió disponible; no se interrumpió su servicio para probar el caso frío.
- El túnel existente del EliteBook en `127.0.0.1:18787/health` volvió a responder.
- El acceso consta como fijado en `dumpsys shortcut` y visible en el launcher.
- Se mantuvieron la APK, los datos, las claves, la sesión AKI y los lanzadores
  anteriores. No se realizaron generaciones de pago ni reinicios del móvil.

## Referencias

- [Termux para Google Play](https://github.com/termux-play-store): widgets
  integrados, sin complemento adicional.
- [Directorios de Termux:Widget](https://github.com/termux/termux-widget#script-directories-mandatory):
  `~/.shortcuts/tasks` contiene tareas en segundo plano.
