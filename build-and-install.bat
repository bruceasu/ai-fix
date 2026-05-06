@echo off
setlocal
call setup-jdk25.bat
call mvnw.cmd clean package -DskipTests
move /Y target\ai-fix-1.0-SNAPSHOT.jar c:\green\ai-fix.jar
echo Build and installation complete.
endlocal