<#
.SYNOPSIS
    Called by the hub server. Creates/updates (or removes) the one-shot task that wakes
    the PC from sleep at the given local time and shows the dashboard.
.EXAMPLE
    .\schedule_wake.ps1 -At 2026-11-11T06:59:00
    .\schedule_wake.ps1 -Cancel
#>
param(
    [string]$At,
    [switch]$Cancel
)
. (Join-Path $PSScriptRoot 'common.ps1')

if ($Cancel) {
    Unregister-ScheduledTask -TaskName $WakeTaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Output 'Wake task removed'
    return
}

if (-not $At) { throw 'Specify -At <yyyy-MM-ddTHH:mm:ss> or -Cancel' }
try {
    $when = [datetime]::ParseExact($At, 'yyyy-MM-ddTHH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
} catch {
    $when = [datetime]::Parse($At, [Globalization.CultureInfo]::CurrentCulture)
}
Enable-WakeTimers

$script = Join-Path $PSScriptRoot 'wake_and_show.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
$trigger = New-ScheduledTaskTrigger -Once -At $when
$settings = New-ScheduledTaskSettingsSet -WakeToRun -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 45)
# Interactive principal: runs in the logged-on session so the browser window is visible.
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive

Register-ScheduledTask -TaskName $WakeTaskName -Action $action -Trigger $trigger -Settings $settings `
    -Principal $principal -Description 'Daily Hub: wake PC at phone alarm' -Force | Out-Null
Write-Output "Wake task scheduled at $($when.ToString('yyyy-MM-dd HH:mm'))"
