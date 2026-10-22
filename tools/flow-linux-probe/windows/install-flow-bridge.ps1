# Install the current bridge in one stable runtime; preserve the existing profile, token and job journal.
param([switch]$Restart)
$ErrorActionPreference = 'Stop'
$source = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $env:LOCALAPPDATA 'NexusFlow\bridge'
$root = Join-Path $env:USERPROFILE '.local\share\nexus-flow-probe'
$modules = @('recovery.py','recovery.html','auto_auth.py','byteplus_admin.py','flow_agent.py',
    'flow_gallery.py','flow_references.py','flow_session.py','flow_settings.py','login.py')
foreach ($name in $modules) {
    if (-not (Test-Path -LiteralPath (Join-Path $source $name))) {throw "Missing bridge module: $name"}
}
$listener = Get-NetTCPConnection -LocalAddress '100.72.111.97' -LocalPort 8899 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    if (-not $Restart) {throw 'Bridge already running. Use -Restart only when no generation is active.'}
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if (-not $process.CommandLine.Contains((Join-Path $runtime 'recovery.py'))) {
        throw 'Another bridge installation owns this port. Preserve it and migrate its launcher explicitly.'
    }
    $journal = Join-Path $root 'flow-jobs.json'
    if (Test-Path -LiteralPath $journal) {
        $jobs = @(Get-Content -Raw -LiteralPath $journal | ConvertFrom-Json)
        if ($jobs | Where-Object {$_.state -in @('submitting','submitted','generating')}) {
            throw 'An existing generation is active. Finish or recover it before restarting.'
        }
    }
    Stop-Process -Id $listener.OwningProcess
}
New-Item -ItemType Directory -Force $runtime | Out-Null
foreach ($name in $modules) {Copy-Item -LiteralPath (Join-Path $source $name) -Destination (Join-Path $runtime $name)}
$windows = Join-Path $runtime 'windows'
New-Item -ItemType Directory -Force $windows | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-flow-bridge.ps1') -Destination $windows
$startup = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\Nexus Flow bridge.vbs'
if (Test-Path -LiteralPath $startup) {
    Copy-Item -LiteralPath $startup -Destination (Join-Path $runtime ('startup-before-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.vbs'))
}
$launcher = Join-Path $windows 'start-flow-bridge.ps1'
$line = 'CreateObject("WScript.Shell").Run "powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File ""'+$launcher+'""", 0, False'
Set-Content -LiteralPath $startup -Value $line -Encoding ascii
& $launcher
Write-Output "Installed Flow bridge: $runtime"
