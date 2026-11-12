# Build lq-loader jar for the version JSON "libraries" section.
# Usage: powershell -ExecutionPolicy Bypass -File build.ps1
param(
    [string]$Jdk = "E:\office\java\JDK\jdk-21.0.5-full",
    [string]$Output = "build\lq-loader-0.1.jar"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path $Jdk)) {
    throw "JDK not found: $Jdk (use -Jdk to override)"
}

$javac = Join-Path $Jdk "bin\javac.exe"
$jar = Join-Path $Jdk "bin\jar.exe"

$libs = Get-ChildItem "lib" -Filter *.jar | ForEach-Object { $_.FullName }
$classpath = $libs -join ';'

$classes = "build\classes"
if (Test-Path "build") { Remove-Item "build" -Recurse -Force }
New-Item -ItemType Directory $classes -Force | Out-Null

$sources = Get-ChildItem "src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 8 -encoding UTF-8 -proc:none -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "compile failed" }

Copy-Item "src\mixins.json" $classes -Force
if (Test-Path "src\META-INF") {
    Copy-Item "src\META-INF" $classes -Recurse -Force
}

New-Item -ItemType Directory (Split-Path $Output) -Force | Out-Null
& $jar --create --file $Output -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "built: $((Resolve-Path $Output).Path)"
