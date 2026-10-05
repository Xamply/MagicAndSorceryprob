@echo off
echo ===================================================
echo   DESCARGANDO ULTIMA VERSION DESDE GITHUB
echo ===================================================
echo.
git pull origin main
echo.
echo Compilando el mod y actualizando tu Launcher...
call gradlew.bat build -x test
echo.
echo ===================================================
echo   TODO LISTO! Tienes la version mas reciente para jugar.
echo ===================================================
pause
