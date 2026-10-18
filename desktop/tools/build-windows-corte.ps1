param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
# Compile here on AXELPC. Keep the Android checkout and its wrapper untouched.
$java = Get-Command java.exe -ErrorAction Stop
$gradle = Get-ChildItem -LiteralPath "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.1.0-bin" -Filter gradle.bat -Recurse | Select-Object -First 1
if (-not $gradle) { throw 'Falta Gradle 9.1.0. Instala la distribución antes de compilar.' }
& python -X utf8 (Join-Path $PSScriptRoot 'verify-corte-source.py')
if ($LASTEXITCODE) { throw 'Las fuentes Corte no coinciden con su referencia.' }
$tasks = @('runtime')
if (-not $SkipTests) { $tasks = @('test', ':corte-timeline:test', ':corte-storage:test', 'runtime') }
& $gradle.FullName -p "$root\desktop\agent" @tasks --max-workers=2 --no-parallel --console=plain
if ($LASTEXITCODE) { throw 'Falló la compilación JVM.' }
$runtime = "$root\desktop\agent\runtime"
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$built = Get-ChildItem -LiteralPath "$root\desktop\agent\build\runtime" -Filter '*.jar'
Get-ChildItem -LiteralPath $runtime -Filter '*.jar' | Where-Object { $_.Name -notin $built.Name } | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
$built | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $runtime }
Write-Output 'Runtime Windows y motor Corte reconstruidos en AXELPC.'
