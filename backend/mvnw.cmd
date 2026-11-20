@echo off
setlocal
set "MVNW_MVN="
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVNW_MVN=%MAVEN_HOME%\bin\mvn.cmd"
if defined MVNW_MVN goto run
for /d %%G in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16\*") do (
  if exist "%%~fG\bin\mvn.cmd" set "MVNW_MVN=%%~fG\bin\mvn.cmd"
)
if defined MVNW_MVN goto run
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0.mvn\wrapper\bootstrap.ps1" %*
exit /b %ERRORLEVEL%
:run
call "%MVNW_MVN%" %*
exit /b %ERRORLEVEL%
