@echo off
title Obfuscator by superblaubeere27

echo.
echo Obfuscator by superblaubeere27
echo.
echo Processing input.jar...
echo.

:: Run the obfuscator with the standard configuration
java -jar obfuscator.jar --jarIn input.jar --jarOut protected.jar --config config.json

:: Check if there was an error
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Error obfuscating file. Please check the logs above.
    echo.
    pause
    exit /b 1
)

echo.
echo Obfuscation completed successfully!
echo Your protected file is available as "protected.jar"
echo.
pause 