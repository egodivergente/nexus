param(
    [Parameter(Mandatory=$true)][string]$Destination,
    [string[]]$Source = @((Join-Path $env:USERPROFILE 'Downloads')),
    [string]$InstallDirectory = (Join-Path $env:LOCALAPPDATA 'PuenteS25\FlowDownloads')
)
$ErrorActionPreference='Stop'
$python=(Get-Command python.exe -ErrorAction Stop).Source
$pythonw=Join-Path (Split-Path $python -Parent) 'pythonw.exe'
if(-not(Test-Path -LiteralPath $pythonw)){throw 'A real Python installation with pythonw.exe is required'}
New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
$program=Join-Path $InstallDirectory 'flow-download-router.py'
$configPath=Join-Path $InstallDirectory 'config.json'
if(Test-Path -LiteralPath $configPath){
    $existing=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    if($existing.destination -ne $Destination -or (@($existing.sources) -join '|') -ne ($Source -join '|')){
        throw 'An existing router has different paths; preserve its configuration and review it first'
    }
}else{
    [ordered]@{sources=@($Source);destination=$Destination;settle_seconds=4;poll_seconds=4} |
        ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $configPath -Encoding UTF8
}
$healthPath=Join-Path $InstallDirectory 'health.json'
if(Test-Path -LiteralPath $healthPath){
    $health=Get-Content -LiteralPath $healthPath -Raw | ConvertFrom-Json
    $existingProcess=Get-CimInstance Win32_Process -Filter ("ProcessId="+[int]$health.pid)
    if($existingProcess -and $existingProcess.Name -eq 'pythonw.exe' -and
        $existingProcess.CommandLine.Contains($program)){
        Stop-Process -Id $existingProcess.ProcessId -ErrorAction Stop
    }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'flow-download-router.py') -Destination $program -Force
& $python $program --config $configPath --initialize
if($LASTEXITCODE -ne 0){throw 'Could not initialize the router baseline'}
$startup=[Environment]::GetFolderPath('Startup')
$shell=New-Object -ComObject WScript.Shell
$shortcut=$shell.CreateShortcut((Join-Path $startup 'Flow a Puente S25.lnk'))
$shortcut.TargetPath=$pythonw
$shortcut.Arguments='"'+$program+'" --config "'+$configPath+'"'
$shortcut.WorkingDirectory=$InstallDirectory
$shortcut.WindowStyle=7
$shortcut.Description='Traslada nuevas imágenes y vídeos descargados de Flow al puente S25'
$shortcut.Save()
Start-Process -FilePath $pythonw -WindowStyle Hidden -ArgumentList @(('"'+$program+'"'),'--config',('"'+$configPath+'"'))
[pscustomobject]@{Installed=$program;Configuration=$configPath;StartupShortcut=$shortcut.FullName} | ConvertTo-Json
