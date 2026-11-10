<#
.SYNOPSIS
    Turns the monitor on, keeps the display awake for a while and opens the dashboard
    full screen in Microsoft Edge kiosk mode.
.PARAMETER KeepAwakeMinutes
    How long to prevent the display from turning off again.
#>
param([int]$KeepAwakeMinutes = 30)
. (Join-Path $PSScriptRoot 'common.ps1')

if (-not ('DailyHub.Native' -as [type])) {
    Add-Type -Namespace DailyHub -Name Native -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);
[DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, System.UIntPtr dwExtraInfo);
'@
}

$ES_CONTINUOUS       = [uint32]'0x80000000'
$ES_SYSTEM_REQUIRED  = [uint32]'0x00000001'
$ES_DISPLAY_REQUIRED = [uint32]'0x00000002'
$MOUSEEVENTF_MOVE    = [uint32]'0x0001'

# A tiny synthetic mouse move is what reliably turns the monitor back on after a wake timer.
[DailyHub.Native]::mouse_event($MOUSEEVENTF_MOVE, 1, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 200
[DailyHub.Native]::mouse_event($MOUSEEVENTF_MOVE, -1, 0, 0, [UIntPtr]::Zero)

Ensure-HubEnv
$hubEnv = Get-HubEnv
$port = Get-HubPort
$token = $hubEnv['HUB_API_TOKEN']
$url = "http://localhost:${port}/#token=$token"

if (-not (Wait-HubReady)) {
    throw "Il server non risponde su http://localhost:$port. Controlla hub-server\logs\hub.log"
}

Get-CimInstance Win32_Process -Filter "Name = 'msedge.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*edge-kiosk*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$edge = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) { throw 'Microsoft Edge not found' }

$profileDir = Join-Path $ServerDir 'data\edge-kiosk'
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null
$edgeArgs = "--kiosk `"$url`" --edge-kiosk-type=fullscreen --no-first-run --no-default-browser-check --user-data-dir=`"$profileDir`""
Start-Process -FilePath $edge -ArgumentList $edgeArgs

# Keep the display on, then release so normal power settings apply again.
$null = [DailyHub.Native]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED -bor $ES_DISPLAY_REQUIRED)
Start-Sleep -Seconds ($KeepAwakeMinutes * 60)
$null = [DailyHub.Native]::SetThreadExecutionState($ES_CONTINUOUS)
