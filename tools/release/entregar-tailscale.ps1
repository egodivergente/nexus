<#
Entrega verificada por Tailscale, sin ADB.

Ejemplo para Nexus Cut:
  .\tools\release\entregar-tailscale.ps1 -Version 1.9.169 -ExpectedVersionCode 205 -SshTarget usuario@telefono

El worktree predeterminado es el que contiene este script.
Versión, versionCode y destino son obligatorios para evitar entregar otro artefacto o a otro equipo.
No acredita la instalación: abre el instalador para la actualización manual del usuario.
#>
[CmdletBinding()]
param(
    [string]$Worktree = "$PSScriptRoot\..\..",
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$ExpectedVersionCode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+$')]
    [string]$SshTarget,
    [ValidateRange(1, 65535)]
    [int]$SshPort = 8022
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$expectedPackage = 'com.vidadomu.nexus.agent'
$expectedVersionName = "$Version-agent-preview"
$expectedCertificate = '8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127'
$ssh = 'C:\Windows\System32\OpenSSH\ssh.exe'
$scp = 'C:\Windows\System32\OpenSSH\scp.exe'
$sshOptions = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=8')

if (-not (Test-Path -LiteralPath $Worktree -PathType Container)) { throw "No existe el worktree: $Worktree" }
$worktreePath = (Resolve-Path -LiteralPath $Worktree).Path
$releaseScript = Join-Path $worktreePath 'tools\release\entregar-apk.ps1'
$builtApk = Join-Path $worktreePath 'app\build\outputs\apk\agentPreview\app-agentPreview.apk'
foreach ($required in @($releaseScript, $builtApk, $ssh, $scp)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Falta el archivo requerido: $required" }
}
$buildTools = Get-ChildItem -LiteralPath "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe') } |
    Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending |
    Select-Object -First 1
if ($null -eq $buildTools) { throw 'No hay Android build-tools con aapt disponible.' }
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
foreach ($required in @($apksigner, $zipalign)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Falta la herramienta: $required" }
}

function Assert-ApkIdentity([string]$Path) {
    $badging = @(& $aapt dump badging $Path 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "aapt no pudo leer la APK: $Path" }
    $packageLine = $badging | Where-Object { "$_" -like 'package:*' } | Select-Object -First 1
    if ("$packageLine" -notmatch "^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
        throw "No se pudo interpretar la identidad de la APK: $Path"
    }
    $actualPackage = $Matches[1]
    $actualCode = [int]$Matches[2]
    $actualName = $Matches[3]
    if ($actualPackage -ne $expectedPackage -or $actualCode -ne $ExpectedVersionCode -or $actualName -ne $expectedVersionName) {
        throw "APK inesperada: paquete=$actualPackage; versionCode=$actualCode; versionName=$actualName"
    }
}

# Preflight sobre la APK exacta, antes de llamar a la herramienta que firma.
Assert-ApkIdentity $builtApk
$unsignedHash = (Get-FileHash -LiteralPath $builtApk -Algorithm SHA256).Hash.ToLowerInvariant()
$checkoutCommit = (& git -C $worktreePath rev-parse HEAD 2>$null) -join ''
if ($LASTEXITCODE -ne 0) { throw 'No se pudo identificar el checkout de Git.' }

# Cada entrega firma una copia aislada dentro de build/; no sobrescribe otra APK de entrega.
$deliveryDir = Join-Path (Split-Path -Parent $builtApk) ("tailscale-$Version-" + [guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $deliveryDir)
$signingInput = Join-Path $deliveryDir 'app-agentPreview.apk'
Copy-Item -LiteralPath $builtApk -Destination $signingInput
if ((Get-FileHash -LiteralPath $signingInput -Algorithm SHA256).Hash.ToLowerInvariant() -ne $unsignedHash) {
    throw 'La copia de preparación no coincide con la APK compilada.'
}
Assert-ApkIdentity $signingInput

# El script del proyecto contiene la gestión privada de firma. SinInstalar impide cualquier ADB.
# Se omite su copia a Drive: la firma debe verificarse antes de cualquier distribución.
$skipDrive = Join-Path $deliveryDir ('no-drive-' + [guid]::NewGuid().ToString('N'))
if (Test-Path -LiteralPath $skipDrive) { throw 'La ruta de omisión de Drive ya existe.' }
& powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $releaseScript -SinInstalar -Apk $signingInput -Drive $skipDrive
if ($LASTEXITCODE -ne 0) { throw 'El script de firma del proyecto falló.' }
$signedCandidates = @(Get-ChildItem -LiteralPath $deliveryDir -File -Filter "Nexus-Agent-$Version-*-preview.apk")
if ($signedCandidates.Count -ne 1) { throw 'La firma no produjo exactamente una APK de entrega.' }
$signedApk = $signedCandidates[0].FullName
Assert-ApkIdentity $signedApk
$certificateOutput = @(& $apksigner verify --verbose --print-certs $signedApk 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'La firma de la APK no verifica.' }
$certificateMatches = [regex]::Matches(($certificateOutput -join "`n"), '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$')
if ($certificateMatches.Count -ne 1) { throw 'No se ha identificado exactamente un firmante de la APK.' }
$actualCertificate = $certificateMatches[0].Groups[1].Value.Replace(':', '').ToLowerInvariant()
if ($actualCertificate -ne $expectedCertificate) { throw 'El certificado no coincide con la identidad existente de Nexus Agent.' }
& $zipalign -c -P 16 4 $signedApk | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'La APK no cumple la alineación de 16 KiB.' }
$localHash = (Get-FileHash -LiteralPath $signedApk -Algorithm SHA256).Hash.ToLowerInvariant()

# Nombres generados y restringidos: se usan como argumentos literales de la shell remota.
$remoteFile = "nexus-$Version-$localHash.apk"
$downloadName = [IO.Path]::GetFileNameWithoutExtension($signedCandidates[0].Name) + "-$localHash.apk"
foreach ($safeName in @($remoteFile, $downloadName)) {
    if ($safeName -notmatch '^[A-Za-z0-9._-]+$') { throw 'Nombre de archivo remoto no permitido.' }
}
& $scp @sshOptions -P $SshPort $signedApk "${SshTarget}:$remoteFile"
if ($LASTEXITCODE -ne 0) { throw 'La transferencia al S25 falló.' }
$remoteHashOutput = @(& $ssh @sshOptions -p $SshPort $SshTarget "sha256sum -- '$remoteFile'" 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'No se pudo calcular la huella del archivo recibido en el S25.' }
$remoteHashMatches = [regex]::Matches(($remoteHashOutput -join "`n"), '(?im)^([0-9a-f]{64})\s+\*?nexus-[A-Za-z0-9._-]+\.apk\s*$')
if ($remoteHashMatches.Count -ne 1) { throw 'No se pudo interpretar una única huella SHA-256 del S25.' }
$remoteHash = $remoteHashMatches[0].Groups[1].Value.ToLowerInvariant()
if ($remoteHash -ne $localHash) { throw 'SHA-256 distinto en el S25. No se copia a Descargas ni se abre el instalador.' }

# Solo se llega aquí tras igualdad SHA-256 completa. Se comprueba también la copia en Descargas.
$remoteProgram = @'
set -eu
source='@REMOTE@'
destination='/sdcard/Download/@DOWNLOAD@'
expected='@HASH@'
cp -- "$source" "$destination"
actual=$(sha256sum -- "$destination")
actual=${actual%% *}
test "$actual" = "$expected" || { echo 'ERROR: SHA-256 distinto en Descargas' >&2; exit 1; }
printf 'DOWNLOAD_SHA256=%s\n' "$actual"
termux-open --content-type application/vnd.android.package-archive "$source"
printf 'INSTALLER_REQUESTED\n'
'@
$remoteProgram = $remoteProgram.Replace('@REMOTE@', $remoteFile).Replace('@DOWNLOAD@', $downloadName).Replace('@HASH@', $localHash)
$remoteProgram = $remoteProgram.Replace("`r", "")
$openOutput = @(& $ssh @sshOptions -p $SshPort $SshTarget $remoteProgram 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'Falló la copia verificada a Descargas o la solicitud de apertura del instalador.' }
if (($openOutput -join "`n") -notmatch '(?m)^INSTALLER_REQUESTED\s*$') { throw 'No se recibió la confirmación de solicitud de apertura del instalador.' }
Write-Output "APK preparada: $signedApk"
Write-Output "Identidad: $expectedPackage; $expectedVersionName; versionCode=$ExpectedVersionCode"
Write-Output "Certificado SHA-256: $actualCertificate"
Write-Output "APK SHA-256 local/S25/Descargas: $localHash"
Write-Output "Checkout observado: $checkoutCommit (no demuestra por sí solo la procedencia binaria de la APK)."
Write-Output 'Entrega verificada y apertura del instalador solicitada. Instalación y prueba funcional pendientes de comprobación manual.'
