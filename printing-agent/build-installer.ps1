<#
Builds the Ember Agent Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> printing-agent/dist/runtime               (jlink JRE image)
  appimage  -> printing-agent/dist/app-image             (jpackage + shaded jar)
  installer -> printing-agent/dist/EmberAgentSetup-*.exe  (Inno Setup)
Requires: JDK 17 on PATH (java, jlink, jpackage), mvn, Inno Setup (iscc) for the last stage.
The agent installer bakes in no secrets/URLs -- the backend URL comes from POST /printing/agents/pair.
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$agentDir   = $PSScriptRoot
$distDir    = Join-Path $agentDir "dist"
$runtimeDir = Join-Path $distDir "runtime"

$appImageParent = Join-Path $distDir "app-image"
$appImageDir    = Join-Path $appImageParent "Ember Agent"
$installerDir   = Join-Path $agentDir "installer"

function Get-AgentVersion {
    $pom = Get-Content (Join-Path $agentDir "pom.xml") -Raw
    if ($pom -notmatch "<artifactId>printing-agent</artifactId>\s*<version>([^<]+)</version>") {
        throw "could not read <version> from printing-agent/pom.xml"
    }
    return $Matches[1] -replace "-SNAPSHOT",""
}

function Build-Runtime {
    Write-Host "== jlink runtime ==" -ForegroundColor Cyan
    $modules = (Get-Content (Join-Path $agentDir "jlink-modules.txt") |
                Where-Object { $_ -and -not $_.StartsWith("#") }) -join ","
    if (Test-Path $runtimeDir) { Remove-Item -Recurse -Force $runtimeDir }
    & jlink `
        --add-modules $modules `
        --strip-debug --no-header-files --no-man-pages `
        --compress=2 `
        --include-locales=en,es `
        --output $runtimeDir
    if ($LASTEXITCODE -ne 0) { throw "jlink failed ($LASTEXITCODE)" }
    & (Join-Path $runtimeDir "bin\java.exe") --version
    if ($LASTEXITCODE -ne 0) { throw "runtime java.exe is not runnable" }
}

function Build-AppImage {
    Write-Host "== app-image ==" -ForegroundColor Cyan
    if (-not (Test-Path $runtimeDir)) { Build-Runtime }

    Write-Host "-- mvn package --"
    & mvn -f (Join-Path $agentDir "pom.xml") -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

    $jar = Get-ChildItem (Join-Path $agentDir "target") -Filter "printing-agent-*.jar" |
           Where-Object { $_.Name -notmatch "original|sources|javadoc" } |
           Select-Object -First 1
    if (-not $jar) { throw "no shaded printing-agent-*.jar in printing-agent/target" }

    # jpackage needs the jar alone in an input dir under a stable name
    $inputDir = Join-Path $distDir "jpackage-input"
    if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
    Copy-Item $jar.FullName (Join-Path $inputDir "printing-agent.jar")

    if (Test-Path $appImageParent) { Remove-Item -Recurse -Force $appImageParent }
    & jpackage `
        --type app-image `
        --name "Ember Agent" `
        --app-version (Get-AgentVersion) `
        --vendor "Vanter" `
        --input $inputDir `
        --main-jar "printing-agent.jar" `
        --main-class "com.vanter.emberagent.Main" `
        --runtime-image $runtimeDir `
        --icon (Join-Path $installerDir "ember-agent.ico") `
        --java-options "-Dfile.encoding=UTF-8" `
        --dest $appImageParent
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed ($LASTEXITCODE)" }

    Copy-Item (Join-Path $installerDir "Iniciar Ember Agent.cmd") $appImageDir

    if (-not (Test-Path (Join-Path $appImageDir "Ember Agent.exe"))) { throw "app-image launcher missing" }
    Write-Host "app-image at $appImageDir" -ForegroundColor Green
}

function Build-Installer {
    Write-Host "== installer ==" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $appImageDir "Ember Agent.exe"))) { Build-AppImage }
    $iscc = (Get-Command iscc.exe -ErrorAction SilentlyContinue).Source
    if (-not $iscc) {
        $iscc = @(
            "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
            "${env:ProgramFiles}\Inno Setup 6\ISCC.exe",
            "${env:LOCALAPPDATA}\Programs\Inno Setup 6\ISCC.exe"
        ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    }
    if (-not $iscc -or -not (Test-Path $iscc)) { throw "Inno Setup (ISCC.exe) not found - install Inno Setup 6." }

    $version = Get-AgentVersion
    Push-Location $installerDir
    try {
        & $iscc "/DAppVersion=$version" "EmberAgent.iss"
        if ($LASTEXITCODE -ne 0) { throw "iscc failed ($LASTEXITCODE)" }
    } finally { Pop-Location }

    $out = Join-Path $distDir "EmberAgentSetup-$version.exe"
    if (-not (Test-Path $out)) { throw "installer not produced at $out" }
    Write-Host "installer: $out" -ForegroundColor Green
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
if ($Stage -in @("all","installer")) { Build-Installer }
Write-Host "Done ($Stage)." -ForegroundColor Green
