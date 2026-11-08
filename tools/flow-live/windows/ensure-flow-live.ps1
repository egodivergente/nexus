# Mantiene vivo «Flow en directo» en AXELPC. Lo lanza la tarea «Nexus-Flow-Live» al iniciar sesión y cada 2 minutos.
# Si el servicio ya responde no hace nada; si no, lo arranca oculto con el Python del usuario.
$ErrorActionPreference = 'SilentlyContinue'
$root = Join-Path $env:USERPROFILE 'nexus-flow-live'
$python = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python312\pythonw.exe'
$running = Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' OR Name='python.exe'" |
    Where-Object { $_.CommandLine -match 'flow_live\.py' } | Select-Object -First 1
if ($running) { exit 0 }
Start-Process -FilePath $python -ArgumentList (Join-Path $root 'flow_live.py') -WorkingDirectory $root -WindowStyle Hidden
('{0} START' -f (Get-Date).ToString('o')) | Add-Content (Join-Path $root 'ensure.log')
