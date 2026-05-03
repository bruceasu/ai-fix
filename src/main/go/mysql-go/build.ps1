# ============================================================
# mysql-go build script (PowerShell)
# ============================================================
$ErrorActionPreference = "Stop"

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path bin)) { New-Item -ItemType Directory -Path bin | Out-Null }

    Write-Host "=== Building mysql-go (Windows amd64) ==="
    $env:GOOS = "windows"
    $env:GOARCH = "amd64"
    $env:CGO_ENABLED = "0"
    go build -o bin/mysql-go.exe .
    if ($LASTEXITCODE -ne 0) { throw "Windows build failed" }
    Write-Host "Built: bin/mysql-go.exe"

    Write-Host "=== Cross-compiling mysql-go (Linux amd64) ==="
    $env:GOOS = "linux"
    $env:GOARCH = "amd64"
    $env:CGO_ENABLED = "0"
    go build -o bin/mysql-go .
    if ($LASTEXITCODE -ne 0) { throw "Linux build failed" }
    Write-Host "Built: bin/mysql-go"

    Write-Host "=== Done ==="
} finally {
    # Restore GOOS to windows so subsequent go commands work normally
    $env:GOOS = "windows"
    Pop-Location
}
