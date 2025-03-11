@echo off
title Obfuscator with Basic Config

echo.
echo Obfuscator with Basic Configuration
echo.
echo Processing input.jar...
echo.

echo Configuration file contents:
type basic-config.yml
echo.
echo Running obfuscator...
echo.

:: Get Java home path for libraries
FOR /F "tokens=*" %%a IN ('where java') DO SET JAVA_EXE=%%a
FOR %%i IN ("%JAVA_EXE%") DO SET JAVA_BIN_DIR=%%~dpi
FOR %%i IN ("%JAVA_BIN_DIR%..") DO SET JAVA_HOME=%%~fi

echo Using Java from: %JAVA_HOME%
echo.

:: Check for rt.jar in different locations (handles both JDK 8 and newer versions)
SET RT_JAR=

IF EXIST "%JAVA_HOME%\jre\lib\rt.jar" (
    SET RT_JAR=%JAVA_HOME%\jre\lib\rt.jar
    echo Found rt.jar at: %RT_JAR%
) ELSE IF EXIST "%JAVA_HOME%\lib\rt.jar" (
    SET RT_JAR=%JAVA_HOME%\lib\rt.jar
    echo Found rt.jar at: %RT_JAR%
) ELSE (
    echo Warning: Could not find rt.jar. System class references may not be processed correctly.
)

:: Run the obfuscator with the YAML configuration and Java libraries
IF DEFINED RT_JAR (
    echo Adding Java standard library to classpath
    java -jar obfuscator.jar --jarIn input.jar --jarOut protected-basic.jar --config basic-config.yml --libraries "%RT_JAR%"
) ELSE (
    echo Running without Java standard library
    java -jar obfuscator.jar --jarIn input.jar --jarOut protected-basic.jar --config basic-config.yml
)

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
echo Your protected file is available as "protected-basic.jar"
echo.
pause 