#!/data/data/com.termux/files/usr/bin/bash
# One-time provisioning of the dedicated Pixel. Preserves Termux and existing SSH settings.
set -euo pipefail
ECO_TRANSFER=/sdcard/Download/ecosystem-build-setup
ECO_STATE="$HOME/.local/share/ecosystem-build"
mkdir -p "$ECO_STATE"
chmod 700 "$ECO_STATE"
exec > "$ECO_TRANSFER/setup.log" 2>&1
trap 'printf "%s\n" "$?" > "$ECO_TRANSFER/setup.exit"' EXIT
if ! command -v sshd >/dev/null || ! command -v python >/dev/null; then
  pkg install -y openssh python
fi
test -f "$ECO_TRANSFER/authorized-key.pub"
cp "$ECO_TRANSFER/authorized-key.pub" "$ECO_STATE/authorized_keys"
chmod 600 "$ECO_STATE/authorized_keys"
if [ ! -f "$ECO_STATE/host_ed25519" ]; then
  ssh-keygen -q -t ed25519 -N '' -f "$ECO_STATE/host_ed25519"
fi
cat > "$ECO_STATE/sshd_config" <<EOF
Port 8022
ListenAddress 127.0.0.1
HostKey $ECO_STATE/host_ed25519
AuthorizedKeysFile $ECO_STATE/authorized_keys
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitEmptyPasswords no
UsePAM no
PidFile $ECO_STATE/sshd.pid
PrintMotd no
LogLevel ERROR
Subsystem sftp internal-sftp
EOF
python - "$ECO_STATE" "$ECO_TRANSFER" <<'PY'
import json,os,pathlib,pwd,shutil,sys
root=pathlib.Path(sys.argv[1]); transfer=pathlib.Path(sys.argv[2])
identity={'model':'Pixel 8 Pro','physical_serial':'38271FDJG00264','origin':'verified ADB identity 2026-10-06'}
(root/'identity.json').write_text(json.dumps(identity))
info={'user':pwd.getpwuid(os.getuid()).pw_name,'python':shutil.which('python'),'state_root':str(root),'identity_file':str(root/'identity.json')}
(transfer/'setup-info.json').write_text(json.dumps(info))
PY
sshd -t -f "$ECO_STATE/sshd_config"
if [ -f "$ECO_STATE/sshd.pid" ] && kill -0 "$(cat "$ECO_STATE/sshd.pid")" 2>/dev/null; then
  echo 'Existing ecosystem SSH is running; preserved.'
else
  sshd -f "$ECO_STATE/sshd_config" -E "$ECO_STATE/sshd.log"
fi
cp "$ECO_STATE/host_ed25519.pub" "$ECO_TRANSFER/host-key.pub"
echo 'Background channel ready; listen address 127.0.0.1 only.'
