#!/data/data/com.termux/files/usr/bin/bash
# Install as $PREFIX/bin/nexus-continuity-start. Keep all state in private Termux/Debian storage.
set -eu
folder="$HOME/.nexus-continuity"
mkdir -p "$folder"
if [ "${1:-}" = "--boot" ]; then
    proot-distro login debian -- /usr/bin/node -e '
      const fs=require("fs");
      try { const o=JSON.parse(fs.readFileSync("/root/.nexus-continuity/options.json"));
        process.exit(o.enabled && o.afterBoot ? 0 : 1); } catch { process.exit(1); }
    ' || exit 0
fi
if [ "${1:-}" = "--boot" ] && command -v nexus-agy-start >/dev/null 2>&1; then
    nexus-agy-start
fi
if [ -f "$folder/process.pid" ]; then
    pid="$(cat "$folder/process.pid")"
    if kill -0 "$pid" 2>/dev/null && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q '/root/.nexus-continuity/adapter.mjs'; then
        exit 0
    fi
fi
nohup proot-distro login debian -- /usr/bin/node /root/.nexus-continuity/adapter.mjs > "$folder/adapter.log" 2>&1 < /dev/null &
echo "$!" > "$folder/process.pid"
