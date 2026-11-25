# Builds NEXUS-modifiche.zip in the project folder with every file that differs from the latest version
# on GitHub. On the hub PC, scripts\windows\applica_modifiche.bat applies it, commits it and pushes it.
#
# Besides the files, the zip always carries VERSION and NEXUS-base.txt (the GitHub commit it starts from,
# so the hub never applies it on another history), plus NEXUS-cancellati.txt when files were deleted here.
#
# GitHub commits that did not come from a zip (another PC, a pull request) have to be on this PC first:
# the zip carries whole files, so an older copy from here would undo them on the hub. Until then the zip
# is not built. -Merged builds it anyway, once those changes have been merged into the files by hand.
#
#   powershell -ExecutionPolicy Bypass -File scripts\windows\build_zip.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\windows\build_zip.ps1 -Merged

param([switch]$Merged)

$ErrorActionPreference = "Stop"
$project = (Resolve-Path "$PSScriptRoot\..\..").Path
$zipPath = Join-Path $project "NEXUS-modifiche.zip"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-Git {
    # git reports progress on stderr, which PowerShell 5.1 turns into an error under "Stop".
    $ErrorActionPreference = "Continue"
    $lines = & git -C $project -c core.quotePath=false @args 2>$null
    if ($LASTEXITCODE -ne 0) { throw "git $args non riuscito" }
    # The comma keeps a one-line answer as a list instead of letting PowerShell unwrap it to a string.
    , @($lines | Where-Object { $_ })
}

function Test-Git {
    $ErrorActionPreference = "Continue"
    & git -C $project @args 2>$null | Out-Null
    $LASTEXITCODE -eq 0
}

function Test-Skipped([string]$path) {
    $path -like "New Text Document*" -or $path -eq "NEXUS-modifiche.zip" -or
        ($path -notlike "*/*" -and $path -like "NEXUS-*.png")
}

function Test-DiffersFromGitHub([string]$path) {
    $onGitHub = Test-Git cat-file -e "${base}:$path"
    if (-not (Test-Path -LiteralPath (Join-Path $project $path) -PathType Leaf)) { return $onGitHub }
    if (-not $onGitHub) { return $true }
    (Invoke-Git hash-object '--' $path)[0] -ne (Invoke-Git rev-parse "${base}:$path")[0]
}

# Reading from GitHub works from this PC (only pushes are blocked), so the zip starts from what the hub pushed last.
try { Invoke-Git fetch --quiet origin | Out-Null } catch { Write-Warning "git fetch non riuscito: parto dall'ultima copia nota di GitHub" }

$base = (Invoke-Git rev-parse origin/main)[0]
$changed = Invoke-Git diff --name-only --no-renames origin/main
$untracked = Invoke-Git ls-files --others --exclude-standard
$gone = Invoke-Git diff --name-only --no-renames '--diff-filter=D' origin/main

$files = @(@($changed) + @($untracked) + "VERSION" | Sort-Object -Unique |
    Where-Object { -not (Test-Skipped $_) -and (Test-Path -LiteralPath (Join-Path $project $_) -PathType Leaf) })
# A file git lists as deleted but still on disk is only untracked here (e.g. the mockups): the hub keeps it.
$deleted = @($gone | Where-Object { -not (Test-Skipped $_) -and -not (Test-Path -LiteralPath (Join-Path $project $_)) })

# Every GitHub commit after the base of the last zip that is not "NEXUS <version>" was made somewhere else.
$lastBaseFile = Join-Path (Invoke-Git rev-parse --absolute-git-dir)[0] "nexus-zip-base"
$lastBase = $base
if (Test-Path -LiteralPath $lastBaseFile) { $lastBase = (Get-Content -LiteralPath $lastBaseFile -Raw).Trim() }
$knownBase = ($lastBase -match '^[0-9a-f]{40}$') -and (Test-Git merge-base --is-ancestor $lastBase $base)
$foreign = @()
if ($knownBase) {
    foreach ($line in (Invoke-Git log '--format=%H%x09%s' "$lastBase..$base")) {
        $sha, $subject = $line -split "`t", 2
        if ($subject -notmatch '^NEXUS \d+(\.\d+)+$') { $foreign += Invoke-Git diff --name-only --no-renames "$sha^1" $sha }
    }
}
$missing = @($foreign | Sort-Object -Unique | Where-Object { -not (Test-Skipped $_) -and (Test-DiffersFromGitHub $_) })
if (-not $knownBase -or $missing.Count -gt 0) {
    if (-not $knownBase) {
        Write-Warning "GitHub non contiene piu la versione da cui era partito l'ultimo zip ($lastBase): controlla a mano cosa e cambiato su GitHub."
    } else {
        Write-Warning ("Su GitHub sono cambiati, senza passare da uno zip, questi file, che qui sono diversi:`n  " + ($missing -join "`n  "))
    }
    if (-not $Merged) {
        Write-Host "Zip non creato. Prima porta qui quelle modifiche: per un file che non hai toccato basta 'git checkout origin/main -- <file>', altrimenti uniscile a mano e rilancia con -Merged." -ForegroundColor Red
        exit 1
    }
    Write-Host "-Merged: creo lo zip con i file di questo PC."
}

Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
if (Test-Path $zipPath) { Remove-Item $zipPath }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $project $file), $file, [System.IO.Compression.CompressionLevel]::Optimal)
    }
    $notes = @{ "NEXUS-base.txt" = @($base) }
    if ($deleted.Count -gt 0) { $notes["NEXUS-cancellati.txt"] = $deleted }
    foreach ($name in $notes.Keys) {
        $writer = New-Object System.IO.StreamWriter($zip.CreateEntry($name).Open(), $utf8)
        try { foreach ($line in $notes[$name]) { $writer.Write("$line`n") } } finally { $writer.Dispose() }
    }
} finally {
    $zip.Dispose()
}
[System.IO.File]::WriteAllText($lastBaseFile, "$base`n", $utf8)

$version = (Get-Content (Join-Path $project "VERSION") -Raw).Trim()
Write-Host ("Zip pronto: NEXUS {0}, {1} file, {2} da cancellare, base GitHub {3} ({4:N0} KB)" -f `
    $version, $files.Count, $deleted.Count, $base.Substring(0, 7), ((Get-Item $zipPath).Length / 1KB))
Write-Host $zipPath
