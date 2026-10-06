$ErrorActionPreference='Stop'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk=Join-Path $root 'vendor/webview2'
$dist=Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
Copy-Item -Path (Join-Path $sdk '*.dll') -Destination $dist -Force
Copy-Item -LiteralPath (Join-Path $root 'ui') -Destination $dist -Recurse -Force
Copy-Item -LiteralPath (Join-Path $root 'local_host.py'),(Join-Path $root 'agent_host.py'),(Join-Path $root 'nexus.ico') -Destination $dist -Force
$agentDist=Join-Path $dist 'agent'
New-Item -ItemType Directory -Force $agentDist | Out-Null
foreach($folder in @('runtime','assets','agent-skills')) {
    Copy-Item -LiteralPath (Join-Path $root "agent/$folder") -Destination $agentDist -Recurse -Force
}
& 'C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe' /nologo /target:winexe /platform:x64 /optimize+ /out:"$dist\Nexus.exe" /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Net.Http.dll /r:System.Web.Extensions.dll /r:System.Security.dll /r:"$sdk\Microsoft.Web.WebView2.Core.dll" /r:"$sdk\Microsoft.Web.WebView2.WinForms.dll" "$root\src\Program.cs"
if($LASTEXITCODE -ne 0) { throw 'No se pudo compilar Nexus Desktop.' }
Write-Output 'Nexus Desktop compilado.'
