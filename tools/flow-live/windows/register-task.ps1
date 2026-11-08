# Registra la tarea de usuario «Nexus-Flow-Live»: al iniciar sesión y cada 2 minutos comprueba el servicio.
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:USERPROFILE 'nexus-flow-live'
$script = Join-Path $root 'ensure-flow-live.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
$logon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$every = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 2)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'Nexus-Flow-Live' -Action $action -Trigger @($logon, $every) -Settings $settings `
    -Description 'Flow en directo para Nexus: pantalla y control de Google Flow por Tailscale.' -Force | Out-Null
'Tarea Nexus-Flow-Live registrada'
