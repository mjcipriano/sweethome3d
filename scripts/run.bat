@echo off
setlocal

set ROOT_DIR=%~dp0..
set JAR_PATH=%ROOT_DIR%\install\SweetHome3D-7.5.jar

if not exist "%JAR_PATH%" (
  echo Missing %JAR_PATH%. Run "make jar" first.
  exit /b 1
)

if "%JAVA_OPTS%"=="" set JAVA_OPTS=-Xmx1024m --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/com.apple.eio=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED
if "%JAVA_LIB_PATH%"=="" set JAVA_LIB_PATH=%ROOT_DIR%\lib\windows\x64;%ROOT_DIR%\lib\java3d-1.6\windows\amd64;%ROOT_DIR%\lib\yafaray\windows\x64

java %JAVA_OPTS% -Djava.library.path="%JAVA_LIB_PATH%" -Djogamp.gluegen.UseTempJarCache=false -jar "%JAR_PATH%" %*

endlocal
