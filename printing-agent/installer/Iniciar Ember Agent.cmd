@echo off
rem Ember Agent launcher shim (ships next to the jpackage app launcher).
rem Forwards any args -- the Start-menu/desktop shortcut passes none, the
rem {commonstartup} shortcut passes --tray.
start "" "%~dp0Ember Agent.exe" %*
