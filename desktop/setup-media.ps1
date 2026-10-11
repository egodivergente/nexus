$ErrorActionPreference = 'Stop'
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (!$python) { $python = "$env:LOCALAPPDATA/Programs/Python/Python312/python.exe" }
& $python -m pip install --disable-pip-version-check -r "$PSScriptRoot/requirements-media.txt"
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias multimedia.' }
