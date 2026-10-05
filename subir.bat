@echo off
setlocal
title Magic and Sorcery - Subir y Compilar

echo ========================================================
echo         MAGIC AND SORCERY - SUBIR A GITHUB
echo ========================================================
echo.
set /p mensaje="Describe brevemente tus cambios (o presiona Enter): "
if "%mensaje%"=="" set mensaje=Actualizacion de Magic and Sorcery (%date% %time%)

echo.
echo [1/3] Guardando cambios locales...
git add .
git commit -m "%mensaje%"

echo.
echo [2/3] Subiendo cambios a GitHub...
git push origin main
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [AVISO] Hubo un problema al subir a GitHub.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Compilando mod y actualizando tu Launcher...
call gradlew.bat build -x test

echo.
echo ========================================================
echo   [COMPLETADO] Cambios subidos a GitHub y mod compilado!
echo ========================================================
echo.
pause
