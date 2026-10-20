# Vigilante del puente privado de Flow

## Dónde corre el puente (leído del código, no verificado en vivo)

| Equipo | Papel | Dirección | Arranque actual |
|---|---|---|---|
| AXELPC (Windows) | Principal | `http://100.72.111.97:8899/api/flow` (MagicDNS `axelpc.tail6ac6b4.ts.net`) | `tools/flow-linux-probe/windows/start-flow-bridge.ps1`: Edge con perfil propio y CDP en `127.0.0.1:9229` y `pythonw recovery.py` |
| ASUS (Linux) | Respaldo e importación de proyectos | `http://100.112.57.2:8899/api/flow` (MagicDNS `asusvidal.tail6ac6b4.ts.net`) | unidad systemd de usuario `tools/flow-linux-probe/nexus-flow-recovery.service` (`~/.local/share/nexus-flow-probe`) |

- Servidor: `tools/flow-linux-probe/recovery.py` (FastAPI + uvicorn, Playwright sobre el navegador con la sesión de Google). Puerto `NEXUS_FLOW_PORT` (8899), host `NEXUS_FLOW_HOST`.
- Seguridad: el middleware solo acepta la cabecera `Host` con la IP o el nombre MagicDNS del equipo, y exige `Bearer` (token en `<NEXUS_FLOW_ROOT>/recovery-token`) en todo salvo `/` y `/unlock`.
- Salud: `GET /` es público, así que indica si el proceso vive. `GET /api/flow/status` (con token) devuelve `bridge_status`, `generation_ready`, `reason`.
- App: `FlowBridgeClient` prueba AXELPC con 15 s de margen. Si no tiene `generation_ready`, usa el ASUS y fija la elección durante 10 min. Cada trabajo lleva el prefijo `a.`/`s.` para que su seguimiento y su descarga vuelvan al mismo puente.

## Qué hace el vigilante (`flow_watchdog.py`, solo biblioteca estándar)

- Cada 20 s comprueba `GET /` con un tiempo límite de 8 s.
- Tras 3 fallos seguidos lanza la orden de reinicio. La espera entre reinicios crece así: 15 s → 30 → 60 → … → 600 s. Vuelve a cero tras 5 min de puente sano.
- Si se le pasa el token (solo lectura), anota en el log los cambios de estado de Flow. Un `auth_required` o `flow_project_not_open` **no** provoca reinicio, porque reiniciar no repara la sesión.
- El log rota con `RotatingFileHandler` (1 MB × 5). El token nunca aparece en el log.
- Nunca borra ni modifica credenciales, cookies, perfiles ni el token, y no desvincula cuentas. En AXELPC solo reinicia el `python` de `recovery.py`; Edge sigue abierto.
- Prioridad: el vigilante corre con prioridad baja (`Nice=15`, `CPUWeight=20`, tarea con prioridad 7). El puente sube su prioridad (`Nice=0`, `CPUWeight/IOWeight=1000`, `OOMScoreAdjust=-100`; en Windows `AboveNormal`).
- `--once` hace una sola comprobación (código 0 si el puente responde). `--dry-run` registra el reinicio sin ejecutarlo.

## Despliegue (manual; aquí no se ha instalado ni ejecutado nada)

**ASUS (Linux, respaldo):** copia `tools/flow-bridge/watchdog/` al ASUS y ejecuta `sh linux/install-watchdog.sh`. El script:
1. copia `flow_watchdog.py` a `~/.local/share/nexus-flow-watchdog/`;
2. instala `nexus-flow-watchdog.service` y el drop-in `nexus-flow-recovery.service.d/priority.conf`;
3. ejecuta `daemon-reload`, una prueba `--once` y `enable --now`.

Si quieres que arranque sin sesión abierta: `loginctl enable-linger $USER`. Para ver el log: `~/.local/state/nexus-flow-watchdog/watchdog.log`. Para retirarlo: `systemctl --user disable --now nexus-flow-watchdog.service` y borra solo los dos archivos de unidad.

**AXELPC (Windows, principal):** desde una copia del repo, ejecuta `powershell -ExecutionPolicy Bypass -File tools\flow-bridge\watchdog\windows\register-watchdog-task.ps1`. Crea la tarea de usuario «NEXUS Flow Watchdog»: arranca al iniciar sesión con 1 min de retraso, se relanza si cae y usa `restart-flow-bridge.ps1`. Para probarla: `Start-ScheduledTask "NEXUS Flow Watchdog"`. Log: `%LOCALAPPDATA%\NexusFlow\watchdog\watchdog.log`.

Pruebas: `python -m unittest test_flow_watchdog` (5 pruebas, sin red ni procesos).

## Mejoras más eficientes del puente (por impacto/esfuerzo)

1. **`/healthz` público y barato en `recovery.py`.** Que devuelva `{"alive":true,"browser":bool}` sin abrir Flow. Ahora el vigilante solo puede usar `/`, y `/api/flow/status` tarda 1-3 s porque abre CDP.
2. **Que AXELPC arranque con una unidad o tarea en vez de `Start-Process`.** El script actual no relanza `recovery.py` si se cuelga con el puerto abierto. El vigilante cubre ese caso.
3. **Persistir `FLOW_JOBS` antes de responder `201`.** Así, tras un reinicio, `GET /jobs/{id}` no devolvería 404 y la app no daría el trabajo por perdido. Ya existe una persistencia atómica de metadatos opacos: conviene garantizar que se escribe antes de responder.
4. **Exponer capacidades en `/api/flow/status`:** formatos/resoluciones de descarga por trabajo y una operación `extend`. Hoy `/jobs/{id}/downloads/{n}` solo entrega el original. `?size=` solo existe en la importación de proyectos (`DOWNLOAD_SIZES`). Flow Studio deja la conversión y «Extender vídeo» desactivados hasta que el puente los anuncie.
5. **Un solo envío por lote con «xN» nativo.** Con `runs` y `expected_results`, Flow puede generar 4 resultados de una vez en lugar de 4 envíos, lo que reduce el tiempo de UI y el riesgo de `submit_uncertain`.
6. **Reintento con fluctuación en la app** (`FlowRetryPolicy`) para que AXELPC y el ASUS no reciban picos simultáneos después de un corte de Tailscale.
7. **Avisar de una sesión de Google caducada sin reiniciar.** El vigilante ya registra `auth_required`. El siguiente paso sería enviar ese aviso a NEXUS, nunca intentar volver a iniciar sesión automáticamente.
