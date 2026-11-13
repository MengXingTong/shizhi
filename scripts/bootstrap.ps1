$ErrorActionPreference='Stop'
$Root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'));$Tools=Join-Path $Root 'tools';New-Item -ItemType Directory -Force -Path $Tools|Out-Null
$Files=@(
    @{File='platform.zip';Url='https://dl.google.com/android/repository/platform-35_r02.zip';SHA1='0bb560a90a7a2cbd0dd8348224d518b638fe7949';Dest='sdk-platform'},
    @{File='build-tools.zip';Url='https://dl.google.com/android/repository/build-tools_r35_windows.zip';SHA1='af059bb67cf7786f45ee0db85e2d24985df1b4b6';Dest='sdk-build-tools'}
)
foreach($f in $Files){$Target=Join-Path $Tools $f.File
    if(!(Test-Path -LiteralPath $Target)){& curl.exe --noproxy '*' -fL --connect-timeout 20 --max-time 300 -o $Target $f.Url
        if($LASTEXITCODE -ne 0){$Proxy=$env:HTTPS_PROXY;if(!$Proxy){$Proxy=$env:HTTP_PROXY};if(!$Proxy){throw '直连下载失败且未配置备用代理，请稍后再试。'};& curl.exe --proxy $Proxy -fL --connect-timeout 20 --max-time 300 -o $Target $f.Url;if($LASTEXITCODE -ne 0){throw '备用代理下载也失败。'}}}
    if((Get-FileHash -LiteralPath $Target -Algorithm SHA1).Hash -ne $f.SHA1){throw "官方包校验失败：$($f.File)，请不要运行其中工具。"}
    Expand-Archive -LiteralPath $Target -DestinationPath (Join-Path $Tools $f.Dest) -Force
}
Write-Output 'Android SDK ready (API 35, Build Tools 35.0.0).'