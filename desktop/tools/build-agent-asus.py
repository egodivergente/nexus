"""Build a private, source-only desktop-agent snapshot on ASUS with one worker."""
from pathlib import Path
import argparse
import json
import shlex
import subprocess
import tarfile
import time
import paramiko

parser = argparse.ArgumentParser()
parser.add_argument('--retrieve', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
output = root / 'outputs/desktop-agent-parity'
output.mkdir(parents=True, exist_ok=True)
record = output / 'asus-build.json'
client = paramiko.SSHClient()
client.load_system_host_keys()
client.load_host_keys(str(Path.home() / '.ssh/known_hosts'))
client.set_missing_host_key_policy(paramiko.RejectPolicy())
client.connect('100.112.57.2', username='vidal', key_filename=str(Path.home() / '.ssh/id_ed25519'), timeout=20)
sftp = client.open_sftp()
if not args.retrieve:
    remote = '/home/vidal/builds/nexus-desktop-parity-'+str(int(time.time()))
    archive = output / 'agent-source.tar.gz'
    with tarfile.open(archive, 'w:gz') as bundle:
        for file in (root / 'desktop/agent').rglob('*'):
            if file.is_file() and file.suffix in {'.kt', '.kts', '.json', '.md', '.yaml', '.yml'} and not set(file.relative_to(root / 'desktop/agent').parts) & {'build', 'runtime', '.gradle'}:
                bundle.add(file, arcname=str(file.relative_to(root / 'desktop/agent')))
    _, stdout, stderr = client.exec_command('mkdir -p '+shlex.quote(remote))
    if stdout.channel.recv_exit_status():
        raise RuntimeError(stderr.read().decode())
    sftp.put(str(archive), remote+'/source.tar.gz')
    gradle = '/home/vidal/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle'
    shell = f'cd {shlex.quote(remote)} && tar xzf source.tar.gz && export JAVA_HOME=/home/vidal/aki/toolchain/jdk17 && export PATH="$JAVA_HOME/bin:$PATH" && nice -n 15 {shlex.quote(gradle)} runtime test --no-daemon --max-workers=1 --no-parallel -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=384m" -Pkotlin.compiler.execution.strategy=in-process > build.log 2>&1; result=$?; printf "%s" "$result" > exit-code'
    launch = 'nohup sh -c '+shlex.quote(shell)+' </dev/null >/dev/null 2>&1 &'
    _, stdout, stderr = client.exec_command(launch)
    stdout.channel.recv_exit_status()
    record.write_text(json.dumps({'remote': remote, 'source': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()}), encoding='utf-8')
    print(json.dumps({'started': remote}))
else:
    remote = json.loads(record.read_text('utf-8'))['remote']
    try:
        with sftp.open(remote+'/exit-code') as stream:
            code = stream.read().decode().strip()
    except IOError:
        code = None
    sftp.get(remote+'/build.log', str(output/'asus-build.log'))
    print((output/'asus-build.log').read_text('utf-8')[-7000:])
    if code == '0':
        runtime = root / 'desktop/agent/runtime'
        runtime.mkdir(exist_ok=True)
        for name in sftp.listdir(remote+'/build/runtime'):
            if name.endswith('.jar'):
                sftp.get(remote+'/build/runtime/'+name, str(runtime/name))
        for relative in ['build/reports/tests/test', 'core/build/reports/tests/test']:
            def retrieve(remote_dir, local):
                local.mkdir(parents=True, exist_ok=True)
                for entry in sftp.listdir_attr(remote_dir):
                    import stat
                    if stat.S_ISDIR(entry.st_mode):
                        retrieve(remote_dir+'/'+entry.filename, local/entry.filename)
                    else:
                        sftp.get(remote_dir+'/'+entry.filename, str(local/entry.filename))
            retrieve(remote+'/'+relative, output/relative)
        print('PASS: runtime and JVM test reports retrieved')
    else:
        print('Build running' if code is None else 'FAIL: exit '+code)
sftp.close()
client.close()
