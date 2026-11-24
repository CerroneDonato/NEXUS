<#
.SYNOPSIS
    Copies the project onto the hub PC, keeping the hub's own state intact.
.DESCRIPTION
    Mirrors ADayInALife to a shared folder. Never copies .env, secrets, data,
    logs or the virtualenv, so the hub token, Google login, tasks and browser
    profile stay where they are.
.EXAMPLE
    .\sync_to_hub.ps1 -Destination \\192.168.99.2\NEXUS -Watch
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,
    [switch]$Watch
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

function Sync-Once {
    if (-not (Test-Path $Destination)) {
        throw "Cartella hub non raggiungibile: $Destination"
    }
    & robocopy $RepoRoot $Destination /E /XO /NFL /NDL /NJH /R:2 /W:2 `
        /XD .git .venv __pycache__ .pytest_cache logs secrets data .gradle build `
        /XF .env kiosk.pid
    if ($LASTEXITCODE -ge 8) { throw "Copia fallita (robocopy $LASTEXITCODE)" }
    Write-Host "Aggiornato $Destination"
}

Sync-Once
if ($Watch) {
    Write-Host "Controllo ogni 5 secondi. Ctrl+C per fermare."
    while ($true) {
        Start-Sleep -Seconds 5
        Sync-Once
    }
}
