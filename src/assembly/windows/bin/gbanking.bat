@echo off
setlocal

set APP_HOME=%~dp0..
set LIB=%APP_HOME%\lib

where java >nul 2>nul
if errorlevel 1 (
    echo Fehler: Java wurde nicht gefunden.
    echo Bitte installiere Java 21 und stelle sicher, dass "java" im PATH verfuegbar ist.
    pause
    exit /b 1
)

java -version 2>&1 | findstr /r /c:"version \"21\." >nul
if errorlevel 1 (
    echo Warnung: Es wurde kein Java 21 erkannt.
    echo Die Anwendung wurde fuer Java 21 gebaut und startet moeglicherweise nicht korrekt.
)

java --module-path "%LIB%" --add-modules javafx.controls -cp "%LIB%\*" de.gbanking.GBanking %*
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Die Anwendung wurde mit Fehlercode %EXIT_CODE% beendet.
    pause
)

endlocal & exit /b %EXIT_CODE%