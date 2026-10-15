# AKI: recuperación del puente y widget

La captura mostraba `Failed to fetch`. En el S25 SM-S938B se confirmó
que el puerto 8765 no respondía, mientras que el proxy Nexus 8787 sí
respondía desde localhost. El widget `Arrancar Aki` invocaba rutas
`/root/aki/scripts/` directamente desde Termux, aunque están en Debian.

Se arrancó el puente dentro de `proot-distro login debian` y se sustituyó
el widget por `tools/aki/Arrancar-Aki.sh`. El original se conserva en
`~/.local/state/aki/Arrancar-Aki.backup-20261016` del teléfono.
El lanzador obtiene wake-lock, comprueba HTTP, arranca el puente cuando
no responde y ejecuta el watchdog existente dentro de Debian.

Verificación: `bash -n` correcto; ejecución real del widget correcta;
`GET /health` accesible por Tailscale en 8765. No se cambiaron claves,
modelos ni datos de Nexus y no se reinstaló ninguna APK.

Una petición real a `/chat`, modelo `Sonnet 5`, devolvió
`Midway session expired. Run mwinit -o to refresh.` Por tanto, la
conectividad está recuperada pero la respuesta del modelo requiere
renovar la sesión Midway. El campo `midway_authenticated: true` del
healthcheck no es evidencia de aceptación del backend. No se ejecutaron
generaciones multimedia.
