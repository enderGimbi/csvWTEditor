@echo off
chcp 65001 > nul

if exist "dist\csvWTEditor" rmdir /s /q "dist\csvWTEditor"

set "JDK_PATH=C:\Users\Kirill\.jdks\ms-21.0.11"

"%JDK_PATH%\bin\jpackage.exe" ^
  --type app-image ^
  --name "csvWTEditor" ^
  --input "out\artifacts\csvWTEditor_jar" ^
  --main-jar "csvWTEditor.jar" ^
  --main-class "com.enderGimbi.wtlocal.AppLauncher" ^
  --runtime-image "%JDK_PATH%" ^
  --icon "app.ico" ^
  --dest "dist"

echo.
echo =========================================
echo  EXE утилита успешно собрана в папке dist!
echo =========================================
pause