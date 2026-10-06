param([switch]$NoLaunch)
$ErrorActionPreference = 'Stop'
$python = (Get-Command python.exe -ErrorAction Stop).Source
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
$link.Arguments = '"' + $script + '"'
$link.WorkingDirectory = $PSScriptRoot
$link.IconLocation = $icon
$link.Description = 'Nexus Studio local, privado y con datos protegidos en Windows'
$link.Save()
Write-Output ('Acceso directo creado: ' + $shortcut)
if (-not $NoLaunch) {
    Start-Process -FilePath $pythonw -ArgumentList ('"' + $script + '"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
    Write-Output 'Nexus Studio iniciado.'
}
