param(
    [string]$SshHost = "ssh.chat2like.com",
    [switch]$Stop,
    [switch]$KeepTunnel,
    # Legacy app-launch flags are accepted as no-ops; this script now manages only the tunnel.
    [switch]$OnlyTunnel,
    [switch]$SkipTunnelSetup,
    [switch]$SkipBackend,
    [switch]$SkipWeb,
    [ValidateSet("dev", "dev-turbo", "prod")]
    [string]$WebMode = "dev"
)

$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
$stateDir = Join-Path $workspaceRoot ".live-local"

$remoteMysqlProxyPort = 23306
$remoteRedisProxyPort = 26379
$localMysqlPort = 3307
$localRedisPort = 6379

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-CommandPath {
    param([string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Missing required command: $Name"
    }

    return $command.Source
}

function Test-PortListening {
    param([int]$Port)

    $listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -eq $Port }
    return [bool]$listeners
}

function Get-PortProcesses {
    param([int]$Port)

    return Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -eq $Port } |
        Select-Object -ExpandProperty OwningProcess -Unique
}

function Wait-ForPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) {
            return $true
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

function Save-Pid {
    param(
        [string]$Name,
        [int]$ProcessId
    )

    Set-Content -LiteralPath (Join-Path $stateDir "$Name.pid") -Value $ProcessId
}

function Stop-TrackedProcess {
    param([string]$Name)

    $pidFile = Join-Path $stateDir "$Name.pid"
    if (-not (Test-Path $pidFile)) {
        return $false
    }

    $rawProcessId = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    $trackedProcessId = 0
    if (-not [int]::TryParse($rawProcessId, [ref]$trackedProcessId)) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        return $false
    }

    $process = Get-Process -Id $trackedProcessId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $trackedProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped $Name (PID $trackedProcessId)." -ForegroundColor Yellow
    }

    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    return [bool]$process
}

function Stop-PortListeners {
    param(
        [int]$Port,
        [string]$ServiceName
    )

    $owners = Get-PortProcesses -Port $Port
    foreach ($owner in $owners) {
        $process = Get-Process -Id $owner -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped $ServiceName on port $Port (PID $owner)." -ForegroundColor Yellow
        }
    }
}

if ($Stop) {
    Write-Step "Stopping live local tunnel"

    if ($KeepTunnel) {
        Write-Host "Tunnel is still running on ports $localMysqlPort and $localRedisPort." -ForegroundColor Yellow
        exit 0
    }

    [void](Stop-TrackedProcess -Name "tunnel")
    Stop-PortListeners -Port $localMysqlPort -ServiceName "MySQL tunnel"
    Stop-PortListeners -Port $localRedisPort -ServiceName "Redis tunnel"

    Write-Host ""
    Write-Host "Live local tunnel has been stopped." -ForegroundColor Green
    Write-Host "Ports checked: $localMysqlPort, $localRedisPort"
    exit 0
}

function Start-BackgroundCommand {
    param(
        [string]$Command,
        [string]$WorkingDirectory
    )

    return Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @("-NoLogo", "-NoProfile", "-Command", $Command) `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -PassThru
}

Write-Step "Checking tunnel ports"

$mysqlTunnelExists = Test-PortListening -Port $localMysqlPort
$redisTunnelExists = Test-PortListening -Port $localRedisPort
$reuseExistingTunnel = $false

if ($mysqlTunnelExists -or $redisTunnelExists) {
    if ($mysqlTunnelExists -and $redisTunnelExists) {
        $reuseExistingTunnel = $true
        Write-Host "Tunnel ports $localMysqlPort and $localRedisPort are already listening. Reusing them." -ForegroundColor Yellow
    } else {
        $usedPorts = @()
        if ($mysqlTunnelExists) { $usedPorts += $localMysqlPort }
        if ($redisTunnelExists) { $usedPorts += $localRedisPort }
        throw "Only part of the tunnel is already running. In-use port(s): $($usedPorts -join ', '). Stop them first or start from a clean state."
    }
}

if ($SkipTunnelSetup -and -not $reuseExistingTunnel) {
    Write-Host "SkipTunnelSetup was requested; skipping SSH tunnel creation." -ForegroundColor Yellow
}

if (-not $reuseExistingTunnel -and -not $SkipTunnelSetup) {
    $sshPath = Get-CommandPath -Name "ssh"
    $sshpassCmd = Get-Command "sshpass" -ErrorAction SilentlyContinue
    $sshpassPath = if ($sshpassCmd) { $sshpassCmd.Source } else { $null }

    $sshOptions = @(
        "-4",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "PreferredAuthentications=password,publickey",
        "-o", "ConnectTimeout=10",
        "-o", "ConnectionAttempts=1",
        "-o", "ServerAliveInterval=10",
        "-o", "ServerAliveCountMax=2",
        "-o", "LogLevel=ERROR"
    )
    $sshOptionText = $sshOptions -join " "

    $passwordFile = Join-Path $stateDir "sshpass.txt"
    $parentPasswordFile = Join-Path (Split-Path $workspaceRoot -Parent) ".live-local\sshpass.txt"
    
    if (Test-Path $passwordFile) {
        $plainPassword = Get-Content -LiteralPath $passwordFile -Raw
        Write-Host "Reusing saved SSH password from $passwordFile" -ForegroundColor Yellow
    } elseif (Test-Path $parentPasswordFile) {
        $plainPassword = Get-Content -LiteralPath $parentPasswordFile -Raw
        $passwordFile = $parentPasswordFile
        Write-Host "Reusing saved SSH password from $parentPasswordFile" -ForegroundColor Yellow
    } else {
        Write-Step "Requesting SSH password for $SshHost"
        $securePassword = Read-Host "SSH password" -AsSecureString
        $credential = New-Object System.Management.Automation.PSCredential ("ignored", $securePassword)
        $plainPassword = $credential.GetNetworkCredential().Password
        if ([string]::IsNullOrWhiteSpace($plainPassword)) {
            throw "SSH password cannot be empty."
        }
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($passwordFile, $plainPassword, $utf8NoBom)
    }

    try {
        Write-Step "Ensuring remote DB and Redis proxy containers"
        $remoteProxyCommand = @"
docker rm -f agri-db-proxy agri-redis-proxy || true
docker run -d --name agri-db-proxy --network agrishrimp-net -p 127.0.0.1:${remoteMysqlProxyPort}:${remoteMysqlProxyPort} alpine/socat TCP-LISTEN:${remoteMysqlProxyPort},fork,reuseaddr TCP4:agrishrimp-db:3306
docker run -d --name agri-redis-proxy --network agrishrimp-net -p 127.0.0.1:${remoteRedisProxyPort}:${remoteRedisProxyPort} alpine/socat TCP-LISTEN:${remoteRedisProxyPort},fork,reuseaddr TCP4:agrishrimp-redis:6379
echo REMOTE_PROXY_READY
"@
        $remoteProxyCommand = $remoteProxyCommand -replace "`r", ""
        if ($sshpassPath) {
            & $sshpassPath -f $passwordFile $sshPath @sshOptions $SshHost $remoteProxyCommand | Out-Null
        } else {
            & $sshPath @sshOptions $SshHost $remoteProxyCommand | Out-Null
        }

        Write-Step "Starting local SSH tunnel for MySQL and Redis"
        if ($sshpassPath) {
            $tunnelCommand = "& '$sshpassPath' -f '$passwordFile' '$sshPath' $sshOptionText -o ExitOnForwardFailure=yes -N -L ${localMysqlPort}:127.0.0.1:${remoteMysqlProxyPort} -L ${localRedisPort}:127.0.0.1:${remoteRedisProxyPort} $SshHost"
        } else {
            $tunnelCommand = "& '$sshPath' $sshOptionText -o ExitOnForwardFailure=yes -N -L ${localMysqlPort}:127.0.0.1:${remoteMysqlProxyPort} -L ${localRedisPort}:127.0.0.1:${remoteRedisProxyPort} $SshHost"
        }
        $tunnelProcess = Start-BackgroundCommand `
            -Command $tunnelCommand `
            -WorkingDirectory $workspaceRoot
        Save-Pid -Name "tunnel" -ProcessId $tunnelProcess.Id

        if (-not (Wait-ForPort -Port $localMysqlPort -TimeoutSeconds 20)) {
            throw "MySQL tunnel did not start on localhost:$localMysqlPort"
        }
        if (-not (Wait-ForPort -Port $localRedisPort -TimeoutSeconds 20)) {
            throw "Redis tunnel did not start on localhost:$localRedisPort"
        }
    } finally {
        Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "[READY] MySQL: localhost:$localMysqlPort" -ForegroundColor Green
Write-Host "[READY] Redis: localhost:$localRedisPort" -ForegroundColor Green
if ($SkipTunnelSetup -or $reuseExistingTunnel) {
    Write-Host "Tunnel mode: reused existing"
} else {
    Write-Host "Tunnel mode: fresh SSH tunnel"
}
Write-Host "State: $stateDir"
Write-Host ""
Write-Host "Run BE:" -ForegroundColor Cyan
Write-Host '$env:JAVA_HOME="C:\Users\ACER\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows\java-runtime-delta"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,live-local" "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"'
Write-Host "Run FE:" -ForegroundColor Cyan
Write-Host "npm run dev"
