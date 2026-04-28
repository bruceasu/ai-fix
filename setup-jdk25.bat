@echo off
REM Set JDK 25 as JAVA_HOME for this build session
set JAVA_HOME=C:\green\jdk-25.0.1+8
set PATH=%JAVA_HOME%\bin;%PATH%
set MAVEN_USER_HOME=d:\.m2
if not exist "%MAVEN_USER_HOME%" mkdir "%MAVEN_USER_HOME%"

echo Using JDK: %JAVA_HOME%
echo Using MAVEN_USER_HOME: %MAVEN_USER_HOME%
echo.
echo Java Version:
"%JAVA_HOME%\bin\java.exe" -version
