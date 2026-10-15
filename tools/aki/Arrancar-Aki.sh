#!/data/data/com.termux/files/usr/bin/bash
# Termux:Widget launcher. Debian paths must execute inside proot, not Termux.
set -u
log_dir="$HOME/.local/state/aki"
mkdir -p "$log_dir"
termux-wake-lock 2>/dev/null || true
health() { curl -fsS --max-time 3 http://127.0.0.1:8765/health >/dev/null 2>&1; }
if ! health; then
    echo "Arrancando el puente de Aki..."
    nohup proot-distro login debian -- python3 /root/aki/scripts/axel_bridge.py \
        >> "$log_dir/bridge.log" 2>&1 < /dev/null &
fi
# The existing watchdog is a singleton and only recovers a failing bridge.
nohup proot-distro login debian -- bash /root/aki/scripts/s25_watchdog.sh \
    >> "$log_dir/watchdog.log" 2>&1 < /dev/null &
for attempt in $(seq 1 15); do
    if health; then
        echo "Aki conectado. Puedes volver al chat."
        exit 0
    fi
    sleep 1
done
echo "No se ha podido arrancar Aki. Revisa $log_dir/bridge.log"
exit 1
