param(
    [string]$Libraries = "$env:APPDATA\.minecraft\libraries",
    [string]$Wonderland = "",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$McVersion = "1.20.1"
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Find-Tool([string]$name) {
    if ($Jdk) {
        $candidate = Join-Path $Jdk "bin\$name.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "$name not found. Install a JDK 17+ or pass -Jdk <path>."
}

function Get-VersionKey([string]$version) {
    (($version -split '[^0-9]+') | Where-Object { $_ -ne '' } | ForEach-Object { '{0:D12}' -f [long]$_ }) -join '.'
}

if (-not (Test-Path $Libraries)) { throw "Libraries folder not found: $Libraries" }
if (-not $Wonderland) {
    $found = Get-ChildItem "$env:APPDATA\.minecraft\mods" -Filter 'the_wonderland-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { throw "The Wonderland jar not found. Pass -Wonderland <path to the_wonderland-*.jar>." }
    $Wonderland = $found.FullName
}
if (-not (Test-Path $Wonderland)) { throw "The Wonderland jar not found: $Wonderland" }

$javac = Find-Tool 'javac'
$jar = Find-Tool 'jar'

$libRoot = (Resolve-Path $Libraries).Path
$mcArtifacts = @('net\minecraft\client', 'net\minecraft\server', 'net\minecraftforge\forge')
$classpath = New-Object System.Collections.Generic.List[string]
$patched = New-Object System.Collections.Generic.List[string]

Get-ChildItem $libRoot -Recurse -Filter *.jar |
    Group-Object { $_.Directory.Parent.FullName } |
    ForEach-Object {
        $artifact = $_.Name.Substring($libRoot.Length).TrimStart('\')
        $versions = $_.Group | Group-Object { $_.Directory.Name }
        if ($mcArtifacts -contains $artifact) {
            $versions = $versions | Where-Object { $_.Name -like "$McVersion*" }
        }
        $best = $versions | Sort-Object { Get-VersionKey $_.Name } -Descending | Select-Object -First 1
        foreach ($file in $best.Group) {
            if ($artifact -eq 'net\minecraftforge\forge' -and $file.Name -notlike '*-universal.jar') {
                $patched.Add($file.FullName)
            } else {
                $classpath.Add($file.FullName)
            }
        }
    }

if ($patched.Count -eq 0) { throw "Forge $McVersion not found in $libRoot" }
$classpath.InsertRange(0, $patched)
$classpath.Add((Resolve-Path $Wonderland).Path)

$props = Get-Content (Join-Path $root 'src\main\resources\META-INF\mods.toml') -Raw
$version = [regex]::Match($props, '(?m)^version="([^"]+)"').Groups[1].Value
$modId = [regex]::Match($props, '(?m)^modId="([^"]+)"').Groups[1].Value

$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$libs = Join-Path $build 'libs'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes, $libs | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$argFile = Join-Path $build 'javac.args'
$javacArgs = @(
    '--release', '17',
    '-encoding', 'UTF-8',
    '-proc:none',
    '-Xlint:-options',
    '-d', ('"' + ($classes -replace '\\', '/') + '"'),
    '-cp', ('"' + (($classpath | ForEach-Object { $_ -replace '\\', '/' }) -join ';') + '"')
) + $sources
[System.IO.File]::WriteAllLines($argFile, [string[]]$javacArgs)

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force

$output = Join-Path $libs "$modId-$version.jar"
if (Test-Path $output) { Remove-Item $output -Force }
& $jar --create --file $output -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "Built $output"

<#
 ═══════════════════════════════════════
  build.ps1 — сборка без Gradle
 ═══════════════════════════════════════
  javac --release 17 против production-библиотек Forge из папки libraries (лаунчер или сервер).
  Classpath: по каждому артефакту берётся старшая версия; MC и Forge — только $McVersion.
  Патченный forge-*-client/server.jar идёт первым, до ванильного srg-jar.
  The Wonderland нужен только для компиляции (MapVariables), в итоговый jar не входит.
  Результат: build/libs/<modId>-<version>.jar (версия из mods.toml).
 ═══════════════════════════════════════
#>
