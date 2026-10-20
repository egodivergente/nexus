#!/bin/sh
# Despliegue manual en el ASUS (Linux, puente de respaldo). NO lo ejecuta ningún agente.
# Solo copia archivos del vigilante y recarga systemd de usuario; no toca el perfil, cookies ni el token.
set -eu
here=$(cd "$(dirname "$0")/.." && pwd)
dest="$HOME/.local/share/nexus-flow-watchdog"
units="$HOME/.config/systemd/user"
mkdir -p "$dest" "$units/nexus-flow-recovery.service.d" "$HOME/.local/state/nexus-flow-watchdog"
install -m 0644 "$here/flow_watchdog.py" "$dest/flow_watchdog.py"
install -m 0644 "$here/linux/nexus-flow-watchdog.service" "$units/nexus-flow-watchdog.service"
install -m 0644 "$here/linux/nexus-flow-recovery.service.d-priority.conf" "$units/nexus-flow-recovery.service.d/priority.conf"
systemctl --user daemon-reload
python3 "$dest/flow_watchdog.py" --once --url http://100.112.57.2:8899/ || echo "Aviso: el puente no responde ahora; el vigilante lo reiniciará."
systemctl --user enable --now nexus-flow-watchdog.service
echo "Para arrancar sin sesión abierta (opcional): loginctl enable-linger \"$USER\""
