# Builds the tenant frontend and copies it into backend/src/main/resources/static/, so the
# "hub" profile's Spring Boot server can serve the compiled React app directly (see
# HubWebConfig.addResourceHandlers). Run this before `mvnw package` whenever the frontend has
# changed and you need a fresh Hub-distributable jar; the copied output is gitignored, same as
# frontend/dist itself, and is NOT wired into the Maven build yet (manual step until the real
# jpackage/jlink packaging lands).
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$staticDir = Join-Path $repoRoot "backend\src\main\resources\static"

Write-Host "Building frontend..."
Push-Location $frontendDir
try {
    pnpm run build:hub
    if ($LASTEXITCODE -ne 0) { throw "pnpm run build:hub failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "Copying dist/ into backend static resources..."
if (Test-Path $staticDir) {
    Remove-Item -Recurse -Force $staticDir
}
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
Copy-Item -Recurse -Path (Join-Path $frontendDir "dist\*") -Destination $staticDir

$envConfig = @'
window.ENV = {
  EMBW_API_URL: window.location.origin,
  EMBW_WS_URL: (window.location.protocol === "https:" ? "wss://" : "ws://") + window.location.host + "/ws",
};
'@
Set-Content -Path (Join-Path $staticDir "env-config.js") -Value $envConfig -Encoding ascii -NoNewline

Write-Host "Done. Frontend bundled into $staticDir"
