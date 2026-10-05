@echo off
setlocal
title Magic and Sorcery - Actualizar y Compilar

echo ========================================================
echo       MAGIC AND SORCERY - ACTUALIZADOR AUTOMATICO
echo ========================================================
echo.
echo [1/2] Descargando ultimos cambios de GitHub...
git pull origin main
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [AVISO] Hubo un problema al descargar de GitHub.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Compilando mod y desplegando en Minecraft...
echo Esto tomara unos segundos...
call gradlew.bat build -x test
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================================
    echo   [ERROR] Fallo la compilacion. Revisa el mensaje arriba.
    echo ========================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo   [COMPLETADO] Nueva version compilada e instalada!
echo   El archivo .jar ya esta en tu carpeta de mods.
echo   Ya puedes abrir el Launcher y jugar.
echo ========================================================
echo.
pause
