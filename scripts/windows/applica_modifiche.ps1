<#
.SYNOPSIS
    Applies NEXUS-modifiche.zip from the development PC, commits it as "NEXUS <version>",
    pushes it to GitHub and restarts NEXUS.
.DESCRIPTION
    Double-click applica_modifiche.bat to take the newest NEXUS-modifiche*.zip in Downloads, or drop
    a zip on it. Nothing from the zip is applied when:
      - files of the repo have local changes on this PC,
      - the zip starts from a GitHub version this PC does not have (NEXUS-base.txt),
      - the zip is older than this repo (VERSION),
      - the zip holds hub state (.env, secrets, data, .venv, logs, .git) or paths outside the repo,
      - after the zip was built, a GitHub commit that is not "NEXUS <version>" (another PC, a pull request)
        changed a file the zip would overwrite or delete.
    If the push fails the commit stays and NEXUS runs the new version: run the script again to retry the push.
.EXAMPLE
    .\applica_modifiche.ps1 "$env:USERPROFILE\Downloads\NEXUS-modifiche.zip"
#>
param(
    [Parameter(Position = 0)]
    [string]$Zip,
    [switch]$NoRestart
)
. (Join-Path $PSScriptRoot 'common.ps1')
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

$BaseNote = 'NEXUS-base.txt'
$DeletedNote = 'NEXUS-cancellati.txt'
# Only the hub has these: token, Google login, tasks, kiosk profile, virtualenv, logs, git history.
$HubState = '(^|/)\.git(/|$)|(^|/)\.env$|^hub-server/(secrets|data|\.venv|logs)(/|$)'

function Invoke-Git {
    # git reports progress on stderr, which PowerShell 5.1 turns into an error under 'Stop'.
    $ErrorActionPreference = 'Continue'
    $out = @()
    $err = @()
    & git -C $RepoRoot @args 2>&1 | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $err += "$_" } elseif ("$_") { $out += "$_" }
    }
    [pscustomobject]@{ Ok = ($LASTEXITCODE -eq 0); Out = $out; Err = ($err -join ' ') }
}

function Test-SafePath([string]$path) {
    if (-not $path -or $path -match '^/|\\|:') { return $false }
    @($path -split '/' | Where-Object { $_ -in '', '.', '..' }).Count -eq 0
}

function Read-Entry($entry) {
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}

$pushed = $true
$staged = @()
try {
    if (-not $Zip) {
        $downloads = Join-Path $env:USERPROFILE 'Downloads'
        $Zip = Get-ChildItem -LiteralPath $downloads -Filter 'NEXUS-modifiche*.zip' -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
        if (-not $Zip) { throw "Nessun NEXUS-modifiche.zip in $downloads. Trascina lo zip su applica_modifiche.bat." }
    }
    if (-not (Test-Path -LiteralPath $Zip -PathType Leaf)) { throw "Zip non trovato: $Zip" }
    Write-Host "Zip: $Zip"

    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Zip).Path)
    try {
        $entries = @($archive.Entries | Where-Object { -not $_.FullName.EndsWith('/') })
        $notes = @{}
        foreach ($entry in $entries) {
            if ($entry.FullName -in 'VERSION', $BaseNote, $DeletedNote) { $notes[$entry.FullName] = (Read-Entry $entry).Trim() }
        }
        if (-not $notes['VERSION'] -or -not $notes[$BaseNote]) { throw "Lo zip non contiene VERSION e ${BaseNote}: rifallo con build_zip.ps1." }
        $zipVersion = $null
        if (-not [version]::TryParse($notes['VERSION'], [ref]$zipVersion)) { throw "VERSION non valido nello zip: $($notes['VERSION'])" }
        $base = $notes[$BaseNote]
        if ($base -notmatch '^[0-9a-f]{40}$') { throw "$BaseNote non valido nello zip." }
        $toDelete = @()
        if ($notes[$DeletedNote]) { $toDelete = @($notes[$DeletedNote] -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

        $bad = @(@($entries | ForEach-Object { $_.FullName }) + $toDelete | Where-Object { -not (Test-SafePath $_) -or $_ -match $HubState })
        if ($bad.Count -gt 0) { throw ("Lo zip contiene percorsi non ammessi, non applico niente:`n  " + ($bad -join "`n  ")) }

        if (-not (Invoke-Git rev-parse --is-inside-work-tree).Ok) { throw "$RepoRoot non risulta un repo git: clonalo da GitHub (docs\windows-setup.md)." }
        if (-not (Invoke-Git var GIT_COMMITTER_IDENT).Ok) { throw 'Git non sa con che nome firmare i commit: imposta user.name e user.email su questo PC.' }
        $dirty = (Invoke-Git status --porcelain '--untracked-files=no').Out
        if ($dirty.Count -gt 0) {
            throw ("Ci sono modifiche locali ai file del repo, non applico niente. Salvale o annullale e riprova:`n  " + (($dirty | Select-Object -First 10) -join "`n  "))
        }

        $upstream = (Invoke-Git rev-parse --abbrev-ref --symbolic-full-name '@{u}').Ok
        if ($upstream) {
            if ((Invoke-Git fetch --quiet).Ok) {
                $merge = Invoke-Git merge --ff-only --quiet '@{u}'
                if (-not $merge.Ok) { throw "Questo PC e GitHub hanno storie diverse, non applico niente. $($merge.Err)" }
            } else {
                Write-Warning 'GitHub non raggiungibile: applico in locale, il push si ritenta al prossimo avvio.'
            }
        } else {
            Write-Warning 'Ramo senza collegamento a GitHub: applico solo in locale.'
        }
        if (-not (Invoke-Git merge-base --is-ancestor $base HEAD).Ok) {
            throw "Lo zip parte dalla versione GitHub $($base.Substring(0, 7)), che questo PC non ha: collegalo a internet e riprova, oppure rifai lo zip."
        }
        $repoVersion = [version]'0.0'
        $versionFile = Join-Path $RepoRoot 'VERSION'
        $parsed = $null
        if ((Test-Path $versionFile) -and [version]::TryParse((Get-Content $versionFile -Raw).Trim(), [ref]$parsed)) { $repoVersion = $parsed }
        if ($zipVersion -lt $repoVersion) { throw "Versione dello zip $zipVersion inferiore a quella del repo ${repoVersion}: non applico niente." }

        # The zip carries whole files: one that GitHub changed after the zip was built would go back to the older copy.
        $foreign = @{}
        foreach ($line in (Invoke-Git log '--format=%H%x09%s' "$base..HEAD").Out) {
            $sha, $subject = $line -split "`t", 2
            if ($subject -match '^NEXUS \d+(\.\d+)+$') { continue }
            foreach ($path in (Invoke-Git diff --name-only --no-renames "$sha^1" $sha).Out) { $foreign[$path] = $true }
        }
        $undone = @()
        if ($foreign.Count -gt 0) {
            $probe = Join-Path (Invoke-Git rev-parse --absolute-git-dir).Out[0] 'nexus-controllo.tmp'
            try {
                foreach ($entry in $entries) {
                    if (-not $foreign.ContainsKey($entry.FullName)) { continue }
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $probe, $true)
                    $inZip = (Invoke-Git hash-object "--path=$($entry.FullName)" $probe).Out
                    $onGitHub = (Invoke-Git rev-parse --verify --quiet "HEAD:$($entry.FullName)").Out
                    if ("$inZip" -ne "$onGitHub") { $undone += $entry.FullName }
                }
            } finally {
                Remove-Item -LiteralPath $probe -ErrorAction SilentlyContinue
            }
            $undone += @($toDelete | Where-Object { $foreign.ContainsKey($_) -and (Invoke-Git cat-file -e "HEAD:$_").Ok })
        }
        if ($undone.Count -gt 0) {
            throw ("Su GitHub questi file sono cambiati dopo lo zip e lo zip li riporterebbe indietro: non applico niente. Rifai lo zip sul PC di sviluppo:`n  " + ($undone -join "`n  "))
        }

        $written = New-Object System.Collections.Generic.List[string]
        foreach ($entry in $entries) {
            if ($entry.FullName -in $BaseNote, $DeletedNote) { continue }
            $target = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $entry.FullName))
            New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            # The APK is a build output and git-ignores it on purpose: it lands next to the repo files, never staged.
            if ($entry.FullName -notlike '*.apk') { $written.Add($entry.FullName) }
        }
        foreach ($path in $toDelete) {
            $target = Join-Path $RepoRoot $path
            if (Test-Path -LiteralPath $target -PathType Leaf) { Remove-Item -LiteralPath $target -Force }
        }

        # Only what the zip brought is staged, so files that exist only on the hub never end up on GitHub.
        $list = Join-Path (Invoke-Git rev-parse --absolute-git-dir).Out[0] 'nexus-applica.txt'
        [System.IO.File]::WriteAllText($list, (($written -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
        try {
            $add = Invoke-Git --literal-pathspecs add "--pathspec-from-file=$list"
            if (-not $add.Ok) { throw "git add non riuscito: $($add.Err)" }
        } finally {
            Remove-Item -LiteralPath $list -ErrorAction SilentlyContinue
        }
        foreach ($path in $toDelete) {
            $rm = Invoke-Git --literal-pathspecs rm --cached --ignore-unmatch --quiet '--' $path
            if (-not $rm.Ok) { throw "git rm non riuscito per ${path}: $($rm.Err)" }
        }

        $staged = (Invoke-Git diff --cached --name-only).Out
        if ($staged.Count -gt 0) {
            $commit = Invoke-Git commit --quiet -m "NEXUS $zipVersion"
            if (-not $commit.Ok) { throw "Commit non riuscito: $($commit.Err)" }
            Write-Host "Commit NEXUS ${zipVersion}: $($staged.Count) file cambiati."
        } else {
            Write-Host 'Niente di nuovo: i file dello zip coincidono con quelli del repo.'
        }

        if ($upstream) {
            $ahead = (Invoke-Git rev-list --count '@{u}..HEAD').Out
            if ($ahead.Count -gt 0 -and [int]$ahead[0] -gt 0) {
                $push = Invoke-Git push --quiet
                $pushed = $push.Ok
                if ($pushed) {
                    Write-Host 'Push su GitHub fatto.'
                } else {
                    Write-Warning "Push non riuscito: NEXUS usa comunque la nuova versione, rilancia lo script per riprovare. $($push.Err)"
                }
            }
        }
    } finally {
        $archive.Dispose()
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

if ($staged.Count -gt 0 -and -not $NoRestart) {
    if ($staged -contains 'hub-server/requirements.txt') {
        & $VenvPython -m pip install -r (Join-Path $ServerDir 'requirements.txt') --quiet
    }
    & (Join-Path $PSScriptRoot 'riavvia_nexus.ps1')
    if (-not (Test-HubListening)) { Write-Warning 'NEXUS non risponde dopo il riavvio: prova riavvia_nexus.bat.' }
}
if (-not $pushed) { exit 2 }
