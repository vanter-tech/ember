<#
Downloads and verifies the portable binaries bundled into the Hub installer:
  - PostgreSQL 16.6-1  (EDB "binaries only", Windows x64)
  - MinIO server       (dated Windows release)
Caches downloads under ember-hub/.vendor-cache/ and stages an extracted copy
under ember-hub/.vendor-cache/staging/ for build-installer.ps1 to pick up.
On first run, if a *_SHA256 constant below is "REPLACE_ME", the script prints
the computed hash and stops — paste it in and commit, then re-run.
#>
$ErrorActionPreference = "Stop"
$hubDir   = $PSScriptRoot
$cacheDir = Join-Path $hubDir ".vendor-cache"
$stageDir = Join-Path $cacheDir "staging"

$PG_URL       = "https://get.enterprisedb.com/postgresql/postgresql-16.6-1-windows-x64-binaries.zip"
$PG_SHA256    = "6a1bfb6435b13d9563ae481445c70ac2a19846bd8a430b12903b408eec300f9b"
$MINIO_URL    = "https://dl.min.io/server/minio/release/windows-amd64/archive/minio.RELEASE.2025-04-22T22-12-26Z"
$MINIO_SHA256 = "2ceb3b3d68bdf1c4def9702cb02c5c8adb235197d1c8f2eaad24136833ab9a57"

function Get-Verified([string]$url, [string]$expected, [string]$outFile) {
    if (-not (Test-Path $outFile)) {
        Write-Host "downloading $url"
        Invoke-WebRequest -Uri $url -OutFile $outFile
    }
    $actual = (Get-FileHash $outFile -Algorithm SHA256).Hash.ToLower()
    if ($expected -eq "REPLACE_ME") {
        throw "SHA256 for $(Split-Path $outFile -Leaf) is $actual - paste it into fetch-vendor-binaries.ps1 and re-run."
    }
    if ($actual -ne $expected.ToLower()) {
        throw "SHA256 mismatch for $outFile`n  expected $expected`n  actual   $actual"
    }
}

New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
if (Test-Path $stageDir) { Remove-Item -Recurse -Force $stageDir }
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null

# --- PostgreSQL ---
$pgZip = Join-Path $cacheDir "postgresql-16.6-1-windows-x64-binaries.zip"
Get-Verified $PG_URL $PG_SHA256 $pgZip
$pgTmp = Join-Path $cacheDir "pg-extract"
if (Test-Path $pgTmp) { Remove-Item -Recurse -Force $pgTmp }
Expand-Archive -Path $pgZip -DestinationPath $pgTmp
# the zip contains a top-level "pgsql/" folder
$pgStaged = Join-Path $stageDir "pgsql"
Copy-Item -Recurse (Join-Path $pgTmp "pgsql") $pgStaged
Remove-Item -Recurse -Force $pgTmp
# The EDB "binaries" package ships pgAdmin 4 (~630 MB), StackBuilder, docs, headers and
# debug symbols — the Hub only needs bin/ + lib/ + share/. Drop the rest.
foreach ($drop in @("pgAdmin 4", "StackBuilder", "doc", "include", "symbols", "pgAdmin4.exe")) {
    $p = Join-Path $pgStaged $drop
    if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}
& (Join-Path $stageDir "pgsql\bin\initdb.exe") --version
if ($LASTEXITCODE -ne 0) { throw "staged initdb.exe is not runnable" }

# --- MinIO ---
$minioExe = Join-Path $cacheDir "minio.exe"
Get-Verified $MINIO_URL $MINIO_SHA256 $minioExe
New-Item -ItemType Directory -Force -Path (Join-Path $stageDir "minio") | Out-Null
Copy-Item $minioExe (Join-Path $stageDir "minio\minio.exe")
& (Join-Path $stageDir "minio\minio.exe") --version
if ($LASTEXITCODE -ne 0) { throw "staged minio.exe is not runnable" }

Write-Host "Vendor binaries staged under $stageDir" -ForegroundColor Green
