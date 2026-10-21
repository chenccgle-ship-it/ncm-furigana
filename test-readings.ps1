$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
$jdkRoot=$env:JAVA_HOME
if (-not $jdkRoot) { $jdkRoot=(Get-ChildItem (Join-Path $projectRoot 'tools\jdk') -Directory | Select-Object -First 1).FullName }
$latestBuild=(Get-ChildItem (Join-Path $projectRoot 'build') -Directory | Where-Object Name -Like 'probe-*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
$testRoot=Join-Path $projectRoot 'build\reading-tests'
New-Item -ItemType Directory -Force $testRoot | Out-Null
[xml]$manifestXml=Get-Content (Join-Path $projectRoot 'module\AndroidManifest.xml') -Raw
$appVersion=$manifestXml.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android').Split('-')[0]
$testClasspath="$testRoot;$latestBuild\classes;$projectRoot\tools\kuromoji\kuromoji-classes-only.jar;$projectRoot\outputs\ncm-furigana-$appVersion.apk"
& "$jdkRoot\bin\javac.exe" -encoding UTF-8 -cp $testClasspath -d $testRoot "$projectRoot\tests\ReadingIntegrationTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$jdkRoot\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $testClasspath dev.local.ncmfurigana.ReadingIntegrationTest
if ($LASTEXITCODE -ne 0) { throw 'Reading integration test failed' }
