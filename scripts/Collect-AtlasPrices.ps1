$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$catalog=Get-Content (Join-Path $root 'catalog-public-2026-09-24.json') -Raw | ConvertFrom-Json
$targets=$catalog.data | Where-Object { $_.type -in @('Image','Video') }
$results=$targets | ForEach-Object -Parallel {
    $model=$_; $root=$using:root; $id=$model.model
    $url='https://www.atlascloud.ai/models/'+$id
    $file=Join-Path $root ('outputs/atlas-pages/'+($id -replace '[^a-zA-Z0-9_-]','_')+'.md')
    try {
        if(Test-Path -LiteralPath $file){$page=Get-Content -LiteralPath $file -Raw}
        else {$page=(Invoke-WebRequest $url -Headers @{Accept='text/markdown'} -TimeoutSec 25).Content; [IO.File]::WriteAllText($file,$page)}
        $declared=[regex]::Match($page,'\*\*Model ID\*\*:\s*`([^`]+)`').Groups[1].Value
        $section=[regex]::Match($page,'(?s)## Pricing on Atlas Cloud\s+(.*?)(?=\n## |\z)').Groups[1].Value
        $price=[regex]::Match($section,'(?im)^- \$([0-9]+(?:\.[0-9]+)?) per (second|image|generation|minute|operation)\b')
        if($declared -eq $id -and $price.Success){
            [pscustomobject]@{id=$id;usd=[double]::Parse($price.Groups[1].Value,[Globalization.CultureInfo]::InvariantCulture);unit=$price.Groups[2].Value;checkedAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();source=$url;status='ok'}
        }else{[pscustomobject]@{id=$id;status='unsupported';pricing=$section.Trim()}}
    }catch{[pscustomobject]@{id=$id;status='error';error=$_.Exception.Message}}
} -ThrottleLimit 4
$results | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $root 'outputs/atlas-pricing-audit.json') -Encoding utf8
@($results | Where-Object status -eq 'ok') | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $root 'app/src/main/assets/atlas_published_prices.json') -Encoding utf8
$results | Group-Object status | Select-Object Name,Count
