@echo off
echo ===================================================
echo   SUBIENDO CAMBIOS DE MAGIC AND SORCERY A GITHUB
echo ===================================================
echo.
set /p mensaje="Escribe que cambiaste (o presiona Enter para mensaje automatico): "
if "%mensaje%"=="" set mensaje=Actualizacion de Magic and Sorcery (%date% %time%)
git add .
git commit -m "%mensaje%"
echo.
echo Subiendo cambios a GitHub...
git push origin main
echo.
echo Compilando el mod y actualizando tu Launcher...
call gradlew.bat build -x test
echo.
echo ===================================================
echo   TODO LISTO! Tus cambios ya estan en GitHub y en tu juego.
echo ===================================================
pause
