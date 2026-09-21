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

# The shared index.html loads env-config.js with a RELATIVE src (needed so --base=/app/ works).
# On a hard refresh (F5) of any inner route, e.g. /app/waiter/tables, the browser then asks for
# /app/waiter/env-config.js; HubWebConfig's SPA fallback answers that with index.html (HTML), the
# script never runs, window.ENV stays undefined and the API client falls back to the baked-in
# http://localhost:8080/v1 - a prefix the Hub does not have, or another PC's localhost - so every
# call fails ("Failed to fetch", blank page). Pin the tag to the real path. Same fix the cloud
# build applies in frontend/scripts/gen-env-config.mjs.
$indexPath = Join-Path $staticDir "index.html"
$indexHtml = [System.IO.File]::ReadAllText($indexPath)
if (-not $indexHtml.Contains('src="env-config.js"')) {
    throw "${indexPath}: expected <script src=""env-config.js""> not found - did frontend/index.html change?"
}
[System.IO.File]::WriteAllText(
    $indexPath,
    $indexHtml.Replace('src="env-config.js"', 'src="/app/env-config.js"'),
    (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Done. Frontend bundled into $staticDir"
