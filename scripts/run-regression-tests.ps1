param(
    [string]$UnitTestTask = ":app:testDebugUnitTest",
    [string]$UiTestTask = ":app:connectedDebugAndroidTest",
    [string]$KotlinCompilerExecutionStrategy = "in-process",
    [switch]$AllowSandbox
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-IsSandboxEnvironment {
    $codexSandbox = $env:CODEX_SANDBOX_NETWORK_DISABLED
    if ([string]::IsNullOrWhiteSpace($codexSandbox)) {
        return $false
    }
    return $codexSandbox -eq "1"
}

if ((Test-IsSandboxEnvironment) -and -not $AllowSandbox) {
    throw "Sandbox environment detected. To avoid hanging execution, this script exits fast by default. Re-run with -AllowSandbox if you intentionally execute in sandbox."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $projectRoot "docs/build-logs"
$tempDir = Join-Path $projectRoot ".tmp"
$androidUserHomeDir = Join-Path $projectRoot ".android"
$env:GRADLE_USER_HOME = Join-Path $projectRoot ".gradle-user-home"
if (-not (Test-Path $logDir)) {
    New-Item -Path $logDir -ItemType Directory | Out-Null
}
if (-not (Test-Path $tempDir)) {
    New-Item -Path $tempDir -ItemType Directory | Out-Null
}
if (-not (Test-Path $androidUserHomeDir)) {
    New-Item -Path $androidUserHomeDir -ItemType Directory | Out-Null
}
$env:ANDROID_USER_HOME = $androidUserHomeDir
Remove-Item Env:ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
$env:TEMP = $tempDir
$env:TMP = $tempDir

function Get-AdbPath {
    $adbFromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbFromPath) {
        return $adbFromPath.Source
    }

    $localPropertiesPath = Join-Path $projectRoot "local.properties"
    if (-not (Test-Path $localPropertiesPath)) {
        return $null
    }

    $sdkDirLine = Get-Content -Path $localPropertiesPath |
        Where-Object { $_ -like "sdk.dir=*" } |
        Select-Object -First 1
    if (-not $sdkDirLine) {
        return $null
    }

    $sdkDirRaw = $sdkDirLine.Substring("sdk.dir=".Length)
    $sdkDir = $sdkDirRaw.Replace("\:", ":").Replace("\\", "\")
    $adbPath = Join-Path $sdkDir "platform-tools\adb.exe"
    if (Test-Path $adbPath) {
        return $adbPath
    }
    return $null
}

function Assert-DeviceOnline {
    param([string]$AdbPath)

    if (-not $AdbPath) {
        throw "adb was not found. Add Android SDK platform-tools to PATH or verify sdk.dir in local.properties."
    }

    $devicesOutput = & $AdbPath devices
    $onlineDevice = $devicesOutput |
        Where-Object { $_ -match "^\S+\s+device$" } |
        Select-Object -First 1
    if (-not $onlineDevice) {
        throw "No connected device/emulator found. Start an emulator before running UI tests."
    }
}

function Invoke-GradleTaskWithLog {
    param(
        [string]$TaskName,
        [string]$LogFileName,
        [string[]]$AdditionalArguments = @()
    )

    $gradleWrapperPath = Join-Path $projectRoot "gradlew.bat"
    $logPath = Join-Path $logDir $LogFileName
    $gradleArguments = @()
    if ($AdditionalArguments.Count -gt 0) {
        $gradleArguments += $AdditionalArguments
    }
    $gradleArguments += $TaskName

    Write-Host "Run: .\\gradlew.bat $($gradleArguments -join ' ')"
    & $gradleWrapperPath @gradleArguments *>&1 | Tee-Object -FilePath $logPath
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $TaskName (log: $logPath)"
    }

    Write-Host "Success: $TaskName (log: $logPath)"
}

$sharedGradleArguments = @()
if (-not [string]::IsNullOrWhiteSpace($KotlinCompilerExecutionStrategy)) {
    $sharedGradleArguments += "-Pkotlin.compiler.execution.strategy=$KotlinCompilerExecutionStrategy"
}

Invoke-GradleTaskWithLog `
    -TaskName $UnitTestTask `
    -LogFileName "p4-04-testDebugUnitTest.txt" `
    -AdditionalArguments $sharedGradleArguments
$adbPath = Get-AdbPath
Assert-DeviceOnline -AdbPath $adbPath
Invoke-GradleTaskWithLog `
    -TaskName $UiTestTask `
    -LogFileName "p4-04-connectedDebugAndroidTest.txt" `
    -AdditionalArguments $sharedGradleArguments

Write-Host "P4-04 regression test flow completed."
