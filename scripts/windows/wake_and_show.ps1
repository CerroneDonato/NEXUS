<#
.SYNOPSIS
    Turns the monitor on immediately, then shows the dashboard full screen in Edge kiosk mode.
    If the kiosk is already open it is reused: the page refreshes itself on resume.
.PARAMETER KeepAwakeMinutes
    How long to prevent the display from turning off again.
#>
param([int]$KeepAwakeMinutes = 30)
. (Join-Path $PSScriptRoot 'common.ps1')

if (-not ('DailyHub.Native' -as [type])) {
    Add-Type -Namespace DailyHub -Name Native -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);
[DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, System.UIntPtr dwExtraInfo);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(System.IntPtr hWnd);
[DllImport("user32.dll")] public static extern bool ShowWindow(System.IntPtr hWnd, int nCmdShow);
'@
}

$ES_CONTINUOUS       = [uint32]'0x80000000'
$ES_SYSTEM_REQUIRED  = [uint32]'0x00000001'
$ES_DISPLAY_REQUIRED = [uint32]'0x00000002'
$MOUSEEVENTF_MOVE    = [uint32]'0x0001'

function Wake-Display {
    $null = [DailyHub.Native]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED -bor $ES_DISPLAY_REQUIRED)
    [DailyHub.Native]::mouse_event($MOUSEEVENTF_MOVE, 1, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 120
    [DailyHub.Native]::mouse_event($MOUSEEVENTF_MOVE, -1, 0, 0, [UIntPtr]::Zero)
}

# Display first: everything else happens while the screen is already on.
Wake-Display

Ensure-HubEnv
$hubEnv = Get-HubEnv
$port = Get-HubPort
$token = $hubEnv['HUB_API_TOKEN']
$url = "http://127.0.0.1:${port}/#token=$token"

$profileDir = Join-Path $ServerDir 'data\edge-kiosk'
$pidFile = Join-Path $ServerDir 'data\kiosk.pid'
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

$kiosk = $null
if (Test-Path $pidFile) {
    $kioskId = [int](Get-Content $pidFile -ErrorAction SilentlyContinue)
    $kiosk = Get-Process -Id $kioskId -ErrorAction SilentlyContinue
    if ($kiosk -and $kiosk.ProcessName -ne 'msedge') { $kiosk = $null }
}

if (-not (Wait-HubReady)) {
    Write-Warning "Il server non risponde su http://127.0.0.1:$port (log: hub-server\logs\hub.log)"
}

if ($kiosk) {
    if ($kiosk.MainWindowHandle -ne [IntPtr]::Zero) {
        [void][DailyHub.Native]::ShowWindow($kiosk.MainWindowHandle, 3)
        [void][DailyHub.Native]::SetForegroundWindow($kiosk.MainWindowHandle)
    }
} else {
    $edge = @(
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $edge) { throw 'Microsoft Edge not found' }

    $edgeArgs = "--kiosk `"$url`" --edge-kiosk-type=fullscreen --no-first-run --no-default-browser-check " +
        "--disable-features=msEdgeSidebarV2 --user-data-dir=`"$profileDir`""
    $process = Start-Process -FilePath $edge -ArgumentList $edgeArgs -PassThru
    Set-Content -Path $pidFile -Value $process.Id
}

if ($KeepAwakeMinutes -gt 0) {
    # Re-assert periodically: some displays ignore a single request after a wake timer.
    $until = (Get-Date).AddMinutes($KeepAwakeMinutes)
    while ((Get-Date) -lt $until) {
        $null = [DailyHub.Native]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED -bor $ES_DISPLAY_REQUIRED)
        Start-Sleep -Seconds 60
    }
}
$null = [DailyHub.Native]::SetThreadExecutionState($ES_CONTINUOUS)
