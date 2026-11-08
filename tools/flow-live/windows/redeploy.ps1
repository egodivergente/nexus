# Actualiza «Flow en directo» en AXELPC: trae la rama, copia el servicio y deja UNA copia con la versión nueva.
$ErrorActionPreference = 'Continue'
$root = 'C:\Users\User\nexus-flow-live'
$py = 'C:\Users\User\AppData\Local\Programs\Python\Python312\pythonw.exe'
cmd /c "cd /d C:\Users\User\nexus-vc185 && git pull -q --ff-only origin feat/flow-live-20261108 && git log --oneline -1 && copy /y tools\flow-live\flow_live.py $root\flow_live.py >nul && copy /y tools\flow-live\windows\*.ps1 $root\ >nul"
$want = (Select-String -Path "$root\flow_live.py" -Pattern '^VERSION = "([^"]+)"').Matches[0].Groups[1].Value
for ($i = 0; $i -lt 3; $i++) {
    Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' OR Name='python.exe'" | Where-Object { $_.CommandLine -match 'flow_live\.py' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Process -FilePath $py -ArgumentList "$root\flow_live.py" -WorkingDirectory $root -WindowStyle Hidden
    Start-Sleep -Seconds 8
    try { $h = Invoke-RestMethod -Uri 'http://100.72.111.97:8797/health' -TimeoutSec 20 } catch { $h = $null }
    if ($h -and $h.version -eq $want) { "Flow en directo $want en marcha"; break }
    "Versión en marcha: $($h.version) (se esperaba $want); reintento"
}
