param([string]$Directory = (Join-Path $PSScriptRoot 'dist'))
$ErrorActionPreference = 'Stop'
$Directory = (Resolve-Path -LiteralPath $Directory).Path
$names = @('Nexus.exe', 'Microsoft.Web.WebView2.Core.dll', 'Microsoft.Web.WebView2.WinForms.dll', 'WebView2Loader.dll')
$results = foreach ($name in $names) {
    $path = Join-Path $Directory $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Falta el archivo requerido: $name" }
    $signature = Get-AuthenticodeSignature -LiteralPath $path
    [pscustomobject]@{
        File = $name
        Signature = [string]$signature.Status
        Signer = if ($signature.SignerCertificate) { $signature.SignerCertificate.Subject } else { $null }
        SHA256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    }
}
$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Directory 'signature-report.json') -Encoding UTF8
$results | Format-Table File, Signature -AutoSize
if (@($results | Where-Object Signature -ne 'Valid').Count) {
    Write-Error 'Entrega bloqueada: uno o mas binarios carecen de firma Authenticode valida. No modificar las protecciones de Windows. Se requiere firma reconocida o distribucion oficial firmada.'
    exit 1
}
Write-Output 'Firmas validas. Aun se requiere comprobar el arranque bajo la politica real de Windows y ejecutar QA antes de declarar la entrega operativa.'
