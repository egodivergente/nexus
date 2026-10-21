# Puente de Google Flow en AXELPC (principal). El ASUS queda como respaldo cuando este PC está apagado.
# 1) Abre un Edge con un perfil propio y depuración local en 127.0.0.1:9229 (solo loopback): ahí inicias sesión
#    en Google una vez y dejas abierto UN proyecto de Flow.
# 2) Arranca el servidor del puente (recovery.py) escuchando en la IP de Tailscale de AXELPC, puerto 8899.
# No guarda contraseñas ni cookies fuera del perfil de Edge; el token del puente vive en NEXUS_FLOW_ROOT.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $PSScriptRoot
$profileDir = Join-Path $env:LOCALAPPDATA 'NexusFlow\edge-profile'
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
# python.exe (no pythonw): sin consola, uvicorn se cierra al no tener stderr. La ventana va oculta y el log a un archivo.
$python = 'C:\Users\User\AppData\Local\Programs\Python\Python312\python.exe'

function Test-Port($port, $address = '127.0.0.1') {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect($address, $port); $c.Close(); $true } catch { $false }
}

if (-not (Test-Port 9229)) {
    New-Item -ItemType Directory -Force $profileDir | Out-Null
    Start-Process $edge -ArgumentList @(
        "--user-data-dir=`"$profileDir`"", '--remote-debugging-port=9229', '--remote-debugging-address=127.0.0.1',
        '--no-first-run', '--no-default-browser-check', 'https://flow.google.com/')
}

if (-not (Test-Port 8899 '100.72.111.97')) {
    $env:NEXUS_FLOW_HOST = '100.72.111.97'
    $env:NEXUS_FLOW_MAGICDNS_HOST = 'axelpc.tail6ac6b4.ts.net'
    $env:NEXUS_FLOW_PORT = '8899'
    Start-Process $python -ArgumentList "`"$(Join-Path $here 'recovery.py')`"" -WorkingDirectory $here -WindowStyle Hidden -RedirectStandardError (Join-Path $env:LOCALAPPDATA 'NexusFlow\bridge.log')
}
