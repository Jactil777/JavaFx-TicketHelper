# ========================================
# JavaFx-TicketHelper Windows Build Script
# ========================================
$ProgressPreference = "SilentlyContinue"
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  JavaFx-TicketHelper Windows Build Tool" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# 1. Check Java 17
Write-Host "[1/6] Checking Java environment..." -ForegroundColor Yellow
$jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackageCmd) {
    Write-Host "  ERROR: jpackage not found (requires JDK 17+)" -ForegroundColor Red
    exit 1
}
$jdk17Path = Split-Path (Split-Path $jpackageCmd.Source)
Write-Host "  JDK 17 detected: $jdk17Path" -ForegroundColor Green
$env:JAVA_HOME = $jdk17Path
$javaExe = Join-Path $jdk17Path "bin\java.exe"
$javaVersionOutput = & $javaExe -version 2>&1
$javaVersionLine = ($javaVersionOutput | Select-String "version" | Select-Object -First 1).Line
Write-Host "  $javaVersionLine" -ForegroundColor Green

# 2. Check Maven
Write-Host "`n[2/6] Checking Maven..." -ForegroundColor Yellow
$mvnVersionOutput = mvn -version 2>&1
$mvnVersionLine = ($mvnVersionOutput | Select-String "Apache Maven" | Select-Object -First 1).Line
Write-Host "  $mvnVersionLine" -ForegroundColor Green

# 3. Check WiX Toolset
Write-Host "`n[3/6] Checking WiX Toolset..." -ForegroundColor Yellow
$wixFound = $false
$candleCmd = Get-Command candle -ErrorAction SilentlyContinue
if ($candleCmd) {
    Write-Host "  WiX found in PATH" -ForegroundColor Green
    $wixFound = $true
} else {
    $possiblePaths = @(
        "C:\Program Files (x86)\WiX Toolset v3.14\bin",
        "C:\Program Files (x86)\WiX Toolset v3.11\bin",
        "C:\Program Files\WiX Toolset v3.14\bin",
        "C:\Program Files\WiX Toolset v3.11\bin"
    )
    foreach ($path in $possiblePaths) {
        $candlePath = Join-Path $path "candle.exe"
        if (Test-Path $candlePath) {
            Write-Host "  WiX detected: $path" -ForegroundColor Green
            $env:PATH = "$path;$env:PATH"
            $wixFound = $true
            break
        }
    }
}
if (-not $wixFound) {
    Write-Host "  WARN: WiX Toolset not found, will only output JAR" -ForegroundColor Yellow
    Write-Host "  Download: https://github.com/wixtoolset/wix3/releases" -ForegroundColor Cyan
}

# 4. Clean old files
Write-Host "`n[4/6] Cleaning old files..." -ForegroundColor Yellow

# Copy icon to temp location before cleaning (avoids file lock issues)
$iconSource = "src/main/resources/images/JavaFx-TicketHelper-iocn.png"
$iconTemp = "$env:TEMP/tickethelper-icon.png"
if (Test-Path $iconSource) {
    Copy-Item -Path $iconSource -Destination $iconTemp -Force
    Write-Host "  Icon copied to temp" -ForegroundColor Gray
}

if (Test-Path "dist") {
    Remove-Item -Recurse -Force "dist" -ErrorAction SilentlyContinue
    if (Test-Path "dist") {
        Write-Host "  WARN: dist/ has locked files, please close the running app and retry" -ForegroundColor Yellow
    } else {
        Write-Host "  Deleted dist directory" -ForegroundColor Gray
    }
}
if (Test-Path "target") {
    Remove-Item -Recurse -Force "target" -ErrorAction SilentlyContinue
    if (Test-Path "target") {
        Write-Host "  WARN: target/ has locked files, please close the running app and retry" -ForegroundColor Yellow
    } else {
        Write-Host "  Deleted target directory" -ForegroundColor Gray
    }
}

# 5. Maven build
Write-Host "`n[5/6] Maven building..." -ForegroundColor Yellow
Write-Host "  Running: mvn clean package -DskipTests" -ForegroundColor Gray
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
mvn clean package -DskipTests 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n  Build failed, re-running with output..." -ForegroundColor Red
    mvn clean package -DskipTests
    exit 1
}
Write-Host "  Build success" -ForegroundColor Green

# 6. Collect dependencies and generate installer
Write-Host "`n[6/6] Collecting dependencies and generating installer..." -ForegroundColor Yellow
mvn dependency:copy-dependencies -DoutputDirectory=target/libs 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Dependency collection failed" -ForegroundColor Red
    exit 1
}

$jarPath = "target\tickethelper-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "  ERROR: $jarPath not found" -ForegroundColor Red
    exit 1
}

if ($wixFound) {
    Write-Host "  Running jpackage (may take 2-3 minutes)..." -ForegroundColor Gray
    jpackage `
        --type msi `
        --name "JavaFx-TicketHelper" `
        --app-version "1.0.0" `
        --dest dist `
        --input target `
        --main-jar tickethelper-1.0.0-SNAPSHOT.jar `
        --main-class com.jactil.javafx.tickethelper.Launcher `
        --icon $iconTemp `
        --java-options "-Dfile.encoding=UTF-8" `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n  jpackage failed, re-running with output..." -ForegroundColor Red
        jpackage `
            --type msi `
            --name "JavaFx-TicketHelper" `
            --app-version "1.0.0" `
            --dest dist `
            --input target `
            --main-jar tickethelper-1.0.0-SNAPSHOT.jar `
            --main-class com.jactil.javafx.tickethelper.Launcher `
            --icon $iconTemp `
            --java-options "-Dfile.encoding=UTF-8" `
            --win-dir-chooser `
            --win-menu `
            --win-shortcut
        exit 1
    }
    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host "  Build complete!" -ForegroundColor Green
    Write-Host "========================================`n" -ForegroundColor Green
    Write-Host "  Installer: dist\JavaFx-TicketHelper-1.0.0.msi" -ForegroundColor Cyan
    if (Test-Path "dist\JavaFx-TicketHelper-1.0.0.msi") {
        $size = (Get-Item "dist\JavaFx-TicketHelper-1.0.0.msi").Length / 1MB
        Write-Host "  Size: $([Math]::Round($size, 1)) MB" -ForegroundColor Gray
    }
} else {
    if (-not (Test-Path "dist")) { New-Item -ItemType Directory -Path "dist" | Out-Null }
    Copy-Item -Path $jarPath -Destination "dist/" -Force
    Write-Host "`n========================================" -ForegroundColor Yellow
    Write-Host "  JAR build complete (no WiX, installer not generated)" -ForegroundColor Yellow
    Write-Host "========================================`n" -ForegroundColor Yellow
    Write-Host "  JAR: dist\tickethelper-1.0.0-SNAPSHOT.jar" -ForegroundColor Cyan
}

Write-Host "`n  Ready to share with users!`n" -ForegroundColor Yellow
