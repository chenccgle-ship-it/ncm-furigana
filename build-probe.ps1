param([string]$OutputApkName = '', [string]$SourceDirectory = 'module')
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$toolsRoot = Join-Path $projectRoot 'tools'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem (Join-Path $toolsRoot 'jdk') -Directory | Select-Object -First 1).FullName }
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { throw 'Set JAVA_HOME to JDK 21 before building' }
$buildTools = Join-Path $toolsRoot 'build-tools\android-15'
$androidJar = Join-Path $toolsRoot 'platform\android-35\android.jar'
$apiJar = Join-Path $toolsRoot 'libxposed-api-102\classes.jar'
$dictionaryJars = @((Join-Path $toolsRoot 'kuromoji\kuromoji-core-0.9.0.jar'), (Join-Path $toolsRoot 'kuromoji\kuromoji-ipadic-0.9.0.jar'))
$sourceRoot = Join-Path $projectRoot $SourceDirectory
$buildRoot = Join-Path $projectRoot ('build\probe-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$outRoot = Join-Path $projectRoot 'outputs'
New-Item -ItemType Directory -Force $buildRoot,$outRoot,(Join-Path $buildRoot 'classes'),(Join-Path $buildRoot 'dex') | Out-Null
function Run-Checked([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE): $program" }
}
$sources = @(Get-ChildItem (Join-Path $sourceRoot 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
Run-Checked (Join-Path $env:JAVA_HOME 'bin\javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',("$androidJar;$apiJar;" + ($dictionaryJars -join ';')),'-d',(Join-Path $buildRoot 'classes')) + $sources)
Run-Checked (Join-Path $env:JAVA_HOME 'bin\jar.exe') @('--create','--file',(Join-Path $buildRoot 'module.jar'),'-C',(Join-Path $buildRoot 'classes'),'.')
Run-Checked (Join-Path $env:JAVA_HOME 'bin\java.exe') (@('-cp',(Join-Path $buildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','29','--lib',$androidJar,'--classpath',$apiJar,'--output',(Join-Path $buildRoot 'dex'),(Join-Path $buildRoot 'module.jar')) + $dictionaryJars)
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('compile','--dir',(Join-Path $sourceRoot 'res'),'-o',(Join-Path $buildRoot 'resources.zip'))
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('link','-I',$androidJar,'--manifest',(Join-Path $sourceRoot 'AndroidManifest.xml'),'-o',(Join-Path $buildRoot 'base.apk'),(Join-Path $buildRoot 'resources.zip'))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = Join-Path $buildRoot 'unsigned.apk'
Copy-Item -LiteralPath (Join-Path $buildRoot 'base.apk') -Destination $unsigned
$archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $buildRoot 'dex\classes.dex'),'classes.dex') | Out-Null
    $resourceRoot = Join-Path $sourceRoot 'resources'
    Get-ChildItem $resourceRoot -Recurse -File | ForEach-Object {
        $entryName = $_.FullName.Substring($resourceRoot.Length + 1).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$_.FullName,$entryName) | Out-Null
    }
} finally { $archive.Dispose() }
$aligned = Join-Path $buildRoot 'aligned.apk'
Run-Checked (Join-Path $buildTools 'zipalign.exe') @('-p','-f','4',$unsigned,$aligned)
$keyPath = if ($env:NCM_SIGNING_KEY) { $env:NCM_SIGNING_KEY } else { Join-Path $toolsRoot 'probe-debug.p12' }
if (-not (Test-Path -LiteralPath $keyPath)) {
    Run-Checked (Join-Path $env:JAVA_HOME 'bin\keytool.exe') @('-genkeypair','-keystore',$keyPath,'-storetype','PKCS12','-storepass','android','-keypass','android','-alias','probe','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=NCM Furigana Local Test')
}
[xml]$manifestXml = Get-Content (Join-Path $sourceRoot 'AndroidManifest.xml') -Raw
$appVersion = $manifestXml.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android').Split('-')[0]
if ($OutputApkName -and ([IO.Path]::GetFileName($OutputApkName) -ne $OutputApkName -or -not $OutputApkName.EndsWith('.apk'))) { throw 'OutputApkName must be an APK file name' }
$finalApk = Join-Path $outRoot $(if ($OutputApkName) { $OutputApkName } else { "ncm-furigana-$appVersion.apk" })
Run-Checked (Join-Path $env:JAVA_HOME 'bin\java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'sign','--ks',$keyPath,'--ks-key-alias','probe','--ks-pass','pass:android','--key-pass','pass:android','--out',$finalApk,$aligned)
Run-Checked (Join-Path $env:JAVA_HOME 'bin\java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'verify','--verbose',$finalApk)
Get-FileHash -LiteralPath $finalApk -Algorithm SHA256
Write-Output "APK: $finalApk"
