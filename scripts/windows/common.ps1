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

function Stop-HubServer {
    $port = Get-HubPort
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

function Start-HubServer {
    Start-ScheduledTask -TaskName $ServerTaskName
}
