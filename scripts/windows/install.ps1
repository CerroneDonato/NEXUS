<#
.SYNOPSIS
    One-time setup on the hub PC (no admin rights needed).
    - creates the Python virtualenv and installs dependencies
    - creates hub-server\.env with a random token if missing
    - removes the old logon task: the dashboard starts the server, which then stays up so the phone can sync
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

$task = Get-ScheduledTask -TaskName $ServerTaskName -ErrorAction SilentlyContinue
if ($task) {
    Unregister-ScheduledTask -TaskName $ServerTaskName -Confirm:$false
    Write-Host "Removed scheduled task '$ServerTaskName'. The dashboard starts the server, which then stays up so the phone can sync."
}

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

Write-Host "Open the dashboard with scripts\windows\show_dashboard.ps1 (it starts the server if it is not running)."
Write-Host 'Next: enable wake timers (see docs/windows-setup.md) and run google_auth for the calendar.'
