@echo off
REM ============================================================
REM mysql-go build script (Windows)
REM ============================================================
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

if not exist bin mkdir bin

echo === Building mysql-go (Windows amd64) ===
set GOOS=windows
set GOARCH=amd64
set CGO_ENABLED=0
go build -ldflags="-s -w" -o bin\mysql-go.exe .
if errorlevel 1 (
    echo ERROR: Windows build failed
    exit /b 1
)
echo Built: bin\mysql-go.exe

echo === Cross-compiling mysql-go (Linux amd64) ===
set GOOS=linux
set GOARCH=amd64
set CGO_ENABLED=0
go build -ldflags="-s -w" -o bin\mysql-go .
if errorlevel 1 (
    echo ERROR: Linux build failed
    exit /b 1
)
echo Built: bin\mysql-go

echo === Done ===
endlocal
