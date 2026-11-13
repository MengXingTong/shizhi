$ErrorActionPreference='Stop';$Root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'));$Dir=Join-Path $Root 'tools';New-Item -ItemType Directory -Force -Path $Dir|Out-Null
$Jar=Join-Path $Dir 'json-tests.jar'
if(!(Test-Path -LiteralPath $Jar)){& curl.exe --noproxy '*' -fL --connect-timeout 20 --max-time 90 'https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar' -o $Jar
    if($LASTEXITCODE -ne 0){$Proxy=$env:HTTPS_PROXY;if(!$Proxy){$Proxy=$env:HTTP_PROXY};if(!$Proxy){throw '测试依赖直连失败，未配置备用代理。'};& curl.exe --proxy $Proxy -fL --max-time 90 'https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar' -o $Jar;if($LASTEXITCODE -ne 0){throw '代理下载也失败'}}}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash -ne '3EA61B2A06E31EDF1C91134FE9106B0EBB16628BE169F3DB75BC7A2B06B45796'){throw '测试 JAR 校验失败'}
(Get-FileHash -Algorithm SHA1 -LiteralPath $Jar).Hash.ToLowerInvariant()|Set-Content -LiteralPath ($Jar+'.sha1')