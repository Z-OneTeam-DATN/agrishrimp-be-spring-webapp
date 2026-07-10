param(
    [string]$BaseEnvFile = ".env",
    [string]$OverrideEnvFile = ".env.local"
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }

    foreach ($rawLine in Get-Content -LiteralPath $Path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
    }

    return $true
}

$projectRoot = $PSScriptRoot
$baseEnvPath = Join-Path $projectRoot $BaseEnvFile
$overrideEnvPath = Join-Path $projectRoot $OverrideEnvFile

$baseLoaded = Import-EnvFile -Path $baseEnvPath
$overrideLoaded = Import-EnvFile -Path $overrideEnvPath

if (-not $overrideLoaded) {
    Write-Host "Missing $OverrideEnvFile." -ForegroundColor Yellow
    Write-Host "Copy .env.local.example to $OverrideEnvFile and update the DB host/user/password first." -ForegroundColor Yellow
    exit 1
}

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = "dev"
}

if ($env:SPRING_DATASOURCE_URL -like "*YOUR_DB_HOST*" -or $env:SPRING_DATASOURCE_USERNAME -eq "YOUR_DB_USER") {
    Write-Host "The override file still contains placeholder remote DB values." -ForegroundColor Yellow
    Write-Host "Update $OverrideEnvFile before starting the backend." -ForegroundColor Yellow
    exit 1
}

Write-Host "Starting AgriShrimp backend with local app config + remote DB override..." -ForegroundColor Cyan
Write-Host "Profile: $($env:SPRING_PROFILES_ACTIVE)" -ForegroundColor Cyan
Write-Host "API URL: $($env:APP_SERVER_URL)" -ForegroundColor Cyan
Write-Host "Datasource: $($env:SPRING_DATASOURCE_URL)" -ForegroundColor Cyan
Write-Host "Redis host: $($env:SPRING_DATA_REDIS_HOST)" -ForegroundColor Cyan

if ($baseLoaded) {
    Write-Host "Loaded base env: $BaseEnvFile" -ForegroundColor DarkGray
}
Write-Host "Loaded override env: $OverrideEnvFile" -ForegroundColor DarkGray

& (Join-Path $projectRoot "mvnw.cmd") spring-boot:run "-Dspring-boot.run.profiles=$($env:SPRING_PROFILES_ACTIVE)" "-Dspring-boot.run.jvmArguments=-Xmx384m"
exit $LASTEXITCODE

