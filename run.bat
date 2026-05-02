@echo off
setlocal
cd /d "%~dp0"
javac DAW\src\*.java
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
java -cp DAW\src SequencerTest
pause
