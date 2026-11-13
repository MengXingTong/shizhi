param([string]$JavaHome='C:\Program Files\Java\jdk-17')
$ErrorActionPreference='Stop';$Root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'));$Lib=Join-Path $Root 'tools\json-tests.jar'
if(!(Test-Path -LiteralPath $Lib)){throw '缺少测试用 org.json JAR，请按 README 下载。APK 不依赖此 JAR。'}
$Expected=(Get-Content -Raw -LiteralPath ($Lib+'.sha1')).Trim();if((Get-FileHash -LiteralPath $Lib -Algorithm SHA1).Hash -ne $Expected){throw '测试依赖校验失败'}
$Out=Join-Path $Root 'build\core-tests';New-Item -ItemType Directory -Path $Out -Force|Out-Null
$Names='QueuePolicy','ApiEndpoint','RetentionPolicy','SpeechChunks','Net','BiliClient','LlmClient','BiliLoginCookies','LoginAttemptGate','SubtitleUrlPolicy','SourceBinding','SourceNavigation','RefillPolicy','RefillLoop';$Sources=@($Names|ForEach-Object {Join-Path $Root "app\src\main\java\com\shizhi\app\$_.java"})+@(Join-Path $Root 'tests\CoreTests.java')
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $Lib -d $Out @Sources;if($LASTEXITCODE -ne 0){throw '核心测试编译失败'}
& "$JavaHome\bin\java.exe" '-Dfile.encoding=UTF-8' -cp "$Out;$Lib" com.shizhi.app.CoreTests;if($LASTEXITCODE -ne 0){throw '核心测试失败'}