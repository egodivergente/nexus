#!/data/data/com.termux/files/usr/bin/bash
# Run from native Termux. Keeps a separate Debian process alive, not the Codex shell.
set -eu
if curl --fail --silent --max-time 2 http://127.0.0.1:8787/health >/dev/null; then
    echo 'Nexus Sol ya está activo.'
    exit 0
fi
exec proot-distro login debian -- python /root/.local/share/nexus-aki/code/aki_proxy.py --config /root/.local/share/nexus-aki/config.json
