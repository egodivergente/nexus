#!/usr/bin/env bash
# Auditoría de salud de ASUSVIDAL. Solo lectura: no usa sudo, no reinicia
# servicios ni borra nada. Imprime un informe de texto por stdout.
# Uso: ssh vidal@100.112.57.2 'bash -s' < tools/asus-health/audit.sh > asus-audit.txt
# Guardar el informe fuera de Git: puede contener rutas y nombres de procesos.
set -u
LC_ALL=C
export LC_ALL
# swapon, smartctl, etc. viven en sbin y no están en el PATH de un usuario normal.
PATH="$PATH:/usr/sbin:/sbin"

section() { printf '\n===== %s =====\n' "$1"; }
run() { command -v "${1%% *}" >/dev/null 2>&1 && eval "$1" 2>&1 || echo "(no disponible: ${1%% *})"; }

section "Identidad"
hostname; date -Is; uptime
grep -E '^(PRETTY_NAME|VERSION_ID)=' /etc/os-release; uname -r
run "cat /sys/class/dmi/id/sys_vendor /sys/class/dmi/id/product_name /sys/class/dmi/id/bios_version"

section "CPU"
run "lscpu | grep -E 'Model name|^CPU\\(s\\)|Thread|max MHz|min MHz'"
run "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
cat /proc/loadavg
run "cat /proc/pressure/cpu"

section "Memoria"
free -m
swapon --show
run "cat /sys/block/zram0/disksize /sys/block/zram0/comp_algorithm"
cat /proc/sys/vm/swappiness
run "cat /proc/pressure/memory"
run "cat /proc/pressure/io"
grep -E 'MemAvailable|Committed_AS|CommitLimit' /proc/meminfo
echo "-- OOM recientes (7 dias):"
run "journalctl --user -q --since '-7d' | grep -ci 'oom\\|out of memory'"
run "journalctl -k -q --since '-7d' 2>/dev/null | grep -i 'out of memory\\|oom-kill' | tail -5"

section "Procesos por RSS (top 15)"
ps -eo pid,user,rss,pcpu,etime,comm --sort=-rss | head -16
echo "-- Linea de comandos de los 5 mayores:"
ps -eo pid,user,args --sort=-rss | head -6 | cut -c1-220

section "Procesos por CPU (top 10)"
ps -eo pid,user,pcpu,rss,etime,comm --sort=-pcpu | head -11

section "Java / Gradle / Kotlin residentes"
ps -eo pid,rss,etime,args --sort=-rss | grep -E '[G]radleDaemon|[K]otlinCompileDaemon|[j]ava ' | cut -c1-200 | head -10

section "Chromium / Flow"
ps -eo pid,rss,etime,args --sort=-rss | grep -E '[c]hrom|[X]vfb|[x]11vnc|[w]ebsockify|recovery.py|flow' | cut -c1-200 | head -20
echo "-- RSS total Chromium (MiB):"
ps -eo rss,comm | awk '/chrom/ {s+=$1} END {printf "%.0f\n", s/1024}'

section "Servicios systemd (sistema, activos)"
run "systemctl list-units --type=service --state=running --no-pager --no-legend | awk '{print \$1}'"
section "Servicios systemd (usuario)"
run "systemctl --user list-units --type=service --all --no-pager --no-legend"
run "systemctl --user list-timers --all --no-pager"
run "systemctl list-timers --no-pager"
section "Servicios fallidos"
run "systemctl --failed --no-pager --no-legend"
run "systemctl --user --failed --no-pager --no-legend"
section "Arranque"
run "systemd-analyze"
run "systemd-analyze blame --no-pager | head -12"
run "systemctl get-default"

section "Cron"
crontab -l 2>/dev/null || echo "(sin crontab de usuario)"
ls /etc/cron.d 2>/dev/null

section "Puertos en escucha"
run "ss -tulpnH"

section "Red / Tailscale"
run "tailscale status --self=true --peers=false"
run "ip -br addr"

section "Discos"
df -hT -x tmpfs -x devtmpfs -x overlay -x squashfs
lsblk -o NAME,SIZE,ROTA,TRAN,MODEL,TYPE,MOUNTPOINT
run "cat /sys/block/*/queue/scheduler"
echo "-- SMART (sin sudo suele fallar; ejecutar aparte con sudo smartctl -H -A /dev/sdX):"
run "smartctl -H /dev/sda"

section "Espacio por directorio en HOME (top 15)"
du -xsh "$HOME"/.[!.]* "$HOME"/* 2>/dev/null | sort -rh | head -15
echo "-- Cachés y builds conocidos:"
for d in "$HOME/.gradle" "$HOME/.gradle/caches" "$HOME/.gradle/daemon" "$HOME/builds" \
         "$HOME/.cache" "$HOME/.cache/ms-playwright" "$HOME/.local/share/nexus-flow-probe" \
         "$HOME/.android" "$HOME/aki" "$HOME/Android" "$HOME/.m2" "$HOME/.npm"; do
  [ -e "$d" ] && du -xsh "$d" 2>/dev/null
done
run "journalctl --disk-usage"

section "Temperatura y energía"
run "sensors"
for z in /sys/class/thermal/thermal_zone*/temp; do [ -r "$z" ] && echo "$z $(cat "$z")"; done
run "cat /sys/class/power_supply/BAT*/capacity /sys/class/power_supply/BAT*/status /sys/class/power_supply/BAT*/energy_full /sys/class/power_supply/BAT*/energy_full_design"
run "cat /sys/module/pcie_aspm/parameters/policy"
run "systemctl is-active tlp power-profiles-daemon"

section "Escritorio / gráfico"
run "systemctl is-active gdm gdm3 lightdm sddm display-manager"
echo "XDG_SESSION_TYPE=${XDG_SESSION_TYPE:-}"
run "loginctl list-sessions --no-legend"
run "grep -E 'HandleLidSwitch' /etc/systemd/logind.conf"

section "Errores recientes (prioridad err, 24 h, últimos 25)"
run "journalctl -p err -q --since '-24h' --no-pager | tail -25"

section "Paquetes"
run "apt list --upgradable 2>/dev/null | wc -l"
run "dpkg-query -W -f='\${Installed-Size}\t\${Package}\n' | sort -rn | head -15"
run "snap list"
run "flatpak list --app"

section "Fin"
date -Is
