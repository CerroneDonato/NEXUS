<#
.SYNOPSIS
    One-time setup on the hub PC (no admin rights needed).
    - creates the Python virtualenv and installs dependencies
    - creates hub-server\.env with a random token if missing
    - registers the "DailyHub Server" task that starts the server at logon
    - opens the Windows Firewall port (only if run as administrator)
#>
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Host "Repo: $RepoRoot"

if (-not (Test-Path $VenvPython)) {
    Write-Host 'Creating virtualenv...'
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { & py -3 -m venv (Join-Path $ServerDir '.venv') }
    else { & python -m venv (Join-Path $ServerDir '.venv') }
}
& $VenvPython -m pip install --upgrade pip --quiet
& $VenvPython -m pip install -r (Join-Path $ServerDir 'requirements.txt') --quiet

if (-not (Test-Path $EnvFile)) {
    $token = & $VenvPython -c "import secrets; print(secrets.token_urlsafe(32))"
    (Get-Content (Join-Path $ServerDir '.env.example')) -replace '^HUB_API_TOKEN=.*', "HUB_API_TOKEN=$token" |
        Set-Content -Path $EnvFile -Encoding UTF8
    Write-Host "Created $EnvFile"
    Write-Host "API token (configure it in the Android app): $token" -ForegroundColor Yellow
}

$action = New-ScheduledTaskAction -Execute $VenvPythonW -Argument 'run.py' -WorkingDirectory $ServerDir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable
Register-ScheduledTask -TaskName $ServerTaskName -Action $action -Trigger $trigger -Settings $settings `
    -Description 'Daily Hub API + dashboard' -Force | Out-Null
Write-Host "Registered scheduled task '$ServerTaskName' (starts at logon)."

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
$port = Get-HubPort
if ($isAdmin) {
    if (-not (Get-NetFirewallRule -DisplayName 'Daily Hub' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName 'Daily Hub' -Direction Inbound -Protocol TCP -LocalPort $port `
            -Action Allow -Profile Private | Out-Null
        Write-Host "Firewall: opened TCP $port on private networks."
    }
} else {
    Write-Host "Not admin: if the phone cannot reach the hub, run once as admin:" -ForegroundColor Yellow
    Write-Host "  New-NetFirewallRule -DisplayName 'Daily Hub' -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow -Profile Private"
}

Stop-HubServer
Start-HubServer
Write-Host "Server started: http://localhost:$port"
Write-Host 'Next: enable wake timers (see docs/windows-setup.md) and run google_auth for the calendar.'
