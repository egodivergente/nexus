#!/bin/bash
# One-time provisioning inside the Pixel's native Linux VM; existing data and services stay intact.
set -euo pipefail
ECO_TRANSFER=/mnt/shared/Download/ecosystem-build-setup
ECO_STATE="$HOME/.local/share/ecosystem-build"
test -f "$ECO_TRANSFER/authorized-key.pub"
exec > "$ECO_TRANSFER/setup-linux.log" 2>&1
trap 'printf "%s\n" "$?" > "$ECO_TRANSFER/setup-linux.exit"' EXIT
sudo -n true
if [ ! -x /usr/sbin/sshd ] || ! command -v python3 >/dev/null; then
  sudo env DEBIAN_FRONTEND=noninteractive apt-get update -qq
  sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openssh-server python3
fi
mkdir -p "$ECO_STATE"
chmod 700 "$ECO_STATE"
cp "$ECO_TRANSFER/authorized-key.pub" "$ECO_STATE/authorized_keys"
chmod 600 "$ECO_STATE/authorized_keys"
if [ ! -f "$ECO_STATE/host_ed25519" ]; then
  ssh-keygen -q -t ed25519 -N '' -f "$ECO_STATE/host_ed25519"
fi
cat > "$ECO_STATE/sshd_config" <<EOF
Port 2222
ListenAddress 0.0.0.0
HostKey $ECO_STATE/host_ed25519
AuthorizedKeysFile $ECO_STATE/authorized_keys
AllowUsers $(id -un)
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PermitEmptyPasswords no
UsePAM no
PidFile $ECO_STATE/sshd.pid
PrintMotd no
LogLevel ERROR
Subsystem sftp internal-sftp
EOF
python3 - "$ECO_STATE" "$ECO_TRANSFER" <<'PY'
import json,os,pathlib,pwd,shutil,sys,platform
root=pathlib.Path(sys.argv[1]); transfer=pathlib.Path(sys.argv[2])
identity={'model':'Pixel 8 Pro','physical_serial':'38271FDJG00264','origin':'verified ADB identity 2026-10-06, native Linux VM'}
(root/'identity.json').write_text(json.dumps(identity))
backup=pathlib.Path('/mnt/backup')
info={'user':pwd.getpwuid(os.getuid()).pw_name,'python':shutil.which('python3'),'state_root':str(root),
      'identity_file':str(root/'identity.json'),'os':platform.system(),'arch':platform.machine(),
      'backup_exists':backup.is_dir(),'backup_root_entries':len(list(backup.iterdir())) if backup.is_dir() else None,
      'backup_mount':next((line for line in pathlib.Path('/proc/mounts').read_text().splitlines() if ' /mnt/backup ' in line),None)}
(transfer/'setup-linux-info.json').write_text(json.dumps(info,indent=2))
PY
sudo mkdir -p /run/sshd
sudo /usr/sbin/sshd -t -f "$ECO_STATE/sshd_config"
if [ -f "$ECO_STATE/sshd.pid" ] && sudo kill -0 "$(cat "$ECO_STATE/sshd.pid")" 2>/dev/null; then
  echo 'Existing ecosystem SSH is running; preserved.'
else
  sudo /usr/sbin/sshd -f "$ECO_STATE/sshd_config" -E "$ECO_STATE/sshd.log"
fi
cp "$ECO_STATE/host_ed25519.pub" "$ECO_TRANSFER/linux-host-key.pub"
echo 'Native Linux executor ready. Forward guest port 2222 through Terminal settings.'
