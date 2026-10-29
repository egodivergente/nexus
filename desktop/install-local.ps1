param([switch]$NoLaunch, [string]$AdditionalShortcut, [int]$Port = 0, [string]$PythonExecutable)
$ErrorActionPreference = 'Stop'
$python = if ($PythonExecutable) { (Resolve-Path -LiteralPath $PythonExecutable).Path } else { (Get-Command python.exe -ErrorAction Stop).Source }
$pythonw = Join-Path (Split-Path -Parent $python) 'pythonw.exe'
if (-not (Test-Path -LiteralPath $pythonw -PathType Leaf)) { throw 'Falta Python para Windows.' }
if ((Get-AuthenticodeSignature -LiteralPath $pythonw).Status -ne 'Valid') { throw 'Python no tiene una firma valida.' }
$script = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'local_host.py')).Path
$icon = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'nexus.ico')).Path
$desktop = [Environment]::GetFolderPath('DesktopDirectory')
$shortcut = Join-Path $desktop 'Nexus Studio.lnk'
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = $pythonw
$link.Arguments = '"' + $script + '"' + $(if ($Port) { ' --port ' + $Port } else { '' })
$link.WorkingDirectory = $PSScriptRoot
$link.IconLocation = $icon
$link.Description = 'Nexus Studio local, privado y con datos protegidos en Windows'
$link.Save()
if ($AdditionalShortcut) {
    $extra = $shell.CreateShortcut($AdditionalShortcut)
    $extra.TargetPath = $link.TargetPath
    $extra.Arguments = $link.Arguments
    $extra.WorkingDirectory = $link.WorkingDirectory
    $extra.IconLocation = $link.IconLocation
    $extra.Description = 'Nexus Studio · Agente, proyectos, edición local y Whisper'
    $extra.Save()
}
$taskbarShortcut = Join-Path $env:APPDATA 'Microsoft\Internet Explorer\Quick Launch\User Pinned\TaskBar\Nexus Studio.lnk'
if (Test-Path -LiteralPath $taskbarShortcut -PathType Leaf) {
    $pinned = $shell.CreateShortcut($taskbarShortcut)
    $pinned.TargetPath = $link.TargetPath
    $pinned.Arguments = $link.Arguments
    $pinned.WorkingDirectory = $link.WorkingDirectory
    $pinned.IconLocation = $link.IconLocation
    $pinned.Description = 'Nexus Studio · Agente, proyectos, edición local y Whisper'
    $pinned.Save()
}
Write-Output ('Acceso directo creado: ' + $shortcut)
if (-not $NoLaunch) {
    $cmdLine = '"' + $pythonw + '" ' + $link.Arguments
    $null = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmdLine; CurrentDirectory = $PSScriptRoot }
    Write-Output 'Nexus Studio iniciado.'
}

