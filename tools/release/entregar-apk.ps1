<#
  Entrega un APK de Nexus Agent en un solo paso: firma, instala en los móviles y lo copia a Drive.

  Uso (desde la raíz del worktree, después de compilar con :app:assembleAgentPreview):
      .\tools\release\entregar-apk.ps1

  Hace, en orden:
    1. Lee versionCode / versionName del APK compilado y calcula el nombre de tanda (cada 10 versionCode).
    2. Firma con el keystore fijo C:\Users\User\Documents\nexus-audit\signing\debug.keystore (fuera de Git).
    3. Instala con `adb install -r` en el Pixel y en el S25, SOLO si la app no tiene servicios en marcha.
    4. Copia el APK a G:\Mi unidad\Nexus_Android_APK\.
  Un móvil que no responde se avisa y se salta; no es un error.
#>
param(
    [string]$Apk = "$PSScriptRoot\..\..\app\build\outputs\apk\agentPreview\app-agentPreview.apk",
    [string]$Keystore = 'C:\Users\User\Documents\nexus-audit\signing\debug.keystore',
    [string]$Drive = 'G:\Mi unidad\Nexus_Android_APK',
    [switch]$SinInstalar
)
$ErrorActionPreference = 'Stop'
$pixel = 'adb-38271FDJG00264-MUUaiB._adb-tls-connect._tcp'
$s25 = '100.119.88.105:5555'
$pkg = 'com.vidadomu.nexus.agent'
$nombres = 'Aurora','Boreal','Cénit','Dorado','Eclipse','Fénix','Glaciar','Horizonte','Índigo','Júpiter','Kairos','Lumen','Mistral','Nébula','Ónix','Polaris','Quásar','Ródano','Sirio','Tundra'

if (-not (Test-Path $Apk)) { throw "No existe $Apk. Compila antes: .\gradlew.bat :app:assembleAgentPreview" }
if (-not (Test-Path $Keystore)) { throw "Falta el keystore en $Keystore. Pídeselo al usuario (es el debug.keystore del ASUS, alias androiddebugkey, contraseña android)." }
$bt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1).FullName

$badging = & "$bt\aapt.exe" dump badging $Apk | Select-Object -First 1
if ($badging -notmatch "versionCode='(\d+)' versionName='([^']+)'") { throw "No pude leer la versión del APK." }
$code = [int]$Matches[1]; $ver = $Matches[2] -replace '-agent.*$', ''
$nombre = $nombres[[math]::Floor($code / 10) % $nombres.Count]
$ascii = $nombre.Normalize([Text.NormalizationForm]::FormD) -replace '\p{M}', ''
$salida = Join-Path (Split-Path $Apk) "Nexus-Agent-$ver-$ascii-preview.apk"

& "$bt\apksigner.bat" sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $salida $Apk
if ($LASTEXITCODE -ne 0) { throw 'La firma falló.' }
& "$bt\apksigner.bat" verify $salida
if ($LASTEXITCODE -ne 0) { throw 'La firma no verifica.' }
Write-Host "Firmado: $salida  (versionCode $code, «${nombre}»)"

if (-not $SinInstalar) {
    & adb connect $s25 2>&1 | Out-Null
    foreach ($d in @(@('Pixel', $pixel), @('S25', $s25))) {
        $estado = (& adb devices) -match [regex]::Escape($d[1]) -and ((& adb devices) -join "`n") -match "$([regex]::Escape($d[1]))\s+device"
        if (-not $estado) { Write-Host "$($d[0]): no responde, se salta."; continue }
        $servicios = (& adb -s $d[1] shell "dumpsys activity services $pkg | grep -c ServiceRecord") -join ''
        if ($servicios.Trim() -ne '0') { Write-Host "$($d[0]): hay trabajo en curso, NO instalo."; continue }
        $r = & adb -s $d[1] install -r $salida 2>&1 | Select-Object -Last 1
        Write-Host "$($d[0]): $r"
    }
}

if (Test-Path $Drive) { Copy-Item $salida $Drive -Force; Write-Host "Copiado a $Drive" } else { Write-Host "Drive no disponible en $Drive; no se copió." }
Write-Host "Listo: Nexus-Agent-$ver-$ascii-preview.apk"
