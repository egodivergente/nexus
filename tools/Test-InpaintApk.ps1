param([Parameter(Mandatory=$true)][string]$ApkPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$resolved = (Resolve-Path -LiteralPath $ApkPath).Path
$archive = [IO.Compression.ZipFile]::OpenRead($resolved)
try {
    $required = @(
        'lib/arm64-v8a/libnexus_inpaint.so',
        'lib/arm64-v8a/libstable-diffusion.so',
        'lib/arm64-v8a/libc++_shared.so',
        'lib/arm64-v8a/libnexus_whisper.so',
        'lib/x86_64/libnexus_inpaint.so',
        'lib/x86_64/libc++_shared.so'
    )
    foreach ($name in $required) {
        $entry = $archive.GetEntry($name)
        if ($null -eq $entry -or $entry.Length -eq 0) { throw "Falta biblioteca nativa empaquetada: $name" }
        "PASS $name ($($entry.Length) bytes)"
    }
} finally { $archive.Dispose() }
"APK_SHA256=$((Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash)"
