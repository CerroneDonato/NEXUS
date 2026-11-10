<#
.SYNOPSIS
    Opens the dashboard full screen now (same as the wake task, without keeping the display awake).
#>
& (Join-Path $PSScriptRoot 'wake_and_show.ps1') -KeepAwakeMinutes 0
