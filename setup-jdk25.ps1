# Set JDK 25 as JAVA_HOME for this Maven build
# PowerShell version
# powershell -ExecutionPolicy Bypass -File setup-jdk25.ps1

$env:JAVA_HOME = "C:\green\jdk-25.0.1+8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:MAVEN_USER_HOME = "d:\.m2"

if (-not (Test-Path $env:MAVEN_USER_HOME)) {
    New-Item -ItemType Directory -Path $env:MAVEN_USER_HOME | Out-Null
}

Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Using MAVEN_USER_HOME: $env:MAVEN_USER_HOME" -ForegroundColor Green
Write-Host "Java Version:" -ForegroundColor Green
& "$env:JAVA_HOME\bin\java.exe" -version

Write-Host "`nMaven Version:" -ForegroundColor Green
.\mvnw.cmd -s .mvn-local-settings.xml --version

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Environment Ready!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "You can now run Maven commands, for example:" -ForegroundColor Yellow
Write-Host "  .\mvnw.cmd -s .mvn-local-settings.xml clean compile" -ForegroundColor White
Write-Host "  .\mvnw.cmd -s .mvn-local-settings.xml clean package -DskipTests" -ForegroundColor White
Write-Host "`nTo build and run:" -ForegroundColor Yellow
Write-Host "  .\mvnw.cmd -s .mvn-local-settings.xml -q package -DskipTests" -ForegroundColor White
Write-Host "  java -jar target\app.jar --help" -ForegroundColor White
