# Reinicio del puente en AXELPC (Windows, puente principal). Lo invoca el vigilante; no lo ejecuta ningún agente.
# Solo detiene el python que ejecuta recovery.py; Edge y su perfil (sesión de Google) no se tocan.
$ErrorActionPreference = 'Stop'
$start = Join-Path $PSScriptRoot '..\..\..\flow-linux-probe\windows\start-flow-bridge.ps1'
Get-CimInstance Win32_Process -Filter "Name='python.exe' OR Name='pythonw.exe'" |
    Where-Object { $_.CommandLine -match 'recovery\.py' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep -Seconds 2
& $start
Start-Sleep -Seconds 3
Get-CimInstance Win32_Process -Filter "Name='pythonw.exe'" |
    Where-Object { $_.CommandLine -match 'recovery\.py' } |
    ForEach-Object { (Get-Process -Id $_.ProcessId).PriorityClass = 'AboveNormal' }
