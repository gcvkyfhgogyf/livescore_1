@echo off
set GRADLE_VERSION=8.7
set BASE=%USERPROFILE%\.gradle\bootstrap
set DIST=%BASE%\gradle-%GRADLE_VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VERSION% ...
  powershell -NoProfile -Command "New-Item -ItemType Directory -Force -Path $env:USERPROFILE\.gradle\bootstrap | Out-Null; $z = Join-Path $env:TEMP \"gradle-8.7.zip\"; Invoke-WebRequest https://services.gradle.org/distributions/gradle-8.7-bin.zip -OutFile $z; Expand-Archive -Path $z -DestinationPath $env:USERPROFILE\.gradle\bootstrap -Force"
)
call "%DIST%\bin\gradle.bat" -p "%~dp0." %*
