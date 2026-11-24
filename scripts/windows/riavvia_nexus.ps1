<#
.SYNOPSIS
    Closes the NEXUS server and its full-screen page, then opens both again.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

Stop-HubDashboard
Stop-HubServer

$deadline = (Get-Date).AddSeconds(8)
while ((Test-HubListening) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 400
}

& (Join-Path $PSScriptRoot 'wake_and_show.ps1') -KeepAwakeMinutes 0
if (-not (Test-HubListening)) { exit 1 }
