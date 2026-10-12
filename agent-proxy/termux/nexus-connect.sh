#!/data/data/com.termux/files/usr/bin/bash
# Install as ~/.shortcuts/tasks/Nexus - Conectar (native Termux, not Debian).
set -eu
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
umask 077

notice() {
    printf '%s\n' "$1"
    timeout 3 termux-toast -s "$1" >/dev/null 2>&1 || true
}

healthy() {
    curl --fail --silent --max-time 2 http://127.0.0.1:8787/health >/dev/null
}

# Double taps must not start two Debian/proxy processes. The server must not
# inherit this descriptor, otherwise the next button press would stay locked.
exec 9>"$HOME/.nexus-connect.lock"
if ! flock -n 9; then
    notice 'Nexus: la conexión ya se está preparando.'
    exit 0
fi

if ! pgrep -x sshd >/dev/null; then
    if ! sshd; then
        notice 'Nexus: no se pudo arrancar SSH. Abre Termux para revisarlo.'
        exit 1
    fi
fi

if healthy; then
    notice 'Nexus y AKI ya están conectados. SSH listo.'
    exit 0
fi

code=/root/.local/share/nexus-aki/code/aki_proxy.py
config=/root/.local/share/nexus-aki/config.json
log="$HOME/.nexus-sol.log"
notice 'Conectando Nexus con AKI…'
nohup proot-distro login debian -- python "$code" --config "$config" \
    >>"$log" 2>&1 < /dev/null 9>&- &
bridge_pid=$!

for ((attempt=0; attempt<20; attempt++)); do
    if healthy; then
        notice 'Nexus y AKI conectados. Ya puedes usar el agente.'
        exit 0
    fi
    if ! kill -0 "$bridge_pid" 2>/dev/null; then
        notice 'Nexus: el puente no arrancó. Revisa ~/.nexus-sol.log en Termux.'
        exit 1
    fi
    sleep 1
done

notice 'Nexus: el puente aún no responde. Revisa ~/.nexus-sol.log en Termux.'
exit 1
