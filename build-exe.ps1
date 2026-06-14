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

# 方式1：检查 PATH 中是否已有 candle
$candleCmd = Get-Command candle -ErrorAction SilentlyContinue
if ($candleCmd) {
    Write-Host "  WiX found in PATH: $($candleCmd.Source)" -ForegroundColor Green
    $wixFound = $true
}

# 方式2：从 Windows 注册表查找 WiX 安装路径
if (-not $wixFound) {
    $regPaths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    foreach ($regRoot in $regPaths) {
        if (Test-Path $regRoot) {
            $wixReg = Get-ChildItem $regRoot -ErrorAction SilentlyContinue |
                Where-Object { $_.GetValue("DisplayName") -like "*WiX*" } |
                Select-Object -First 1
            if ($wixReg) {
                $installDir = $wixReg.GetValue("InstallLocation")
                if ($installDir -and (Test-Path (Join-Path $installDir "bin\candle.exe"))) {
                    $wixBinPath = Join-Path $installDir "bin"
                    Write-Host "  WiX detected (registry): $wixBinPath" -ForegroundColor Green
                    $env:PATH = "$wixBinPath;$env:PATH"
                    $wixFound = $true
                    break
                }
            }
        }
    }
}

# 方式3：搜索常见安装目录（含自定义路径）
if (-not $wixFound) {
    $searchRoots = @(
        "C:\Program Files (x86)",
        "C:\Program Files",
        "D:\Program Files (x86)",
        "D:\Program Files",
        "$env:LOCALAPPDATA\Programs"
    )
    foreach ($root in $searchRoots) {
        if (Test-Path $root) {
            $found = Get-ChildItem -Path $root -Filter "candle.exe" -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($found) {
                $wixBinPath = $found.DirectoryName
                Write-Host "  WiX detected (search): $wixBinPath" -ForegroundColor Green
                $env:PATH = "$wixBinPath;$env:PATH"
                $wixFound = $true
                break
            }
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
$iconTempPng = "$env:TEMP/tickethelper-icon.png"
$iconTempIco = "$env:TEMP/tickethelper-icon.ico"
$iconForJpackage = $iconTempIco

if (Test-Path $iconSource) {
    Copy-Item -Path $iconSource -Destination $iconTempPng -Force
    Write-Host "  Icon copied to temp" -ForegroundColor Gray

    # Convert PNG to ICO (jpackage on Windows requires proper .ico format)
    try {
        Add-Type -AssemblyName System.Drawing
        $bmp = [System.Drawing.Bitmap]::new($iconTempPng)
        # Resize to 256x256
        $resized = [System.Drawing.Bitmap]::new($bmp, 256, 256)

        # Extract PNG bytes
        $pngStream = [System.IO.MemoryStream]::new()
        $resized.Save($pngStream, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngBytes = $pngStream.ToArray()
        $pngStream.Close()

        # Write proper ICO file: 6-byte header + 16-byte entry + PNG data
        $icoStream = [System.IO.FileStream]::new($iconTempIco, [System.IO.FileMode]::Create)
        $icoWriter = [System.IO.BinaryWriter]::new($icoStream)
        $dataOffset = 22  # 6 (header) + 16 (entry)
        # ICO header
        $icoWriter.Write([uint16]0)       # Reserved
        $icoWriter.Write([uint16]1)       # Type: ICO
        $icoWriter.Write([uint16]1)       # Image count
        # ICO entry
        $icoWriter.Write([byte]0)         # Width (0 = 256)
        $icoWriter.Write([byte]0)         # Height (0 = 256)
        $icoWriter.Write([byte]0)         # Color palette
        $icoWriter.Write([byte]0)         # Reserved
        $icoWriter.Write([uint16]1)       # Color planes
        $icoWriter.Write([uint16]32)      # Bits per pixel
        $icoWriter.Write([uint32]$pngBytes.Length)  # Data size
        $icoWriter.Write([uint32]$dataOffset)       # Data offset
        # PNG image data
        $icoWriter.Write($pngBytes)
        $icoWriter.Close()
        $icoStream.Close()
        $resized.Dispose()
        $bmp.Dispose()
        Write-Host "  Icon converted to ICO (256x256)" -ForegroundColor Gray
    } catch {
        Write-Host "  WARN: PNG->ICO conversion failed: $_" -ForegroundColor Yellow
        $iconForJpackage = $null
    }
} else {
    Write-Host "  WARN: Icon file not found: $iconSource" -ForegroundColor Yellow
    $iconForJpackage = $null
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
    $jpackageArgs = @(
        "--type", "msi",
        "--name", "JavaFx-TicketHelper",
        "--app-version", "1.0.0",
        "--dest", "dist",
        "--input", "target",
        "--main-jar", "tickethelper-1.0.0-SNAPSHOT.jar",
        "--main-class", "com.jactil.javafx.tickethelper.Launcher",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut"
    )
    if ($iconForJpackage -and (Test-Path $iconForJpackage)) {
        $jpackageArgs += @("--icon", $iconForJpackage)
        Write-Host "  Using icon: $iconForJpackage" -ForegroundColor Gray
    }
    & jpackage @jpackageArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n  jpackage failed, re-running with output..." -ForegroundColor Red
        & jpackage @jpackageArgs
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
