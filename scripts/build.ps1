param([string]$JavaHome='C:\Program Files\Java\jdk-17')
$ErrorActionPreference='Stop'
$VersionName='0.1.3-alpha1';$VersionCode='4'
$Root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:JAVA_HOME=$JavaHome;$env:PATH="$JavaHome\bin;$env:PATH"
$Sdk=Join-Path $Root 'tools\sdk-platform\android-35\android.jar'
$Bt=Join-Path $Root 'tools\sdk-build-tools\android-15'
if(!(Test-Path -LiteralPath $Sdk) -or !(Test-Path -LiteralPath "$Bt\aapt2.exe")){throw '缺少 Android SDK。请先运行 scripts/bootstrap.ps1。'}
if(!(Test-Path -LiteralPath "$JavaHome\bin\javac.exe")){throw '请用 -JavaHome 指定 JDK 17 或更新版本。'}
$Build=Join-Path $Root ('build\run-'+[DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff'))
@($Build,"$Build\generated","$Build\classes","$Build\dex",(Join-Path $Root 'dist')) | ForEach-Object {New-Item -ItemType Directory -Force -Path $_|Out-Null}
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "$Exe 退出码 $LASTEXITCODE"}}
Run "$Bt\aapt2.exe" @('compile','--dir',"$Root\app\src\main\res",'-o',"$Build\res.zip")
Run "$Bt\aapt2.exe" @('link','-o',"$Build\resources.apk",'--manifest',"$Root\app\src\main\AndroidManifest.xml",'-I',$Sdk,'--java',"$Build\generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code',$VersionCode,'--version-name',$VersionName,'--debug-mode','-A',"$Root\app\src\main\assets","$Build\res.zip")
$Sources=@(Get-ChildItem -LiteralPath "$Root\app\src\main\java","$Build\generated" -Filter '*.java' -Recurse | ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'})
[IO.File]::WriteAllLines("$Build\sources.txt",$Sources,[Text.UTF8Encoding]::new($false))
Run "$JavaHome\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-Xlint:unchecked','-classpath',$Sdk,'-d',"$Build\classes","@$Build\sources.txt")
Run "$JavaHome\bin\jar.exe" @('cf',"$Build\classes.jar",'-C',"$Build\classes",'.')
Run "$Bt\d8.bat" @('--lib',$Sdk,'--min-api','26','--output',"$Build\dex","$Build\classes.jar")
Copy-Item -LiteralPath "$Build\resources.apk" -Destination "$Build\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip=[IO.Compression.ZipFile]::Open("$Build\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{Get-ChildItem -LiteralPath "$Build\dex" -Filter '*.dex'|ForEach-Object {[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip,$_.FullName,$_.Name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$Zip.Dispose()}
Run "$Bt\zipalign.exe" @('-f','4',"$Build\unsigned.apk","$Build\aligned.apk")
$Keystore=Join-Path $Root 'tools\shizhi-debug.jks'
if(!(Test-Path -LiteralPath $Keystore)){Run "$JavaHome\bin\keytool.exe" @('-genkeypair','-keystore',$Keystore,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Shizhi Debug,O=Local Development,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000')}
$Output=Join-Path $Root "dist\shizhi-$VersionName-debug.apk"
Run "$Bt\apksigner.bat" @('sign','--ks',$Keystore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$Output,"$Build\aligned.apk")
Run "$Bt\apksigner.bat" @('verify','--verbose',$Output)
(Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($Output)|Set-Content -LiteralPath ($Output+'.sha256')
Write-Output "APK: $Output"