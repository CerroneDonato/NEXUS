<#
.SYNOPSIS
    Pulls the latest code from git, refreshes dependencies and restarts the server.
    Run manually, or register nightly with:  .\update.ps1 -RegisterNightly
#>
param(
    [switch]$RegisterNightly,
    [string]$At = '04:00'
)
. (Join-Path $PSScriptRoot 'common.ps1')

if ($RegisterNightly) {
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' `
        -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSCommandPath`""
    $trigger = New-ScheduledTaskTrigger -Daily -At $At
    Register-ScheduledTask -TaskName 'DailyHub Update' -Action $action -Trigger $trigger `
        -Settings (New-ScheduledTaskSettingsSet -StartWhenAvailable) -Force | Out-Null
    Write-Host "Registered nightly update at $At."
    return
}

Push-Location $RepoRoot
try {
    $before = git rev-parse HEAD
    git pull --ff-only
    if ($LASTEXITCODE -ne 0) { throw 'git pull failed (local changes on the hub PC?)' }
    $after = git rev-parse HEAD
} finally {
    Pop-Location
}

if ($before -eq $after) {
    Write-Host 'Already up to date.'
    return
}

Write-Host "Updated $($before.Substring(0,7)) -> $($after.Substring(0,7))"
& $VenvPython -m pip install -r (Join-Path $ServerDir 'requirements.txt') --quiet
Stop-HubServer
Start-HubServer
Write-Host 'Server restarted.'
