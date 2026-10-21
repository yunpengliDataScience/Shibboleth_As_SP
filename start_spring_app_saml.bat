:: Get the directory where the script is actually located
set "BASE_DIR=%~dp0"
:: Remove the trailing backslash
set "BASE_DIR=%BASE_DIR:~0,-1%"

:: set "JAVA_HOME=%BASE_DIR%\jdk-21.0.8"
set "JAVA_HOME=C:\Java\jdk-21"
echo JAVA_HOME is set to: %JAVA_HOME%

set "APP1_HOME=%BASE_DIR%\SpringBoot_SAML_App"
echo APP1_HOME is set to: %APP1_HOME%

call java -jar %APP1_HOME%\target\SpringBoot_SAML_App.jar


