<#
Builds the Ember Hub Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> ember-hub/dist/runtime            (jlink JRE image)
  appimage  -> ember-hub/dist/app-image          (jpackage + assembled binaries)   [Task 7]
  installer -> ember-hub/dist/EmberHubSetup-*.exe (Inno Setup)                      [Task 8]
Requires: JDK 17 on PATH (java, jlink, jpackage), pnpm, mvn, Inno Setup (iscc).
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$hubDir     = $PSScriptRoot
$distDir    = Join-Path $hubDir "dist"
$runtimeDir = Join-Path $distDir "runtime"

function Build-Runtime {
    Write-Host "== jlink runtime ==" -ForegroundColor Cyan
    $modules = (Get-Content (Join-Path $hubDir "jlink-modules.txt") |
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

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
# appimage / installer stages added in later tasks
Write-Host "Done ($Stage)." -ForegroundColor Green
