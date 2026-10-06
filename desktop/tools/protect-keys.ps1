$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Security
$text=[Console]::In.ReadToEnd()
$values=$text | ConvertFrom-Json
$providerNames=@($values.PSObject.Properties.Name)
if(@($providerNames | Where-Object { $_ -notin @('atlas_cloud','runway','crun') }).Count -gt 0) { throw 'Proveedor inesperado.' }
$data=Join-Path $env:LOCALAPPDATA 'NexusDesktop'
[IO.Directory]::CreateDirectory($data) | Out-Null
$bytes=[Text.Encoding]::UTF8.GetBytes($text)
$encrypted=[Security.Cryptography.ProtectedData]::Protect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
$roundtrip=[Security.Cryptography.ProtectedData]::Unprotect($encrypted,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
if([Convert]::ToBase64String($bytes) -ne [Convert]::ToBase64String($roundtrip)) { throw 'No se verificó el cifrado.' }
[IO.File]::WriteAllBytes((Join-Path $data 'credentials.dpapi'),$encrypted)
@{ imported=$providerNames;protection='Windows DPAPI CurrentUser';verified=$true } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $data 'migration-status.json')
[Array]::Clear($bytes,0,$bytes.Length)
[Array]::Clear($roundtrip,0,$roundtrip.Length)
$text=$null;$values=$null
