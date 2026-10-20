# Registro manual en AXELPC (Windows, puente principal). NO lo ejecuta ningún agente.
# Crea la tarea programada de usuario «NEXUS Flow Watchdog»: al iniciar sesión, prioridad baja, reinicio si cae.
param(
    [string]$Python = 'C:\Users\User\AppData\Local\Programs\Python\Python312\pythonw.exe',
    [string]$Url = 'http://100.72.111.97:8899/'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $env:LOCALAPPDATA 'NexusFlow\watchdog\watchdog.log'
$restart = Join-Path $PSScriptRoot 'restart-flow-bridge.ps1'
$arguments = "`"$(Join-Path $root 'flow_watchdog.py')`" --url $Url --log-file `"$log`" --restart-ps1 `"$restart`""
$action = New-ScheduledTaskAction -Execute $Python -Argument $arguments -WorkingDirectory $root
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$trigger.Delay = 'PT1M'
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew -Priority 7
Register-ScheduledTask -TaskName 'NEXUS Flow Watchdog' -Action $action -Trigger $trigger -Settings $settings `
    -Description 'Vigilante del puente privado de Flow: salud cada 20 s y reinicio con espera creciente.' -Force | Out-Null
Write-Output 'Tarea registrada. Arranque manual: Start-ScheduledTask "NEXUS Flow Watchdog"'
