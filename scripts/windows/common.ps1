$ErrorActionPreference = 'Stop'

$RepoRoot   = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ServerDir  = Join-Path $RepoRoot 'hub-server'
$VenvPython = Join-Path $ServerDir '.venv\Scripts\python.exe'
$VenvPythonW = Join-Path $ServerDir '.venv\Scripts\pythonw.exe'
$EnvFile    = Join-Path $ServerDir '.env'

$ServerTaskName = 'DailyHub Server'
$WakeTaskName   = 'DailyHub Wake'

function Get-HubEnv {
    $values = @{}
    if (Test-Path $EnvFile) {
        foreach ($line in Get-Content $EnvFile) {
            if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
                $values[$Matches[1]] = $Matches[2].Trim('"')
            }
        }
    }
    return $values
}

function Get-HubPort {
    $port = (Get-HubEnv)['HUB_PORT']
    if ([string]::IsNullOrWhiteSpace($port)) { return 8765 }
    return [int]$port
}

function Test-HubListening {
    $port = Get-HubPort
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/health" -UseBasicParsing -TimeoutSec 2 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Stop-HubServer {
    Get-CimInstance Win32_Process -Filter "Name = 'python.exe' OR Name = 'pythonw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*run.py*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

function Ensure-HubEnv {
    if (Test-Path $EnvFile) { return }
    if (-not (Test-Path $VenvPython)) {
        throw "Manca hub-server\.venv. Dalla cartella del repo esegui: powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1"
    }
    $token = (& $VenvPython -c "import secrets; print(secrets.token_urlsafe(32))").Trim()
    $example = Join-Path $ServerDir '.env.example'
    (Get-Content $example) -replace '^HUB_API_TOKEN=.*', "HUB_API_TOKEN=$token" |
        Set-Content -Path $EnvFile -Encoding UTF8
    Write-Host "Creato $EnvFile"
    Write-Host "Token API (va messo nell'app Android): $token" -ForegroundColor Yellow
}

function Start-HubServer {
    if (Test-HubListening) { return }
    $task = Get-ScheduledTask -TaskName $ServerTaskName -ErrorAction SilentlyContinue
    if ($task) {
        Start-ScheduledTask -TaskName $ServerTaskName
        return
    }
    if (-not (Test-Path $VenvPythonW)) {
        throw "Python del virtualenv non trovato. Esegui scripts\windows\install.ps1"
    }
    Start-Process -FilePath $VenvPythonW -ArgumentList 'run.py' -WorkingDirectory $ServerDir -WindowStyle Hidden
}

function Enable-WakeTimers {
    powercfg /SETACVALUEINDEX SCHEME_CURRENT SUB_SLEEP RTCWAKE 1 | Out-Null
    powercfg /SETDCVALUEINDEX SCHEME_CURRENT SUB_SLEEP RTCWAKE 1 | Out-Null
    powercfg /SETACTIVE SCHEME_CURRENT | Out-Null
}

function Wait-HubReady {
    param([int]$TimeoutSeconds = 45)
    $port = Get-HubPort
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $started = $false
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/health" -UseBasicParsing -TimeoutSec 2 | Out-Null
            return $true
        } catch {
            if (-not $started) {
                $started = $true
                Start-HubServer
            }
            Start-Sleep -Seconds 1
        }
    }
    return $false
}
