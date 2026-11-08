# Vigilante de «Flow en directo» en AXELPC. Lo lanza la tarea «Nexus-Flow-Live» al iniciar sesión y cada 2 minutos.
#  1. Se actualiza solo desde la rama feat/flow-live-20261108 (solo tools/flow-live) y reinicia el servicio si cambió.
#  2. Si el servicio no existe o no contesta dos veces seguidas, lo reinicia.
#  3. Si el Edge de Flow (CDP 9338) no contesta tres veces seguidas, lo relanza con el mismo perfil (misma sesión).
$ErrorActionPreference = 'SilentlyContinue'
$root = Join-Path $env:USERPROFILE 'nexus-flow-live'
$repo = Join-Path $env:USERPROFILE 'nexus-vc185'
$branch = 'feat/flow-live-20261108'
$python = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python312\pythonw.exe'
$state = Join-Path $root 'watchdog.json'
$log = Join-Path $root 'ensure.log'
function Note($m) { ('{0} {1}' -f (Get-Date).ToString('s'), $m) | Add-Content $log }
$s = @{ health = 0; cdp = 0 }
if (Test-Path $state) { try { $j = Get-Content $state -Raw | ConvertFrom-Json; $s.health = [int]$j.health; $s.cdp = [int]$j.cdp } catch {} }

function Stop-Service { Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' OR Name='python.exe'" |
    Where-Object { $_.CommandLine -match 'flow_live\.py' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }
function Start-Service { Start-Process -FilePath $python -ArgumentList (Join-Path $root 'flow_live.py') -WorkingDirectory $root -WindowStyle Hidden }

# 1) Actualización
$restart = $false
if (Test-Path (Join-Path $repo '.git')) {
    git -C $repo fetch -q origin $branch 2>$null
    $local = git -C $repo rev-parse HEAD 2>$null
    $remote = git -C $repo rev-parse FETCH_HEAD 2>$null
    if ($remote -and $local -ne $remote) {
        git -C $repo merge -q --ff-only FETCH_HEAD 2>$null
        if ((git -C $repo rev-parse HEAD) -eq $remote) {
            Copy-Item (Join-Path $repo 'tools\flow-live\flow_live.py') $root -Force
            Copy-Item (Join-Path $repo 'tools\flow-live\windows\*.ps1') $root -Force
            Note "actualizado a $($remote.Substring(0,7))"
            $restart = $true
        }
    }
}

# 2) Servicio
$alive = Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' OR Name='python.exe'" | Where-Object { $_.CommandLine -match 'flow_live\.py' }
$ok = $false
try { $ok = [bool](Invoke-RestMethod -Uri 'http://100.72.111.97:8797/health' -TimeoutSec 15) } catch {}
if ($ok) { $s.health = 0 } else { $s.health++ }
if ($restart -or -not $alive -or $s.health -ge 2) {
    Stop-Service; Start-Sleep -Seconds 2; Start-Service
    Note ("servicio (re)arrancado: actualizacion={0} vivo={1} fallos={2}" -f $restart, [bool]$alive, $s.health)
    $s.health = 0
}

# 3) Edge de Flow
$cdp = $false
try { $cdp = [bool](Invoke-RestMethod -Uri 'http://127.0.0.1:9338/json/version' -TimeoutSec 5).webSocketDebuggerUrl } catch {}
if ($cdp) { $s.cdp = 0 } else { $s.cdp++ }
if ($s.cdp -ge 3) {
    Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*ChatGPT-Flow*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 3
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $env:USERPROFILE 'flow-chatgpt-bridge\ensure-browser.ps1') | Out-Null
    Note 'Edge de Flow colgado: relanzado con el mismo perfil'
    $s.cdp = 0
}
$s | ConvertTo-Json -Compress | Set-Content $state
