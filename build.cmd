@echo off
rem Compilacion local (EliteBook) con carga limitada. Uso: build.cmd <tareas gradle>
set "JAVA_HOME=C:\Users\vidadomu\.aki\toolchains\corretto17-20260831"
set "ANDROID_HOME=C:\Users\vidadomu\.aki\toolchains\android-sdk-35"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
call gradlew.bat --no-daemon --console=plain --max-workers=2 %*
