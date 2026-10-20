@echo off
setlocal

REM ---------------------------------------------------------------------------
REM Starts a locally extracted Keycloak server for this SAML demo.
REM
REM Before running this script, set KEYCLOAK_HOME to your Keycloak directory.
REM Example:
REM   set KEYCLOAK_HOME=C:\tools\keycloak-26.7.1
REM   scripts\start-keycloak.bat
REM ---------------------------------------------------------------------------

:: Get the directory where the script is actually located
set "BASE_DIR=%~dp0"
:: Remove the trailing backslash
set "BASE_DIR=%BASE_DIR:~0,-1%"

set "KEYCLOAK_HOME=%BASE_DIR%\keycloak-26.7.1"
set JAVA_HOME=C:\Java\jdk-21


echo Starting Keycloak at https://localhost:9991 ...
echo Demo bootstrap admin: admin / admin

call "%KEYCLOAK_HOME%\bin\kc.bat" start-dev ^
  --https-port=9991 ^
  --http-host=0.0.0.0 ^
  --https-certificate-file=.\keycloak-certs\keycloak.crt ^
  --https-certificate-key-file=.\keycloak-certs\keycloak.key ^
  --https-client-auth=request ^
  --bootstrap-admin-username=admin ^
  --bootstrap-admin-password=admin
  