$ErrorActionPreference = 'Stop'
$flowRuntime = Join-Path $HOME '.codex/integrations/google-flow'
$flowPython = Join-Path $flowRuntime 'venv/Scripts/python.exe'
New-Item -ItemType Directory -Path $flowRuntime -Force | Out-Null
if (-not (Test-Path -LiteralPath $flowPython)) {
    python -m venv (Join-Path $flowRuntime 'venv')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el entorno Python.' }
}
& $flowPython -m pip install --only-binary=:all: --no-compile -r (Join-Path $PSScriptRoot 'requirements.lock')
if ($LASTEXITCODE -ne 0) { throw 'Instalación de dependencias fallida.' }
& $flowPython -m pip check
if ($LASTEXITCODE -ne 0) { throw 'Dependencias incompatibles.' }
$runtimeFiles = @('launcher.py','runtime_compat.py','image_guard.py','bridge_tools.py','asset_process.py','video_edit.py','flow_voices.py','flow_inputs.py','recover_image.py','recover_video.py','flow_quote.py','flow_session.py','video_file.py','video_guard.py','client.py','configure.py','probe_model.py','probe_video.py')
foreach ($runtimeFile in $runtimeFiles) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $runtimeFile) -Destination $flowRuntime
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'flow_health.py') -Destination $flowRuntime
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../desktop/flow_contract.py') -Destination $flowRuntime
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $flowRuntime 'browsers'
& $flowPython -m playwright install chromium
if ($LASTEXITCODE -ne 0) { throw 'Instalación de Chromium fallida.' }
$codexConfig = Join-Path $HOME '.codex/config.toml'
$backup = Join-Path $flowRuntime 'codex-config-before.toml'
if ((Test-Path -LiteralPath $codexConfig) -and -not (Test-Path -LiteralPath $backup)) {
    Copy-Item -LiteralPath $codexConfig -Destination $backup
}
codex mcp add google-flow -- $flowPython (Join-Path $flowRuntime 'launcher.py') mcp run --profile nexus
if ($LASTEXITCODE -ne 0) { throw 'Registro MCP fallido.' }
& $flowPython -X utf8 (Join-Path $flowRuntime 'configure.py')
if ($LASTEXITCODE -ne 0) { throw 'Configuración MCP fallida.' }
Write-Output 'Runtime preparado. Si no existe sesión: launcher.py auth login --browser internal --profile nexus. El usuario debe completar el acceso de Google y cerrar esa ventana.'
