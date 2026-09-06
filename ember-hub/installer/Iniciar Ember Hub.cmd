@echo off
setlocal EnableDelayedExpansion
rem Ember Hub launcher shim. Loads runtime config from %ProgramData%\EmberHub\hub.env,
rem exports it, and starts the jpackage app with the hub profile.

set "HUB_ENV=%ProgramData%\EmberHub\hub.env"
if not exist "%HUB_ENV%" (
    echo No se encontro "%HUB_ENV%". Reinstala Ember Hub.
    pause
    exit /b 1
)

set "SPRING_PROFILES_ACTIVE=hub"
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%HUB_ENV%") do (
    if not "%%~A"=="" set "%%~A=%%~B"
)

set "LOGDIR=%ProgramData%\EmberHub\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

rem %~dp0 is "...\Ember Hub\" (this shim ships next to the app launcher)
"%~dp0Ember Hub.exe" --autostart >> "%LOGDIR%\hub.out.log" 2>> "%LOGDIR%\hub.err.log"
