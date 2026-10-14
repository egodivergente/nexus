"""Install the verified portable build and replace only the existing Nexus host."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time,urllib.request,zipfile
root=Path(__file__).resolve().parents[2]
data=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop'
session=json.loads((data/'local-session.json').read_text('utf-8')) if (data/'local-session.json').is_file() else {}
pid=int(session.get('pid') or 0);port=int(session.get('port') or 63896)
if pid > 0:
    command=subprocess.check_output(['powershell','-NoProfile','-Command',f'$p = Get-CimInstance Win32_Process -Filter "ProcessId = {pid}" -ErrorAction SilentlyContinue; if ($p) {{ $p.CommandLine }}'],text=True)
    if command.strip() and 'local_host.py' not in command and 'NexusStudio' not in command:
        raise ValueError('The session PID is not the expected Nexus desktop host.')
package=root/'entregas/Nexus-Studio-Agent-1.9.4-Windows.zip'
digest=hashlib.sha256(package.read_bytes()).hexdigest()
install=Path(os.environ['LOCALAPPDATA'])/'Programs/NexusStudio'/('1.9.4-'+digest[:8])
install.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(package) as archive:
    assert archive.testzip() is None
    for member in archive.infolist():
        target=(install/member.filename).resolve()
        if not target.is_relative_to(install.resolve()):raise ValueError('Invalid package path.')
    archive.extractall(install)
desktop=install/'Nexus-Studio/desktop'
assert (desktop/'agent/runtime/nexus-desktop-agent.jar').is_file()
if pid > 0:
    subprocess.run(['powershell','-NoProfile','-Command',f'Stop-Process -Id {pid} -Force -ErrorAction SilentlyContinue'],check=False)
# Update code only: preserve the existing desktop profile, settings and credentials.
shortcut='C:/Users/User/Documents/Biblioteca/Aplicaciones/Nexus Studio.lnk'
environment=os.environ.copy()
environment['PSModulePath']='C:/Windows/System32/WindowsPowerShell/v1.0/Modules'
subprocess.run(['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-File',str(desktop/'install-local.ps1'),'-NoLaunch','-AdditionalShortcut',shortcut,'-Port',str(port)],check=True,env=environment)
pythonw=Path(sys.executable).with_name('pythonw.exe')
cmd_line=f'"{pythonw}" "{desktop / "local_host.py"}" --port {port}'
wmi_cmd=(
    f'$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create '
    f'-Arguments @{{ CommandLine = \'{cmd_line}\'; CurrentDirectory = \'{desktop}\' }}; '
    f'if ($r.ReturnValue -ne 0) {{ throw "Win32_Process.Create failed: $($r.ReturnValue)" }}; '
    f'$r.ProcessId'
)
spawned_pid=int(subprocess.check_output(['powershell','-NoProfile','-Command',wmi_cmd],text=True).strip().splitlines()[-1])
for _ in range(40):
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/health',timeout=.5) as response:
            if json.load(response)['app']=='Nexus Studio':break
    except (OSError,ValueError):time.sleep(.25)
else:raise RuntimeError('The installed host did not start. Backup and previous source remain available.')
live_session=json.loads((data/'local-session.json').read_text('utf-8'))
report={'installed':True,'directory':str(desktop),'pid':int(live_session.get('pid',spawned_pid)),'port':int(live_session.get('port',port)),'packageSha256':digest,'version':live_session.get('version','1.9.4-agent-desktop')}
(root/'outputs/desktop-agent-parity/deployment.json').write_text(json.dumps(report),encoding='utf-8')
print(json.dumps(report))


