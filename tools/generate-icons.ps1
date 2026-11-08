$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$javaSource = Join-Path $PSScriptRoot 'GenerateWeatherIcons.java'
$classesDir = Join-Path $PSScriptRoot '.generator-build'
$backgroundDir = Join-Path $PSScriptRoot 'weather-backgrounds'
$outputDir = Join-Path $projectRoot 'app\src\main\res\mipmap-nodpi'
$manifestFile = Join-Path $projectRoot 'app\src\main\AndroidManifest.xml'

$jdkBins = [System.Collections.Generic.List[string]]::new()
if ($env:JAVA_HOME) { $jdkBins.Add((Join-Path $env:JAVA_HOME 'bin')) }

# Codex's development tools may be available beside the project checkout.
$workspaceJdks = Join-Path $projectRoot '..\..\work\tools\jdk'
if (Test-Path $workspaceJdks) {
    Get-ChildItem $workspaceJdks -Directory | Sort-Object Name -Descending | ForEach-Object {
        $jdkBins.Add((Join-Path $_.FullName 'bin'))
    }
}

$jdkBin = $jdkBins | Where-Object {
    (Test-Path (Join-Path $_ 'javac.exe')) -and (Test-Path (Join-Path $_ 'java.exe'))
} | Select-Object -First 1
$javac = if ($jdkBin) { Join-Path $jdkBin 'javac.exe' } else { (Get-Command javac -ErrorAction SilentlyContinue).Source }
$java = if ($jdkBin) { Join-Path $jdkBin 'java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
if (-not $javac -or -not $java) {
    throw 'JDK 17 or newer is required. Set JAVA_HOME to its installation directory or add javac and java to PATH.'
}
$javacVersion = (& $javac -version 2>&1 | Select-Object -First 1)
if ($javacVersion -notmatch 'javac\s+(\d+)' -or [int]$Matches[1] -lt 17) {
    throw "JDK 17 or newer is required; found: $javacVersion"
}

New-Item -ItemType Directory -Force $classesDir | Out-Null
& $javac --release 17 -d $classesDir $javaSource
if ($LASTEXITCODE -ne 0) { throw 'Could not compile the icon generator.' }
& $java -cp $classesDir GenerateWeatherIcons $backgroundDir $outputDir $manifestFile
if ($LASTEXITCODE -ne 0) { throw 'Icon generation failed.' }
