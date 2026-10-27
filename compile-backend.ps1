$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "                SLOTSYNC ZERO-DEPENDENCY COMPILER (JDK 23)                     " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host ""

$javac = "javac"
if (Test-Path "C:\Program Files\Java\jdk-23\bin\javac.exe") {
    $javac = "C:\Program Files\Java\jdk-23\bin\javac.exe"
}

# Ensure directories exist
$dirs = @(
    "backend/dsa-engine/target/classes",
    "backend/waiting-room/target/classes",
    "backend/slot-lock/target/classes",
    "backend/payment-worker/target/classes",
    "backend/api-gateway/target/classes",
    "backend/target/classes"
)
foreach ($d in $dirs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
    }
}

Write-Host "[1/6] Compiling dsa-engine..." -ForegroundColor Yellow
$dsaFiles = (Get-ChildItem -Recurse "backend/dsa-engine/src/main/java/*.java" | ForEach-Object { $_.FullName })
& $javac -d "backend/dsa-engine/target/classes" $dsaFiles

Write-Host "[2/6] Compiling waiting-room..." -ForegroundColor Yellow
$wrFiles = (Get-ChildItem -Recurse "backend/waiting-room/src/main/java/*.java" | ForEach-Object { $_.FullName })
& $javac -cp "backend/dsa-engine/target/classes" -d "backend/waiting-room/target/classes" $wrFiles

Write-Host "[3/6] Compiling slot-lock..." -ForegroundColor Yellow
$slFiles = (Get-ChildItem -Recurse "backend/slot-lock/src/main/java/*.java" | ForEach-Object { $_.FullName })
& $javac -cp "backend/dsa-engine/target/classes" -d "backend/slot-lock/target/classes" $slFiles

Write-Host "[4/6] Compiling payment-worker..." -ForegroundColor Yellow
$pwFiles = (Get-ChildItem -Recurse "backend/payment-worker/src/main/java/*.java" | ForEach-Object { $_.FullName })
& $javac -cp "backend/dsa-engine/target/classes;backend/slot-lock/target/classes" -d "backend/payment-worker/target/classes" $pwFiles

Write-Host "[5/6] Compiling api-gateway..." -ForegroundColor Yellow
$gwFiles = (Get-ChildItem -Recurse "backend/api-gateway/src/main/java/*.java" | ForEach-Object { $_.FullName })
& $javac -cp "backend/dsa-engine/target/classes" -d "backend/api-gateway/target/classes" $gwFiles

Write-Host "[6/6] Compiling root launcher & Demo CLI suite..." -ForegroundColor Yellow
$rootFiles = (Get-ChildItem -Recurse "backend/src/main/java/*.java" | ForEach-Object { $_.FullName })
$fullCP = "backend/dsa-engine/target/classes;backend/waiting-room/target/classes;backend/slot-lock/target/classes;backend/payment-worker/target/classes;backend/api-gateway/target/classes"
& $javac -cp $fullCP -d "backend/target/classes" $rootFiles

Write-Host ""
Write-Host "[SUCCESS] All backend microservices and Demo CLI compiled successfully!" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan
