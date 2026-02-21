param(
    [string]$CertFilePath = "cert.txt",
    [string]$TestClass = "com.yamichi77.movement_log.data.network.RealEndpointConnectivityE2ETest"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $projectRoot "docs/build-logs"
$tempDir = Join-Path $projectRoot ".tmp"
$androidUserHomeDir = Join-Path $projectRoot ".android"
$env:GRADLE_USER_HOME = Join-Path $projectRoot ".gradle-user-home"
$env:ANDROID_USER_HOME = $androidUserHomeDir
$env:TEMP = $tempDir
$env:TMP = $tempDir

foreach ($dir in @($logDir, $tempDir, $androidUserHomeDir, $env:GRADLE_USER_HOME)) {
    if (-not (Test-Path $dir)) {
        New-Item -Path $dir -ItemType Directory | Out-Null
    }
}

$certFile = Join-Path $projectRoot $CertFilePath
if (-not (Test-Path $certFile)) {
    throw "Cert file not found: $certFile"
}

$pairs = @{}
Get-Content -Path $certFile | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        return
    }
    $parts = $line -split "=", 2
    if ($parts.Count -ne 2) {
        return
    }
    $pairs[$parts[0].Trim()] = $parts[1].Trim()
}

foreach ($key in @("SERVER_URL", "SERVER_PORT", "SERVER_CERT_KEY")) {
    if (-not $pairs.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($pairs[$key])) {
        throw "Missing $key in $CertFilePath"
    }
    Set-Item -Path "Env:$key" -Value $pairs[$key]
}

$logFile = Join-Path $logDir "connection-e2e-real-endpoint.txt"
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$args = @(
    ":app:testDebugUnitTest",
    "--tests",
    $TestClass,
    "-Pkotlin.compiler.execution.strategy=in-process"
)

Write-Host "Run: .\gradlew.bat $($args -join ' ')"
& $gradleWrapper @args *>&1 | Tee-Object -FilePath $logFile
if ($LASTEXITCODE -ne 0) {
    throw "Connection E2E test failed. log=$logFile"
}

Write-Host "Connection E2E test succeeded. log=$logFile"
